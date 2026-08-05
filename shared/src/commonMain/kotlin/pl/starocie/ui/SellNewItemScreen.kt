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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
 *
 * It is laid out as the buy screen is — photo first, then the name, then the count
 * beside what was paid — because it is the buy form with a sale price on the end.
 * The same purchase should not feel like a different act depending on how quickly
 * the thing was sold on.
 */
@Composable
fun SellNewItemScreen(viewModel: SellViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val takePhoto = rememberPhotoCapture(viewModel::onNewPhotoCaptured)
    val priceFocus = remember { FocusRequester() }
    val form = state.newItem

    // The form is emptied by a successful sale, which is the cue to leave. Waiting
    // to have seen it open first keeps a not-yet-delivered state from popping the
    // screen the instant it appears.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(form == null) {
        if (form != null) opened = true else if (opened) onDone()
    }

    Scaffold { padding ->
        // The app draws edge to edge, so the keyboard covers the window instead of
        // shrinking it — without imePadding the buttons sit underneath it.
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(20.dp),
        ) {
            // As on the buy screen: the form scrolls, the buttons stay put, because
            // with the keyboard up the fields alone outgrow what is left of the
            // screen and "Sprzedaj" must not be what ends up underneath it.
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                Text("Sprzedaj nowy przedmiot", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Nigdzie go nie mamy — zapisujemy i sprzedajemy za jednym razem.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (form == null) return@Column

                Spacer(Modifier.height(16.dp))

                // The photo leads here for the same reason it leads the buy form:
                // the thing is in your hand, so it is shot and then described.
                PhotoArea(
                    photo = form.photo,
                    onCapture = takePhoto,
                    onClear = viewModel::clearNewPhoto,
                    modifier = Modifier.height(200.dp),
                )

                Spacer(Modifier.height(16.dp))

                // Two lines, so a name written to be found again later reads whole.
                // Enter moves on to the sale price — the one field this screen
                // cannot do without — rather than opening a third line.
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { typed ->
                        // A newline can still arrive from a hardware key or an IME
                        // that sends one instead of the action; it means the same
                        // thing here, so it moves on rather than breaking the line.
                        if (typed.contains('\n')) {
                            viewModel.onNewItemChange(form.copy(name = typed.replace("\n", "")))
                            runCatching { priceFocus.requestFocus() }
                        } else {
                            viewModel.onNewItemChange(form.copy(name = typed))
                        }
                    },
                    minLines = 2,
                    maxLines = 2,
                    label = { Text("Nazwa") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { runCatching { priceFocus.requestFocus() } },
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                // The count rides with what was paid, exactly as on the buy screen:
                // one lot, one price, and how many things are in it.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = form.quantityText,
                        onValueChange = { viewModel.onNewItemChange(form.copy(quantityText = it)) },
                        singleLine = true,
                        label = { Text("Sztuki") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(0.4f),
                    )
                    OutlinedTextField(
                        value = form.paidText,
                        onValueChange = { viewModel.onNewItemChange(form.copy(paidText = it)) },
                        singleLine = true,
                        label = { Text("Kupiliśmy za") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                }

                SplittableHint(visible = form.splittable)

                Spacer(Modifier.height(6.dp))

                Text(
                    "Nie wiemy, za ile kupiliśmy? Zostawmy puste — koszt zostanie nieznany.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))

                // Where the buy form asks what we want for it, this one already
                // knows: the same slot, filled in with what it actually went for.
                OutlinedTextField(
                    value = form.priceText,
                    onValueChange = { viewModel.onNewItemChange(form.copy(priceText = it)) },
                    singleLine = true,
                    label = { Text("Sprzedajemy za") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().focusRequester(priceFocus),
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
            }

            // Both actions sit together at the bottom, within thumb reach.
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = viewModel::confirmNewItem,
                enabled = form?.canConfirm == true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Sprzedaj", fontWeight = FontWeight.Medium) }

            Spacer(Modifier.height(10.dp))

            // Emptying the form is what leaves — the effect above does the popping,
            // so going back and selling cannot pop twice between them.
            BackButton(viewModel::cancelNewItem)
        }
    }
}
