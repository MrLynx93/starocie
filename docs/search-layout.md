# A smaller search box, and more rows under it

A design, not a change. Nothing here is implemented yet.

## The complaint, measured

Four screens carry the search box — the magazyn, the sold list, the giełdy (and its
twin "Nasze zakupy") and one giełda's own screen — and it is four copies of the same
`OutlinedTextField`, each with its own `Spacer(16)` above and `Spacer(12)` below. The
placeholder is one string in four places, exactly as the voice rule requires, and
everything around it is four of everything.

It is also as tall as a field in a form, because that is what it is. Material 3 floors
`OutlinedTextField` and `TextField` at `TextFieldDefaults.MinHeight = 56.dp`; no
parameter goes under it. So the box we search with is the same height as the box we
type a price into, and it is the only one of the two that is never the point of the
screen.

What that costs, on a 411×891 dp phone with the keyboard up — about 500 dp of screen
left, and rows 77 dp tall:

| Screen, while typing | chrome today | rows visible |
|---|---|---|
| magazyn (filter off) | 178 dp | 4.1 |
| magazyn (filter on)  | 178 dp | 4.1 |
| sold                 | 232 dp | 3.4 |
| giełdy               | 152 dp | 4.5 |
| one giełda           | 308 dp | 2.0 |

The magazyn is the only one that collapses anything at all while the keyboard is up —
its two figures, "Wstecz" and most of the bottom margin go. The other three do not, and
one giełda's screen is the worst in the app: a 56 dp name field, its date, the day's two
figure lines and the profit beside them, and then the search box, all still drawn while
somebody is typing a name into it. Two and a half rows of answer under three rows of
chrome.

## The shape of the fix

Three moves, in order of what they return.

### 1. One `SearchField`, 44 dp, built on `BasicTextField`

A new `ui/Search.kt` holding the field, the collapsing header and the keyboard test, so
the four screens stop each owning a copy.

```
 ┌──────────────────────────────────────────────┐
 │ ⌕   Czego szukasz?                           │   44 dp
 └──────────────────────────────────────────────┘
 ┌──────────────────────────────────────────────┐
 │ ⌕   lamp                                 ✕   │   44 dp
 └──────────────────────────────────────────────┘
```

- **44 dp fixed**, which means `BasicTextField` inside a `Surface` rather than either
  M3 text field — the 56 dp floor is not a default, it is a floor. That is the whole
  implementation cost of this item, and it is small: no label, no error state, no
  supporting text, one line.
- **Filled rather than outlined** — `surfaceVariant`, no border. A search box is chrome,
  not a question being asked; a magnifier, a border and a clear button are three strokes
  doing one job. Radius 22 dp, so it reads as a pill and as nothing you are expected to
  fill in. (If that feels like a second shape language next to the app's 14/16 dp
  rounding, 14 dp here loses nothing but the hint.)
- **A magnifier on the left**, 18 dp, `onSurfaceVariant`. It is what lets the box be
  short: the placeholder no longer has to carry the whole of what the box is, and the
  words "Czego szukasz?" survive unchanged in the one place they now live.
- **A clear button on the right, only while there is something to clear.** There is no
  way to empty the box today but holding backspace, and on the magazyn emptying it is
  what brings the figures and "Wstecz" back. It costs no height.
- **`ImeAction.Search`, and it clears focus.** Today that key does nothing at all — the
  same dead-key problem the buy form's number pads had. Pressing it puts the keyboard
  away, which is what un-collapses the screen.
- Text at `bodyMedium` (14 sp) rather than `bodyLarge`. A query is two or three words.

Saves 12 dp everywhere, and removes four copies.

### 2. The header collapses on all four screens, not one

`rememberTyping()` — the `WindowInsets.ime` derivation the magazyn already does — moves
into `ui/Search.kt`, and a `SearchHeader` draws the heading, the figures and the field
together:

```
 not typing                              typing
 ─────────────────────────────           ─────────────────────────────
 Nasz magazyn        headlineSmall  32   Nasz magazyn     titleSmall  20
 Mamy 12 przedmiotów · …  bodySmall 16                                 6
                                    12   ┌───────────────────────┐
 ┌───────────────────────┐               │ ⌕  lamp           ✕  │    44
 │ ⌕  Czego szukasz?    │           44   └───────────────────────┘
 └───────────────────────┘                                            8
                                     8   ─────────────────────────────
 ─────────────────────────────           = 78 dp, + 8 dp top pad
 = 112 dp (was 152)
```

The heading **shrinks rather than goes**. It is what says which of the two doors the
magazyn was opened by — "Co chcesz sprzedać?" against "Nasz magazyn" — and on a giełda's
"Sprzedaj" it is the only thing on screen saying the sale still lands in that day. At
`titleSmall` it is 20 dp instead of 32 and still says it.

`ScreenColumn` gains a `top` to match the `bottom` it already has, and the collapse pulls
it to 8 dp. The status-bar inset is `Scaffold`'s and is untouched — this is only the 20 dp
we add on top of it.

Two per-screen rules fall out:

- **One giełda's screen collapses its name field to a read-out.** `NameField` is 56 dp
  plus a date line; while the search has the keyboard, the day is one `titleSmall` line
  of its name-or-date and the two figure lines are gone. The field is back the moment the
  keyboard is. One value in two states is not one value in two places — only ever one of
  them is drawn.
- **The unpriced chip goes only when it is off.** It is an offer while it is off and a
  fact about what you are looking at while it is on, and a narrowed list whose reason has
  scrolled away is a list lying about itself. (Today it stays either way, and costs 42 dp
  of the magazyn's worst case.)

### 3. Rows lose 8 dp and the rules between them

- Thumbnail 52 → **48 dp**, row padding 12 → **10 dp**: 76 dp rows become 68.
- **No `HorizontalDivider` between rows.** A 48 dp thumbnail in its own column already
  gives the list its rhythm, and on the sold and giełda rows — three lines on the left
  against two on the right — a full-bleed rule chops each row into a block and the list
  into a grid. The one under the last row is a line to nowhere. If they are wanted, inset
  them past the thumbnail and they at least read as separators rather than as a table.

## What it comes to

Same phone, same keyboard, rows now 68 dp:

| Screen, while typing | chrome today | chrome proposed | rows today | rows after |
|---|---|---|---|---|
| magazyn (filter off) | 178 dp | 86 dp | 4.1 | **6.0** |
| magazyn (filter on)  | 178 dp | 128 dp | 4.1 | **5.4** |
| sold                 | 232 dp | 86 dp | 3.4 | **6.0** |
| giełdy               | 152 dp | 86 dp | 4.5 | **6.0** |
| one giełda           | 308 dp | 86 dp | 2.0 | **5.5** |

A giełda's two counts are the odd ones out because its list carries a section heading
inside it — `SectionLabel` is a `titleSmall` and a rule, 39 dp — and that is drawn either
way. It is the one place where what you can see is not simply the list area divided by
the row height.

Idle, the four screens are 40 dp shorter in the head and every row is 8 dp shorter, which
is the "more concise" half: about one extra row on each list before anybody types
anything.

**No predicate changes.** `Item.matchesQuery`, `Event.matchesQuery`, the sold list's
in-memory filter and the day's two-section narrowing are all untouched, and so are the
figures above them — still the magazyn's and the day's own, never the search's. This is
layout only, which is also why it needs no new tests: `NarrowedForStockTest` and the rest
still describe the same behaviour.

## What this deliberately does not do

- **No top app bar, and the search does not move into one.** Every control in this app is
  at the bottom because that is where the thumb is, and a search box at the top of the
  phone is one you reach for with two hands at a stall.
- **The box does not scroll away with the list.** A search box you have to scroll back up
  to is a search box you retype into.
- **No wording changes.** "Czego szukasz?" stays, in what is now genuinely one place; the
  figures keep their sentences; `rzeczy(n)` stays where the width still needs it.
- **The heading is not dropped while typing.** There is a version of this that puts the
  shrunk heading and the field on one row and saves another 26 dp — on a 360 dp-wide
  phone that leaves the field about 190 dp, which is enough to type into and too little
  to read a long query back from, and next to "Nasze giełdy" the heading would read as
  the field's label. Offered, not recommended.

## Two things in CLAUDE.md this changes

Worth agreeing to before any code, because both are written down as rules rather than as
descriptions:

1. *"The magazyn list alone puts ["Wstecz"] away while its search is being typed
   into"* — after this it is all four searchable lists. The reason the rule gives, that
   the keyboard covers where the button sits, was never true of only one of them.
2. The magazyn's *"It is drawn only while there is something for it to find, and kept
   while it is on"* gains the keyboard clause: and hidden while the keyboard is up unless
   it is on.

## Files

| New | |
|---|---|
| `ui/Search.kt` | `SearchField`, `SearchHeader`, `rememberTyping()` |

| Changed | |
|---|---|
| `ui/StockScreen.kt` | header + field → `SearchHeader`; chip rule |
| `ui/SoldScreen.kt` | same, and collapses for the first time |
| `ui/SessionsScreen.kt` | same |
| `ui/SessionDetailScreen.kt` | same, plus the name field's collapsed read-out |
| `ui/ScreenColumn.kt` | `top: Dp = 20.dp` |
| `ui/StockRow.kt` | `ItemThumb` 48 dp, padding 10 dp, divider |
| `ui/SoldScreen.kt`, `ui/SessionsScreen.kt` | the same on their own rows |
| `CLAUDE.md` | the two rules above, and the search paragraph |
