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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Buying and selling in one sitting, for a thing that was never entered.
 *
 * Only the name and the final price are asked for. "Kupiliśmy za" left empty is a
 * real answer — the cost is then unknown and stays unknown, which is the whole
 * point of tolerating a shortcut rather than demanding tidy books.
 *
 * A screen rather than a dialog: it carries as many fields as the buy screen does,
 * and a dialog squeezed them against the keyboard until the photo was off-screen.
 * It shares [SellViewModel] with the list behind it, so the typed search that
 * opened it is also what seeds the name, and selling clears both.
 */
@Composable
fun SellNewItemScreen(viewModel: SellViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val takePhoto = rememberPhotoCapture(viewModel::onNewPhotoCaptured)
    val form = state.newItem

    // The form is emptied by a successful sale, which is the cue to leave. Waiting
    // to have seen it open first keeps a not-yet-delivered state from popping the
    // screen the instant it appears.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(form == null) {
        if (form != null) opened = true else if (opened) onDone()
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text("Nowa rzecz", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Nigdzie jej nie mamy — zapisujemy i sprzedajemy za jednym razem.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (form == null) return@Column

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { viewModel.onNewItemChange(form.copy(name = it)) },
                    singleLine = true,
                    label = { Text("Co to jest") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.quantityText,
                    onValueChange = { viewModel.onNewItemChange(form.copy(quantityText = it)) },
                    singleLine = true,
                    label = { Text("Sztuki") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(0.4f),
                )
            }

            SplittableHint(visible = form.splittable)

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = form.paidText,
                    onValueChange = { viewModel.onNewItemChange(form.copy(paidText = it)) },
                    singleLine = true,
                    label = { Text("Kupiliśmy za") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.priceText,
                    onValueChange = { viewModel.onNewItemChange(form.copy(priceText = it)) },
                    singleLine = true,
                    label = { Text("Sprzedaliśmy za") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                "Nie wiemy, za ile kupiliśmy? Zostawmy puste — koszt zostanie nieznany.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = form.note,
                onValueChange = { viewModel.onNewItemChange(form.copy(note = it)) },
                singleLine = true,
                label = { Text("Notatka (opcjonalnie)") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (form.splittable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = form.soldCompletely,
                        onCheckedChange = {
                            viewModel.onNewItemChange(form.copy(soldCompletely = it))
                        },
                    )
                    Text("Sprzedane w całości")
                }
            }

            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(16.dp))

            PhotoArea(
                photo = form.photo,
                onCapture = takePhoto,
                onClear = viewModel::clearNewPhoto,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = viewModel::confirmNewItem,
                enabled = form.canConfirm,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Sprzedane", fontWeight = FontWeight.Medium) }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                // Emptying the form is what leaves — the effect above does the
                // popping, so cancelling and selling cannot pop twice between them.
                onClick = viewModel::cancelNewItem,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Anuluj") }
        }
    }
}
