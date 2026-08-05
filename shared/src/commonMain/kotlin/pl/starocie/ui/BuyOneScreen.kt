package pl.starocie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
 * One thing at a time. Saving clears the form and puts the cursor back in the price
 * field, so a run of purchases is a run of typing rather than a run of navigation.
 */
@Composable
fun BuyOneScreen(buyId: String? = null, onDone: () -> Unit) {
    val viewModel: BuyOneViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val paidFocus = remember { FocusRequester() }
    val nameFocus = remember { FocusRequester() }
    val takePhoto = rememberPhotoCapture(viewModel::onPhotoCaptured)

    LaunchedEffect(buyId) { viewModel.attachTo(buyId) }

    // Refocus after each save so the next thing can be typed immediately. The
    // price is what you are told at the stall, so it leads; unpacking a box has no
    // price to enter, and there the name is the first thing asked for.
    LaunchedEffect(state.recordedCount, state.showPaid) {
        runCatching { if (state.showPaid) paidFocus.requestFocus() else nameFocus.requestFocus() }
    }

    Scaffold { padding ->
        // The app draws edge to edge, so the keyboard covers the window instead of
        // shrinking it — without imePadding the buttons sit underneath it.
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(20.dp),
        ) {
            // The form scrolls, the buttons do not: with the keyboard up the fields
            // alone are taller than what is left of the screen, and "Zapisz" must
            // never be the thing that ends up underneath it.
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (state.buyId == null) "Kupujemy rzecz" else "Co było w paczce",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = when {
                        state.recordedCount > 0 ->
                            "Zapisaliśmy w tej serii: ${state.recordedCount}"
                        state.buyId != null -> "Wpisujemy po kolei — same trafiają do paczki."
                        else -> "Zapisujemy i od razu wpisujemy następną."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                // The photo comes first because it is the first thing that happens:
                // the thing is in your hand, so you shoot it and then describe it.
                // A fixed height rather than the leftover space — it only has to be
                // big enough to aim at and to recognise the thing in afterwards.
                PhotoArea(
                    photo = state.photo,
                    onCapture = takePhoto,
                    onClear = viewModel::clearPhoto,
                    modifier = Modifier.height(200.dp),
                )

                Spacer(Modifier.height(16.dp))

                // Two lines, because a name typed to be found again later is rarely
                // one word — "filiżanka Ćmielów, złoty rant" has to be readable
                // whole, not scrolled sideways through a one-line field.
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    minLines = 2,
                    maxLines = 2,
                    label = { Text("Co to jest") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                )

                Spacer(Modifier.height(10.dp))

                // The count rides with what was paid: one lot, one price, and how
                // many things are in it. A box was paid for once, so it is not
                // asked again there and the count stands alone.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.quantityText,
                        onValueChange = viewModel::onQuantityChange,
                        singleLine = true,
                        label = { Text("Sztuki") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(0.4f),
                    )
                    if (state.showPaid) {
                        OutlinedTextField(
                            value = state.paidText,
                            onValueChange = viewModel::onPaidChange,
                            singleLine = true,
                            label = { Text("Kupiliśmy za") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).focusRequester(paidFocus),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }

                SplittableHint(visible = state.splittable)

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = state.askingText,
                    onValueChange = viewModel::onAskingChange,
                    singleLine = true,
                    label = { Text("Chcemy sprzedać za") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
            }

            // Both actions sit together at the bottom, within thumb reach.
            Spacer(Modifier.height(16.dp))

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
