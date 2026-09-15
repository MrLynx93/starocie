package pl.starocie.domain

/**
 * Several of a day's sales read as one line, because at the stall they were one
 * thing sold over and over: ten rings at 15,00 zł each, rung up one at a time.
 *
 * Nothing is merged in the records — every [Sell] stays its own document, with its
 * own undo — so this is purely how a day is *read*, and every figure here is the sum
 * of the per-sale figures [Ledger.sellCost] and [Ledger.sellProfit] already give. The
 * row and the day's total therefore still add up to the grosz.
 */
data class SaleGroup(
    /** Newest first, in the order they were handed in. Never empty. */
    val sells: List<Sell>,
    /** The newest sale's thing — the one a tap opens. Null once it was deleted. */
    val item: Item?,
    val pieces: Int,
    val proceeds: Money,
    /** Null when these pieces have no cost we know of. */
    val cost: SellCost?,
    val profit: Money,
)

/**
 * [sells] with the repeats collapsed, keeping the order of each group's first sale.
 *
 * Sales collapse when they are the same thing said again: the same name, what one
 * piece cost us and what one piece went for both equal, and no photo that could tell
 * them apart — either none on either thing, or the very same item, a lot sold a piece
 * at a time. Both prices are compared **per piece and exactly**, as fractions, so a
 * sale of two for 30,00 zł joins one of one for 15,00 zł, and a lot of three for
 * 10,00 zł is not rounded into agreeing with a single thing at 3,33 zł.
 *
 * What a piece cost is read off the item rather than off each sale's share: a lot's
 * cost is split across its sales with largest-remainder rounding, so its pieces
 * differ from each other by a grosz on purpose, and they are still one purchase.
 *
 * A sale whose item is gone has no name to match on and stays a line of its own.
 */
fun Ledger.saleGroups(sells: List<Sell>): List<SaleGroup> {
    val grouped = LinkedHashMap<Any, MutableList<Sell>>()
    for (sell in sells) {
        val item = itemById(sell.itemId)
        val key: Any = if (item == null) sell.id else {
            val stats = itemStats(item)
            GroupKey(
                name = item.name.nameKey(),
                photo = if (item.photo == null) null else item.id,
                cost = stats.cost?.let { perPiece(it, item.quantity) },
                costIsEstimated = stats.costIsEstimated,
                price = perPiece(sell.price, sell.quantity),
            )
        }
        grouped.getOrPut(key) { mutableListOf() } += sell
    }

    return grouped.values.map { group ->
        val costs = group.map { sellCost(it) }
        SaleGroup(
            sells = group,
            item = itemById(group.first().itemId),
            pieces = group.sumOf { it.quantity },
            proceeds = group.map { it.price }.sum(),
            cost = if (costs.any { it == null }) null else SellCost(
                cost = costs.map { it!!.cost }.sum(),
                isEstimated = costs.any { it!!.isEstimated },
            ),
            profit = group.map { sellProfit(it) }.sum(),
        )
    }
}

private data class GroupKey(
    val name: String,
    /** Null for no photo; otherwise the item, since only it can share its picture. */
    val photo: String?,
    val cost: Pair<Long, Long>?,
    val costIsEstimated: Boolean,
    val price: Pair<Long, Long>,
)

/** An amount over a count of pieces, as a reduced fraction of grosze. */
private fun perPiece(money: Money, pieces: Int): Pair<Long, Long> {
    val count = pieces.coerceAtLeast(1).toLong()
    val divisor = gcd(money.minor, count).coerceAtLeast(1)
    return money.minor / divisor to count / divisor
}

private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) kotlin.math.abs(a) else gcd(b, a % b)
