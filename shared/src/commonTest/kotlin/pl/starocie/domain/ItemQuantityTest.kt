package pl.starocie.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemQuantityTest {

    @Test
    fun a_single_piece_is_not_splittable() {
        assertFalse(item("a").splittable, "quantity defaults to one")
        assertFalse(item("a", quantity = 1).splittable)
    }

    @Test
    fun several_pieces_can_be_sold_in_parts() {
        assertTrue(item("a", quantity = 2).splittable)
        assertTrue(item("a", quantity = 12).splittable)
    }
}
