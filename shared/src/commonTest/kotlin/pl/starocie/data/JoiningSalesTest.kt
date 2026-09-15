package pl.starocie.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import pl.starocie.domain.DraftItem
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.Money

/**
 * The same thing sold again on the same day is one sale of several pieces, written
 * that way — not a pile of identical records the day has to be read through.
 */
class JoiningSalesTest {

    private suspend fun InMemoryLedgerRepository.sellRing(
        price: Long = 1500,
        paid: Long? = null,
        photo: String? = null,
        name: String = "Pierścionek",
    ) = recordBuyAndSell(
        paid = paid?.let(::Money),
        draft = DraftItem(name = name, photo = photo),
        price = Money(price),
    )

    @Test
    fun ten_rings_rung_up_one_at_a_time_are_one_sale_of_ten() = runTest {
        val repository = InMemoryLedgerRepository()
        repeat(10) { repository.sellRing() }

        val ledger = repository.ledger.value
        val item = ledger.items.single()
        val sell = ledger.sells.single()
        assertEquals(10, item.quantity)
        assertEquals(ItemStatus.SOLD, item.status)
        assertEquals(10, sell.quantity)
        assertEquals(Money(15000), sell.price)
        assertTrue(ledger.buys.isEmpty(), "an unknown cost stays unknown however many there are")
        assertEquals(Money(15000), ledger.eventStats(ledger.events.single()).profit)
    }

    @Test
    fun a_stated_cost_grows_the_one_buy() = runTest {
        val repository = InMemoryLedgerRepository()
        repeat(3) { repository.sellRing(paid = 500) }

        val ledger = repository.ledger.value
        assertEquals(Money(1500), ledger.buys.single().price)
        assertEquals(Money(3000), ledger.itemStats(ledger.items.single()).profit)
    }

    @Test
    fun a_different_price_cost_or_a_photo_keeps_its_own_record() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.sellRing()
        repository.sellRing(price = 2000)
        repository.sellRing(paid = 500)
        repository.sellRing(photo = "jpeg")
        repository.sellRing(name = "Naszyjnik")

        assertEquals(5, repository.ledger.value.items.size)
    }

    @Test
    fun names_join_whatever_their_case() = runTest {
        val repository = InMemoryLedgerRepository()
        repository.sellRing(name = "Pierścionek")
        repository.sellRing(name = "pierścionek ")

        assertEquals(2, repository.ledger.value.sells.single().quantity)
    }

    @Test
    fun the_same_ring_on_another_day_is_another_sale() = runTest {
        var clock = Instant.fromEpochSeconds(1_785_000_000)
        val repository = InMemoryLedgerRepository(now = { clock })
        repository.sellRing()
        clock += 1.days
        repository.sellRing()

        assertEquals(2, repository.ledger.value.sells.size)
    }

    @Test
    fun a_lot_sold_a_piece_at_a_time_grows_one_sale() = runTest {
        val repository = InMemoryLedgerRepository()
        val itemId = repository.addItem(buyId = null, draft = DraftItem(name = "talerze", quantity = 12))
        repeat(3) { repository.recordSell(itemId, price = Money(1500)) }
        repository.recordSell(itemId, price = Money(4000), quantity = 2)

        val sells = repository.ledger.value.sellsOfItem(itemId)
        assertEquals(listOf(3 to Money(4500), 2 to Money(4000)), sells.map { it.quantity to it.price })
        assertEquals(7, repository.ledger.value.piecesLeft(repository.ledger.value.itemById(itemId)!!))
    }

    /** Three of the ten came back, and the other seven are still sold. */
    @Test
    fun taking_back_some_pieces_shrinks_the_sale_and_returns_them_to_stock() = runTest {
        val repository = InMemoryLedgerRepository()
        repeat(10) { repository.sellRing(paid = 500) }
        val sell = repository.ledger.value.sells.single()

        repository.undoSell(sell.id, pieces = 3)

        val ledger = repository.ledger.value
        val item = ledger.items.single()
        val shrunk = ledger.sells.single()
        assertEquals(7, shrunk.quantity)
        assertEquals(Money(10500), shrunk.price)
        assertFalse(shrunk.soldCompletely)
        assertEquals(ItemStatus.IN_STOCK, item.status)
        assertEquals(3, ledger.piecesLeft(item))
        assertEquals(Money(5000), ledger.buys.single().price, "what we paid is untouched")
    }

    @Test
    fun taking_back_every_piece_deletes_the_sale() = runTest {
        val repository = InMemoryLedgerRepository()
        repeat(4) { repository.sellRing() }

        repository.undoSell(repository.ledger.value.sells.single().id, pieces = 4)

        assertTrue(repository.ledger.value.sells.isEmpty())
        assertEquals(ItemStatus.IN_STOCK, repository.ledger.value.items.single().status)
    }
}
