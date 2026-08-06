package pl.starocie.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pl.starocie.domain.DraftItem
import pl.starocie.domain.Money

/**
 * Putting right what was entered wrong — a price fat-fingered while somebody waits
 * for change, a thing typed in the evening after the market and so dated a day late.
 *
 * The corrections are the sold item screen's whole purpose, and each of them has one
 * thing it must *not* touch as well as one it must.
 */
class CorrectingRecordsTest {

    @Test
    fun a_corrected_sale_price_is_what_the_profit_is_computed_from() = runTest {
        val repository = InMemoryLedgerRepository()
        val itemId = repository.recordBuyAndSell(
            paid = Money(2000),
            draft = DraftItem(name = "lampa"),
            price = Money(5000),
        )

        val sellId = repository.ledger.value.sellsOfItem(itemId).single().id
        repository.setSellPrice(sellId, Money(4000))

        val ledger = repository.ledger.value
        val stats = ledger.itemStats(ledger.itemById(itemId)!!)

        assertEquals(Money(4000), stats.proceeds)
        assertEquals(Money(2000), stats.profit)
    }

    /** The event is where the sale was made; a date typed in later is not evidence. */
    @Test
    fun a_corrected_sale_date_leaves_the_event_where_it_was() = runTest {
        val repository = InMemoryLedgerRepository()
        val itemId = repository.recordBuyAndSell(
            paid = null,
            draft = DraftItem(name = "wazon"),
            price = Money(3000),
        )

        val before = repository.ledger.value.sellsOfItem(itemId).single()
        repository.setSellDate(before.id, LocalDate(2026, 1, 2))

        val after = repository.ledger.value.sellsOfItem(itemId).single()
        assertEquals(LocalDate(2026, 1, 2), after.date)
        assertEquals(before.eventId, after.eventId)
        assertNotEquals(after.date.toString(), after.eventId, "the event did not follow the date")
    }

    /** One purchase, one date: a buy holding only this item cannot disagree with it. */
    @Test
    fun a_corrected_buy_date_moves_a_buy_that_holds_only_that_item() = runTest {
        val repository = InMemoryLedgerRepository()
        val buyId = repository.recordBuy(
            price = Money(2000),
            name = null,
            items = listOf(DraftItem(name = "lampa")),
        )
        val itemId = repository.ledger.value.items.single().id

        repository.setBoughtDate(itemId, LocalDate(2026, 3, 4))

        val ledger = repository.ledger.value
        assertEquals(LocalDate(2026, 3, 4), ledger.itemById(itemId)!!.date)
        assertEquals(LocalDate(2026, 3, 4), ledger.buyById(buyId)!!.date)
    }

    /** A box was bought once, whatever one thing out of it turns out to be dated. */
    @Test
    fun a_corrected_buy_date_leaves_a_box_alone() = runTest {
        val repository = InMemoryLedgerRepository()
        val buyId = repository.recordBuy(
            price = Money(6000),
            name = "pudło",
            items = listOf(DraftItem(name = "lampa"), DraftItem(name = "wazon")),
        )
        val boxDate = repository.ledger.value.buyById(buyId)!!.date
        val itemId = repository.ledger.value.items.first { it.name == "lampa" }.id

        repository.setBoughtDate(itemId, LocalDate(2026, 3, 4))

        val ledger = repository.ledger.value
        assertEquals(LocalDate(2026, 3, 4), ledger.itemById(itemId)!!.date)
        assertEquals(boxDate, ledger.buyById(buyId)!!.date)
    }
}
