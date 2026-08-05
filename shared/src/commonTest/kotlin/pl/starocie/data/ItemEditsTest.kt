package pl.starocie.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import pl.starocie.domain.DraftItem
import pl.starocie.domain.Money

/**
 * What the item screen can change after the fact: the two prices, and the deletion.
 *
 * Both reach past the item — a price correction lands on the buy, and a deletion
 * takes the buy with it once it is empty — so what happens to that buy is the
 * whole of the behaviour worth pinning down.
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
