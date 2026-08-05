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
| `Sell` | `id`, `itemId`, `eventId`, `date`, `price`, `note?`, `soldCompletely` |

All four also carry `createdBy`, `createdAt`, `updatedAt`.

`status` is `IN_STOCK | REMOVED | SOLD`. `REMOVED` covers broken, lost, given away
or kept — without it such an item never leaves stock and its buy never resolves.

### Invariants — break these and the numbers lie

1. **`Item.price` is the asking price, never what the item cost.** There is no cost
   field on `Item` at all. Cost lives on `Buy` and only on `Buy`. Label it "asking
   price" in the UI, because "price" on a thing instinctively reads as cost.
2. **Unknown cost stays unknown.** An item with no `buyId` was invented at point of
   sale; its cost and profit are `null`, never zero. Reporting such a sale as pure
   profit would inflate margins every time a shortcut is taken — exactly the
   behaviour this app exists to tolerate.
3. **Items are never hard-deleted.** `REMOVED` is the exit. Every `Sell.itemId`
   must stay resolvable.
4. **Nothing derivable is stored** — no denormalised names, no device paths, no
   cached statistics. The inline photo is an exception only in appearance: it is
   original data, not a copy of something held elsewhere.
5. **`quantity` is a count, not a stock level.** One means a single thing; more
   means a lot that may sell in parts. It is *not* decremented per sale — a
   splittable lot stays in stock until a sale is marked as completing it, which
   is the deliberate choice against forcing an accurate running count.
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

Removed items still take an allocation — their share is a loss, not a gap.

Every estimated figure must be visibly marked. An exact cost and a guess must never
look alike.

## Statistics — computed, never stored

Pure functions over the in-memory collections, exposed as nested `stats` objects.
Nothing is written to Firestore, so nothing can drift. Unit-testable without
Firebase and identical on every platform.

- `Item.stats` — `sellCount`, `proceeds`, `cost?`, `costIsEstimated`, `profit?`,
  `profitIsEstimated`, `soldAt?`
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
- **buttons stay imperative** ("Zapisz", "Gotowe", "Anuluj", "Usuń"): a button is
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

- **Home** — big BUY and SELL buttons over recent activity; the current event's
  name sits at the top, tappable to name it. The stock summary card is the way in
  to the stock list.
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
  there would be a skipped field rather than an honest unknown. An untouched form
  keeps "Kup" enabled, because there it is only the way back out.
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
- **Sell** — a search field over a list of `IN_STOCK` items: type, tap the row,
  enter the final price, done. Splittables show a note field and a "fully sold"
  tick setting `soldCompletely`. The dialog does only that one thing.
- **Stock** — the same `IN_STOCK` list, newest first, reached from the home
  summary card and browsed rather than searched: the sell screen answers "what am
  I holding", this one answers "what have we still got". A row opens the item,
  which shows what it cost — marked as a guess when it is a share of a box — what
  it has already taken, and offers **sell or remove**.
  **Removing lives here and nowhere else.** It used to sit inside the sell dialog,
  a thumb-width from the price field, where the one screen you reach by hunting
  for something to sell also offered the button that resolves an item with no
  proceeds. Selling stays one tap; taking a thing out of stock now costs a
  deliberate detour and a confirmation. The detail screen leaves by itself the
  moment its item stops being `IN_STOCK`, so a completed sale or a removal lands
  back in the list; a lot sold in part stays put and shows the extra sale.
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
