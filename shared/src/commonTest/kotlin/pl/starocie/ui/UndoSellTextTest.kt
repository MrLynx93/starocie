package pl.starocie.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import pl.starocie.domain.Money
import pl.starocie.domain.format

class UndoSellTextTest {

    /** "3 sztuk wróci" is the small wrongness that makes an app feel like a machine. */
    @Test
    fun the_verb_agrees_with_the_count() {
        assertEquals("1 sztuka wróci", piecesComeBack(1))
        assertEquals("3 sztuki wrócą", piecesComeBack(3))
        assertEquals("5 sztuk wróci", piecesComeBack(5))
        assertEquals("12 sztuk wróci", piecesComeBack(12))
        assertEquals("22 sztuki wrócą", piecesComeBack(22))
    }

    @Test
    fun a_lot_still_closed_by_another_sale_only_loses_the_money() {
        val price = Money(4500)
        assertEquals(
            "Ta sprzedaż za ${price.format()} zniknie z naszych rachunków.",
            undoSellText(price, pieces = 3, splittable = true, backInStock = false),
        )
        assertEquals(
            "3 sztuki wrócą do magazynu, a sprzedaż za ${price.format()} zniknie z naszych rachunków.",
            undoSellText(price, pieces = 3, splittable = true, backInStock = true),
        )
    }
}
