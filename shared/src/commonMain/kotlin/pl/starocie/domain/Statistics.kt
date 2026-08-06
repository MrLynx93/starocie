package pl.starocie.domain

import kotlin.time.Instant

/**
 * Statistics are always computed, never stored, so nothing can drift out of sync
 * and no write has to be atomic.
 */

data class ItemStats(
    val sellCount: Int,
    /**
     * How many pieces have gone, summed across the sales rather than counted down
     * on the item — the item's own `quantity` is what it always was.
     */
    val soldQuantity: Int,
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

/**
 * A day's figures.
 *
 * [spent] and [earned] are cash out and cash in, and **never** to be subtracted from
 * each other: what we sell at a giełda is rarely what we bought there, so their
 * difference is not profit and nothing may present it as one.
 *
 * [profit] is the real thing, and it is not that subtraction: it is each sale of the
 * day set against what the pieces it took had cost, item by item, which is the same
 * arithmetic [ItemStats.profit] does one thing at a time.
 */
data class EventStats(
    val spent: Money,
    val earned: Money,
    val buyCount: Int,
    val sellCount: Int,
    /** Pieces that came in — a lot counts as the many things it is. */
    val itemsBought: Int,
    /** Pieces that went out, which is what [earned] is the price of. */
    val itemsSold: Int,
    /**
     * What the day's sales made over what those things cost. Sales whose cost we do
     * not know are left out of it entirely rather than counted as pure gain —
     * [sellsOfUnknownCost] says how many, so a screen can admit the gap.
     */
    val profit: Money,
    val profitIsEstimated: Boolean,
    val sellsOfUnknownCost: Int,
)

/** What the pieces one sale took had cost us. */
data class SellCost(val cost: Money, val isEstimated: Boolean)

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

    /**
     * What one item cost us, and whether that figure is a guess.
     *
     * A sole item's cost is its buy's price exactly; one of many gets a share. Null
     * when there is no buy behind it, which is a genuine unknown and never a zero.
     */
    private fun costOf(item: Item): Pair<Money?, Boolean> {
        val buy = item.buyId?.let { buysById[it] }
        val siblingCount = item.buyId?.let { itemsByBuy[it]?.size } ?: 0

        val estimated = buy?.price != null && siblingCount > 1
        val cost: Money? = when {
            buy?.price == null -> null
            siblingCount <= 1 -> buy.price
            else -> allocationByItem[item.id]
        }
        return cost to estimated
    }

    /**
     * Each sale's share of what its item cost, split across the item's pieces.
     *
     * A whole thing gone in one sale carries its whole cost, exactly. A lot going a
     * few pieces at a time splits that cost by pieces — including the pieces still
     * unsold, which hold their share back rather than loading it onto whatever went
     * first. The split uses the same largest-remainder rounding a box does, so the
     * shares of a fully sold lot come to exactly its cost and a day's profit agrees
     * with the item's own to the grosz.
     */
    private val sellCostById: Map<String, SellCost> = buildMap {
        for (item in items) {
            val itemSells = sellsByItem[item.id].orEmpty()
            if (itemSells.isEmpty()) continue
            val (cost, estimated) = costOf(item)
            if (cost == null) continue

            val gone = itemSells.sumOf { it.quantity }
            if (itemSells.size == 1 && gone >= item.quantity) {
                put(itemSells[0].id, SellCost(cost, estimated))
                continue
            }

            // What has gone outranks what the record says, so a lot that turned out
            // to hold more pieces than we counted still splits across all of them.
            val pieces = maxOf(item.quantity, gone, 1)
            // The empty key is the pieces nobody has bought yet; no document id is
            // empty, so it cannot collide with a sale.
            val shares = CostAllocator.largestRemainder(
                total = cost,
                keys = itemSells.map { it.id } + "",
                weights = itemSells.map { it.quantity.toLong() } + (pieces - gone).toLong(),
            )
            for (sell in itemSells) {
                put(sell.id, SellCost(shares.getValue(sell.id), isEstimated = true))
            }
        }
    }

    fun itemStats(item: Item): ItemStats {
        val itemSells = sellsByItem[item.id].orEmpty()
        val proceeds = itemSells.map { it.price }.sum()
        val (cost, estimated) = costOf(item)

        return ItemStats(
            sellCount = itemSells.size,
            soldQuantity = itemSells.sumOf { it.quantity },
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

    /**
     * What one sale's pieces had cost, or null when there is nothing to measure it
     * against: the item has no buy, or no longer exists at all — deleting a thing
     * leaves its sales unresolvable on purpose, the proceeds still counting.
     */
    fun sellCost(sell: Sell): SellCost? = sellCostById[sell.id]

    fun eventStats(event: Event): EventStats {
        val eventBuys = buys.filter { it.eventId == event.id }
        val eventSells = sells.filter { it.eventId == event.id }

        // Sale by sale against its own cost — never earned minus spent, which is two
        // unrelated days' worth of money and would read as profit while being no
        // such thing.
        var profit = Money.ZERO
        var estimated = false
        var unknown = 0
        for (sell in eventSells) {
            val cost = sellCost(sell)
            if (cost == null) {
                unknown++
                continue
            }
            profit += sell.price - cost.cost
            estimated = estimated || cost.isEstimated
        }

        return EventStats(
            spent = eventBuys.mapNotNull { it.price }.sum(),
            earned = eventSells.map { it.price }.sum(),
            buyCount = eventBuys.size,
            sellCount = eventSells.size,
            itemsBought = eventBuys.sumOf { buy ->
                itemsByBuy[buy.id].orEmpty().sumOf { it.quantity }
            },
            itemsSold = eventSells.sumOf { it.quantity },
            profit = profit,
            profitIsEstimated = estimated,
            sellsOfUnknownCost = unknown,
        )
    }

    /** Everything one buy covers, and everything bought at one event. */
    fun itemsOfBuy(id: String): List<Item> = itemsByBuy[id].orEmpty()

    fun buysOfEvent(id: String): List<Buy> = buys.filter { it.eventId == id }

    fun sellsOfEvent(id: String): List<Sell> = sells.filter { it.eventId == id }

    fun eventById(id: String): Event? = events.firstOrNull { it.id == id }

    fun itemsInStock(): List<Item> = items.filter { it.status == ItemStatus.IN_STOCK }

    /**
     * How many of a lot's pieces have not gone yet.
     *
     * Never below zero and never below one while the item is still in stock: an old
     * record whose sales predate [Sell.quantity] reads as one piece each, so a lot
     * that really did go a few at a time can look oversold. Something still sitting
     * in the magazyn must stay sellable, and the arithmetic must not be what stops
     * it.
     */
    fun piecesLeft(item: Item): Int {
        val gone = sellsByItem[item.id].orEmpty().sumOf { it.quantity }
        val left = item.quantity - gone
        return if (item.status == ItemStatus.IN_STOCK) left.coerceAtLeast(1) else left.coerceAtLeast(0)
    }

    fun itemById(id: String): Item? = itemsById[id]

    /**
     * Everything one item went out on, oldest first — the sales themselves rather
     * than the total, which is what a screen correcting one of them needs.
     */
    fun sellsOfItem(id: String): List<Sell> =
        sellsByItem[id].orEmpty().sortedBy { it.createdAt }

    fun buyById(id: String): Buy? = buysById[id]

    /** How many things that buy covers — one means its price is that item's cost. */
    fun itemCountOfBuy(id: String): Int = itemsByBuy[id]?.size ?: 0
}
