package pl.starocie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Step one of two: what was paid for the box. Its contents are entered afterwards
 * on the ordinary item screen, so there is only one form to learn.
 */
@Composable
fun BuyBoxScreen(onOpened: (String) -> Unit, onCancel: () -> Unit) {
    val viewModel: BuyBoxViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val totalFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { totalFocus.requestFocus() } }
    LaunchedEffect(state.openedBuyId) { state.openedBuyId?.let(onOpened) }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {

            Text("Kupujemy paczkę", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Użyj tej opcji, jeżeli kupiłeś paczkę i nie znasz cen pojedynczych " +
                    "przedmiotów w środku.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = state.totalText,
                onValueChange = viewModel::onTotalChange,
                singleLine = true,
                label = { Text("Całą paczkę kupiliśmy za") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(totalFocus),
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                singleLine = true,
                label = { Text("Skąd / co to (opcjonalnie)") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.weight(1f))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = viewModel::open,
                    enabled = state.canOpen,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Dalej — co było w środku", fontWeight = FontWeight.Medium) }

                // Nothing is written until "Dalej", so leaving here throws away
                // only what is on screen — the same thing "Anuluj" used to say.
                BackButton(onCancel)
            }
        }
    }
}
