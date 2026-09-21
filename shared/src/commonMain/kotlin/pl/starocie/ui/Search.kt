package pl.starocie.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Searching is a place you go, not a box that sits there whether or not anybody is
 * looking for anything.
 *
 * The box used to be permanent: four copies of a 56 dp `OutlinedTextField`, each with a
 * heading and a line of figures over it and a margin under it, and all of that on screen
 * while the keyboard covered the bottom half of the phone. On the list you reach by
 * pressing "Sprzedaj" that left two and a half rows of the thing being hunted for — the
 * friction rule losing to a control, on the one screen where somebody is standing at a
 * table holding the object.
 *
 * So there are two states now, and the screen is a different shape in each:
 *
 * - **at rest** — one 44 dp magnifier at the right-hand end of the heading's line
 *   ([SearchAction]), exactly where and how the home screen puts its light/dark switch.
 *   Nothing else moves: the heading, the figures under it and the list stay as they were.
 * - **open** — [SearchLine], and the screen becomes the list. The heading goes, the
 *   figures go, "Wstecz" goes, the magazyn's unpriced filter goes, and what is left is
 *   the line being typed into, the rows, and — on the selling route only — the one
 *   button for a thing that was never recorded.
 *
 * **The heading going is what pays for the change, and its job does not go with it.** A
 * heading is there to say which of the two doors you came in by — "Co chcesz sprzedać?"
 * against "Nasz magazyn" — and while the search is open the bottom of the screen says
 * the same thing better: the selling route has "Dodaj … i sprzedaj" under the list and
 * the magazyn route has nothing at all. The distinction survives in the thing you would
 * actually press.
 *
 * There is no border, no container and no fill on the open line. A search box is chrome;
 * drawing a form field round it asks a question the screen is not asking, and spends a
 * border, a radius and twelve dp of height asking it.
 */

/**
 * Whether a list is being searched. The query itself is not in here, and deliberately:
 * on the magazyn it belongs to `SellViewModel`, which is what narrows the list, and a
 * second copy beside it would be two answers to what was typed.
 */
@Stable
internal class SearchState(open: Boolean) {
    var isOpen by mutableStateOf(open)
        private set

    fun open() { isOpen = true }

    fun close() { isOpen = false }
}

/**
 * [open] is for a screen whose query outlives it — the magazyn's lives in
 * `SellViewModel`, which survives a trip out to "Dodaj … i sprzedaj" and back. Coming
 * back to a list narrowed by typing that is nowhere on screen is the one state this
 * must never land in.
 */
@Composable
internal fun rememberSearchState(open: Boolean = false): SearchState = remember { SearchState(open) }

/**
 * The magnifier, at the right-hand end of a heading's line.
 *
 * A 44 dp target on the line the heading already occupies rather than anything of its
 * own, which is the trade the home screen's theme switch already makes: a control worth
 * a corner of a line is not worth a bar to hold it.
 */
@Composable
internal fun SearchAction(onOpen: () -> Unit) {
    IconButton(onClick = onOpen, modifier = Modifier.size(44.dp)) {
        Icon(
            Icons.Filled.Search,
            contentDescription = "Szukaj",
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * A heading with the magnifier on its line — what a searchable list draws at rest.
 *
 * The heading takes what is left beside the icon and ellipsises rather than wrapping: a
 * heading that wrapped would push the figures and the first row down a line, and the
 * longest of them ("Co chcesz sprzedać?") fits with room to spare on any phone this runs
 * on.
 */
@Composable
internal fun ListHeading(text: String, onOpenSearch: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        SearchAction(onOpenSearch)
    }
}

/**
 * The open search: a magnifier, the text, and a way out. Nothing drawn around it.
 *
 * **The × means one thing at a time.** With something typed it clears the box and leaves
 * the keyboard where it is, the commonest reason to reach for it being a second search
 * rather than no search. With the box already empty there is nothing to clear, so it
 * closes the search and the heading comes back — which is what the phone's own back does
 * too, one press after the keyboard has taken its turn.
 *
 * The keyboard's action key is [ImeAction.Search] and it puts the keyboard away. It used
 * to be a plain default that did nothing at all, which is the same dead key the buy
 * form's number pads had: a key that looks like a control and is not one is worse than no
 * key, and this is the screen where it gets pressed hardest.
 *
 * It opens with the keyboard up, because the only reason to have pressed the magnifier is
 * to type. Focusing a field *is* the request for the keyboard, so the two are one gesture
 * and cannot be separated without losing the race half the time.
 */
@Composable
internal fun SearchLine(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))

        // A BasicTextField rather than either Material one: those bring a container, a
        // label slot and a 56 dp floor, which is the whole of what is being taken away.
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    // The one string in the app that speaks to the person holding the
                    // phone rather than as the two of us — and now it is in one place
                    // rather than four.
                    if (query.isEmpty()) {
                        Text(
                            "Czego szukasz?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    field()
                }
            },
        )

        IconButton(
            onClick = { if (query.isEmpty()) onClose() else onQueryChange("") },
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = if (query.isEmpty()) "Zamknij wyszukiwanie" else "Wyczyść",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    HorizontalDivider()
}

/**
 * Everything a searchable list draws above its rows, in one place so the four of them
 * cannot drift apart.
 *
 * [figures] is whatever that list says about itself — a count and a sum, a day's two
 * lines — and it is drawn only at rest. It is never recomputed over what the search
 * found: the heading says what we *have*, and looking for one lamp does not change it.
 *
 * Closing throws the query away, and has to: a list left narrowed by something nobody
 * can see they typed is a list lying about what we have got.
 *
 * It decides nothing below itself. The list, the buttons and each route's own
 * differences stay on the screen that owns them, which is where they can be read.
 */
@Composable
internal fun SearchableHeader(
    heading: String,
    search: SearchState,
    query: String,
    onQueryChange: (String) -> Unit,
    figures: @Composable () -> Unit = {},
) {
    val close = {
        onQueryChange("")
        search.close()
    }

    SystemBack(enabled = search.isOpen, onBack = close)

    if (search.isOpen) {
        SearchLine(query, onQueryChange, close)
    } else {
        ListHeading(heading, search::open)
        figures()
    }
}
