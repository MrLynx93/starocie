package pl.starocie.domain

import kotlinx.coroutines.flow.StateFlow

/** An item as the user drafts it while unpacking, before it becomes an [Item]. */
data class DraftItem(
    val name: String,
    val price: Money? = null,
    val splittable: Boolean = false,
    val note: String? = null,
)

/**
 * The whole dataset, observed as one value. Screens never reach past this.
 *
 * Every write resolves the current event first, creating it if absent, so callers
 * never think about grouping.
 */
interface LedgerRepository {

    val ledger: StateFlow<Ledger>

    /**
     * Records one payment and the items it covered. A single item means its cost is
     * exactly [price]; several mean [price] is a box total to be allocated.
     */
    suspend fun recordBuy(price: Money?, name: String?, items: List<DraftItem>): String

    /** Creates an item with no buy — invented at point of sale, so cost is unknown. */
    suspend fun createLooseItem(draft: DraftItem): String

    suspend fun recordSell(
        itemId: String,
        price: Money,
        note: String? = null,
        soldCompletely: Boolean = true,
    )

    /** Broken, lost, given away or kept. Resolves the item without proceeds. */
    suspend fun removeItem(itemId: String)

    suspend fun nameEvent(eventId: String, name: String)
}
