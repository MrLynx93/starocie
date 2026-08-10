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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pl.starocie.domain.format

/**
 * One thing at a time. Saving clears the form and puts the cursor back in the name
 * field, so a run of purchases is a run of typing rather than a run of navigation.
 */
@Composable
fun BuyOneScreen(buyId: String? = null, onDone: () -> Unit) {
    val viewModel: BuyOneViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nameFocus = remember { FocusRequester() }
    val paidFocus = remember { FocusRequester() }
    val askingFocus = remember { FocusRequester() }
    val takePhoto = rememberPhotoCapture(viewModel::onPhotoCaptured)

    LaunchedEffect(buyId) { viewModel.attachTo(buyId) }

    // Nothing is focused on arrival, so the screen opens whole and the keyboard
    // stays down: the first move here is as often the camera as the name. Focusing
    // a text field *is* the request for the keyboard on both platforms — asking for
    // one without the other means hiding it again a frame later, which flickers and
    // depends on winning a race with the IME.
    //
    // After a save it is the opposite: the name takes focus and the keyboard comes
    // with it, because a run of purchases is a run of typing.
    LaunchedEffect(state.recordedCount) {
        if (state.recordedCount > 0) runCatching { nameFocus.requestFocus() }
    }

    // Every field hands on to the one after it, so a whole purchase is typed without
    // the thumb leaving the keyboard. A box has no price of its own, so there the
    // field after the name is the asking price instead.
    val moveOnToPrice = {
        runCatching {
            if (state.showPaid) paidFocus.requestFocus() else askingFocus.requestFocus()
        }
        Unit
    }
    val moveOnToAsking = {
        runCatching { askingFocus.requestFocus() }
        Unit
    }

    Scaffold { padding ->
        // The app draws edge to edge, so the keyboard covers the window instead of
        // shrinking it — without imePadding the buttons sit underneath it.
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(20.dp),
        ) {
            // The form scrolls, the buttons do not: with the keyboard up the fields
            // alone are taller than what is left of the screen, and the buy buttons
            // must never be the thing that ends up underneath it.
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (state.buyId == null) "Kupujemy" else "Co było w paczce",
                    style = MaterialTheme.typography.headlineSmall,
                )
                // A plain buy says nothing under the heading — the form is its own
                // explanation. Only the box, and a run in progress, have something
                // to add.
                val hint = when {
                    state.recordedCount > 0 -> "Zapisaliśmy w tej serii: ${state.recordedCount}"
                    state.buyId != null -> "Wpisujemy po kolei — same trafiają do paczki."
                    else -> null
                }
                hint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

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

                // One line: a long name scrolls sideways within it rather than
                // giving the form a field twice the height of every other one. Enter
                // moves on to the price — there is no second line to open.
                //
                // The keyboard opens shifted, the way it does for a sentence: a name
                // is written down as a name, and a list of lowercase ones reads as
                // notes to self rather than a record of what we have.
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    singleLine = true,
                    label = { Text("Nazwa") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { moveOnToPrice() }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                )

                Spacer(Modifier.height(10.dp))

                // A single thing keeps the count beside what was paid: one price and
                // how many things it covered, which for one thing is the same number
                // twice. A lot breaks that pairing on purpose — the price is no
                // longer the lot's — and its label is a phrase rather than two words,
                // which beside a number field would be ellipsised down to "Kupiliśmy
                // po cenie za s…". Saying which price this is, is the whole job of
                // that label, so it gets the width instead.
                //
                // A box was paid for once and is not asked again, so there the count
                // stands alone whichever it is.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.quantityText,
                        onValueChange = viewModel::onQuantityChange,
                        singleLine = true,
                        label = { Text("Sztuki") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onNext = { moveOnToPrice() }),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(0.4f),
                    )
                    if (state.showPaid && !state.splittable) {
                        BuyPaidField(
                            state = state,
                            onChange = viewModel::onPaidChange,
                            onNext = moveOnToAsking,
                            modifier = Modifier.weight(1f).focusRequester(paidFocus),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }

                if (state.showPaid && state.splittable) {
                    Spacer(Modifier.height(10.dp))
                    BuyPaidField(
                        state = state,
                        onChange = viewModel::onPaidChange,
                        onNext = moveOnToAsking,
                        modifier = Modifier.fillMaxWidth().focusRequester(paidFocus),
                    )

                    // What the whole lot cost, said back. Typing the pile's total into
                    // a per-piece field is the one easy mistake here, and it is
                    // invisible until the profit is wrong weeks later — a figure three
                    // times what anybody remembers paying is not.
                    state.paid?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Kupiliśmy ${sztuki(state.quantity)} za ${it.format()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SplittableHint(visible = state.splittable)

                Spacer(Modifier.height(10.dp))

                // A lot's ask is what *one* of them goes for — the sale multiplies it
                // by however many are handed over — so the label has to say so, or
                // the number reads as the price of the whole pile.
                OutlinedTextField(
                    value = state.askingText,
                    onValueChange = viewModel::onAskingChange,
                    singleLine = true,
                    label = {
                        Text(
                            if (state.splittable) {
                                "Sprzedamy po cenie za sztukę"
                            } else {
                                "Chcemy sprzedać za"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().focusRequester(askingFocus),
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
                onClick = { viewModel.save() },
                enabled = state.canSave,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Kup i kupuj dalej", fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                // The arrow trails the label and points the other way from
                // "Wstecz": that one leaves, this one carries on to the next thing.
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Buys what is on screen and leaves — and does nothing else. It used to
            // stay enabled on an untouched form because it was also the only way
            // out; "Wstecz" is that now, so it can hold out for the name and the
            // price like the button above it.
            OutlinedButton(
                onClick = { viewModel.saveAndLeave(onDone) },
                enabled = state.canSave,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Kup") }

            Spacer(Modifier.height(10.dp))

            BackButton(onDone)
        }
    }
}

/**
 * What it cost — one of them, on a lot.
 *
 * The same field in two places, because a lot moves it out of the count's line and a
 * single thing keeps it there; the label is what differs between them, and having it
 * in one place is what stops the two drifting into saying different things about the
 * same number.
 */
@Composable
private fun BuyPaidField(
    state: BuyOneUiState,
    onChange: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = state.paidText,
        onValueChange = onChange,
        singleLine = true,
        label = {
            Text(if (state.splittable) "Kupiliśmy po cenie za sztukę" else "Kupiliśmy za")
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}
