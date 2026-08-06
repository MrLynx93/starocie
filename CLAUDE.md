# starocie

A Kotlin Multiplatform app (Android + iOS) for two people who buy and sell
second-hand goods and want to know how profitable it is. Compose Multiplatform UI,
Firebase for storage and sync between two phones.

## The one principle everything else follows from

**Friction, not completeness.** A tracker that demands tidy bookkeeping gets
abandoned at the market stall. Partial records are first-class:

- an item's **name is required** — it is how the item is found when selling, by
  typing. Photos are optional and supplementary; they never carry identity
- costs may be unknown, and unknown must stay unknown rather than being guessed —
  an unknown cost comes from the shortcut sale, where there may genuinely have been
  no purchase to record; the buy form itself asks for the price, because at the
  moment of buying you know it
- adding must never block on the network
- the buy and sell paths are a handful of taps each

When a change would make the app more correct but more demanding, it is the wrong
change. Prefer tolerating a messy record over requiring a tidy one.

Two known users, one shared pot. Profit is never attributed to or split between
them; `createdBy` is provenance for debugging and must not appear in any statistic
or filter.

## Domain model

Four entities, `Event → Buy → Item → Sell`, each arrow one-to-many.

```
Event(1) ──< Buy(n) ──< Item(n) ──< Sell(n)
   └──────────────────────────────< Sell(n)
```

**Foreign keys live on the "many" side, without exception**: `Buy.eventId`,
`Sell.eventId`, `Item.buyId`, `Sell.itemId`. There is deliberately no `sellId` on
`Item` — an item has many sells, so one id could not represent it.

A `Sell` points at both an item and an event, and they usually disagree: things are
sold at a different event from the one where they were bought.

| Entity | Fields |
|---|---|
| `Event` | `id`, `date`, `name?`, `note?` |
| `Buy` | `id`, `eventId`, `date`, `price?`, `name?`, `note?`, `photoUrls` |
| `Item` | `id`, `buyId?`, `date`, `name`, `note?`, `photo?`, `price?`, `quantity`, `status` |
| `Sell` | `id`, `itemId`, `eventId`, `date`, `price`, `note?`, `quantity`, `soldCompletely` |

All four also carry `createdBy`, `createdAt`, `updatedAt`.

`status` is `IN_STOCK | REMOVED | SOLD`. `REMOVED` covered broken, lost, given away
or kept; **nothing writes it any more** — removing now deletes the item outright.
The value stays in the enum, and `ItemDoc` still parses it, because records written
before that change are still in Firestore and must keep loading.

### Invariants — break these and the numbers lie

1. **`Item.price` is the asking price, never what the item cost.** There is no cost
   field on `Item` at all. Cost lives on `Buy` and only on `Buy`. Label it "asking
   price" in the UI, because "price" on a thing instinctively reads as cost.
2. **Unknown cost stays unknown.** An item with no `buyId` was invented at point of
   sale; its cost and profit are `null`, never zero. Reporting such a sale as pure
   profit would inflate margins every time a shortcut is taken — exactly the
   behaviour this app exists to tolerate.
3. **Removing an item deletes it, and takes its buy with it once that buy is
   empty.** A buy exists to say what was paid for its contents, so one with nothing
   left in it is a price with nothing to be the cost of; a box therefore survives
   until the last thing out of it is deleted too. The cost of this is real and
   accepted: a `Sell` against a deleted item is left unresolvable, so its row reads
   "—" and its profit "nie wiemy" while its proceeds still count toward the event.
   Screens must degrade to an unknown here, never assume `itemById` resolves.
4. **Nothing derivable is stored** — no denormalised names, no device paths, no
   cached statistics. The inline photo is an exception only in appearance: it is
   original data, not a copy of something held elsewhere.
5. **`Item.quantity` is what the lot was, and is never decremented.** One means a
   single thing; more means a lot that sells in parts. What is *left* is derived —
   `Ledger.piecesLeft` subtracts the sum of `Sell.quantity` — so the running count
   is computed like every other statistic and cannot drift.
   **A lot leaves stock when its last piece goes**, worked out in the repository
   from the ledger rather than trusted from the caller, so two offline phones
   selling the last pieces converge. Selling **more** pieces than the record holds
   raises `Item.quantity` to meet the total instead of being refused — that is the
   one thing that ever writes it after creation, and it is a correction of a
   miscount, not a stock movement. `Sell.soldCompletely` stays as the override
   for "and the rest is not coming back" — kept, lost or given away — and it
   **defaults to false** on `recordSell`: a default of true would close a lot on
   every partial sale by a caller that had no opinion. For something that only ever
   was one thing the count reaches the end on its first sale anyway.
   The friction stays paid-for because the count defaults to **1** and is a stepper,
   not a field: the commonest answer costs no taps.
   A `Sell` written before `quantity` existed reads as one piece — those sales
   closed their lot with `soldCompletely`, so the count never had to be right for
   them. `piecesLeft` therefore floors at 1 while an item is `IN_STOCK`: an old lot
   can look oversold, and arithmetic must never be what makes something in the
   magazyn unsellable.
6. **Three kinds of "when", each with exactly one job:**
   - `createdAt` — audit stamp. Set once, never edited, never used for reporting.
   - `date` — defaulted from `createdAt`, freely editable, drives sorting/filters.
   - `eventId` — the *sole* authority for grouping.

   Never group by `date`. If one screen groups by `date` and another by `eventId`,
   an edited date silently yields two answers to the same question.

## Money

```kotlin
@JvmInline value class Money(val minor: Long)   // grosz
```

Every monetary field is `Money` — never `Long`, never a float. This is why fields
are named plainly (`price`, not `priceMinor`): the type enforces the unit at
compile time. A custom `KSerializer` stores it as a plain integer. Single currency
(PLN). Formatting happens only at the UI edge.

`@JvmInline` lives in `kotlin.jvm` but is usable from `commonMain` — required on
JVM/Android, a no-op on Kotlin/Native. Value classes are mangled crossing into
Swift, which does not matter here: iOS only receives a Compose `UIViewController`.

## Cost allocation

- **no buy** → cost unknown, profit `null`
- **sole item of its buy** → cost is the buy price, exact
- **one of many** → the buy price is split across items in proportion to
  `Item.price`, and flagged estimated

`Item.price` is optional, so the split degrades:

1. all items priced → proportional
2. some priced → unpriced items take the mean of the priced ones, then proportional
3. none priced → even split

**Rounding is largest-remainder**: floor each share, then hand out leftover grosz
one at a time, largest fractional remainder first. Shares must sum to *exactly* the
buy price, so a buy's profit always equals the sum of its items' profits. Naive
rounding drops a grosz and the two figures silently disagree.

A `REMOVED` item — only ever one written before removing became a delete — still
takes an allocation: its share is a loss, not a gap. A deleted one takes none, and
the box price simply redistributes across whatever is left of it.

Every estimated figure must be visibly marked. An exact cost and a guess must never
look alike.

## Statistics — computed, never stored

Pure functions over the in-memory collections, exposed as nested `stats` objects.
Nothing is written to Firestore, so nothing can drift. Unit-testable without
Firebase and identical on every platform.

- `Item.stats` — `sellCount`, `soldQuantity`, `proceeds`, `cost?`,
  `costIsEstimated`, `profit?`, `profitIsEstimated`, `soldAt?`
- `Buy.stats` — `itemCount`, `resolvedItemCount`, `cost`, `proceeds`, `profit?`,
  `fullyResolved`
- `Event.stats` — `spent`, `earned`, `buyCount`, `sellCount`, `itemsBought`,
  `itemsSold`

Buy-level profit is never an estimate — measured cost against measured proceeds.
Only the split beneath it is inferred.

`Event.stats` is cash in and cash out, **not** profit: what you sell at an event is
rarely what you bought there. Never subtract one from the other.

Profit is sale prices minus buying prices. No fees, no shipping, no overhead.

## Firestore

**Four flat sibling collections — not nested subcollections.**

```
workspaces/{wsId}                      members: [uid, uid]
workspaces/{wsId}/events/{eventId}     id = ISO date when auto-created
workspaces/{wsId}/buys/{buyId}
workspaces/{wsId}/items/{itemId}
workspaces/{wsId}/sells/{sellId}
```

The hierarchy lives purely in the reference fields. Nesting is impossible because
`Sell` has two parents; it would also turn "all `IN_STOCK` items" (the Sell grid,
the most important query in the app) into a `collectionGroup` query, and turn
reassigning a buy to another event into a document move.

Security rules are a single predicate: the caller's uid is in the workspace's
`members` array. No per-collection variations.

- **Firestore, not Realtime Database**, for its offline persistence: markets have
  bad signal, and it gives durable local writes and automatic sync for free.
- **No SQLDelight** — Firestore's cache *is* the local database.
- All collections are held in memory. That is what makes computed statistics, name
  joins and instant offline search viable. Revisit only at orders-of-magnitude
  growth.
- **Multi-document writes use `WriteBatch`, never a transaction.** Transactions
  need a server round-trip and fail offline, breaking the stall case exactly when
  it matters.
- **Writes never block on the network.** Firestore's local cache echoes the write
  immediately and the UI reflects that optimistic state. Never show a spinner while
  saving.

### Events are automatic but real

Recording a buy or sell resolves the current event: find today's, create it if
absent, attach. The user is never asked.

**The auto-created event's document id is the ISO date** (`2026-08-01`). This is
load-bearing: at a market both phones are likely offline, and with random ids each
device would invent its own "today" and produce duplicate events for one day. A
deterministic id means both write the same document and converge on reconnect. Use
`SetOptions.merge`. Extra events on a day are user-created and get UUIDs.

## Photos

Optional and supplementary — an item is found by typing its name, never by
recognising a picture. A photo simply helps you spot the right thing in a list.

**A small JPEG is stored inline on the item document, Base64 encoded.** Cloud
Storage requires the paid Blaze plan, and photos are not worth a card on file at
this scale — so the picture rides in the document instead:

- capture is handed to the platform camera app, so no CAMERA permission is
  declared and none has to be requested
- it captures to a file via `TakePicture`, not `TakePicturePreview` — the latter
  needs no FileProvider but returns the camera's ~150px thumbnail, which is fine
  in a list and useless full-screen
- the result is scaled to 640px on its long edge and compressed at JPEG 75,
  roughly 45 kB against Firestore's 1 MiB document limit
- **that size is a footprint multiplier**, because every item is held in memory:
  comfortable for a few hundred photographed items, not for a few thousand. Past
  that, photos belong in Cloud Storage with only URLs in the document
- tapping a photo opens it full-screen; overlay buttons retake or remove it
- being part of the document, it syncs like everything else, so both phones see
  it — which local-only files would not have achieved

`photoUrls` remains on the model for a future move to Cloud Storage. If that
happens, the inline photo becomes the thumbnail and the URL the full-resolution
original.

On iOS the same 640px / JPEG 75 pipeline runs through `UIImagePickerController`,
and the picker owns the camera UI exactly as the camera app does on Android — so
again no permission is requested, only the `NSCameraUsageDescription` string iOS
shows when the picker opens.

- **the source falls back to the photo library when no camera exists**, which is
  every simulator. Without that, the feature could not be exercised at all without
  a physical phone
- there is no Exif dance: `drawInRect` already honours `imageOrientation`, so
  redrawing at the smaller size uprights the photo as a side effect. Android has to
  read the tag and rotate the pixels itself
- decoding goes straight from JPEG bytes to `ImageBitmap` through Skia, which
  Compose has already linked in, so the photo never becomes a `UIImage` to be shown

## Theme

Warm brass-and-patina rather than the default purple, in a light and a dark
variant — and **`isSystemInDarkTheme()` is never consulted.** A phone that lives in
dark mode all day still wants this one bright, and a stall in daylight is exactly
where the dark palette reads worst. So the app starts light and stays wherever the
home-screen switch puts it.

The choice is stored **on the device, not in the workspace**: it is a preference
about this phone's screen, not a fact about what the two of us bought, and the
other phone has its own. `SharedPreferences` on Android, `NSUserDefaults` on iOS,
behind one `expect fun rememberThemeChoice()` — one string does not need a
database, and Firestore would sync it to the wrong place.

Ignoring the system setting has a tail: the clock and the gesture bar are drawn by
the system, which colours them from the *phone's* setting, so a bright screen on a
dark-mode phone meant white icons on white. `SystemBarsAppearance(dark)` passes the
choice on to the window. It is a no-op on iOS, where the status bar takes its style
from the hosting view controller rather than a window flag — that would have to be
done in Swift, and the light palette the app starts in is legible either way.

## Signing in

Two known people, one workspace — sign-in exists so the rules have a uid to check
against `members`, not to support arbitrary users. Which is exactly why **one
person must be one account however they sign in**: a second uid for the same human
would read as a third member, and would sign `createdBy` with a stranger's name.

- **E-mail and password** is the original route and still the primary button.
- **Google** sits under it, behind a divider. The button **only appears when the
  build can honour it** — `rememberGoogleSignIn` returns null otherwise, and a
  button that always fails is worse than one that is not there.
- **The two meet on one account.** Firebase does not merge them by itself: signing
  in with Google on an e-mail that already has a password raises a collision. The
  token is then *held*, `SignInResult.NeedsPassword` comes back, and the screen asks
  for the password once — the next successful password sign-in links the held
  credential to that same user, after which either route works. A failure to link
  is swallowed on purpose: the sign-in worked, and the cost is being asked again.

Android uses **Credential Manager**, not `GoogleSignInClient`, which is deprecated
and going away; the system account sheet means there is no sign-in UI to design.
The web client id is read **by resource name at runtime**, not as `R.string`, so a
project without Google enabled still compiles — the same leniency the missing
`google-services.json` already gets.

**iOS has no Google button yet.** It needs Apple's `GoogleSignIn` SDK in the Xcode
project over SPM, a reversed-client-id URL scheme in `Info.plist`, and a Swift
entry point to present the sheet — none of which can be verified without the
`GoogleService-Info.plist` this repo does not carry. The e-mail pair works there.

The stall mark on that screen is **drawn, not loaded** — `StallMark` is the icon's
geometry in Compose, so there is no image resource and no density set to keep in
step, and it is sharp at 112 dp where a launcher PNG would not be. `GoogleLogo` is
Google's own asset in the same form: an `ImageVector` carrying their path data and
their four colours verbatim, because their terms do not allow the mark to be
redrawn or recoloured.

**Compose resources do not work in this project — do not reach for them.** A
`composeResources/drawable/*.xml` generates its accessor and compiles, so the
mistake looks fine right up until the device throws: `:shared` uses AGP's KMP
library plugin, and nothing wires `prepareComposeResourcesTaskForCommonMain` into
its asset packaging, so the file never enters the APK — which contains no `assets/`
entries at all. Vectors therefore live in code as `ImageVector`s, the way Material
ships its own icons.

## The icon

A market stall's canopy, flat and face-on: the peaked roof with its concave sweeps
down to the eaves, a scalloped valance, two legs. Four stripes in the same market
colours on the app's own cream, so the launcher tile matches the screen behind it.
The scallops are load-bearing — with a straight valance the shape reads as a table,
which is what every earlier attempt looked like.

**`icon/` holds the masters and everything else is generated from them**, so the
icon is edited in one place and re-rendered rather than touched up per density:

| Master | Renders to |
|---|---|
| `starocie-icon.svg` | legacy `ic_launcher.png` (48–192 dp), Play Store 512 |
| `starocie-icon-round.svg` | legacy `ic_launcher_round.png` |
| `starocie-icon-foreground.svg` | adaptive-icon foreground (108 dp, 5 densities) |
| `starocie-icon-monochrome.svg` | the themed-icon silhouette |
| `starocie-icon-square.svg` | iOS `AppIcon-1024.png` |

The foreground master is the art at **0.66 scale, centred on its own bounding box
rather than the canvas** — its centre of mass is above the middle, because the legs
reach further down than the roof reaches up. That keeps the furthest point (a leg's
bottom corner) inside the 66 dp keyline, so no launcher mask clips it. Check a
change against a circle mask before shipping it; the square one forgives anything.

    rsvg-convert -w 432 -h 432 icon/starocie-icon-foreground.svg -o fg.png

`minSdk` is 26, so adaptive icons are always available and the legacy PNGs are only
a fallback for tooling that asks for one. iOS takes a single 1024 with **no alpha
channel** — a transparent one is rejected at upload — which is why that master has
a square background rather than the rounded tile.

## Architecture

Single `shared` module — `commonMain` / `androidMain` / `iosMain`, packaged by
layer, with `androidApp` and `iosApp` as thin hosts around it. Splitting the shared
code into several Gradle modules at this size costs more than it returns. Android
and iOS only; no desktop target.

`iosApp` is an Xcode project, not a Gradle module: two Swift files, one of which
presents `MainViewController()` and the other calls `FirebaseApp.configure()`. All
the screens live in `commonMain`, so there is nothing else for Swift to do.

- Compose Multiplatform UI, `androidx.lifecycle` ViewModel (KMP)
- `androidx.navigation` multiplatform
- Koin for DI
- GitLive `dev.gitlive:firebase-kotlin-sdk` — firestore, auth, storage
- Coil 3 for images
- kotlinx: coroutines, datetime, serialization
- Repositories expose Firestore snapshot listeners as `Flow`; ViewModels combine
  the streams and compute `stats`; Compose collects `StateFlow`
- `CurrentEventResolver` — the single place the auto-grouping rule lives
- `ItemNameSuggester` — interface with a no-op default; photo-to-name recognition
  is a later feature and must drop in without reshaping the model

### Conventions

- **No `java.*` in `commonMain`.** kotlinx-datetime for time, `kotlin.uuid.Uuid`
  for ids. Keep the iOS target compiling at all times, even during Android-only
  work — the compiler is what enforces this.
- **Screens never touch Firestore directly.** All access goes through a repository;
  screens observe `Flow`s and call suspend functions on a state holder.
- Domain models are immutable data classes.
- Observing functions return `Flow<T>`; one-shot operations are `suspend`.
- Ids are generated client-side, so a record exists locally the instant it is made.
- Prefer `sealed interface` over strings and booleans for status and result types.

## Voice

Polish, and **always the "we" form** — this is two people's shared notebook, so it
speaks as us, never at the user:

- fields and readouts say what *we* did or want: "Kupiliśmy za", "Chcemy sprzedać
  za", "Sprzedaliśmy za", "Mamy 12 przedmiotów"
- hints keep the same person — "Wpisujemy po kolei", "Nie wiemy, za ile
  kupiliśmy? Zostawmy puste" — never "Zacznij pisać", "Nie wiesz", "Sprzedasz"
- **buttons stay imperative** ("Zapisz", "Wstecz", "Anuluj", "Usuń"): a button is
  an instruction to the app, and "Zapiszmy" reads as a suggestion rather than a
  control
- friendly and plain-spoken. Say what happened rather than name a quantity: a loss
  is "straciliśmy 5,00 zł", not "zysk -5,00 zł". An unknown is "nie wiemy"
- an estimate always says so — "ok. 12,00 zł", with what makes it a guess spelled
  out underneath
- a thing in stock is a **przedmiot**, never a "rzecz" — one word for it everywhere
- count words agree with the number (`przedmioty(n)`, which carries the full
  1 / 2–4 / rest rule and the teens exception); "1 przedmiotów" is the small
  wrongness that makes an app feel like a machine

## Screens

**Every screen but Home ends in the same "Wstecz" button** — bottom of the screen,
outlined, arrow and word, always doing exactly one thing. That is where the thumb
already is, and having one guaranteed exit is what lets the primary button be
strict about its required fields instead of quietly doubling as the way out. It
replaced a "Gotowe" here, a "Wróć" there and an "Anuluj" on the two forms that
write nothing until their main button is pressed.

- **Home** — "Nasze starocie" at the top with **the light/dark switch on its line**,
  right-hand end: it is the only app-wide setting there is, and a top bar to hold
  one button would cost every screen height it earns nothing with. The icon shows
  what the tap *gives* you — a sun to go bright, a moon to go dark.
  The current event's name or date sits under it
  as a caption: it says which day the figures belong to and is not a control. The
  round buttons align their icon and label to the left edge, so three labels of
  different lengths read as one stack rather than three unrelated buttons. Two
  summary cards sit under the day — stock and sold — each a read-out with its list
  behind it, then recent activity.
- **Buy** — two ways in, "Kup" and "Kup paczkę", over a single item form. The form
  **opens with the photo**, in the order it actually happens: the thing is in your
  hand, so it is photographed and then described. One item of its own buy → exact
  cost. Several in a box → the box total, allocated. Never asks for a per-item cost.
  **Neither path asks when it was bought.** A buy is dated the day it is entered,
  which is nearly always the day it happened; the field earned a tap on every
  purchase to correct the rare one. `Buy.date` and `Item.date` stay editable in the
  model for a later edit screen — the entry forms simply do not ask.
  **A name and a price are both required**, and "Kup" is disabled without them:
  the price is the one number you cannot fail to know while buying, and a blank
  there would be a skipped field rather than an honest unknown. It stays disabled
  on an untouched form too — it used to be the only way back out, and "Wstecz" is
  that now, so it no longer has to double as an exit.
  The count sits beside what was paid: one lot, one price, so many things. A box
  was paid for once, so there the count stands alone.
  **Nothing is focused on arrival** — the screen opens whole, keyboard down, since
  the first move is as often the camera as the name. Focusing a text field *is* the
  request for the keyboard, so the two cannot be separated without hiding it again
  a frame later and losing the race half the time. Enter in the name moves to the
  price rather than opening a third line, and after a save the name takes focus
  with the keyboard, because a run of purchases is a run of typing.
  The form **scrolls under two pinned buttons**. The app draws edge to edge, so the
  keyboard covers the window rather than shrinking it; without `imePadding` on the
  root column, "Zapisz" is exactly what ends up underneath it.
- **Stock and Sell are one screen.** They were the same `IN_STOCK` list twice —
  a search box for selling, a browse list for looking — and typing a name is how a
  thing is found either way, so the search belongs to both. Newest first, a heading
  and the count and asking total over it, and **a row opens the item, always**: a
  row that did one thing from one door and something else from the other is exactly
  the kind of difference that gets learned wrong once and then costs money. The
  photo is no longer a separate target, having nowhere else to go.
  **A row carries what we paid**, the way the sold list does — "kupiliśmy za",
  "kupiliśmy za ok." for a share of a box, "nie wiemy, za ile kupiliśmy" — because
  the asking price alone does not say whether there is a gap worth stopping at.
  The count and the total are computed over **what is on screen**, so a search
  answers for what it found rather than for the whole magazyn.
  **The route is the only difference.** Arriving from "Sprzedaj" adds the
  "Dodaj … i sprzedaj" button, pinned at the bottom above "Wstecz" rather than
  sitting under the search box where it used to shove the list down a line every
  time the typing stopped matching. From the magazyn card it is simply absent.
  **The item screen puts the facts above and the three buttons below** — Sprzedaj,
  Usuń, Wstecz — pinned, so what you can do about a thing is always in the same
  place under the thumb while what it is scrolls past.
  **The photo is the same camera target as on the buy form**, not a read-only view:
  a thing shot in a hurry at a stall is exactly the thing worth shooting again in
  better light. Retaking writes straight to the item — there is no draft here to
  hold it in — so backing out of the camera is the only way not to change it, and
  the bin drops the picture in one tap without a confirmation, a photo being
  supplementary rather than a number anything depends on.
  The read-outs come next, **the date at the top** — the one fact here that was
  never a choice — then what it has already taken, then the note. **Both prices are
  editable fields** below them, because both are still decisions: one gets mistyped
  or skipped in a hurry, the other changes every time a thing sits unsold. They
  **save half a second after the typing stops**, with no confirm button: Firestore
  takes the write locally anyway, and a price change that depends on remembering to
  press something is a price change that gets lost.
  **"Sprzedaj" here sells in one tap**, at whatever stands in the asking-price
  field, and asks nothing in between — the number is already on screen and already
  editable, so a dialog would only be a second place to type it. The button waits
  for a price, since without one there is nothing to sell at. The field's text is
  held by the screen rather than the field, so a price typed and sold on in the same
  motion goes out at the new number instead of racing the half-second save.
  **A lot is the one exception, and the only thing the sell dialog is still for**:
  a piece goes at its own price, so "Sprzedaj" opens the dialog instead. The
  **count leads it** — a stepper starting at 1, reading "z 9" beside it — because
  the count is what the price depends on and the thing only somebody standing at
  the stall knows. Since the list stopped selling from under the thumb this is the
  only way to that dialog, so the button does not wait for an asking price on a lot.
  **The price follows the count**, multiplied up from what one piece was asked for:
  three at 15,00 zł fills in 45,00 zł, and the label says what the number covers —
  "Sprzedajemy 3 sztuki za", with `sztuki(n)` carrying the same 1 / 2–4 / rest rule
  as `przedmioty(n)`. It multiplies the price the dialog *opened* with, not whatever
  is in the field, so stepping up and back down lands where it started instead of
  compounding; a price typed by hand stands until the count moves again.
  **Taking the last pieces ticks "Sprzedaliśmy już wszystkie" itself** and stops
  offering it as a choice, there being nothing left for it to write off. Below that
  it is still worth asking, and it no longer means "this finishes it" — the count
  means that now — but that the rest was kept, lost or given away.
  **The count has no ceiling.** Selling more pieces than the lot was recorded as
  holding raises `Item.quantity` to meet the total rather than being refused, and
  the dialog says so: "Było ich więcej, niż zapisaliśmy — poprawimy paczkę na 22
  szt." A box counted in a hurry comes out short far more often than a piece appears
  from nowhere, the pieces in your hand outrank a number typed at a stall, and the
  sale is how we find out — so it is the correction, not an error.
  A half-sold lot shows what is left rather than what it started as, in the list
  ("zostało 9 z 12 szt.") and above the item ("Zostało 9 z 12 szt.").
  What was paid is the **buy's** price, not the item's, and the field says which it
  is editing: alone in its buy it reads "Kupiliśmy za", and with siblings it reads
  "Całą paczkę kupiliśmy za" with this item's share spelled out underneath as a
  guess. Typing a price into an item that had no buy **opens one holding only that
  item**, which is how a cost unknown at the point of sale becomes exact later;
  clearing that same field records nothing, because inventing an empty buy would
  turn an honest unknown into a claim that we paid zero.
  **Removing lives here and nowhere else.** It used to sit inside the sell dialog,
  a thumb-width from the price field, where the one screen you reach by hunting
  for something to sell also offered the button that resolves an item with no
  proceeds. Selling stays one tap; deleting a thing costs a deliberate detour and a
  confirmation, and its button is **red and says only "Usuń"** — it destroys a
  record now, so it must not read like the neutral way out directly beneath it.
  The detail screen leaves by itself the moment its item stops being `IN_STOCK` or
  stops existing, so a completed sale or a deletion lands back in the list; a lot
  sold in part stays put and shows the extra sale.
- **Sold** — the mirror of the stock list, reached from the second home card:
  everything `SOLD`, newest sale first. Each row answers "was it worth it" without
  a tap: **the pair it is drawn from on the left**, what we paid above what we took,
  and **the profit alone on the right**, said as a loss rather than written as a
  negative gain when it is one. A share of a box is marked "ok." on both, so a guess
  never looks like a measured price. A lot
  sold in part is still `IN_STOCK` and stays in the magazyn list; a deleted thing
  is in neither, having stopped existing.
  **A row opens the thing, the way it does in the magazyn**, onto the sold item
  screen — the counterpart of the one in stock, for a thing there is nothing left to
  decide about but plenty left to correct. Four numbers make the whole record and
  every one of them can be mistyped or dated a day late, so **the buying date, what
  we paid, the selling date and what it went for are all fields**, saving themselves
  half a second after the typing stops exactly as the magazyn's prices do. The profit
  sits at the top and is recomputed from them as they change, which is what says the
  correction landed; it is the only figure on the screen nobody entered.
  A **date is picked from a calendar, never typed** — it is the one value the
  keyboard offers no help with, and every separator is a chance to record a day that
  never happened. **The event never follows the date**: grouping is `eventId`'s alone,
  and a date put right weeks later must not silently move a sale into another day's
  takings. Correcting the buying date moves the **buy** with it only when that buy
  holds this item alone; a box was bought once, whatever one thing out of it turns
  out to be dated.
  A lot that went in several sales gets a date and a price **per sale**, each
  happening on its own day for its own money, with the total underneath.
  There is **no "Usuń"** here — deleting belongs where a thing still exists to be got
  rid of, and erasing a sold item would only lose the proceeds it is the record of.
  The photo shows if there is one, with the bin but **no camera**: an empty capture
  target on something that is no longer ours would invite photographing somebody
  else's.
- **Selling a thing that was never recorded is a first-class path**, not a fallback:
  nothing is in the app to begin with, and requiring everything to be entered before
  it can be sold is exactly the friction that gets a tracker abandoned. "Add new"
  offers the whole buy form — name, what was paid, pieces, note, photo — alongside
  the final price, and `recordBuyAndSell` writes the buy, the item and the sale in
  one batch. It gets **a screen of its own rather than a dialog**, sharing
  `SellViewModel` with the list behind it so the typed search seeds the name: it
  carries as many fields as the buy screen, and a dialog held them against the
  keyboard with the photo pushed off-screen. **What was paid is optional and empty is a real answer**: it leaves the
  item with no buy, so the cost stays unknown rather than becoming zero. A stated
  price opens a buy holding only that item, so its cost is exact and the allocator
  is never involved.
- **Buying splits in two, but there is only one item form.** "Kup" records one
  thing at one price and clears for the next, so its cost is exact and the
  allocator is not involved. "Kup paczkę" is a two-step wizard: the price first, which
  opens the buy, then *the same item screen* with the price field hidden and items
  appended to that buy. Unpacking a box is deliberately the same motion as buying
  things one at a time.
- The box's buy is created **before** its contents are known, so each item is saved
  as it is unpacked. Accumulating drafts and writing them at the end would lose the
  lot if the user backed out.

## Setup

Neither secrets file is committed — each machine needs its own:

- `androidApp/google-services.json` (Android)
- `iosApp/GoogleService-Info.plist` (iOS)

Both are gitignored. A second developer needs copies from the Firebase console.

The `com.google.gms.google-services` plugin is applied only when the JSON is
present, so a fresh clone still builds and runs — Firebase is inert until the
config arrives, rather than the build failing on a missing secret.

### Turning the Google button on

It is hidden until the project can honour it, and three things in the Firebase
console are what make that true:

1. **Authentication → Sign-in method → enable Google.**
2. **Add the signing SHA-1** under Project settings → your Android app. The APK is
   a *debug* build, so it is the debug keystore's fingerprint that matters:
   `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey
   -storepass android -keypass android`. CI generates its own debug keystore, so an
   APK from Actions needs that machine's fingerprint too, or Google sign-in will
   fail there while working locally.
3. **Re-download `google-services.json`.** Only then does it carry an `oauth_client`
   of type 3, from which the plugin generates `default_web_client_id` — the string
   `rememberGoogleSignIn` looks up. Until it exists, the lookup returns null and the
   button simply is not drawn.

A quick check that a given JSON will work: `oauth_client` is not `[]`.

**iOS is deliberately the opposite: no plist, no build.** The Android leniency
buys something real — an APK worth having even without Firebase. On iOS there is
no equivalent prize, and `FirebaseApp.configure()` traps at launch without the
file, so `GoogleService-Info.plist` is a member of the Resources phase and its
absence is a build error rather than a crash on the phone.

### Running on iOS

`iosApp/iosApp.xcodeproj` — open it, pick a simulator, run. From the terminal:

    xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
      -destination 'platform=iOS Simulator,name=iPhone 17' build

The Kotlin side is a **static** `Shared.framework`. A "Build Kotlin framework" run
script phase runs `:shared:embedAndSignAppleFrameworkForXcode` ahead of the Swift
compile, so Xcode is always linking a framework built from the current sources —
there is no separate Gradle step to remember, and no stale framework to be
confused by.

The **Firebase iOS SDK comes in over Swift Package Manager**, pinned in
`Package.resolved`. GitLive's iOS artifacts are cinterop bindings *over* Apple's
Firebase SDK rather than a reimplementation of it, so without those packages the
Kotlin framework has nothing to resolve its `FIR*` symbols against and the app
fails at link time, not at runtime. `Package.resolved` is therefore committed,
unlike the rest of Xcode's droppings.

Two things Xcode does not come with:

- **the iOS simulator runtime**, a separate multi-gigabyte download since Xcode 16.
  Without it `xcodebuild` reports "Unable to find a destination", which reads like
  a project fault and is not one: `xcodebuild -downloadPlatform iOS`
- **the right `xcode-select`.** A machine that had the Command Line Tools first
  keeps pointing at them, and then there is no `xcodebuild` at all:
  `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`

### Getting an APK onto a phone

`.github/workflows/android-apk.yml` builds `:androidApp:assembleDebug` and
attaches the APK to a rolling `latest-apk` prerelease, so installing is opening
one URL on the phone rather than plugging it into a laptop:

    https://github.com/MrLynx93/starocie/releases/tag/latest-apk

**Debug, not release**, because the debug keystore is generated and an unsigned
release APK will not install at all. Every push to `main` or a `claude/**`
branch rebuilds it and moves that download on.

To rebuild by hand, use **Actions → Android APK → Run workflow**. It leaves the
release alone by default and attaches the APK to the run instead, because a
hand-started run is usually a question about the build rather than something to
put on a phone; tick *publish* to move the download too. Re-running an existing
run is not the same thing — it replays the original push event and so does
republish.

That button only appears once this workflow is on the **default branch**:
`workflow_dispatch` is invisible, from the UI and the API alike, while the file
lives only on a feature branch.

Firebase is inert without config, and `App()` reaches for `Firebase.auth` on its
first frame — so an APK built without it installs and then dies on launch. CI
gets its copy from a repository secret, base64 so it survives as one line:

    base64 -w0 androidApp/google-services.json

Paste that as the `GOOGLE_SERVICES_JSON` secret under Settings → Secrets and
variables → Actions. The file itself stays out of git; only Actions ever holds
it. Pasting the raw JSON works too — the workflow takes either form.

Without the secret the build still succeeds and warns, because an APK is still
worth having. A secret that is present but unusable fails the build instead: a
bad config produces a green build and an app that dies on the phone, which is
the one outcome worth spending a red build to prevent. The workflow then checks
that the plugin really generated the Firebase resources, since applying it is
conditional and "the secret was set" does not prove "the app can reach
Firebase".
