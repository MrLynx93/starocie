package pl.starocie.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import pl.starocie.domain.Money

class ByThePieceTest {

    @Test
    fun several_pieces_are_said_per_piece() {
        assertEquals("po 15,00 zł za sztukę", byThePiece(Money(15000), 10))
        assertEquals("po ok. 3,00 zł za sztukę", byThePiece(Money(900), 3, approx = true))
    }

    @Test
    fun one_piece_or_an_uneven_total_is_said_as_the_total() {
        assertEquals("za 15,00 zł", byThePiece(Money(1500), 1))
        assertEquals("za 10,00 zł", byThePiece(Money(1000), 3))
    }
}
