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
        val weightSum = weights.sumOf { it }

        // Every weight is zero (all asking prices are zero): fall back to an even split.
        if (weightSum == 0L) return allocate(total, items.map { it.copy(price = null) })

        val exactNumerators = weights.map { total.minor * it }
        val floors = exactNumerators.map { it / weightSum }

        // The fractional part of each share, kept as an integer numerator over
        // weightSum so no floating point enters the calculation.
        val remainders = exactNumerators.mapIndexed { i, numerator ->
            numerator - floors[i] * weightSum
        }

        var leftover = total.minor - floors.sum()
        val shares = floors.toMutableList()

        val order = items.indices.sortedWith(
            compareByDescending<Int> { remainders[it] }.thenBy { items[it].id },
        )
        for (index in order) {
            if (leftover <= 0) break
            shares[index] = shares[index] + 1
            leftover--
        }

        return items.indices.associate { items[it].id to Money(shares[it]) }
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
