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
     * True until the whole ledger has arrived once, and false ever after.
     *
     * [ledger] opens on an empty [Ledger], and an empty ledger before the first
     * snapshot is the same value as an empty ledger belonging to two people who
     * have not bought anything yet. A screen cannot tell them apart, so this says
     * which it is — without it a skeleton would shimmer forever on a magazyn that
     * is honestly empty.
     */
    val loading: StateFlow<Boolean>

    /**
     * Non-null when syncing is failing. Surfaced in the UI because the alternative
     * is an app that silently records nothing, which is indistinguishable from an
     * app that is working but empty.
     */
    val syncError: StateFlow<String?>

    /**
     * The last write the server refused, held until somebody dismisses it.
     *
     * Kept apart from [syncError] because the two end differently. A listener that
     * recovers is fixed by its next snapshot, so that error clears itself there. A
     * refused write is not fixed by anything: Firestore rolls it back, and the
     * rollback *is* the next snapshot — so an error cleared there vanished in the
     * instant it was raised, and the change quietly undid itself on screen. That is
     * how a sale taken back four times stayed exactly where it was without a word.
     */
    val writeError: StateFlow<String?>

    fun dismissWriteError()

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
     *
     * **Another sale of the same item today at the same price per piece is joined
     * rather than repeated** ([Ledger.sellToJoin]): its pieces and its price grow by
     * this sale's, so a lot sold a piece at a time over one afternoon is one sale.
     * Both grow by increments rather than being written as totals, so two phones
     * joining the same sale offline both land in it on reconnect.
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
     * **Sold whole, the same thing sold again today is joined rather than repeated**
     * ([Ledger.sellToJoinWith]) — the tenth ring rung up at 15,00 zł is not a tenth
     * item. That item's count, its buy's price and its sale's pieces and price each
     * grow by this one's, by increments, and no new document is written.
     *
     * Returns the new item's id, or the joined one's.
     */
    suspend fun recordBuyAndSell(
        paid: Money?,
        draft: DraftItem,
        price: Money,
        soldCompletely: Boolean = true,
    ): String

    /**
     * What the thing is called, put right after the fact.
     *
     * A name is typed while somebody is holding the thing and waiting to be paid,
     * so it comes out as "lampa" or as a thumb's worth of nonsense — and it is the
     * one field the app cannot shrug at, being how the thing is found when it is
     * sold. So it is correctable everywhere the prices are.
     *
     * **A blank writes nothing.** The name is the item's identity rather than one
     * of its unknowns: there is no "we do not know" to fall back to the way a cost
     * has one, and clearing the field is a half-typed correction rather than an
     * answer. The old name stands until a new one is typed.
     */
    suspend fun nameItem(itemId: String, name: String)

    /**
     * The asking price, changed after the fact — a thing that has sat around gets
     * marked down. Null puts it back to "we do not know yet".
     */
    suspend fun setAskingPrice(itemId: String, price: Money?)

    /**
     * How many pieces the record covers, corrected while nothing has gone yet.
     *
     * A count typed at a stall comes out wrong the same way a price does — a crate
     * counted in a hurry, or a lot entered as the one thing it looked like — and
     * until something has sold there is nothing the number has to agree with, so it
     * can simply be put right.
     *
     * **Once a piece has gone it is no longer this operation's to change**, and the
     * call does nothing: what has been sold is measured against the count, so moving
     * it would move the cost every one of those sales was set against. The lot still
     * corrects itself the one way it always has — selling more pieces than the record
     * holds raises it to meet them, which is a fact rather than a typed opinion.
     *
     * **The buy follows, where that buy holds only this item.** The price there is
     * per piece — what one of them cost is the figure somebody remembers, and the
     * total is the multiplication — so a lot of three corrected to four is four at
     * that same money, not the same money spread thinner. A count and a price that
     * disagreed about how many pieces they covered would be a cost per piece nobody
     * ever paid.
     *
     * A box is the exception, and it is left alone: it was paid for once, whatever
     * turned out to be inside, so finding a fourth plate in it moves no money. The
     * shares of it simply redistribute.
     *
     * The oversell moves no money either, and the two do not contradict each other:
     * nobody typed a price there, the money left the hand long ago, and what the sale
     * discovers is that the total covered more pieces than we wrote down. Here a
     * count is typed beside a price per piece, so the price is the anchor instead.
     *
     * Never below one, and refused where the count would be a guess: a blank is a
     * half-typed number rather than an answer, the way a blank sale price is.
     */
    suspend fun setQuantity(itemId: String, quantity: Int)

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
     * A sale that should never have been recorded — the wrong row tapped at the
     * stall, or a buyer who changed their mind after the button was pressed. The sale
     * goes, and the pieces it took come back.
     *
     * Deleted rather than flagged: a sale that did not happen has nothing left to
     * say, and every figure it fed is computed, so the item, the day and the totals
     * simply stop counting it. Nothing is written into today, and the event stays
     * where it is — it may hold other things, and an empty day is harmless.
     *
     * The item goes back into stock unless the sales that remain still close it (see
     * [Ledger.statusAfterUndoing]). That includes a thing recorded at the point of
     * sale: it was in our hands, so the magazyn is where it is, with its buy.
     *
     * [Item.quantity] is left alone, even when this sale was the oversell that raised
     * it: what it said before is recorded nowhere, and a guess would be a count nobody
     * typed. With no sale left against it [setQuantity] takes corrections again, so
     * the item screen can put it right.
     *
     * [pieces] takes back only some of a sale of several — the buyer who returned
     * three of the ten rings. The sale stays with its pieces and its price reduced by
     * that share ([Sell.priceOf]), by increments, and the pieces come back to stock;
     * null, or all of them, deletes it as above.
     */
    suspend fun undoSell(sellId: String, pieces: Int? = null)

    /**
     * The rest of a lot is not coming back — kept, lost, given away or simply not
     * worth carrying home. The item closes; nothing about it is erased.
     *
     * This is what [removeItem] would have to be for something that has already
     * sold: deleting it leaves its sales with nothing to resolve to, and an
     * unresolvable sale is set against no cost at all, so money we really did pay
     * would drop out of the books and past profit would rise to meet it. Here the
     * buy and every sale stay exactly where they are, and the pieces that never went
     * keep their share of what the lot cost — which is a loss, and the honest
     * reading of a box we sold three things out of and threw the rest away.
     *
     * It also writes [Sell.soldCompletely] on the latest sale, which is the same
     * record the sell dialog's own "Sprzedaliśmy już wszystkie" leaves behind — the
     * statement belongs to the sale that turned out to be the last one, and it is
     * where [ItemStats.soldAt] reads the closing date from.
     *
     * Nothing happens to an item that has never sold: with no sale to be the last
     * one, "we have already sold everything" is not a thing that can be said, and
     * [removeItem] is what such an item is for.
     */
    suspend fun markSoldOut(itemId: String)

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
     * screens show as an unknown rather than a crash. That is tolerable for a thing
     * that never sold and wrong for one that did, which is why anything with a sale
     * against it closes with [markSoldOut] instead.
     */
    suspend fun removeItem(itemId: String)

    suspend fun nameEvent(eventId: String, name: String)
}
