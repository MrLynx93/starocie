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
import androidx.compose.ui.unit.dp
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
) {
    LaunchedEffect(text) {
        if (parseMoney(text) == saved) return@LaunchedEffect
        delay(SAVE_AFTER_TYPING_MS)
        onSave(text)
    }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            label = { Text(label) },
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
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        hint?.let { FieldHint(it) }
    }
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
internal fun Detail(label: String, value: String, hint: String? = null) {
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
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 16.dp)) {
            Text(value, style = MaterialTheme.typography.bodyLarge)
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
