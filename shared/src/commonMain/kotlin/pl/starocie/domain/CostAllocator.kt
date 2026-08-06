package pl.starocie.domain

/**
 * Splits a [Buy]'s price across the items it covers, in proportion to their asking
 * prices.
 *
 * Two properties are load-bearing and covered by tests:
 *
 * 1. **The shares sum to exactly the total.** Largest-remainder rounding, not naive
 *    division — otherwise a grosz goes missing and a buy's profit stops equalling
 *    the sum of its items' profits.
 * 2. **The result is deterministic.** Ties break on item id rather than list
 *    position, so two devices holding the same items in a different order still
 *    compute identical allocations.
 *
 * Asking prices are optional, so the weighting degrades: unpriced items take the
 * mean of the priced ones, and if nothing is priced the split is even.
 */
object CostAllocator {

    fun allocate(total: Money, items: List<Item>): Map<String, Money> {
        // Integer division truncates toward zero, which would break largest-remainder
        // rounding for negative totals. A negative amount paid is meaningless anyway.
        require(total.minor >= 0) { "cannot allocate a negative total: ${total.minor}" }

        if (items.isEmpty()) return emptyMap()
        if (items.size == 1) return mapOf(items[0].id to total)

        val weights = weightsFor(items)

        // Every weight is zero (all asking prices are zero): fall back to an even split.
        if (weights.sum() == 0L) return allocate(total, items.map { it.copy(price = null) })

        return largestRemainder(total, items.map { it.id }, weights)
    }

    /**
     * The rounding rule on its own, over whatever is being weighed: floor every share,
     * then hand out the leftover grosz one at a time, largest fractional remainder
     * first, ties broken on the key so two devices holding the same things in a
     * different order still agree.
     *
     * Splitting an item's cost across the sales that took its pieces needs exactly
     * this, for exactly the reason a buy does: naive division drops a grosz, and then
     * a day's profit and the item's own profit quietly disagree about the same sale.
     */
    internal fun largestRemainder(
        total: Money,
        keys: List<String>,
        weights: List<Long>,
    ): Map<String, Money> {
        require(total.minor >= 0) { "cannot allocate a negative total: ${total.minor}" }

        val weightSum = weights.sum()
        if (keys.isEmpty() || weightSum == 0L) return keys.associateWith { Money.ZERO }

        val exactNumerators = weights.map { total.minor * it }
        val floors = exactNumerators.map { it / weightSum }

        // The fractional part of each share, kept as an integer numerator over
        // weightSum so no floating point enters the calculation.
        val remainders = exactNumerators.mapIndexed { i, numerator ->
            numerator - floors[i] * weightSum
        }

        var leftover = total.minor - floors.sum()
        val shares = floors.toMutableList()

        val order = keys.indices.sortedWith(
            compareByDescending<Int> { remainders[it] }.thenBy { keys[it] },
        )
        for (index in order) {
            if (leftover <= 0) break
            shares[index] = shares[index] + 1
            leftover--
        }

        return keys.indices.associate { keys[it] to Money(shares[it]) }
    }

    /**
     * Asking price where present; the mean of the present ones where absent; all
     * equal when none are present.
     */
    private fun weightsFor(items: List<Item>): List<Long> {
        val priced = items.mapNotNull { it.price?.minor }
        if (priced.isEmpty()) return List(items.size) { 1L }

        val mean = priced.sum() / priced.size
        return items.map { it.price?.minor ?: mean }
    }
}
