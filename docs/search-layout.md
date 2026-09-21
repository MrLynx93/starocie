# Search as a place you go, not a box that is always there

**Built.** This is the note the change was made from; it is kept as the reasoning
behind it rather than as a proposal. Where the code differs from what is written here,
the differences are listed at the end.

Supersedes the first draft, which kept the search box permanently on screen and only
made it shorter. That was the wrong shape: a box you are not typing into is a box
earning nothing, and shrinking it from 56 dp to 44 dp still left it, its heading, its
figures and its margins between you and the thing you were looking for.

## Where it goes wrong today

Four screens carry the search box — the magazyn, the sold list, the giełdy (and its twin
"Nasze zakupy") and one giełda's own screen. It is four copies of the same
`OutlinedTextField`, each with its own spacers, and it is 56 dp tall because Material 3
floors its text fields there; no parameter goes under it.

On the phone, with the keyboard up, the selling list shows **two and a half rows**. The
heading is still there, the box is still a bordered form field, the photos are 52 dp and
set a 77 dp row height that two lines of 12 px text do not need, and there is a strip of
nothing between the pinned button and the keyboard.

## The shape of the fix

### 1. At rest, the search is one icon

A 44 dp magnifier at the **right-hand end of the heading's line** — the same place, the
same size and the same idea as the home screen's light/dark switch. Nothing else changes
at rest: the heading, the figures under it and the list are as they are.

```
 Co chcesz sprzedać?                       ⌕
 Mamy 34 przedmioty · Chcemy sprzedać za …
 ────────────────────────────────────────────
 [list]
```

### 2. Tapped, the screen becomes the list

The heading goes. The figures go. "Wstecz" goes. The unpriced chip goes. What is left is
the line you are typing into, the list, and — **only on the selling route** — "Dodaj … i
sprzedaj".

```
 ⌕  wazon                                 ✕
 ────────────────────────────────────────────
 [list, all the way down]
 ┌──────────────────────────────────────────┐
 │        Dodaj "wazon" i sprzedaj          │
 └──────────────────────────────────────────┘
 ▁▁▁▁▁▁▁▁▁▁▁ keyboard ▁▁▁▁▁▁▁▁▁▁▁
```

The search line has **no border, no container and no fill** — a magnifier, the text at
17 px, and a way out, over a hairline rule. A search box is chrome; drawing a form field
round it is asking a question the screen is not asking.

**The heading's job passes to the button.** CLAUDE.md keeps the heading because it says
which of the two doors you came in by, and that is right — but while searching, the
selling route is the one with "Dodaj … i sprzedaj" at the bottom and the magazyn route is
the one with nothing. The distinction survives the heading going, carried by the thing
you would actually press.

**Getting out.** ✕ clears what is typed; pressed on an empty box it closes the search and
the heading comes back. System back closes the keyboard first, then the search, then the
screen — three presses, each undoing exactly one thing.

### 3. Rows lose the photo's height

The photo goes **52 dp → 40 dp** and the padding 12 → 8. That flips which thing sets the
row height: a two-line row is now as tall as its two lines, 56 dp, and a three-line one
(the sold list, a lot in the magazyn) is 68. The rules between rows go with it — the
photo column already gives the list its rhythm, and the rule under the last row was a
line to nowhere.

A photo is supplementary in this app; an item is found by typing its name, and the
picture only helps you spot the right one in a list. At 40 dp it still does that, and it
stops deciding how much of the list you can see.

### 4. The gap under the button

Needs one measurement on the phone before a fix is chosen. While typing, `ScreenColumn`
is on `bottom = 8.dp` and "Wstecz" is hidden, so 8 px is all that should be under the
button — the screenshot shows roughly 56. Something is still paying for the navigation
bar after `imePadding` has already covered it, which is the *same* double-payment the
`consumeWindowInsets` comment in `ScreenColumn` was written to prevent; this phone has a
three-button nav bar, which is the case that comment does not obviously cover.

The fix I would reach for is to drop `Scaffold` from `ScreenColumn` and take the insets
once — `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` — so there is one source
of the bottom edge instead of two that have to agree.

## What it comes to

390×844 with the keyboard up: 44 px of status inset, 500 px of app, 300 px of keyboard.

| While typing | rows today | rows after |
|---|---|---|
| sell list (with the add button) | 2.5 | **6.5** |
| magazyn, browsed | 2.5 | **7.5** |
| sold | 3.5 | **6.0** |
| one giełda | 2.0 | **6.5** |

Chrome above the list falls from 178 px (sell) and 308 px (giełda) to **65 px
everywhere**: 8 px of top padding, a 48 px line, a hairline, 8 px of air.

**No predicate changes.** `Item.matchesQuery`, `Event.matchesQuery`, the sold list's
filter and the day's two-section narrowing are untouched, and the figures above the list
stay the magazyn's and the day's own rather than the search's. The one new piece of state
is whether the search is open.

## What this deliberately does not do

- **No top app bar.** The icon sits on the heading's own line, as Home's theme switch
  does — not in a bar that would cost every screen height it earns nothing with.
- **The search does not scroll away** with the list. One you have to scroll back up to is
  one you retype into.
- **No wording changes.** "Czego szukasz?" stays, in what is now genuinely one place.
- **The figures do not follow the search.** They are hidden while searching, not
  recomputed over what was found.

## How the code came out

Three things were settled while building, and one of them against what is written above.

1. **The hairline stayed.** Without it the typed line runs straight into the first row
   and the two read as one block.
2. **The unpriced chip goes while searching**, as written. A list narrowed for a reason
   you cannot see is a list lying about itself, and that is why it survives *closing* the
   search — the query is thrown away, so nothing is narrowed invisibly.
3. **The gap under the button was fixed rather than measured first.** The note above said
   to measure before choosing, and on reflection the structure was wrong whatever the
   measurement said: `ScreenColumn` was taking the bottom edge from a `Scaffold` and from
   `imePadding` with a `consumeWindowInsets` in between to stop it being paid for twice —
   three things that had to agree. It is now one `windowInsetsPadding(safeDrawing)`,
   which already unions the status bar, the navigation bar and the keyboard and so cannot
   disagree with itself. **If the strip is still there on the phone, this was not it** and
   the next place to look is the button's own container.

Two things beyond what is written above:

- **A giełda's own "Sprzedaj" hides while its search is open**, along with "Wstecz".
  That screen's search is over the day's own two sections, and a button that leaves for
  the magazyn is not what a failed search there wants next.
- **The magazyn reopens its search when it comes back holding a query.** `SellViewModel`
  outlives a trip to "Dodaj … i sprzedaj" and back, so without that the list would return
  narrowed by typing that was nowhere on screen.

Still yours to judge: **40 dp for the photo**. It is a question about spotting a thing at
a stall in daylight, which is not mine to answer.

## Not verified here

There is no Android SDK and no macOS in the environment this was written in, and the
`shared` module has no other target — so **none of this has been compiled**. It wants a
build and a run on the phone before it is trusted, and the three things most likely to
need a second pass are the `ImeAction.Search` key, the back-press order (keyboard, then
search, then screen) and the gap above.

## Two things in CLAUDE.md this changes

1. *"The magazyn list alone puts ["Wstecz"] away while its search is being typed into"* —
   after this it is all four searchable lists, and the heading and figures go with it.
2. The heading rule gains its exception: while the search is open the heading is not
   drawn, and on the selling route the "Dodaj … i sprzedaj" button is what says which
   door you came in by.

## Files

| New | |
|---|---|
| `ui/Search.kt` | `SearchIcon`, `SearchLine`, and the open/closed state |

| Changed | |
|---|---|
| `ui/StockScreen.kt` | heading row gains the icon; everything but the list and the add button hides while open |
| `ui/SoldScreen.kt` | same |
| `ui/SessionsScreen.kt` | same |
| `ui/SessionDetailScreen.kt` | same; the name field and the day's figures hide while open |
| `ui/ScreenColumn.kt` | one inset source instead of two (the gap) |
| `ui/StockRow.kt` | `ItemThumb` 40 dp, padding 8 dp, no divider |
| `ui/SoldScreen.kt`, `ui/SessionsScreen.kt` | the same on their own rows |
| `CLAUDE.md` | the two rules above, and the search paragraph |
