package pl.starocie.data

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import pl.starocie.domain.Buy
import pl.starocie.domain.CurrentEventResolver
import pl.starocie.domain.DraftItem
import pl.starocie.domain.Event
import pl.starocie.domain.Item
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.Ledger
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.Sell

/**
 * Firestore's offline cache is the local database: reads come from snapshot
 * listeners that fire immediately from cache, and writes land locally before they
 * reach the network. Nothing here should ever await a server round-trip.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class FirestoreLedgerRepository(
    private val firestore: FirebaseFirestore,
    workspaceId: String,
    private val userId: String,
    private val scope: CoroutineScope,
    private val events: CurrentEventResolver = CurrentEventResolver(),
    private val now: () -> Instant = { Clock.System.now() },
) : LedgerRepository {

    private val workspace = firestore.collection(WORKSPACES).document(workspaceId)
    private val eventsRef = workspace.collection(EVENTS)
    private val buysRef = workspace.collection(BUYS)
    private val itemsRef = workspace.collection(ITEMS)
    private val sellsRef = workspace.collection(SELLS)

    private val _syncError = MutableStateFlow<String?>(null)
    override val syncError: StateFlow<String?> = _syncError.asStateFlow()

    /**
     * Creates the workspace on first run, with this user as its only member. The
     * second person is added to `members` from the Firebase console.
     *
     * The read here is *denied* rather than empty when the workspace is absent,
     * because `isMember()` resolves membership by reading that very document. A
     * failed read is therefore indistinguishable from "not there yet" — so attempt
     * the create either way and let the rules reject it if this user is not
     * entitled. Treating the failure as fatal would leave the app permanently
     * unable to bootstrap itself.
     */
    private suspend fun ensureWorkspace() {
        val exists = runCatching { workspace.get().exists }.getOrDefault(false)
        if (exists) return
        runCatching { workspace.set(WorkspaceDoc(members = listOf(userId)), merge = true) }
            .onFailure { _syncError.value = "Nie można utworzyć magazynu: ${it.message}" }
    }

    /**
     * Everything, held in memory. That is what makes computed statistics, name joins
     * and instant offline search all viable at two users' scale.
     *
     * The workspace is created *before* the listeners attach. Attaching first meant
     * every listen was denied — the rules authorise a read by reading the workspace
     * document, which did not exist yet — and the rejection reached the dispatcher
     * as a fatal exception, killing the app moments after sign-in.
     */
    override val ledger: StateFlow<Ledger> = flow {
        ensureWorkspace()
        emitAll(
            combine(
                eventsRef.snapshots.map { s -> s.documents.map { it.data(EventDoc.serializer()).toDomain() } },
                buysRef.snapshots.map { s -> s.documents.map { it.data(BuyDoc.serializer()).toDomain() } },
                itemsRef.snapshots.map { s -> s.documents.map { it.data(ItemDoc.serializer()).toDomain() } },
                sellsRef.snapshots.map { s -> s.documents.map { it.data(SellDoc.serializer()).toDomain() } },
            ) { events, buys, items, sells ->
                _syncError.value = null
                Ledger(events = events, buys = buys, items = items, sells = sells)
            },
        )
    }
        .retryWhen { cause, attempt ->
            // A listen can still be rejected — the workspace create may have raced,
            // or this account may genuinely not be a member. Never let that reach
            // the dispatcher: back off, surface it, and keep trying.
            _syncError.value = cause.message ?: "Błąd synchronizacji"
            delay(minOf(500L shl attempt.coerceAtMost(4).toInt(), 10_000L))
            true
        }
        .stateIn(scope, SharingStarted.Eagerly, Ledger())

    override suspend fun recordBuy(price: Money?, name: String?, items: List<DraftItem>): String {
        val at = now()
        val eventId = events.eventIdFor(at)
        val buyId = newId()

        val buy = Buy(
            id = buyId,
            eventId = eventId,
            date = events.dateOf(at),
            price = price,
            name = name?.takeIf { it.isNotBlank() },
            createdBy = userId,
            createdAt = at,
            updatedAt = at,
        )

        // One batch: the event, the buy and every item land together or not at all.
        // A batch is used rather than a transaction because transactions require a
        // server round-trip and fail offline — exactly the market-stall case.
        firestore.batch().apply {
            setEvent(this, at)
            set(buysRef.document(buyId), buy.toDoc())
            items.forEach { draft ->
                val item = draft.toItem(buyId = buyId, at = at)
                set(itemsRef.document(item.id), item.toDoc())
            }
        }.commit()

        return buyId
    }

    override suspend fun createLooseItem(draft: DraftItem): String {
        val at = now()
        val item = draft.toItem(buyId = null, at = at)

        firestore.batch().apply {
            setEvent(this, at)
            set(itemsRef.document(item.id), item.toDoc())
        }.commit()

        return item.id
    }

    override suspend fun recordSell(
        itemId: String,
        price: Money,
        note: String?,
        soldCompletely: Boolean,
    ) {
        val at = now()
        val sell = Sell(
            id = newId(),
            itemId = itemId,
            eventId = events.eventIdFor(at),
            date = events.dateOf(at),
            price = price,
            note = note?.takeIf { it.isNotBlank() },
            soldCompletely = soldCompletely,
            createdBy = userId,
            createdAt = at,
            updatedAt = at,
        )

        firestore.batch().apply {
            setEvent(this, at)
            set(sellsRef.document(sell.id), sell.toDoc())
            if (soldCompletely) {
                update(
                    itemsRef.document(itemId),
                    "status" to ItemStatus.SOLD.name,
                    "updatedAt" to at.toEpochMilliseconds(),
                )
            }
        }.commit()
    }

    override suspend fun removeItem(itemId: String) {
        val at = now()
        itemsRef.document(itemId).update(
            "status" to ItemStatus.REMOVED.name,
            "updatedAt" to at.toEpochMilliseconds(),
        )
    }

    override suspend fun nameEvent(eventId: String, name: String) {
        val at = now()
        eventsRef.document(eventId).update(
            "name" to name.takeIf { it.isNotBlank() },
            "updatedAt" to at.toEpochMilliseconds(),
        )
    }

    /**
     * Writes today's event with `merge`, keyed by its ISO date.
     *
     * The deterministic id is what makes this safe offline: two phones at the same
     * market both write `2026-08-01` rather than inventing separate events, and
     * merge means whichever arrives second does not clobber a name already set.
     */
    private fun setEvent(batch: dev.gitlive.firebase.firestore.WriteBatch, at: Instant) {
        val date = events.dateOf(at)
        val event = Event(
            id = events.eventIdFor(date),
            date = date,
            createdBy = userId,
            createdAt = at,
            updatedAt = at,
        )
        batch.set(eventsRef.document(event.id), event.toDoc(), merge = true)
    }

    private fun DraftItem.toItem(buyId: String?, at: Instant) = Item(
        id = newId(),
        buyId = buyId,
        date = events.dateOf(at),
        name = name.trim(),
        note = note?.takeIf { it.isNotBlank() },
        price = price,
        splittable = splittable,
        status = ItemStatus.IN_STOCK,
        createdBy = userId,
        createdAt = at,
        updatedAt = at,
    )

    /** Client-side ids, so a record exists locally the instant it is made. */
    private fun newId(): String = Uuid.random().toString()

    private companion object {
        const val WORKSPACES = "workspaces"
        const val EVENTS = "events"
        const val BUYS = "buys"
        const val ITEMS = "items"
        const val SELLS = "sells"
    }
}
