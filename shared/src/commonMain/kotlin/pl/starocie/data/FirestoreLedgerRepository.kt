package pl.starocie.data

import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.WriteBatch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
import kotlinx.datetime.LocalDate
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
        // Bounded, because both calls await the server and the listeners are queued
        // behind this. Offline neither ever returns, and without the timeout the
        // ledger would never emit at all. Firestore queues the write locally the
        // moment set() is called, so abandoning the wait does not lose it.
        withTimeoutOrNull(WORKSPACE_BOOTSTRAP_TIMEOUT_MS) {
            val exists = runCatching { workspace.get().exists }.getOrDefault(false)
            if (!exists) {
                runCatching { workspace.set(WorkspaceDoc(members = listOf(userId)), merge = true) }
                    .onFailure { _syncError.value = "Nie udało się otworzyć magazynu: ${it.message}" }
            }
        }
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
            _syncError.value = cause.message ?: "Nie udało się zsynchronizować"
            delay(minOf(500L shl attempt.coerceAtMost(4).toInt(), 10_000L))
            true
        }
        .stateIn(scope, SharingStarted.Eagerly, Ledger())

    override suspend fun recordBuy(price: Money?, name: String?, items: List<DraftItem>): String {
        val at = now()
        // The buy inherits the item's date, so the two can never disagree.
        val date = items.firstOrNull()?.date
        val eventId = events.eventIdFor(at)
        val buyId = newId()

        val buy = Buy(
            id = buyId,
            eventId = eventId,
            date = date ?: events.dateOf(at),
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
        }.commitDetached()

        return buyId
    }

    override suspend fun createBuy(price: Money?, name: String?, date: LocalDate?): String {
        val at = now()
        val buy = Buy(
            id = newId(),
            eventId = events.eventIdFor(at),
            date = date ?: events.dateOf(at),
            price = price,
            name = name?.takeIf { it.isNotBlank() },
            createdBy = userId,
            createdAt = at,
            updatedAt = at,
        )

        firestore.batch().apply {
            setEvent(this, at)
            set(buysRef.document(buy.id), buy.toDoc())
        }.commitDetached()

        return buy.id
    }

    override suspend fun addItem(buyId: String?, draft: DraftItem): String {
        val at = now()
        val item = draft.toItem(buyId = buyId, at = at)

        firestore.batch().apply {
            setEvent(this, at)
            set(itemsRef.document(item.id), item.toDoc())
        }.commitDetached()

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
        }.commitDetached()
    }

    /**
     * The buy, the item and the sale in a single batch — the thing existed for the
     * length of one transaction, so recording it in three would let a half-entered
     * sale survive. The item is written already resolved rather than written and
     * then updated, for the same reason.
     */
    override suspend fun recordBuyAndSell(
        paid: Money?,
        draft: DraftItem,
        price: Money,
        soldCompletely: Boolean,
    ): String {
        val at = now()
        val eventId = events.eventIdFor(at)
        val buyId = paid?.let { newId() }

        val item = draft.toItem(buyId = buyId, at = at).copy(
            status = if (soldCompletely) ItemStatus.SOLD else ItemStatus.IN_STOCK,
        )
        val sell = Sell(
            id = newId(),
            itemId = item.id,
            eventId = eventId,
            date = events.dateOf(at),
            price = price,
            soldCompletely = soldCompletely,
            createdBy = userId,
            createdAt = at,
            updatedAt = at,
        )

        firestore.batch().apply {
            setEvent(this, at)
            if (buyId != null) {
                val buy = Buy(
                    id = buyId,
                    eventId = eventId,
                    date = events.dateOf(at),
                    price = paid,
                    createdBy = userId,
                    createdAt = at,
                    updatedAt = at,
                )
                set(buysRef.document(buyId), buy.toDoc())
            }
            set(itemsRef.document(item.id), item.toDoc())
            set(sellsRef.document(sell.id), sell.toDoc())
        }.commitDetached()

        return item.id
    }

    override suspend fun removeItem(itemId: String) {
        val at = now()
        detached {
            itemsRef.document(itemId).update(
                "status" to ItemStatus.REMOVED.name,
                "updatedAt" to at.toEpochMilliseconds(),
            )
        }
    }

    override suspend fun nameEvent(eventId: String, name: String) {
        val at = now()
        detached {
            eventsRef.document(eventId).update(
                "name" to name.takeIf { it.isNotBlank() },
                "updatedAt" to at.toEpochMilliseconds(),
            )
        }
    }

    /**
     * Writes today's event with `merge`, keyed by its ISO date.
     *
     * The deterministic id is what makes this safe offline: two phones at the same
     * market both write `2026-08-01` rather than inventing separate events, and
     * merge means whichever arrives second does not clobber a name already set.
     */
    private fun setEvent(batch: WriteBatch, at: Instant) {
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

    /**
     * Commits without waiting for the server.
     *
     * Firestore applies a write to its local cache straight away and the snapshot
     * listeners fire from that cache, so the UI is already correct. `commit()` only
     * completes once the *server* acknowledges — which never happens offline.
     * Awaiting it leaves a save silently doing nothing at a market stall, which is
     * precisely the situation this app is built for.
     */
    private fun WriteBatch.commitDetached() {
        scope.launch {
            runCatching { commit() }
                .onFailure { _syncError.value = it.message ?: "Nie udało się zapisać" }
        }
    }

    private fun detached(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onFailure { _syncError.value = it.message ?: "Nie udało się zapisać" }
        }
    }

    private fun DraftItem.toItem(buyId: String?, at: Instant) = Item(
        id = newId(),
        buyId = buyId,
        date = date ?: events.dateOf(at),
        name = name.trim(),
        note = note?.takeIf { it.isNotBlank() },
        photo = photo,
        price = price,
        quantity = quantity,
        status = ItemStatus.IN_STOCK,
        createdBy = userId,
        createdAt = at,
        updatedAt = at,
    )

    /** Client-side ids, so a record exists locally the instant it is made. */
    private fun newId(): String = Uuid.random().toString()

    private companion object {
        const val WORKSPACE_BOOTSTRAP_TIMEOUT_MS = 3_000L
        const val WORKSPACES = "workspaces"
        const val EVENTS = "events"
        const val BUYS = "buys"
        const val ITEMS = "items"
        const val SELLS = "sells"
    }
}
