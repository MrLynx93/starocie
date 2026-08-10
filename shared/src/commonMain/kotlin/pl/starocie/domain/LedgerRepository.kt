package pl.starocie.domain

import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/** An item as the user drafts it while unpacking, before it becomes an [Item]. */
data class DraftItem(
    val name: String,
    val price: Money? = null,
    val quantity: Int = 1,
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

    /**
     * One sale against one item.
     *
     * [quantity] is how many of a lot's pieces went; one for a single thing. The
     * item resolves when its last piece goes — the count that decides that is the
     * sum over its sales, worked out here rather than trusted from the caller, so
     * two phones selling the last pieces of the same lot both reach the same answer
     * from whatever each of them has.
     *
     * Selling **more** pieces than the lot was recorded as holding raises
     * [Item.quantity] to meet the total instead of being refused. A box counted in a
     * hurry comes out short far more often than a piece appears from nowhere, and
     * the sale is how we find out — so it is the correction, not an error.
     *
     * [soldCompletely] closes the item outright regardless — the answer to "that's
     * the lot gone" when the rest was lost, kept or given away. It **defaults to
     * false**, which is not the same as saying the item stays in stock: the count
     * decides that, and for anything that was only ever one thing the count says
     * yes. A default of true would close a lot on every partial sale by a caller
     * that simply had no opinion.
     */
    suspend fun recordSell(
        itemId: String,
        price: Money,
        quantity: Int = 1,
        soldCompletely: Boolean = false,
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
     * The day we bought it, corrected after the fact — the buy forms never ask, so
     * a thing entered the evening after the market is dated a day late until
     * somebody says otherwise.
     *
     * The buy moves with the item when it holds only that item, so the two cannot
     * disagree about a purchase that was one purchase. A box keeps its own date:
     * one thing out of it being dated wrong says nothing about the rest.
     *
     * The event does not move. Grouping is [Buy.eventId]'s job alone, and a date
     * edited long afterwards must not silently reassign what was bought where.
     */
    suspend fun setBoughtDate(itemId: String, date: LocalDate)

    /**
     * What a sale went for, corrected after the fact — a price fat-fingered at the
     * stall is found later, and every figure the app shows is drawn from it.
     *
     * There is no null: a sale happened for some amount, and one we cannot name is
     * not the same kind of unknown as a cost we never paid.
     */
    suspend fun setSellPrice(sellId: String, price: Money)

    /**
     * The day a sale happened, corrected the same way — entered a day later, or
     * caught up on at the end of a weekend.
     *
     * As with [setBoughtDate] the event stays where it is: an edited date changes
     * sorting, never which day's takings the sale counts toward.
     */
    suspend fun setSellDate(sellId: String, date: LocalDate)

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
