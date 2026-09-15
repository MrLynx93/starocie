package pl.starocie.domain

/**
 * Where a sale being recorded joins one already written, rather than becoming a
 * document of its own.
 *
 * Ten rings rung up one at a time at 15,00 zł are one sale of ten pieces for
 * 150,00 zł: that is how the day is read, and it is how the thing's own screen can
 * correct all ten at once. Both repositories ask here, so they cannot disagree about
 * what joins what.
 *
 * Only a sale on the **same event** joins — a sale belongs to exactly one day — and
 * only at the **same price per piece**, compared exactly, so two for 30,00 zł joins
 * one for 15,00 zł and nothing is rounded into agreeing.
 */

/** Another sale of the same thing today, at the same price per piece. */
fun Ledger.sellToJoin(itemId: String, eventId: String, price: Money, pieces: Int): Sell? =
    sellsOfEvent(eventId).firstOrNull {
        it.itemId == itemId && samePerPiece(it.price, it.quantity, price, pieces)
    }

/**
 * A thing never recorded, sold whole, that is the same as something already sold
 * today — so it is more pieces of that thing rather than a thing of its own.
 *
 * Same means: the name (trimmed, any case), what one piece went for, what one piece
 * cost, and no photo on either — a picture is the one thing that could say they were
 * different. The cost must agree the same way the price does: both unknown, or both
 * stated. A stated one only joins a buy the shortcut sale itself filed under "Dawno
 * temu" and holding only that thing, because adding pieces to a purchase made at a
 * real giełda would put them in that day's spend, and a box was paid for once.
 *
 * The thing joined must be sold already, so that adding pieces sold in the same
 * breath leaves exactly what was left of it before — nothing, or whatever a closed
 * lot wrote off.
 */
fun Ledger.sellToJoinWith(eventId: String, draft: DraftItem, paid: Money?, price: Money): Sell? {
    if (draft.photo != null) return null
    val pieces = draft.quantity.coerceAtLeast(1)
    val name = draft.name.nameKey()

    return sellsOfEvent(eventId).firstOrNull { sell ->
        val item = itemById(sell.itemId) ?: return@firstOrNull false
        if (item.photo != null || item.status != ItemStatus.SOLD) return@firstOrNull false
        if (item.name.nameKey() != name) return@firstOrNull false
        if (!samePerPiece(sell.price, sell.quantity, price, pieces)) return@firstOrNull false

        val buyId = item.buyId ?: return@firstOrNull paid == null
        val buy = buyById(buyId) ?: return@firstOrNull false
        paid != null && buy.price != null &&
            buy.eventId == LongAgo.EVENT_ID &&
            itemCountOfBuy(buyId) == 1 &&
            samePerPiece(buy.price, item.quantity, paid, pieces)
    }
}

/**
 * What [pieces] of this sale went for — its share of the price, rounded to the
 * grosz. All of them is the price exactly.
 */
fun Sell.priceOf(pieces: Int): Money = price.atSameRate(was = quantity, now = pieces)

/** A name as two of them are compared: "Pierścionek " and "pierścionek" are one. */
internal fun String.nameKey(): String = trim().lowercase()

private fun samePerPiece(a: Money, aPieces: Int, b: Money, bPieces: Int): Boolean =
    a.minor * bPieces.coerceAtLeast(1) == b.minor * aPieces.coerceAtLeast(1)
