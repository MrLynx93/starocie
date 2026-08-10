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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.starocie.domain.format

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
 *
 * **A lot is priced by the piece here, both times.** Somebody standing at the table
 * knows what one of them cost and what one of them is going for; the totals are a
 * multiplication, and doing it in your head while a buyer waits is where the wrong
 * number gets written down. Both labels therefore say "za sztukę" and both totals
 * are read back underneath, so a per-piece price mistaken for a lot price shows up
 * as a figure nobody recognises before "Sprzedaj" is pressed.
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

                // One line, as on the buy form — the two are the same form, and a
                // field twice the height of every other one is what it looked like.
                // Enter moves on to the sale price, the one field this screen
                // cannot do without.
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { viewModel.onNewItemChange(form.copy(name = it)) },
                    singleLine = true,
                    label = { Text("Nazwa") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { runCatching { priceFocus.requestFocus() } },
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                // A single thing keeps the buy screen's line: the count beside what
                // was paid, one price and how many things it covered. A lot breaks
                // that pairing on purpose — the price beside the count is no longer
                // the lot's — and its label is a phrase rather than two words, which
                // beside a number field would be ellipsised down to "Kupiliśmy po
                // tyle za s…". Saying which price this is, is the whole job of that
                // label, so it gets the width instead.
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
                    if (form.splittable) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        PaidField(form, viewModel::onNewItemChange, Modifier.weight(1f))
                    }
                }

                if (form.splittable) {
                    Spacer(Modifier.height(10.dp))
                    PaidField(form, viewModel::onNewItemChange, Modifier.fillMaxWidth())
                }

                // What the whole lot cost, said back. Typing the pile's total into a
                // per-piece field is the one easy mistake here, and it is invisible
                // until the profit is wrong weeks later — a figure three times what
                // anybody remembers paying is not.
                form.paid?.takeIf { form.splittable }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Kupiliśmy ${sztuki(form.quantity)} za ${it.format()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                //
                // On a lot it is the price of one piece, whatever the tick below says
                // — that is what the asking price means everywhere else in the app,
                // and it is what somebody at the table is quoting. How many pieces it
                // covers is the tick's business, not the field's.
                OutlinedTextField(
                    value = form.priceText,
                    onValueChange = { viewModel.onNewItemChange(form.copy(priceText = it)) },
                    singleLine = true,
                    label = {
                        Text(
                            if (form.splittable) {
                                "Sprzedajemy po tyle za sztukę"
                            } else {
                                "Sprzedajemy za"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().focusRequester(priceFocus),
                )

                // And the money actually changing hands, for the same reason as
                // above. Only worth saying when the tick makes it more than one
                // piece — otherwise it repeats the field.
                form.price?.takeIf { form.soldQuantity > 1 }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sprzedajemy ${sztuki(form.soldQuantity)} za ${it.format()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(10.dp))

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

/**
 * What it cost — one of them, on a lot.
 *
 * The same field in two places, because a lot moves it out of the count's line and
 * a single thing keeps it there; the label is what differs between them, and having
 * it in one place is what stops the two drifting into saying different things about
 * the same number.
 *
 * Empty stays a real answer either way: the cost is then unknown, which is the whole
 * reason this screen exists.
 */
@Composable
private fun PaidField(
    form: NewItemForm,
    onChange: (NewItemForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = form.paidText,
        onValueChange = { onChange(form.copy(paidText = it)) },
        singleLine = true,
        label = {
            Text(if (form.splittable) "Kupiliśmy po tyle za sztukę" else "Kupiliśmy za")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}
