package pl.starocie.domain

import kotlin.time.Instant

/**
 * Statistics are always computed, never stored, so nothing can drift out of sync
 * and no write has to be atomic.
 */

data class ItemStats(
    val sellCount: Int,
    val proceeds: Money,
    /** Null when the item has no buy, and therefore no knowable cost. */
    val cost: Money?,
    /** True when the cost is a share of a box rather than a measured price. */
    val costIsEstimated: Boolean,
    val profit: Money?,
    val profitIsEstimated: Boolean,
    val soldAt: Instant?,
)

data class BuyStats(
    val itemCount: Int,
    val resolvedItemCount: Int,
    val cost: Money,
    val proceeds: Money,
    val profit: Money?,
    val fullyResolved: Boolean,
)

/** Cash in and cash out — deliberately **not** profit. Never subtract these. */
data class EventStats(
    val spent: Money,
    val earned: Money,
    val buyCount: Int,
    val sellCount: Int,
    val itemsBought: Int,
    val itemsSold: Int,
)

/**
 * The whole dataset in memory, which is what makes computed statistics, name joins
 * and instant offline search all viable at two users' scale.
 */
data class Ledger(
    val events: List<Event> = emptyList(),
    val buys: List<Buy> = emptyList(),
    val items: List<Item> = emptyList(),
    val sells: List<Sell> = emptyList(),
) {
    private val buysById: Map<String, Buy> = buys.associateBy { it.id }
    private val itemsById: Map<String, Item> = items.associateBy { it.id }
    private val sellsByItem: Map<String, List<Sell>> = sells.groupBy { it.itemId }
    private val itemsByBuy: Map<String, List<Item>> =
        items.filter { it.buyId != null }.groupBy { it.buyId!! }

    /** Every item's share of its buy, computed once per buy. */
    private val allocationByItem: Map<String, Money> = buildMap {
        for (buy in buys) {
            val price = buy.price ?: continue
            val contents = itemsByBuy[buy.id].orEmpty()
            putAll(CostAllocator.allocate(price, contents))
        }
    }

    fun itemStats(item: Item): ItemStats {
        val itemSells = sellsByItem[item.id].orEmpty()
        val proceeds = itemSells.map { it.price }.sum()

        val buy = item.buyId?.let { buysById[it] }
        val siblingCount = item.buyId?.let { itemsByBuy[it]?.size } ?: 0

        // A sole item's cost is its buy's price exactly; one of many gets a share.
        val estimated = buy?.price != null && siblingCount > 1
        val cost: Money? = when {
            buy?.price == null -> null
            siblingCount <= 1 -> buy.price
            else -> allocationByItem[item.id]
        }

        return ItemStats(
            sellCount = itemSells.size,
            proceeds = proceeds,
            cost = cost,
            costIsEstimated = estimated,
            profit = cost?.let { proceeds - it },
            profitIsEstimated = estimated,
            soldAt = itemSells.filter { it.soldCompletely }.maxOfOrNull { it.createdAt },
        )
    }

    fun buyStats(buy: Buy): BuyStats {
        val contents = itemsByBuy[buy.id].orEmpty()
        val proceeds = contents
            .flatMap { sellsByItem[it.id].orEmpty() }
            .map { it.price }
            .sum()

        return BuyStats(
            itemCount = contents.size,
            resolvedItemCount = contents.count { it.status.isResolved },
            cost = buy.price ?: Money.ZERO,
            proceeds = proceeds,
            profit = buy.price?.let { proceeds - it },
            fullyResolved = contents.isNotEmpty() && contents.all { it.status.isResolved },
        )
    }

    fun eventStats(event: Event): EventStats {
        val eventBuys = buys.filter { it.eventId == event.id }
        val eventSells = sells.filter { it.eventId == event.id }

        return EventStats(
            spent = eventBuys.mapNotNull { it.price }.sum(),
            earned = eventSells.map { it.price }.sum(),
            buyCount = eventBuys.size,
            sellCount = eventSells.size,
            itemsBought = eventBuys.sumOf { itemsByBuy[it.id]?.size ?: 0 },
            itemsSold = eventSells.filter { it.soldCompletely }
                .map { it.itemId }
                .distinct()
                .size,
        )
    }

    fun itemsInStock(): List<Item> = items.filter { it.status == ItemStatus.IN_STOCK }

    fun itemById(id: String): Item? = itemsById[id]

    fun buyById(id: String): Buy? = buysById[id]

    /** How many things that buy covers — one means its price is that item's cost. */
    fun itemCountOfBuy(id: String): Int = itemsByBuy[id]?.size ?: 0
}
