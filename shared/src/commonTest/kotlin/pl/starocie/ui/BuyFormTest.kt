package pl.starocie.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pl.starocie.domain.Money

/**
 * What the buy form will and will not let through. The price opens at zero and the
 * buttons wait only for the name: at a stall the thing is already in somebody's
 * hand, and a button that refuses to go is the friction the app exists to avoid.
 */
class BuyFormTest {

    @Test
    fun the_price_opens_at_zero() {
        val form = BuyOneUiState()

        assertEquals("0", form.paidText)
        assertEquals(Money.ZERO, form.paid)
    }

    @Test
    fun a_name_is_the_whole_of_what_the_buy_buttons_wait_for() {
        assertFalse(BuyOneUiState().canSave, "an untouched form buys nothing")
        assertFalse(BuyOneUiState(name = "   ").canSave, "nor does a blank name")
        assertTrue(BuyOneUiState(name = "Waza").canSave)
    }

    /**
     * Clearing the zero is still the honest unknown — it writes no price at all
     * rather than a claim that we paid nothing — and it does not hold the buy up.
     */
    @Test
    fun an_emptied_price_buys_anyway_and_stays_unknown() {
        val form = BuyOneUiState(name = "Waza", paidText = "")

        assertTrue(form.canSave)
        assertNull(form.paid)
    }

    /** Zero times however many is still zero, and the read-back says so. */
    @Test
    fun a_free_lot_is_free_by_the_piece_and_whole() {
        val form = BuyOneUiState(name = "Talerze", quantityText = "3")

        assertTrue(form.splittable)
        assertEquals(Money.ZERO, form.paidPerPiece)
        assertEquals(Money.ZERO, form.paid)
    }

    /** The box's own price step opens on the same zero, so its button goes too. */
    @Test
    fun the_box_price_opens_at_zero() {
        val step = BuyBoxUiState()

        assertEquals("0", step.totalText)
        assertEquals(Money.ZERO, step.total)
        assertTrue(step.canOpen)
    }
}
