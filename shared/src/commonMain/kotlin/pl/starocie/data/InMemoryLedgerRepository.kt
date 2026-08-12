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
import pl.starocie.domain.LongAgo
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

    /** The seed is here the moment this is constructed, so nothing is ever awaited. */
    override val loading: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

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
        quantity: Int,
        soldCompletely: Boolean,
    ) {
        val at = now()
        val eventId = ensureEvent(at)
        val pieces = quantity.coerceAtLeast(1)

        // Selling the last of a lot closes it, whether or not anybody said so.
        val item = state.value.itemById(itemId)
        val resolves = soldCompletely ||
            (item != null && pieces >= state.value.piecesLeft(item))

        // More pieces than the lot was recorded as holding means the record was
        // wrong, not the sale — so the sale corrects it.
        val corrected = item
            ?.let { state.value.itemStats(it).soldQuantity + pieces }
            ?.takeIf { it > item.quantity }

        val sell = Sell(
            id = newId(),
            itemId = itemId,
            eventId = eventId,
            date = events.dateOf(at),
            price = price,
            quantity = pieces,
            soldCompletely = resolves,
            createdBy = userId,
            createdAt = at,
            updatedAt = at,
        )

        state.update { current ->
            current.copy(
                sells = current.sells + sell,
                items = current.items.map {
                    if (it.id != itemId || (!resolves && corrected == null)) {
                        it
                    } else {
                        it.copy(
                            status = if (resolves) ItemStatus.SOLD else it.status,
                            quantity = corrected ?: it.quantity,
                            updatedAt = at,
                        )
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
        if (paid != null) ensureLongAgoEvent(at)

        // A stated price gets a buy of its own, holding only this item, so its cost
        // is exact. No price means no buy at all, which keeps the cost unknown.
        val buy = paid?.let {
            Buy(
                id = newId(),
                eventId = LongAgo.EVENT_ID,
                date = LongAgo.DATE,
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
            // Closing a brand-new lot in the same motion means the whole of it went;
            // otherwise the sale is one piece and the rest is still in stock.
            quantity = if (soldCompletely) item.quantity else 1,
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

    override suspend fun setAskingPrice(itemId: String, price: Money?) {
        val at = now()
        state.update { current ->
            current.copy(
                items = current.items.map {
                    if (it.id == itemId) it.copy(price = price, updatedAt = at) else it
                },
            )
        }
    }

    override suspend fun setPhoto(itemId: String, photo: String?) {
        val at = now()
        state.update { current ->
            current.copy(
                items = current.items.map {
                    if (it.id == itemId) it.copy(photo = photo, updatedAt = at) else it
                },
            )
        }
    }

    override suspend fun setPaidPrice(itemId: String, price: Money?) {
        val at = now()
        val item = state.value.items.firstOrNull { it.id == itemId } ?: return
        val buyId = item.buyId

        if (buyId != null) {
            state.update { current ->
                current.copy(
                    buys = current.buys.map {
                        if (it.id == buyId) it.copy(price = price, updatedAt = at) else it
                    },
                )
            }
            return
        }

        // Nothing paid and no buy to correct — leave the cost honestly unknown.
        if (price == null) return

        val eventId = ensureEvent(at)
        val buy = Buy(
            id = newId(),
            eventId = eventId,
            date = item.date,
            price = price,
            createdBy = userId,
            createdAt = at,
            updatedAt = at,
        )

        state.update { current ->
            current.copy(
                buys = current.buys + buy,
                items = current.items.map {
                    if (it.id == itemId) it.copy(buyId = buy.id, updatedAt = at) else it
                },
            )
        }
    }

    override suspend fun setBoughtDate(itemId: String, date: LocalDate) {
        val at = now()
        val item = state.value.itemById(itemId) ?: return
        // A buy holding only this item is this item's purchase, so it moves too; a
        // box was bought once, on its own day, whatever its contents are dated.
        val buyId = item.buyId?.takeIf { state.value.itemCountOfBuy(it) <= 1 }

        state.update { current ->
            current.copy(
                buys = current.buys.map {
                    if (it.id == buyId) it.copy(date = date, updatedAt = at) else it
                },
                items = current.items.map {
                    if (it.id == itemId) it.copy(date = date, updatedAt = at) else it
                },
            )
        }
    }

    override suspend fun setSellPrice(sellId: String, price: Money) {
        val at = now()
        state.update { current ->
            current.copy(
                sells = current.sells.map {
                    if (it.id == sellId) it.copy(price = price, updatedAt = at) else it
                },
            )
        }
    }

    override suspend fun setSellDate(sellId: String, date: LocalDate) {
        val at = now()
        // The event is left alone on purpose: it is where the sale was made, and a
        // date typed in afterwards is not evidence that it was made somewhere else.
        state.update { current ->
            current.copy(
                sells = current.sells.map {
                    if (it.id == sellId) it.copy(date = date, updatedAt = at) else it
                },
            )
        }
    }

    override suspend fun removeItem(itemId: String) {
        state.update { current ->
            val item = current.items.firstOrNull { it.id == itemId } ?: return@update current
            val remaining = current.items.filterNot { it.id == itemId }
            // The buy outlives its contents only while it still has some: an empty
            // one is a price with nothing left to be the cost of.
            val buyIsEmpty = item.buyId != null && remaining.none { it.buyId == item.buyId }

            current.copy(
                items = remaining,
                buys = if (buyIsEmpty) current.buys.filterNot { it.id == item.buyId } else current.buys,
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

    /** The bucket for purchases that predate the books. */
    private fun ensureLongAgoEvent(at: Instant) {
        state.update { current ->
            if (current.events.any { it.id == LongAgo.EVENT_ID }) {
                current
            } else {
                current.copy(
                    events = current.events + Event(
                        id = LongAgo.EVENT_ID,
                        date = LongAgo.DATE,
                        name = "Dawno temu",
                        createdBy = userId,
                        createdAt = at,
                        updatedAt = at,
                    ),
                )
            }
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
