package pl.starocie.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The way out of any screen that is not the home screen.
 *
 * It sits at the bottom rather than in a top bar because that is where the thumb
 * is: every other control on these screens is already down there, and a back
 * affordance you have to reach the top of the phone for is one the system gesture
 * would win against anyway.
 *
 * Always the same shape, the same word and the same arrow, so leaving is never a
 * thing to look for — which is what lets the primary button be strict about its
 * required fields instead of doubling as the escape hatch.
 */
@Composable
internal fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Wstecz")
    }
}
