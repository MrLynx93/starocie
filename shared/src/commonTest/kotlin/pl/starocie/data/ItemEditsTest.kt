package pl.starocie.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import pl.starocie.domain.DraftItem
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.Money

/**
 * What the item screen can change after the fact: the name, the two prices, the
 * count while nothing has gone yet, the deletion, and the closing that stands in
 * for it once something has sold.
 *
 * They all reach past the item — a price correction lands on the buy, a deletion
 * takes the buy with it once it is empty, and closing a lot deliberately leaves both
 * the buy and the sales alone — so what happens around the item is the whole of the
 * behaviour worth pinning down.
 */
class ItemEditsTest {

    @Test
    fun removing_the_only_item_of_a_buy_takes_the_buy_with_it() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.recordBuy(
            price = Money(2000),
            name = null,
            items = listOf(DraftItem(name = "lampa")),
        )

        repository.removeItem(repository.ledger.value.items.single().id)

        val ledger = repository.ledger.value
        assertTrue(ledger.items.isEmpty())
        assertTrue(ledger.buys.isEmpty(), "an empty buy is a price with nothing to be the cost of")
    }

    @Test
    fun a_box_survives_until_the_last_thing_out_of_it_is_removed() = runTest {
        val repository = InMemoryLedgerRepository()
        val buyId = repository.recordBuy(
            price = Money(6000),
            name = "pudło",
            items = listOf(DraftItem(name = "talerz"), DraftItem(name = "kubek")),
        )

        val first = repository.ledger.value.items.first()
        repository.removeItem(first.id)

        assertEquals(buyId, repository.ledger.value.buys.single().id, "one left, so the box stays")

        repository.removeItem(repository.ledger.value.items.single().id)

        assertTrue(repository.ledger.value.buys.isEmpty(), "nothing left in it, so it goes")
    }

    /** No buy to cascade to — the shortcut sale's item is alone in the world. */
    @Test
    fun removing_an_item_with_no_buy_touches_nothing_else() = runTest {
        val repository = InMemoryLedgerRepository()
        val kept = repository.recordBuy(
            price = Money(1000),
            name = null,
            items = listOf(DraftItem(name = "kubek")),
        )
        val itemId = repository.addItem(buyId = null, draft = DraftItem(name = "wazon"))

        repository.removeItem(itemId)

        val ledger = repository.ledger.value
        assertNull(ledger.itemById(itemId))
        assertEquals(kept, ledger.buys.single().id)
        assertEquals(1, ledger.items.size)
    }

    /**
     * The whole point of closing a lot rather than deleting it: a deleted item
     * strands its sales, and a stranded sale is set against no cost at all, so what
     * we paid drops out of the books and the profit rises to fill the gap.
     */
    @Test
    fun closing_a_part_sold_lot_keeps_its_buy_and_its_sales() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.recordBuy(
            price = Money(6000),
            name = null,
            items = listOf(DraftItem(name = "talerze", quantity = 12, price = Money(1500))),
        )
        val itemId = repository.ledger.value.items.single().id
        repository.recordSell(itemId, price = Money(4500), quantity = 3)

        repository.markSoldOut(itemId)

        val ledger = repository.ledger.value
        val item = ledger.itemById(itemId)!!
        val stats = ledger.itemStats(item)

        assertEquals(ItemStatus.SOLD, item.status, "out of the magazyn")
        assertEquals(Money(6000), ledger.buys.single().price, "the box was still paid for")
        assertEquals(Money(4500), stats.proceeds)
        assertEquals(Money(6000), stats.cost, "the nine that never went keep their share")
        assertEquals(Money(-1500), stats.profit, "sold three out of twelve at a loss")
        assertEquals(12, item.quantity, "never decremented — what is left is derived")
    }

    /** The closing statement belongs to the sale that turned out to be the last. */
    @Test
    fun closing_a_lot_marks_its_latest_sale_as_the_end_of_it() = runTest {
        val repository = InMemoryLedgerRepository()
        val itemId = repository.addItem(
            buyId = null,
            draft = DraftItem(name = "talerze", quantity = 12),
        )
        repository.recordSell(itemId, price = Money(1500), quantity = 1)
        repository.recordSell(itemId, price = Money(3000), quantity = 2)

        repository.markSoldOut(itemId)

        val ledger = repository.ledger.value
        val sells = ledger.sellsOfItem(itemId)
        assertEquals(listOf(false, true), sells.map { it.soldCompletely })
        assertEquals(
            sells.last().createdAt,
            ledger.itemStats(ledger.itemById(itemId)!!).soldAt,
            "the closing sale is where the sold date comes from",
        )
    }

    /**
     * Nothing sold means nothing that could have been the last sale — and such an
     * item is [InMemoryLedgerRepository.removeItem]'s business, not this one's.
     */
    @Test
    fun an_item_that_never_sold_cannot_be_closed() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.recordBuy(
            price = Money(2000),
            name = null,
            items = listOf(DraftItem(name = "lampa")),
        )
        val itemId = repository.ledger.value.items.single().id

        repository.markSoldOut(itemId)

        assertEquals(ItemStatus.IN_STOCK, repository.ledger.value.itemById(itemId)!!.status)
    }

    /**
     * A crate counted in a hurry comes out wrong, and until a piece has gone there is
     * nothing the number has to agree with — so it is simply put right.
     *
     * The buy is priced by the piece, so it follows: three at 30,00 zł corrected to
     * four is four at 30,00 zł, not three things' money spread over four.
     */
    @Test
    fun the_count_can_be_corrected_while_nothing_has_sold() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.recordBuy(
            price = Money(9000),
            name = null,
            items = listOf(DraftItem(name = "talerze", quantity = 3)),
        )
        val itemId = repository.ledger.value.items.single().id

        repository.setQuantity(itemId, 4)

        val ledger = repository.ledger.value
        assertEquals(4, ledger.itemById(itemId)!!.quantity)
        assertEquals(
            Money(12000),
            ledger.buys.single().price,
            "what one of them cost is what stays; the total is the multiplication",
        )
        assertEquals(4, ledger.piecesLeft(ledger.itemById(itemId)!!))
    }

    /** Moved and moved back lands on the price it started at, odd grosz and all. */
    @Test
    fun correcting_the_count_back_again_restores_what_we_paid() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.recordBuy(
            price = Money(10000),
            name = null,
            items = listOf(DraftItem(name = "talerze", quantity = 3)),
        )
        val itemId = repository.ledger.value.items.single().id

        repository.setQuantity(itemId, 4)
        repository.setQuantity(itemId, 3)

        assertEquals(Money(10000), repository.ledger.value.buys.single().price)
    }

    /** A box was paid for once, whatever turned out to be inside it. */
    @Test
    fun correcting_a_count_inside_a_box_leaves_the_box_price_alone() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.recordBuy(
            price = Money(6000),
            name = "pudło",
            items = listOf(
                DraftItem(name = "talerze", quantity = 3, price = Money(1000)),
                DraftItem(name = "kubek", price = Money(1000)),
            ),
        )
        val plates = repository.ledger.value.items.first { it.name == "talerze" }

        repository.setQuantity(plates.id, 12)

        val ledger = repository.ledger.value
        assertEquals(12, ledger.itemById(plates.id)!!.quantity)
        assertEquals(
            Money(6000),
            ledger.buys.single().price,
            "finding more in the box moves no money",
        )
    }

    /**
     * A single thing entered as one, which it turns out there were six of — and what
     * was typed as its price was a price per piece all along.
     */
    @Test
    fun a_single_thing_can_become_a_lot() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.recordBuy(
            price = Money(1500),
            name = null,
            items = listOf(DraftItem(name = "kubek")),
        )
        val itemId = repository.ledger.value.items.single().id

        repository.setQuantity(itemId, 6)

        val ledger = repository.ledger.value
        assertTrue(ledger.itemById(itemId)!!.splittable)
        assertEquals(Money(9000), ledger.buys.single().price)
    }

    /** Never below one: a record of no things is not a record of anything. */
    @Test
    fun the_count_never_goes_below_one() = runTest {
        val repository = InMemoryLedgerRepository()
        val itemId = repository.addItem(
            buyId = null,
            draft = DraftItem(name = "talerze", quantity = 3),
        )

        repository.setQuantity(itemId, 0)

        assertEquals(1, repository.ledger.value.itemById(itemId)!!.quantity)
    }

    /**
     * A sale was measured against the count, so moving it afterwards would move the
     * cost that sale was set against. The lot still corrects itself by being
     * oversold, which is a fact rather than a typed opinion.
     */
    @Test
    fun the_count_stops_being_ours_to_type_once_something_has_sold() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.recordBuy(
            price = Money(9000),
            name = null,
            items = listOf(DraftItem(name = "talerze", quantity = 3)),
        )
        val itemId = repository.ledger.value.items.single().id
        repository.recordSell(itemId, Money(4000), quantity = 1, soldCompletely = false)

        repository.setQuantity(itemId, 9)

        val ledger = repository.ledger.value
        assertEquals(3, ledger.itemById(itemId)!!.quantity)
        assertEquals(Money(9000), ledger.buys.single().price, "and the buy stays with it")
    }

    @Test
    fun a_stated_cost_where_there_was_none_opens_a_buy_of_its_own() = runTest {
        val repository = InMemoryLedgerRepository()
        val itemId = repository.addItem(buyId = null, draft = DraftItem(name = "wazon"))

        repository.setPaidPrice(itemId, Money(1200))

        val ledger = repository.ledger.value
        val item = ledger.itemById(itemId)!!
        val stats = ledger.itemStats(item)

        assertEquals(item.buyId, ledger.buys.single().id)
        assertEquals(Money(1200), stats.cost)
        assertFalse(stats.costIsEstimated, "alone in its buy, so the cost is measured")
    }

    /** Blanking an unknown cost must not invent a buy saying we paid nothing. */
    @Test
    fun clearing_a_cost_that_was_never_stated_records_nothing() = runTest {
        val repository = InMemoryLedgerRepository()
        val itemId = repository.addItem(buyId = null, draft = DraftItem(name = "wazon"))

        repository.setPaidPrice(itemId, null)

        val ledger = repository.ledger.value
        assertTrue(ledger.buys.isEmpty())
        assertNull(ledger.itemStats(ledger.itemById(itemId)!!).cost)
    }

    @Test
    fun correcting_a_box_price_changes_it_for_everything_inside() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.recordBuy(
            price = Money(6000),
            name = "pudło",
            items = listOf(
                DraftItem(name = "talerz", price = Money(1000)),
                DraftItem(name = "kubek", price = Money(1000)),
            ),
        )
        val first = repository.ledger.value.items.first()

        repository.setPaidPrice(first.id, Money(4000))

        val ledger = repository.ledger.value
        assertEquals(Money(4000), ledger.buys.single().price)
        // Evenly priced halves, so the whole correction shows up split in two.
        assertEquals(
            listOf(Money(2000), Money(2000)),
            ledger.items.map { ledger.itemStats(it).cost },
        )
    }

    @Test
    fun a_thing_can_be_renamed() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.recordBuy(
            price = Money(1000),
            name = null,
            items = listOf(DraftItem(name = "lampa")),
        )
        val itemId = repository.ledger.value.items.single().id

        repository.nameItem(itemId, "  lampa naftowa  ")

        assertEquals(
            "lampa naftowa",
            repository.ledger.value.itemById(itemId)!!.name,
            "trimmed, the way a name typed at a stall arrives",
        )
    }

    /**
     * A name is the item's identity rather than one of its unknowns: there is no
     * "we do not know" to fall back to the way a cost has one, and it is how the
     * thing is found when somebody wants to buy it. So a cleared field is a
     * half-typed correction and the old name stands.
     */
    @Test
    fun a_blank_name_writes_nothing() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.recordBuy(
            price = Money(1000),
            name = null,
            items = listOf(DraftItem(name = "lampa")),
        )
        val itemId = repository.ledger.value.items.single().id

        repository.nameItem(itemId, "   ")

        assertEquals("lampa", repository.ledger.value.itemById(itemId)!!.name)
    }

    @Test
    fun the_asking_price_can_be_changed_and_cleared() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.recordBuy(
            price = Money(1000),
            name = null,
            items = listOf(DraftItem(name = "wazon", price = Money(5000))),
        )
        val itemId = repository.ledger.value.items.single().id

        repository.setAskingPrice(itemId, Money(3500))
        assertEquals(Money(3500), repository.ledger.value.itemById(itemId)!!.price)

        repository.setAskingPrice(itemId, null)
        assertNull(repository.ledger.value.itemById(itemId)!!.price, "back to not knowing yet")
    }
}
