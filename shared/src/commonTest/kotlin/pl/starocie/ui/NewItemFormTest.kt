package pl.starocie.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pl.starocie.domain.Money

/**
 * Both prices on the "never recorded" form are typed per piece, and everything the
 * repository is handed is multiplied up from there. The multiplication is the whole
 * of what could go wrong: a lot's cost and its takings differ from what was typed by
 * however many there are, and getting the factor wrong on either side is a stall
 * selling twelve plates for the price of one.
 */
class NewItemFormTest {

    @Test
    fun a_single_thing_is_its_own_total() {
        val form = NewItemForm(name = "Waza", paidText = "10", priceText = "25")

        assertEquals(Money(1000), form.paid)
        assertEquals(Money(2500), form.price)
        assertEquals(1, form.soldQuantity)
        assertTrue(form.closesTheItem)
    }

    @Test
    fun a_lot_handed_over_whole_multiplies_both_prices() {
        val form = NewItemForm(
            name = "Talerze",
            paidText = "10",
            priceText = "15",
            quantityText = "3",
        )

        assertEquals(Money(3000), form.paid)
        assertEquals(Money(4500), form.price)
        assertEquals(3, form.soldQuantity)
    }

    /**
     * Keeping the rest sells one piece at one piece's price — but what was paid is
     * still what the whole pile cost, because the buy is for all of it whether or
     * not it all goes today.
     */
    @Test
    fun a_lot_sold_in_part_sells_one_piece_and_still_buys_them_all() {
        val form = NewItemForm(
            name = "Talerze",
            paidText = "10",
            priceText = "15",
            quantityText = "3",
            soldCompletely = false,
        )

        assertEquals(Money(3000), form.paid)
        assertEquals(Money(1500), form.price)
        assertEquals(1, form.soldQuantity)
        assertFalse(form.closesTheItem)
    }

    /** Empty stays a real answer: the cost is unknown, never zero. */
    @Test
    fun no_price_paid_stays_unknown_however_many_there_are() {
        val form = NewItemForm(name = "Talerze", priceText = "15", quantityText = "3")

        assertNull(form.paidPerPiece)
        assertNull(form.paid)
        assertTrue(form.canConfirm)
    }

    @Test
    fun the_sale_price_is_what_the_form_holds_out_for() {
        assertFalse(NewItemForm(name = "Waza").canConfirm)
        assertFalse(NewItemForm(name = "  ", priceText = "25").canConfirm)
        assertTrue(NewItemForm(name = "Waza", priceText = "25").canConfirm)
    }
}
