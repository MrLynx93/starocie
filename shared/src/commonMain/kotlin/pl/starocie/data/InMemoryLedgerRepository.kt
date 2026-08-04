package pl.starocie.data

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
 * Holds everything in memory, which is the same shape Firestore's offline cache
 * will present. Swapping in the real implementation should not move any of this
 * logic — the event resolution and status transitions belong here either way.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class InMemoryLedgerRepository(
    private val userId: String = "local",
    private val events: CurrentEventResolver = CurrentEventResolver(),
    private val now: () -> Instant = { Clock.System.now() },
    seed: Ledger = Ledger(),
) : LedgerRepository {

    private val state = MutableStateFlow(seed)
    override val ledger: StateFlow<Ledger> = state.asStateFlow()

    /** Nothing to sync with, so nothing can fail. */
    override val syncError: StateFlow<String?> = MutableStateFlow(null).asStateFlow()

    override suspend fun recordBuy(price: Money?, name: String?, items: List<DraftItem>): String {
        val at = now()
        // The buy inherits the item's date, so the two can never disagree.
        val date = items.firstOrNull()?.date
        val eventId = ensureEvent(at)
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
        val newItems = items.map { it.toItem(buyId = buyId, at = at) }

        state.update { it.copy(buys = it.buys + buy, items = it.items + newItems) }
        return buyId
    }

    override suspend fun createBuy(price: Money?, name: String?, date: LocalDate?): String {
        val at = now()
        val eventId = ensureEvent(at)
        val buy = Buy(
            id = newId(),
            eventId = eventId,
            date = date ?: events.dateOf(at),
            price = price,
            name = name?.takeIf { it.isNotBlank() },
            createdBy = userId,
            createdAt = at,
            updatedAt = at,
        )
        state.update { it.copy(buys = it.buys + buy) }
        return buy.id
    }

    override suspend fun addItem(buyId: String?, draft: DraftItem): String {
        val at = now()
        ensureEvent(at)
        val item = draft.toItem(buyId = buyId, at = at)
        state.update { it.copy(items = it.items + item) }
        return item.id
    }

    override suspend fun recordSell(
        itemId: String,
        price: Money,
        note: String?,
        soldCompletely: Boolean,
    ) {
        val at = now()
        val eventId = ensureEvent(at)

        val sell = Sell(
            id = newId(),
            itemId = itemId,
            eventId = eventId,
            date = events.dateOf(at),
            price = price,
            note = note?.takeIf { it.isNotBlank() },
            soldCompletely = soldCompletely,
            createdBy = userId,
            createdAt = at,
            updatedAt = at,
        )

        state.update { current ->
            current.copy(
                sells = current.sells + sell,
                // A non-splittable item is closed by its only sell; a splittable lot
                // stays in stock until one is marked as completing it.
                items = current.items.map { item ->
                    if (item.id == itemId && soldCompletely) {
                        item.copy(status = ItemStatus.SOLD, updatedAt = at)
                    } else {
                        item
                    }
                },
            )
        }
    }

    override suspend fun recordBuyAndSell(
        paid: Money?,
        draft: DraftItem,
        price: Money,
        soldCompletely: Boolean,
    ): String {
        val at = now()
        val eventId = ensureEvent(at)

        // A stated price gets a buy of its own, holding only this item, so its cost
        // is exact. No price means no buy at all, which keeps the cost unknown.
        val buy = paid?.let {
            Buy(
                id = newId(),
                eventId = eventId,
                date = events.dateOf(at),
                price = it,
                createdBy = userId,
                createdAt = at,
                updatedAt = at,
            )
        }
        val item = draft.toItem(buyId = buy?.id, at = at).copy(
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

        state.update {
            it.copy(
                buys = if (buy == null) it.buys else it.buys + buy,
                items = it.items + item,
                sells = it.sells + sell,
            )
        }
        return item.id
    }

    override suspend fun removeItem(itemId: String) {
        val at = now()
        state.update { current ->
            current.copy(
                items = current.items.map {
                    if (it.id == itemId) it.copy(status = ItemStatus.REMOVED, updatedAt = at) else it
                },
            )
        }
    }

    override suspend fun nameEvent(eventId: String, name: String) {
        val at = now()
        state.update { current ->
            current.copy(
                events = current.events.map {
                    if (it.id == eventId) {
                        it.copy(name = name.takeIf { n -> n.isNotBlank() }, updatedAt = at)
                    } else {
                        it
                    }
                },
            )
        }
    }

    /** Finds today's event or creates it. The id is the date, so this is idempotent. */
    private fun ensureEvent(at: Instant): String {
        val date = events.dateOf(at)
        val id = events.eventIdFor(date)

        state.update { current ->
            if (current.events.any { it.id == id }) {
                current
            } else {
                current.copy(
                    events = current.events + Event(
                        id = id,
                        date = date,
                        createdBy = userId,
                        createdAt = at,
                        updatedAt = at,
                    ),
                )
            }
        }
        return id
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
}
