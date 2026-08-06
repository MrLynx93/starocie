package pl.starocie.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import pl.starocie.domain.DraftItem
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.LongAgo
import pl.starocie.domain.Money

/**
 * Selling a thing that was never recorded — the shortcut the app exists to
 * tolerate, and the one most likely to make the numbers lie if it is careless.
 */
class BuyAndSellTest {

    @Test
    fun a_stated_cost_is_exact_because_the_item_is_alone_in_its_buy() = runTest {
        val repository = InMemoryLedgerRepository()

        val itemId = repository.recordBuyAndSell(
            paid = Money(2000),
            draft = DraftItem(name = "lampa"),
            price = Money(5000),
        )

        val ledger = repository.ledger.value
        val stats = ledger.itemStats(ledger.itemById(itemId)!!)

        assertEquals(Money(2000), stats.cost)
        assertFalse(stats.costIsEstimated, "its own buy is measured, not allocated")
        assertEquals(Money(3000), stats.profit)
        assertEquals(ItemStatus.SOLD, ledger.itemById(itemId)!!.status)
    }

    /** Unknown must stay unknown, or every shortcut reads as pure profit. */
    @Test
    fun no_stated_cost_leaves_the_cost_unknown_rather_than_zero() = runTest {
        val repository = InMemoryLedgerRepository()

        val itemId = repository.recordBuyAndSell(
            paid = null,
            draft = DraftItem(name = "wazon"),
            price = Money(3000),
        )

        val ledger = repository.ledger.value
        val stats = ledger.itemStats(ledger.itemById(itemId)!!)

        assertTrue(ledger.buys.isEmpty(), "no price paid means there is no buy to record")
        assertEquals(Money(3000), stats.proceeds)
        assertNull(stats.cost)
        assertNull(stats.profit, "profit is unknown, not equal to the proceeds")
    }

    @Test
    fun a_lot_sold_in_part_stays_in_stock() = runTest {
        val repository = InMemoryLedgerRepository()

        val itemId = repository.recordBuyAndSell(
            paid = Money(6000),
            draft = DraftItem(name = "talerze", quantity = 12),
            price = Money(2500),
            soldCompletely = false,
        )

        val ledger = repository.ledger.value

        assertEquals(ItemStatus.IN_STOCK, ledger.itemById(itemId)!!.status)
        assertEquals(listOf(itemId), ledger.itemsInStock().map { it.id })
        assertEquals(Money(2500), ledger.itemStats(ledger.itemById(itemId)!!).proceeds)
    }

    /**
     * The rule the count exists for: a lot closes itself when its last piece goes,
     * without anybody having to remember to say so.
     */
    @Test
    fun a_lot_leaves_stock_once_its_last_piece_is_sold() = runTest {
        val repository = InMemoryLedgerRepository()

        val itemId = repository.addItem(
            buyId = null,
            draft = DraftItem(name = "talerze", quantity = 3),
        )

        repository.recordSell(itemId = itemId, price = Money(1000), quantity = 2)

        var ledger = repository.ledger.value
        assertEquals(ItemStatus.IN_STOCK, ledger.itemById(itemId)!!.status)
        assertEquals(1, ledger.piecesLeft(ledger.itemById(itemId)!!))
        assertEquals(2, ledger.itemStats(ledger.itemById(itemId)!!).soldQuantity)

        repository.recordSell(itemId = itemId, price = Money(600), quantity = 1)

        ledger = repository.ledger.value
        assertEquals(ItemStatus.SOLD, ledger.itemById(itemId)!!.status)
        assertTrue(ledger.itemsInStock().isEmpty(), "nothing left of it to hold")
        assertEquals(3, ledger.itemStats(ledger.itemById(itemId)!!).soldQuantity)
        assertEquals(Money(1600), ledger.itemStats(ledger.itemById(itemId)!!).proceeds)
    }

    /** The rest was kept, lost or given away: the lot closes with pieces unsold. */
    @Test
    fun a_lot_can_be_closed_early_with_pieces_left_unsold() = runTest {
        val repository = InMemoryLedgerRepository()

        val itemId = repository.addItem(
            buyId = null,
            draft = DraftItem(name = "talerze", quantity = 12),
        )

        repository.recordSell(
            itemId = itemId,
            price = Money(2500),
            quantity = 3,
            soldCompletely = true,
        )

        val ledger = repository.ledger.value
        assertEquals(ItemStatus.SOLD, ledger.itemById(itemId)!!.status)
        assertEquals(3, ledger.itemStats(ledger.itemById(itemId)!!).soldQuantity)
    }

    /**
     * A box counted at a stall comes out short more often than a piece appears from
     * nowhere, so selling past the end of a lot fixes the record rather than being
     * refused by it.
     */
    @Test
    fun selling_more_pieces_than_recorded_corrects_the_lot() = runTest {
        val repository = InMemoryLedgerRepository()

        val itemId = repository.addItem(
            buyId = null,
            draft = DraftItem(name = "talerze", quantity = 6),
        )

        repository.recordSell(itemId = itemId, price = Money(1000), quantity = 2)
        // Four were expected to be left; there turn out to be five.
        repository.recordSell(itemId = itemId, price = Money(2000), quantity = 5)

        val ledger = repository.ledger.value
        val item = ledger.itemById(itemId)!!

        assertEquals(7, item.quantity, "the lot really held seven")
        assertEquals(7, ledger.itemStats(item).soldQuantity)
        assertEquals(ItemStatus.SOLD, item.status, "and all seven have now gone")
        assertEquals(0, ledger.piecesLeft(item))
    }

    /**
     * The sale happened at today's giełda; the purchase did not. Selling something
     * that was never recorded says nothing about when it was bought, so its buy is
     * filed under "dawno temu" rather than claiming this day — which would inflate
     * the day's spend and list the thing among what we carried home from it.
     */
    @Test
    fun a_never_recorded_thing_is_sold_today_but_was_bought_long_ago() = runTest {
        val repository = InMemoryLedgerRepository()

        repository.recordBuyAndSell(
            paid = Money(1000),
            draft = DraftItem(name = "kubek"),
            price = Money(1500),
        )

        val ledger = repository.ledger.value
        val today = ledger.events.first { it.id != LongAgo.EVENT_ID }

        assertEquals(today.id, ledger.sells.single().eventId, "sold at today's giełda")
        assertEquals(LongAgo.EVENT_ID, ledger.buys.single().eventId)
        assertEquals(LongAgo.DATE, ledger.buys.single().date)

        // The day's own figures must not count a purchase it did not make.
        assertEquals(Money.ZERO, ledger.eventStats(today).spent)
        assertTrue(ledger.buysOfEvent(today.id).isEmpty(), "nothing was carried home")

        // The profit still lands, because a sale is costed against its own buy.
        assertEquals(Money(500), ledger.eventStats(today).profit)
    }
}
