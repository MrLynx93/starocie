package pl.starocie.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * What a count above one actually means, said where the count is typed.
 *
 * A lot and a box look alike when you are standing at a stall — both are "a pile
 * of things for one price" — but they part ways at selling: a box becomes many
 * rows priced separately, a lot stays one row that sells piecewise. Nothing on
 * the buy form said so, so the difference was only discoverable by getting it
 * wrong, and a lot cannot be broken apart afterwards.
 *
 * It appears only once the quantity is more than one, so the ordinary single
 * purchase never has to read it.
 *
 * **It belongs to the two buy forms and nowhere else.** The item screen carries the
 * same count field and used to carry this under it, but there the choice is weeks
 * old and cannot be unmade — a paragraph explaining a decision already taken, above
 * the fields somebody opened the screen to correct.
 */
@Composable
internal fun SplittableHint(visible: Boolean) {
    if (!visible) return

    Spacer(Modifier.height(6.dp))
    Text(
        "Sprzedaje się po kawałku — zostaje w magazynie, aż zaznaczymy " +
            "«sprzedane w całości».",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
