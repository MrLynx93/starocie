package pl.starocie.domain

import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/** An item as the user drafts it while unpacking, before it becomes an [Item]. */
data class DraftItem(
    val name: String,
    val price: Money? = null,
    val quantity: Int = 1,
    val note: String? = null,
    val photo: String? = null,
    /** When it was actually bought. Null means today. */
    val date: LocalDate? = null,
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
    suspend fun createBuy(price: Money?, name: String?, date: LocalDate? = null): String

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

    /**
     * The asking price, changed after the fact — a thing that has sat around gets
     * marked down. Null puts it back to "we do not know yet".
     */
    suspend fun setAskingPrice(itemId: String, price: Money?)

    /**
     * A Base64 JPEG replacing whatever the item had, or null to drop it.
     *
     * The photo is the picture itself, not a path to one, so this is the whole of
     * the change: it syncs like every other field and both phones see it.
     */
    suspend fun setPhoto(itemId: String, photo: String?)

    /**
     * What was paid, corrected after the fact — it is the item's buy that changes,
     * so a box's price changes for everything in it at once.
     *
     * An item with no buy gets one holding only itself, which is how a cost that
     * was unknown at the point of sale becomes exact later. Null on such an item
     * does nothing: there is no buy to blank, and inventing an empty one would turn
     * an honest unknown into a record saying we paid nothing.
     */
    suspend fun setPaidPrice(itemId: String, price: Money?)

    /**
     * Broken, lost, given away or kept — and deleted outright, not flagged.
     *
     * Its buy goes with it once the buy has nothing left in it: a buy exists to say
     * what was paid for its contents, so an empty one is only a number with nothing
     * to be the cost of. A box therefore survives until the last thing out of it is
     * deleted too.
     *
     * Note this really does erase the record: proceeds already taken against the
     * item stay in [Ledger.sells] with nothing left to resolve to, which the
     * screens show as an unknown rather than a crash.
     */
    suspend fun removeItem(itemId: String)

    suspend fun nameEvent(eventId: String, name: String)
}
