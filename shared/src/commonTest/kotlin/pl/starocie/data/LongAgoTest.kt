package pl.starocie.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import pl.starocie.domain.DraftItem
import pl.starocie.domain.LongAgo
import pl.starocie.domain.Money
import pl.starocie.domain.isLongAgo

/**
 * Where a thing bought before the books existed gets filed, and why it has to be
 * somewhere other than the day it was sold on.
 */
class LongAgoTest {

    @Test
    fun the_bucket_is_not_one_of_our_giełdy() = runTest {
        val repository = InMemoryLedgerRepository()

        repository.recordBuyAndSell(
            paid = Money(1000),
            draft = DraftItem(name = "kubek"),
            price = Money(1500),
        )

        val ledger = repository.ledger.value
        val bucket = ledger.eventById(LongAgo.EVENT_ID)

        assertTrue(bucket != null, "the buy needs an event to belong to")
        assertTrue(bucket.date.isLongAgo())
    }

    /**
     * Grouping is by event, never by date — so the sentinel date alone would not
     * have kept this out of today's giełda. The separate event is what does it.
     */
    @Test
    fun a_second_such_sale_reuses_the_same_bucket() = runTest {
        val repository = InMemoryLedgerRepository()

        repeat(3) {
            repository.recordBuyAndSell(
                paid = Money(500),
                draft = DraftItem(name = "coś $it"),
                price = Money(900),
            )
        }

        val ledger = repository.ledger.value

        assertEquals(1, ledger.events.count { it.id == LongAgo.EVENT_ID })
        assertEquals(3, ledger.buysOfEvent(LongAgo.EVENT_ID).size)
        assertTrue(
            ledger.buys.all { it.date.isLongAgo() },
            "every one of them predates the books",
        )
    }

    /**
     * A cost remembered after the sale is the same purchase the shortcut would have
     * filed, so it goes to the same place — typed in at the stall, it used to land on
     * today's giełda among what we bought there.
     */
    @Test
    fun a_price_typed_in_after_the_sale_is_filed_there_too() = runTest {
        val repository = InMemoryLedgerRepository()
        val itemId = repository.recordBuyAndSell(
            paid = null,
            draft = DraftItem(name = "wazon"),
            price = Money(3000),
        )

        repository.setPaidPrice(itemId, Money(1200))

        val ledger = repository.ledger.value
        val today = ledger.eventById(ledger.sellsOfItem(itemId).single().eventId)!!

        assertEquals(LongAgo.EVENT_ID, ledger.buys.single().eventId)
        assertTrue(ledger.buysOfEvent(today.id).isEmpty(), "not among what we bought that day")
        assertEquals(Money.ZERO, ledger.eventStats(today).spent)
        assertTrue(ledger.misfiledShortcutBuys().isEmpty())
    }

    /** No price paid means no buy at all, so nothing is filed anywhere. */
    @Test
    fun an_unknown_cost_files_nothing() = runTest {
        val repository = InMemoryLedgerRepository()

        repository.recordBuyAndSell(
            paid = null,
            draft = DraftItem(name = "wazon"),
            price = Money(3000),
        )

        val ledger = repository.ledger.value

        assertTrue(ledger.buys.isEmpty())
        assertTrue(ledger.events.none { it.id == LongAgo.EVENT_ID }, "nothing to file")
    }
}
