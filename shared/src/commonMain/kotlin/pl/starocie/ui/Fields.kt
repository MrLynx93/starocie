package pl.starocie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import pl.starocie.domain.Money
import pl.starocie.domain.parseMoney

/**
 * The read-outs and the correctable fields the item screens are built from.
 *
 * They live together because the magazyn screen and the sold screen ask the same
 * questions about a thing at two different points in its life, and a price that
 * saves itself in one place and waits for a button in the other would be two
 * different promises about the same gesture.
 */

/**
 * A field's label, kept to one line — and shrunk until that line holds all of it.
 *
 * A resting label is what a text field sizes itself around, so one long enough to
 * wrap makes the field two lines tall and drops the typed text a line down the
 * moment it appears — which is exactly what happens when a count above one turns
 * "Kupiliśmy za" into "Kupiliśmy po cenie za szt.". A field that changes size
 * because of its own label is a form rearranging itself under the thumb, and the
 * count is stepped precisely while somebody is looking at the price.
 *
 * Holding it to one line alone only moved the failure: a narrow field, a lot's long
 * phrase or a screen scaled past the ordinary text size, and the tail went instead —
 * "Kupiliśmy po cenie za sz…", which is exactly the half that says whose price it is
 * and per what. **So a label that will not fit is drawn smaller rather than cut.**
 * A point or so of type is a cheap thing to spend; the words are not, and neither is
 * the field's height.
 *
 * It steps down rather than solving for a size, because what the label is measured
 * against is only known once it has been laid out. Each pass that still overflows
 * takes [LABEL_SHRINK_STEP] off and lays out again, which lands inside a few frames
 * and stops for good at [LABEL_FLOOR] — past that the field is too narrow for the
 * phrase at any readable size, and an ellipsis is the honest end of it.
 *
 * The size it settles on is a **ceiling, not a size**: what is drawn is the smaller
 * of it and whatever the text field is currently providing, so the label still
 * shrinks to its small self as it floats up to the border and still animates the
 * whole way. Only what was too big for the line is taken off.
 */
@Composable
internal fun FieldLabel(text: String) {
    val provided = LocalTextStyle.current
    val given = provided.fontSize
    // Ems and an unspecified size are nothing to measure against, and neither ever
    // reaches here — the field provides its label style in sp. Left alone if it does.
    val scalable = given.isSpecified && given.isSp

    // Reset with the text: a count crossing one swaps a short label for a long one,
    // and the long one's size must not be inherited by whatever replaces it.
    var ceiling by remember(text) { mutableStateOf(LABEL_CEILING) }
    val size = if (scalable) minOf(given, ceiling) else given

    Text(
        text = text,
        style = provided.copy(fontSize = size),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { laid ->
            if (scalable && laid.hasVisualOverflow && size > LABEL_FLOOR) {
                ceiling = maxOf(size * LABEL_SHRINK_STEP, LABEL_FLOOR)
            }
        },
    )
}

/** Higher than any label style, so the first pass draws at the size it was given. */
private val LABEL_CEILING = 100.sp

/** Where shrinking stops and the tail goes instead. */
private val LABEL_FLOOR = 9.sp

private const val LABEL_SHRINK_STEP = 0.92f

/**
 * A price you can correct, saved without being asked to confirm it.
 *
 * There is no save button: the write goes out once the typing stops. Firestore
 * takes it locally either way, so the pause costs nothing — whereas a price change
 * that depends on remembering to press something is a price change that gets lost
 * at a stall.
 *
 * The text belongs to the caller, because the sell button has to read what is in
 * the field rather than what has been written so far. [saved] is only what the
 * record currently holds, used to tell an edit from a redisplay — following it into
 * the field would fight the keyboard, since every write comes back through the
 * ledger.
 */
@Composable
internal fun MoneyField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    saved: Money?,
    placeholder: String,
    onSave: (String) -> Unit,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(text) {
        if (parseMoney(text) == saved) return@LaunchedEffect
        delay(SAVE_AFTER_TYPING_MS)
        onSave(text)
    }

    // Bare by default, which is the whole width: the field inside fills whatever it
    // is given, so a caller that says nothing gets a line of its own and one that
    // hands it a weight gets its share of a row.
    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            label = { FieldLabel(label) },
            placeholder = { Text(placeholder) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        hint?.let { FieldHint(it) }
    }
}

internal const val SAVE_AFTER_TYPING_MS = 500L

/**
 * A short piece of text you can correct, written the moment the typing stops.
 *
 * Same bargain as [MoneyField]: no save button, because a name changed at a stall
 * must not depend on remembering to confirm it. The text belongs to the caller and
 * [saved] is only what the record holds, used to tell an edit from a redisplay.
 */
@Composable
internal fun NameField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    saved: String,
    placeholder: String,
    onSave: (String) -> Unit,
    hint: String? = null,
) {
    LaunchedEffect(text) {
        if (text.trim() == saved) return@LaunchedEffect
        delay(SAVE_AFTER_TYPING_MS)
        onSave(text.trim())
    }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            label = { FieldLabel(label) },
            placeholder = { Text(placeholder) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        hint?.let { FieldHint(it) }
    }
}

/**
 * How many pieces a record covers, corrected the same way its prices are.
 *
 * Same bargain again — no save button, the write going out once the typing stops —
 * and the same word the buy form asks for it with, because it is the same number
 * about the same thing. The caller places it: on the buy form it takes a slice of
 * the row it shares with the name, and on the item screen a slice of the one it
 * shares with the price. Never more than two digits either way.
 *
 * **A count that does not parse writes nothing**, the way a blank sale price does:
 * a lot is *some* number of things, and a field caught halfway between 1 and 12 is
 * not an answer about how many there are. Nothing here is what stops a count moving
 * once something has sold — that is the repository's, being a fact about the record
 * rather than about this screen.
 */
@Composable
internal fun CountField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    saved: Int,
    onSave: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(text) {
        val count = text.trim().toIntOrNull()?.takeIf { it >= 1 } ?: return@LaunchedEffect
        if (count == saved) return@LaunchedEffect
        delay(SAVE_AFTER_TYPING_MS)
        onSave(count)
    }

    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        label = { FieldLabel(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}

/**
 * A day you can correct, picked from a calendar rather than typed.
 *
 * It looks like the price fields beside it and behaves like one — the choice is
 * written the moment it is made, with nothing else to press. A calendar rather than
 * a text field because a date is the one value where the keyboard offers no help
 * and every separator is a chance to write down a day that never happened.
 *
 * The picker works in UTC on purpose: the value here is a plain calendar day with
 * no time in it, so converting through the phone's zone would only give midnight
 * somewhere to fall across.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
internal fun DateField(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    hint: String? = null,
) {
    var picking by remember { mutableStateOf(false) }

    Column {
        OutlinedCard(
            onClick = { picking = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(date.asText(), style = MaterialTheme.typography.bodyLarge)
                }
                Icon(
                    Icons.Filled.CalendarToday,
                    contentDescription = "Zmień datę",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        hint?.let { FieldHint(it) }
    }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis
                        ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
                        ?.takeIf { it != date }
                        ?.let(onDateChange)
                    picking = false
                }) { Text("Zapisz") }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text("Anuluj") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** A fact, not a control: the label on the left and the value on the right. */
@Composable
internal fun Detail(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
    HorizontalDivider()
}

/** What makes a figure a guess, or what a field is about to change. */
@Composable
private fun FieldHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
    )
}
