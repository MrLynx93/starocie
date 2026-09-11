package pl.starocie.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import pl.starocie.domain.DraftItem
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.Money

/**
 * Taking back a sale that should never have been recorded — the wrong row tapped at
 * the stall, or a buyer who changed their mind after the button was pressed.
 *
 * The sale goes and its pieces come back. What has to be pinned down is where the
 * item lands afterwards, and what the undo must leave exactly as it was.
 */
class UndoingASaleTest {

    @Test
    fun undoing_the_only_sale_puts_the_thing_back_in_stock() = runTest {
        val repository = InMemoryLedgerRepository()
        val buyId = repository.recordBuy(
            price = Money(2000),
            name = null,
            items = listOf(DraftItem(name = "lampa")),
        )
        val itemId = repository.ledger.value.items.single().id
        repository.recordSell(itemId, price = Money(5000))
        val sellId = repository.ledger.value.sellsOfItem(itemId).single().id

        repository.undoSell(sellId)

        val ledger = repository.ledger.value
        assertEquals(ItemStatus.IN_STOCK, ledger.itemById(itemId)!!.status)
        assertTrue(ledger.sells.isEmpty())
        assertEquals(Money(2000), ledger.buyById(buyId)!!.price, "what we paid is untouched")
        assertTrue(ledger.sellingSessions().isEmpty(), "a day whose only sale never happened is no giełda")
    }

    /** It was in our hands the whole time, so the magazyn is where it is — cost and all. */
    @Test
    fun a_thing_recorded_at_the_point_of_sale_comes_back_with_its_buy() = runTest {
        val repository = InMemoryLedgerRepository()
        val itemId = repository.recordBuyAndSell(
            paid = Money(2000),
            draft = DraftItem(name = "wazon"),
            price = Money(3000),
        )
        val sellId = repository.ledger.value.sellsOfItem(itemId).single().id

        repository.undoSell(sellId)

        val ledger = repository.ledger.value
        val item = ledger.itemById(itemId)!!
        assertEquals(ItemStatus.IN_STOCK, item.status)
        assertEquals(Money(2000), ledger.itemStats(item).cost)
    }

    @Test
    fun undoing_one_sale_of_a_lot_gives_back_only_its_pieces() = runTest {
        val repository = InMemoryLedgerRepository()
        val itemId = repository.addItem(buyId = null, draft = DraftItem(name = "talerze", quantity = 12))
        repository.recordSell(itemId, price = Money(4500), quantity = 3)
        repository.recordSell(itemId, price = Money(3000), quantity = 2)
        val wrong = repository.ledger.value.sellsOfItem(itemId).first()

        repository.undoSell(wrong.id)

        val ledger = repository.ledger.value
        val item = ledger.itemById(itemId)!!
        assertEquals(ItemStatus.IN_STOCK, item.status)
        assertEquals(10, ledger.piecesLeft(item))
        assertEquals(Money(3000), ledger.itemStats(item).proceeds, "the other sale still stands")
    }

    @Test
    fun undoing_the_sale_that_took_the_last_pieces_reopens_the_lot() = runTest {
        val repository = InMemoryLedgerRepository()
        val itemId = repository.addItem(buyId = null, draft = DraftItem(name = "kubki", quantity = 3))
        repository.recordSell(itemId, price = Money(1000), quantity = 1)
        repository.recordSell(itemId, price = Money(2000), quantity = 2)
        assertEquals(ItemStatus.SOLD, repository.ledger.value.itemById(itemId)!!.status)

        repository.undoSell(repository.ledger.value.sellsOfItem(itemId).last().id)

        val ledger = repository.ledger.value
        val item = ledger.itemById(itemId)!!
        assertEquals(ItemStatus.IN_STOCK, item.status)
        assertEquals(2, ledger.piecesLeft(item))
    }

    /** "The rest is not coming back" was said on a sale that is still there to say it. */
    @Test
    fun a_lot_closed_by_a_sale_that_remains_stays_sold() = runTest {
        val repository = InMemoryLedgerRepository()
        val itemId = repository.addItem(buyId = null, draft = DraftItem(name = "talerze", quantity = 12))
        repository.recordSell(itemId, price = Money(3000), quantity = 2)
        repository.recordSell(itemId, price = Money(4500), quantity = 3)
        repository.markSoldOut(itemId)

        repository.undoSell(repository.ledger.value.sellsOfItem(itemId).first().id)

        assertEquals(ItemStatus.SOLD, repository.ledger.value.itemById(itemId)!!.status)
    }

    /**
     * What the count said before the oversell raised it is recorded nowhere, so it
     * stays raised — and with no sale left it is ours to type again.
     */
    @Test
    fun undoing_an_oversell_leaves_the_count_for_us_to_put_right() = runTest {
        val repository = InMemoryLedgerRepository()
        val itemId = repository.addItem(buyId = null, draft = DraftItem(name = "kubki", quantity = 3))
        repository.recordSell(itemId, price = Money(4000), quantity = 4)

        repository.undoSell(repository.ledger.value.sellsOfItem(itemId).single().id)

        val undone = repository.ledger.value.itemById(itemId)!!
        assertEquals(ItemStatus.IN_STOCK, undone.status)
        assertEquals(4, undone.quantity)

        repository.setQuantity(itemId, 3)

        assertEquals(3, repository.ledger.value.itemById(itemId)!!.quantity)
    }
}
