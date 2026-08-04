package pl.starocie.domain

import kotlinx.coroutines.flow.StateFlow

/** An item as the user drafts it while unpacking, before it becomes an [Item]. */
data class DraftItem(
    val name: String,
    val price: Money? = null,
    val quantity: Int = 1,
    val note: String? = null,
    val photo: String? = null,
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
     * Non-null when syncing is failing. Surfaced in the UI because the alternative
     * is an app that silently records nothing, which is indistinguishable from an
     * app that is working but empty.
     */
    val syncError: StateFlow<String?>

    /**
     * Records one payment and the items it covered. A single item means its cost is
     * exactly [price]; several mean [price] is a box total to be allocated.
     */
    suspend fun recordBuy(price: Money?, name: String?, items: List<DraftItem>): String

    /**
     * Opens a buy with no contents yet. Used by the box flow: the price is known
     * before the contents are, and items are appended as they come out of the box.
     */
    suspend fun createBuy(price: Money?, name: String?): String

    /**
     * Adds one item, optionally to an existing buy. A null [buyId] means the item
     * was invented at point of sale, so its cost is unknown.
     */
    suspend fun addItem(buyId: String?, draft: DraftItem): String

    suspend fun recordSell(
        itemId: String,
        price: Money,
        note: String? = null,
        soldCompletely: Boolean = true,
    )

    /**
     * One thing bought and sold in the same motion, having never been in stock —
     * the common case while nothing has been recorded yet, and at a stall where
     * there was no time to enter it beforehand.
     *
     * [paid] is optional and null is a real answer: the item then gets no buy and
     * its cost stays honestly unknown. A stated price opens a buy holding only this
     * item, which makes its cost exact rather than an allocated share.
     *
     * Returns the new item's id.
     */
    suspend fun recordBuyAndSell(
        paid: Money?,
        draft: DraftItem,
        price: Money,
        soldCompletely: Boolean = true,
    ): String

    /** Broken, lost, given away or kept. Resolves the item without proceeds. */
    suspend fun removeItem(itemId: String)

    suspend fun nameEvent(eventId: String, name: String)
}
