package pl.starocie.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The button that erases a record: "Usuń" on a thing in stock, "Cofnij sprzedaż" on
 * a thing that went in one sale.
 *
 * Outlined and the same size as [BackButton], because it sits directly above it at
 * the bottom of the screen — and red, so the one control that destroys something
 * never reads as the neutral way out beneath it. Both screens draw it from here so
 * the two cannot drift into looking like different kinds of act.
 */
@Composable
internal fun DestructiveButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) { Text(label) }
}
