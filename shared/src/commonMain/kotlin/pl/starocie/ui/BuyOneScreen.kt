package pl.starocie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * One thing at a time. Saving clears the form and puts the cursor back in the name
 * field, so a run of purchases is a run of typing rather than a run of navigation.
 */
@Composable
fun BuyOneScreen(buyId: String? = null, onDone: () -> Unit) {
    val viewModel: BuyOneViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nameFocus = remember { FocusRequester() }
    val takePhoto = rememberPhotoCapture(viewModel::onPhotoCaptured)

    LaunchedEffect(buyId) { viewModel.attachTo(buyId) }

    // Refocus after each save so the next thing can be typed immediately.
    LaunchedEffect(state.recordedCount) { runCatching { nameFocus.requestFocus() } }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
        ) {
            Text(
                text = if (state.buyId == null) "Kupuję rzecz" else "Co było w pudle",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = when {
                    state.recordedCount > 0 -> "Zapisano w tej serii: ${state.recordedCount}"
                    state.buyId != null -> "Wpisuj po kolei — same trafiają do pudła."
                    else -> "Zapisz i od razu wpisuj następną."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                singleLine = true,
                label = { Text("Co to jest") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.showPaid) {
                    OutlinedTextField(
                        value = state.paidText,
                        onValueChange = viewModel::onPaidChange,
                        singleLine = true,
                        label = { Text("Zapłacono") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = state.askingText,
                    onValueChange = viewModel::onAskingChange,
                    singleLine = true,
                    label = { Text("Cena wyw.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.quantityText,
                    onValueChange = viewModel::onQuantityChange,
                    singleLine = true,
                    label = { Text("Sztuki") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(0.7f),
                )
            }

            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            PhotoArea(
                photo = state.photo,
                onCapture = takePhoto,
                onClear = viewModel::clearPhoto,
                modifier = Modifier.weight(1f),
            )

            // Both actions sit together at the bottom, within thumb reach.
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Zapisz i następna", fontWeight = FontWeight.Medium) }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onDone,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Gotowe") }
        }
    }
}
