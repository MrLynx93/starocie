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
  no purchase to record; the buy form asks for the price too, because at the moment
  of buying you usually know it, but it **opens at zero and waits for nobody**: a
  thing can be free, and a price nobody got round to is not worth a button that
  will not go
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
| `Event` | `id`, `date`, `name?` |
| `Buy` | `id`, `eventId`, `date`, `price?`, `name?`, `photoUrls` |
| `Item` | `id`, `buyId?`, `date`, `name`, `photo?`, `price?`, `quantity`, `status` |
| `Sell` | `id`, `itemId`, `eventId`, `date`, `price`, `quantity`, `soldCompletely` |

All four also carry `createdBy`, `createdAt`, `updatedAt`.

`status` is `IN_STOCK | REMOVED | SOLD`. `REMOVED` covered broken, lost, given away
or kept; **nothing writes it any more** — removing now deletes the item outright.
The value stays in the enum, and `ItemDoc` still parses it, because records written
before that change are still in Firestore and must keep loading.

**There is no `note` on anything, and there was on all four.** A free-text field
costs a tap to skip on every record and earns it back on almost none — which is the
friction rule applied to a field rather than to a flow. Unlike `REMOVED` it is gone
from the docs as well as the models: decoding is driven by the serializer's
descriptor, so a stored `note` is simply never looked at, and every correction the
app makes writes named fields rather than whole documents. **The strings that were
typed are therefore still in Firestore, untouched** — nothing deletes them, and
putting the field back would find them where they were left.

### Invariants — break these and the numbers lie

1. **`Item.price` is the asking price, never what the item cost.** There is no cost
   field on `Item` at all. Cost lives on `Buy` and only on `Buy`. Label it "asking
   price" in the UI, because "price" on a thing instinctively reads as cost.
   **On a lot it is the price of one piece**, which is what makes the sell dialog
   able to multiply it by the count. Nothing stores that distinction — a lot is
   simply a `quantity` above one — so every field holding this number says which it
   is: "Sprzedamy po cenie za sztukę" and "Chcemy sprzedać za sztukę" against a
   plain "Chcemy sprzedać za". The two differ by a factor of however many there are,
   and a mislabelled one is a stall selling twelve plates for the price of one.
2. **Unknown cost stays unknown, and what it went for is the profit.** An item with
   no `buyId` was invented at point of sale, and `Item.stats.cost` is `null` for it —
   never zero, because nothing may claim we paid nothing. **`profit` is not null**:
   with nothing recorded going out, the whole of what came in is what we made, so it
   is the proceeds. Every screen says both halves of that — "Nie wiemy, za ile
   kupiliśmy" over "Zarobiliśmy 40,00 zł" — and the sale counts in the day's profit
   and the overall total like any other. That is the shortcut sale's honest reading:
   the money is real, and it is the cost that is missing, not the gain.
3. **Removing an item deletes it, and takes its buy with it once that buy is
   empty.** A buy exists to say what was paid for its contents, so one with nothing
   left in it is a price with nothing to be the cost of; a box therefore survives
   until the last thing out of it is deleted too. The cost of this is real and
   accepted: a `Sell` against a deleted item is left unresolvable, so its row reads
   "—" for the thing and "Nie wiemy, za ile kupiliśmy" for what it had cost. Its
   profit follows rule 2 — the whole price, there being nothing left to set it
   against — so the day still agrees with the rows it is made of. Screens must
   degrade to an unknown *thing* here, never assume `itemById` resolves.
   **Only a thing that has never sold can be deleted.** That accepted cost is
   tolerable for a record that produced no money and wrong for one that did:
   stranding a real sale drops what we paid out of `spent` and lifts the profit we
   had already measured to meet it, rewriting a day that was right. A lot with a
   sale behind it therefore closes instead — `markSoldOut` sets it `SOLD` and marks
   its latest `Sell` `soldCompletely`, keeping the buy and every sale. The pieces
   that never went keep their share of what the lot cost, which is a loss and the
   honest reading of a box we sold three things out of and threw the rest away.
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
   raises `Item.quantity` to meet the total instead of being refused — a correction
   of a miscount, not a stock movement.
   **Two things write it after creation, and both are corrections**: that oversell,
   and `setQuantity` while nothing has gone yet. Until the first sale the number
   agrees with nothing, so it can simply be put right — a crate counted in a hurry,
   or a lot entered as the one thing it looked like. After a sale it is refused:
   `sellCost` measures that sale against the count, so moving it would move the cost
   it was set against, and the oversell is then the only thing that may — being a
   fact about pieces in a hand rather than a number somebody typed. Correcting it
   **moves the buy with it**, where that buy holds only this item: the price there is
   per piece, so a lot of three at 30,00 zł corrected to four is four at 30,00 zł and
   a total of 120,00. What one of them cost is the figure somebody remembers; the
   total is the multiplication, and a count and a price disagreeing about how many
   pieces they cover would be a cost per piece nobody ever paid. A **box** is the
   exception and never moves: it was paid for once, whatever turned out to be inside,
   so its shares simply redistribute.
   **The oversell moves no money either**, and the two are not in conflict: there
   nobody typed a price, the money left the hand long ago, and what is discovered is
   that the total covered more pieces than we wrote down. Typing a count beside a
   per-piece price is the opposite — the price is the anchor and the total is the
   multiplication.
   **Taking a sale back moves it neither way.** If that sale was the oversell, what
   the count said before is recorded nowhere, and lowering it to a guess would be a
   number nobody typed; with no sale left against it, `setQuantity` takes the
   correction again.
   **The same thing sold again the same day is written as one sale, not ten.**
   Another sale of the same item on the same event at the same price per piece
   (`Ledger.sellToJoin`) grows that sale's `quantity` and `price` instead of writing a
   new `Sell`. So does a shortcut sale sold whole that matches something already sold
   that day (`Ledger.sellToJoinWith`): the same name, trimmed and in any case, the same
   price and cost per piece compared exactly, and a photo on neither. Then the item's
   `quantity`, its buy's `price` and the sale all grow, and no document is written.
   A stated cost only joins a buy the shortcut sale filed under "Dawno temu" holding
   that thing alone — pieces added to a real giełda's buy would land in that day's
   spend. The thing joined has to be `SOLD` already, so adding pieces sold in the same
   breath leaves what was left of it untouched. Taking back **some** of a sale
   shrinks it by that share (`Sell.priceOf`) and returns those pieces to stock; its
   own `soldCompletely` goes with them, the rest coming back after all.
   `Sell.soldCompletely` stays as the override
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

- **no buy** → cost unknown, and the profit is the whole of what it sold for
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
  `costIsEstimated`, `profit`, `profitIsEstimated`, `soldAt?`
- `Buy.stats` — `itemCount`, `resolvedItemCount`, `cost`, `proceeds`, `profit?`,
  `fullyResolved`
- `Event.stats` — `spent`, `earned`, `buyCount`, `sellCount`, `itemsBought`,
  `itemsSold`, `profit`, `profitIsEstimated`
- `Ledger.overallStats()` — every giełda at once, in `EventStats`' own shape
- `Ledger.sellingSessions()` / `Ledger.buyingSessions()` — the days we sold on, and
  the days we only bought on
- `Ledger.sellCost(sell)` — what one sale's pieces had cost, or null
- `Ledger.sellProfit(sell)` — what one sale made over that share. **Every per-sale
  profit on screen comes from here** — the home screen's "Co ostatnio sprzedaliśmy"
  and a giełda's rows alike — and so does a day's. The home screen once showed the
  thing's whole `Item.stats.profit` on each sale, setting three pieces of a lot of
  twelve against what all twelve cost
- `Ledger.saleGroups(sells)` — a day's sales with the repeats collapsed into one
  line, every figure the sum of the per-sale ones above

Buy-level profit is never an estimate — measured cost against measured proceeds.
Only the split beneath it is inferred.

`Event.stats.spent` and `.earned` are cash out and cash in, and **must never be
subtracted from each other**: what you sell at an event is rarely what you bought
there, so their difference is not profit and nothing may present it as one.
`itemsBought` and `itemsSold` count **pieces**, so each answers for the same things
its money does.

`Event.stats.profit` is the real figure and comes from somewhere else entirely: each
of the day's sales set against what its own pieces cost, which is the arithmetic
`Item.stats.profit` does one thing at a time. A sale whose cost is unknown — no buy
behind the item, or no item left at all — is set against **nothing**, so its whole
price counts, and **every sale is in the figure**. Nothing is left out, so no screen
has a gap to admit: the profit is always a number.

`overallStats` answers the home screen's second and third cards and the sold list's
own two figures, and it **sums the days rather than the sales**: an event is the sole
grouping, so every sale belongs to exactly one day and no sale can fall outside the
total or land in it twice. It returns `EventStats` — the fields all still mean what
they mean for one day — so the cards and the giełdy list cannot disagree. **What we
have sold is `itemsSold`, which is pieces**, never a count of `SOLD` items: that is
one number for a lot of twelve and none at all for a lot sold in part, and both make
the home screen smaller than the days beneath it.

`sellingSessions()` and `buyingSessions()` split the days between them: the ones with
at least one `Sell`, and the ones with buys and no sale. **They are complementary**,
so a day is one or the other and never both, and nothing that happened falls outside
the pair — which is what lets the two home cards be read side by side as a count of
all our days. "Dawno temu" is in neither: it is a filing cabinet for the purchase of a
thing that was never recorded until it sold, not an afternoon anybody spent anywhere.

`sellCost` splits an item's cost across its sales by pieces, the unsold ones holding
their share back, using the same largest-remainder rounding a box does. That is what
makes a fully sold lot's shares come to exactly its cost — without it a day's profit
and the item's own disagree by a grosz about the very same sale.

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
- **A count or a sum that grows or shrinks is written as `FieldValue.increment`**,
  never as a total — joining a sale and taking back part of one. Two phones joining
  the same sale offline each add their pieces; a total computed from each phone's own
  view would keep only one of them and lose a ring and its money on reconnect.
- **Writes never block on the network.** Firestore's local cache echoes the write
  immediately and the UI reflects that optimistic state. Never show a spinner while
  saving.
- **A write the server refuses must be said, over whatever screen is open.**
  Not waiting for the server means the refusal arrives later — and Firestore rolls
  the write back, so on screen the change simply undoes itself. It goes to
  `writeError` and a banner at the top of every screen, held until "Zamknij".
  **Never to `syncError`**: that one is cleared by every snapshot, the rollback is a
  snapshot, and so it vanished the instant it was raised. That is how rules that
  still refused deletes on the server turned "Cofnij sprzedaż" into a button that
  did nothing — `firestore.rules` in git is not the rules that are live until
  `firebase deploy --only firestore:rules` has been run.

### Events are automatic but real

Recording a buy or sell resolves the current event: find today's, create it if
absent, attach. The user is never asked.

**The auto-created event's document id is the ISO date** (`2026-08-01`). This is
load-bearing: at a market both phones are likely offline, and with random ids each
device would invent its own "today" and produce duplicate events for one day. A
deterministic id means both write the same document and converge on reconnect. Use
`SetOptions.merge`. Extra events on a day are user-created and get UUIDs.

**Merge protects nothing — the fields you send do.** A merge leaves alone only what
it is *not* handed, and GitLive's encoder has `encodeDefaults = true`, so a
`@Serializable` doc whose `name` defaults to null really does send `name: null` and
really does blank the stored one. This write runs on every buy and every sell, so
merging a whole `EventDoc` meant a giełda named on the way home lost its name to the
next thing recorded that day — the app quietly deleting what somebody had typed,
with nothing on screen to say so. It therefore merges an **`EventStubDoc`**: id,
date, `createdBy` and the stamps, and no `name` at all, because what a
stamp carries is the whole of what it can destroy. `setLongAgoEvent` does send a
name and may — "Dawno temu" is a constant that write owns, not anything a person
typed.

Nothing caught this, and the reason is worth keeping: every repository test runs
against `InMemoryLedgerRepository`, whose `ensureEvent` creates a day only when it
is absent and so cannot have the bug. The two implementations agreeing is
maintained by hand — see the note on that under Architecture.

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
- tapping a photo opens it full-screen; buttons down its right-hand edge retake it,
  throw it away, or ask Google what it is — stacked rather than in a row, because
  three across the top start covering the thing in the picture, and the Google
  button sits last, under the bin, being the one reached for while looking at the
  picture
- **the Google button is a search by image, and it cannot be a browser URL.**
  Google's reverse image search takes a *public* URL or an upload, and this photo is
  neither — it exists only as Base64 inside the document. So the picture is handed
  over as an image: on Android a `content://` file from the cache, sent to the
  Google app, which is what turns a shared image into a Lens search; on iOS the
  share sheet, since an app cannot name another app's share target. The manifest
  has to name the Google app in `<queries>` or Android 11's package visibility
  makes it invisible and every phone falls back to the chooser. `rememberPhotoSearch`
  returns null where the platform cannot do it, the way `rememberGoogleSignIn` does.
  A move to Cloud Storage would make `lens.google.com/uploadbyurl` possible, and
  that one file is where it would go
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
| `starocie-icon-test*.svg` | the same four, into `androidAppTest`'s `res/` |

**`icon/render.sh` renders all of them**, into `androidApp/src/main/res` and
`androidAppTest/src/main/res` respectively. Nine masters times five densities is too many chances to leave one
behind by hand, and the two icons drifting apart is exactly the failure the mark
exists to prevent.

The test build carries **the same art with a dark disc hung under the canopy,
carrying a T**. It is drawn rather than lettered, because a word is a smudge at
48 px, and it hangs **between the legs rather than over one**: a stall that has
lost a leg reads as a broken icon rather than a marked one. The disc sits within
the art's existing footprint, so the foreground's furthest point is still a leg's
bottom corner and the keyline below still holds. On the themed silhouette, where
there is only one colour to work with, the T is **punched out of the disc** —
one path with `fill-rule="evenodd"`, and the T drawn as a single outline, because
a bar and a stem as separate subpaths would fill their overlap back in.

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
  kupiliśmy? Zostawmy puste" — never "Zacznij pisać", "Nie wiesz"
- **buttons stay imperative** ("Zapisz", "Wstecz", "Anuluj", "Usuń"): a button is
  an instruction to the app, and "Zapiszmy" reads as a suggestion rather than a
  control
- **a read-out starts with a capital** — "Kupiliśmy za ok. 12,00 zł",
  "Zarobiliśmy", "Zostało 9 z 12 szt." A line under a name is a sentence about
  that thing, and a lowercase one reads as a fragment of a heading it does not
  belong to. What continues a sentence after a `+` stays lowercase, being genuinely
  the middle of one: "… · w 3 kawałkach"
- friendly and plain-spoken. Say what happened rather than name a quantity: a loss
  is "Straciliśmy 5,00 zł", not "zysk -5,00 zł". An unknown is "Nie wiemy"
- an estimate always says so — "ok. 12,00 zł", with what makes it a guess spelled
  out underneath
- a thing in stock is a **przedmiot**, never a "rzecz" — one word for it everywhere,
  and **the giełda screens' two figure lines are the single exception**: there the
  count sits beside a sum on a line that must not wrap, so the word is `rzeczy(n)`.
  It is a width buying a shorter word, not a second name for the thing, and it goes
  no further than those two lines
- count words agree with the number (`przedmioty(n)`, which carries the full
  1 / 2–4 / rest rule and the teens exception); "1 przedmiotów" is the small
  wrongness that makes an app feel like a machine

**Two things break the "we" form on purpose.** The paczka screen's explanation
addresses one person — "Użyj tej opcji, jeżeli kupiłeś paczkę i nie znasz cen
pojedynczych przedmiotów w środku." It is not the notebook saying what we did; it is the
app explaining which of the two doors this is, so it speaks to the person choosing
and opens on the same imperative a button uses.

The other is **the search placeholder, "Czego szukasz?"**, over every list there
is — the magazyn, the sold list, the giełdy and one giełda's own two sections. It is
the same exception a different way round: a search box is not a record of anything we
did, it is the app asking the person holding the phone what they want, so it is
second person the way a button is imperative. It used to be **one string in four
places**, and it is now one string in one — `SearchLine` in `Search.kt`, which all four
lists open. Two lists asking the same question in two different voices is exactly the
small wrongness that makes an app feel unfinished, and the surest way to avoid it is for
there to be only one place the question can be asked from.

The sell list's heading, **"Co chcesz sprzedać?"**, is that same exception one line
higher up the screen: the list is being offered to pick from, so the app asks, and it
is second person for exactly as long as the screen is a step in selling. Browsed from
the magazyn card the same screen says "Nasz magazyn", which is us again.

Everything else is "we", and a fourth exception is drift rather than a pattern.

## Screens

**Every screen but Home ends in the same "Wstecz" button** — bottom of the screen,
outlined, arrow and word, always doing exactly one thing. That is where the thumb
already is, and having one guaranteed exit is what lets the primary button be
strict about its required fields instead of quietly doubling as the way out. It
replaced a "Gotowe" here, a "Wróć" there and an "Anuluj" on the two forms that
write nothing until their main button is pressed. **The four searchable lists put it
away while the search is open** — the magazyn, the sold list, the giełdy and one
giełda's own screen — the keyboard then covering where it sits, and the rows wanting
every line of what it leaves. Nothing is lost by it: the phone's own back closes the
keyboard, then the search, then the screen, one press each and each undoing exactly one
thing.

- **Home** — "Nasze starocie" at the top with **the light/dark switch on its line**,
  right-hand end: it is the only app-wide setting there is, and a top bar to hold
  one button would cost every screen height it earns nothing with. The icon shows
  what the tap *gives* you — a sun to go bright, a moon to go dark.
  **Nothing about today sits under the title.** There used to be the current event's
  name and, beneath it, what that day spent and took; both are gone. The screen
  answers for everything we have, not for one day, and a pair of figures side by
  side invites exactly the subtraction that is never profit.
  **The whole screen scrolls, and the three buttons are pinned under it.** Neither
  used to be true: only the recent-sales list at the bottom scrolled, so the cards
  above it could never move out of the way and the list got whatever height was left
  over — close to none of it on a smaller phone, or on a day when both optional cards
  are showing. And the buttons were three extended FABs stacked in the bottom-right
  corner, 188 dp of them, floating over that same list, which is the one thing on this
  screen that is not a read-out. Pinned, they cover nothing, are always there whatever
  has been scrolled past, and come to 118 dp: **"Sprzedaj" keeps a row of its own and
  the two buys share one above it**, because at a stall this screen exists to start a
  sale and the thumb should not have to pick that button out of three of the same
  size. That is also what lets the pair shrink — side by side they need neither the
  full height nor the left-aligned labels the stack needed in order to read as one
  stack. The recent list inside the scroll is a plain column, not a lazy one: a lazy
  list in a scrolling column has no height to be measured against and crashes.
  **The day we are standing in comes first**, then everything we have, then recent
  activity:
  **"Mamy 12 przedmiotów" / "Chcemy sprzedać za łącznie …"**,
  **"Sprzedaliśmy 12 przedmiotów" / "Sprzedaliśmy za łącznie …"**, and
  **"Mamy za sobą 12 giełd" / "Zarobiliśmy na nich ok. …"**.
  **Those four totals are rows of one card, not four cards.** They were four of the
  same shape in the same colour with the same chevron, 320 dp of them, and because
  they looked alike the eye had to read all four to find one — which is also not what
  they are: they are one list of totals, everything we have added up. One container
  says so, and costs three hairlines instead of three gaps and one padding instead of
  four. Every sentence is intact, both lines of each: what was crowding the screen was
  the boxes and not the words. Each row still opens its own list.
  **The second card counts pieces and comes out of `overallStats()`**, exactly as the
  third does, so it is the giełdy's own `itemsSold` and `earned` summed and the home
  screen cannot answer smaller than the days it is the total of. It counted the things
  that were wholly gone, and that is two wrongnesses at once: a lot of twelve plates
  sold at a giełda is twelve things sold there and one record, and a lot sold in part
  is still `IN_STOCK`, so what went out of it counted nowhere on this screen at all —
  its money included, the subtitle having been those same records' proceeds.
  **The third card counts only the days we sold something on** — `Ledger.sellingSessions()`,
  the events with at least one `Sell`. An `Event` is created by buying as readily as
  by selling, so a trip to somebody's garage makes one exactly like a market does;
  it took nothing and made nothing, and counting it would claim a giełda that never
  happened. Nothing is lost by leaving it out: what was bought there is in the
  magazyn like everything else, and its buys still count toward what we have spent.
  The rule also keeps "Dawno temu" out, that bucket holding only buys — which the
  count used to get wrong while the list behind it got right.
  With no such day at all the card says "Jeszcze nigdzie nie byliśmy".
  The third card is the only one showing profit rather than a total, and a loss says
  "Straciliśmy na nich". It is never short of an answer: a sale we know no cost for
  counts for its whole price, so there is nothing the figure has to leave out and no
  gap for a further line to admit.
  **A fourth card answers for the days we only bought on** —
  **"Mamy za sobą 3 dni zakupów" / "Kupiliśmy na nich za łącznie …"**, over
  `buyingSessions()`, the complement of the third card's rule. A trip to somebody's
  garage is not a giełda and must not be counted as one, but it is where a whole
  afternoon's spending went, and until it had a card the only trace of it was the
  things themselves in the magazyn. It says nothing about profit, nothing having been
  sold on any of those days. It is **drawn only when there has been such a day**: a
  card saying we have never had one is a line about nothing, and unlike the three
  above it there is no figure we are waiting on — its own existence is the figure.
  **Today's day sits above all of them, once anything has happened at it** — a card
  reading "Dzisiejsza giełda" / "Sprzedaliśmy … za …" or, before the first sale,
  "Dzisiejsze zakupy" / "Kupiliśmy … za …", turning into the other the moment the
  first thing goes. It opens the day itself, past the list it would be found in.
  It used to sit under the four totals, which is the wrong end: at a stall it is the
  card the app was opened for, and four figures that do not change from one hour to
  the next were between it and the thumb. It stays **a card of its own, in the
  secondary colour**, because it is not one of those totals — they are what we have
  done and this is what is happening — and a colour says that without having to be
  read.
  **While the ledger is still arriving, the read-outs are bars and nothing else is.**
  The card and the rows keep their shape, their colour and their chevron and stay
  openable; the title, the light/dark switch and the three buttons are drawn for real,
  because **a write never waits for the network** — an app that greys out "Kup" and
  "Sprzedaj" until Firestore has answered is lying about what it can do, at exactly
  the moment somebody is holding something out. Each bar takes the line height of the
  text it stands in for, so nothing moves when the figures land.
  Two things make it honest rather than decorative. It needs
  `LedgerRepository.loading`, because `ledger` opens on an empty `Ledger()` and an
  empty ledger still arriving is the same value as one belonging to two people who
  have bought nothing — without the flag a genuinely empty magazyn would shimmer for
  ever. And it **waits 150 ms before appearing**: Firestore answers from its own cache
  on every launch but the first, so the wait is usually a frame or two, and a skeleton
  that comes and goes inside that is a flicker rather than a state.
  The light is **one band crossing the whole screen**, not a band per bar: every bar
  subtracts its own `positionInWindow()` from the band's, so what it shows is the part
  passing over it. The phase comes from the animation clock rather than an
  `InfiniteTransition`, which counts from the frame it was composed on — two bars
  composed a frame apart would otherwise each run a sweep of their own, and a dozen
  of those is a screen flickering rather than a surface being read.
- **Buy** — two ways in, "Kup" and "Kup paczkę", over a single item form. The form
  **opens with the photo**, in the order it actually happens: the thing is in your
  hand, so it is photographed and then described. One item of its own buy → exact
  cost. Several in a box → the box total, allocated. Never asks for a per-item cost.
  **Neither path asks when it was bought.** A buy is dated the day it is entered,
  which is nearly always the day it happened; the field earned a tap on every
  purchase to correct the rare one. `Buy.date` and `Item.date` stay editable in the
  model for a later edit screen — the entry forms simply do not ask.
  **The name is required and nothing else is**, so both buy buttons are disabled
  only on a form with no name: that is what the thing is found by when it comes to
  sell it, and without it there is no record worth writing. Neither stays enabled
  on an untouched form — one of them used to be the only way back out, and "Wstecz"
  is that now, so nothing here has to double as an exit.
  **What was paid opens at zero**, which is both a real answer — things are given
  away, thrown in with something else, carried home from a clear-out — and a number
  somebody in a hurry can accept and move past. The buttons used to hold out for it
  on the grounds that the price is the one figure you cannot fail to know while
  buying; that is true of most purchases and it was still a stall standing over a
  disabled button, which is the friction rule losing to a field. Clearing the zero
  is the honest unknown the shortcut sale already writes — no price at all rather
  than a claim we paid nothing — and it does not hold the purchase up either.
  A zero sitting in a field is typed *over*, not into, so what lands there is "015";
  `typedPrice` drops the leading zero as it is typed, keeping "0,50" and a lone "0"
  exactly as they were written. The box's price step opens on the same zero for the
  same reason. Filling a box is the exception the name rule makes on its own: the
  price field is not on screen there, so it is not waited for.
  **Two buy buttons, and they differ only in what happens next.** "Kup i kupuj
  dalej" records the thing and clears the form for the following one, its arrow
  pointing the opposite way from "Wstecz"'s; "Kup" records it and leaves. A run of
  purchases and a single one are both one tap per thing, and neither is the door
  out — that is "Wstecz", underneath them.
  The name field is labelled **"Nazwa"**, and a plain buy says nothing under the
  heading: the form explains itself. Only a box ("Wpisujemy po kolei — same
  trafiają do paczki.") and a run in progress ("Zapisaliśmy w tej serii: 3") have
  anything to add.
  **A lot is priced by the piece, both times** — "Kupiliśmy po cenie za sztukę" and
  "Sprzedamy po cenie za sztukę" — exactly as on the shortcut sale's form, and the
  screen multiplies what was paid by the count to get the buy's total. What somebody
  holding a crate of twelve plates knows is what one plate cost; the pile's total is
  a multiplication, and doing it in your head at somebody else's table is where the
  wrong number gets written down. The total is read back underneath ("Kupiliśmy 3
  sztuki za 90,00 zł"), because a pile's total typed into a per-piece field is
  otherwise invisible until the profit is wrong weeks later. `Buy.price` is still
  what was handed over — the multiplication happens on the form, and nothing below
  it stores a rate.
  **The count sits beside the name**, because between them they are what the thing
  *is* — one of these, or twelve of them — and both are known before a price is
  thought about. What was paid then gets a whole line to itself whichever it is, its
  label being the thing that says whether it is this thing's price or one piece's;
  a phrase that long ellipsises down to "Kupiliśmy po cenie za s…" in half a line,
  and it is the one label here that must stay readable. A box was paid for once and
  is not asked again, so its line is simply absent.
  **Every field label is held to one line, and shrunk until that line holds all of
  it** — `FieldLabel`, used by the two forms and by the item screens' `MoneyField`,
  `CountField` and `NameField` alike. A resting label is what a text field sizes
  itself around, so a label long enough to wrap makes the field two lines tall and
  drops the typed text a line down; stepping the count above one turned "Kupiliśmy za"
  into the long phrase and the price field grew under the thumb that was setting the
  count. One line alone only moved the failure — a narrow field or a scaled-up screen
  took the tail instead, and "Kupiliśmy po cenie za sz…" loses exactly the half that
  says per what. **A label that will not fit is therefore drawn smaller rather than
  cut**: a point of type is cheap, the words and the field's height are not.
  It steps down rather than solving for a size, what the label is measured against
  being known only once it has been laid out — each overflowing pass takes 8% off and
  lays out again, landing inside a few frames and stopping for good at 9 sp, past
  which an ellipsis is the honest end of it. What it settles on is a **ceiling, not a
  size**: the label draws at the smaller of it and whatever the field is currently
  providing, so it still shrinks and animates its way up to the border as the field
  takes focus.
  **Nothing is focused on arrival** — the screen opens whole, keyboard down, since
  the first move is as often the camera as the name. Focusing a text field *is* the
  request for the keyboard, so the two cannot be separated without hiding it again
  a frame later and losing the race half the time. **Every field hands on to the
  next**, so a whole purchase is typed without the thumb leaving the keyboard: the
  name's Enter goes to what was paid rather than opening a third line, the count and
  the price carry `ImeAction.Next` for the same reason, and the asking price is last
  so its key is a plain Done that puts the keyboard away. A number pad shows an OK
  where a letter one shows Enter, and it does nothing at all unless the field asks
  for `Next` *and* handles it — the keyboard type alone leaves it a dead key. Saving
  gives the name focus with the keyboard, because a run of purchases is a run of
  typing.
  **A name field opens the keyboard shifted** — `KeyboardCapitalization.Sentences`,
  here and on the sell-new-item form and the giełda's name alike. A name is written
  down as a name, and a magazyn of lowercase ones reads as notes to self rather than
  a record of what we have. It is the shift key pressed for you, not a rule: what is
  typed still stands, so a name that genuinely starts small is one backspace away.
  The form **scrolls under two pinned buttons**. The app draws edge to edge, so the
  keyboard covers the window rather than shrinking it; without `imePadding` on the
  root column, the buy buttons are exactly what ends up underneath it.
- **Stock and Sell are one screen.** They were the same `IN_STOCK` list twice —
  a search box for selling, a browse list for looking — and typing a name is how a
  thing is found either way, so the search belongs to both. Newest first, a heading
  and the count and asking total over it, and **a row opens the item, always**: a
  row that did one thing from one door and something else from the other is exactly
  the kind of difference that gets learned wrong once and then costs money. The
  photo is no longer a separate target, having nowhere else to go.
  **The writing sets the row's height, not the photo.** The thumbnail is 40 dp, and it
  was 52 — the tallest thing on a row whose two lines of text needed 36, so every list
  was spending a third of its height on a picture. A photo here is supplementary and
  always was: an item is found by typing its name, and the picture only helps you pick
  the right one of two similar names out of a list, which it does just as well small. A
  row is now as tall as what it says — 56 dp for two lines, 68 for the three a lot or a
  sold thing carries — and there are no rules between rows, the photo column giving the
  list its rhythm already.
  **A row carries what we paid**, the way the sold list does — "Kupiliśmy za",
  "Kupiliśmy za ok." for a share of a box, "Nie wiemy, za ile kupiliśmy" — because
  the asking price alone does not say whether there is a gap worth stopping at.
  **A lot says it by the piece**: "Kupiliśmy po 15,00 zł za sztukę", a guess reading
  "po ok.". The figure beside it on the row is the ask for *one* of them, per
  invariant 1, so a lot's whole cost sitting under a single piece's ask is a gap that
  is not there — a crate of twelve read as a disaster, on the one list whose job is
  saying what is worth selling.
  The count and the total are **the magazyn's, not the search's**: typing a name to
  find one thing does not change how much we have, so the heading stays put while the
  list under it narrows. The unpriced filter below is the one thing that moves them,
  being a question about the magazyn rather than a search through it.
  **Searching is a place you go, and while you are there the screen is the list.**
  At rest there is no box at all — one 44 dp magnifier at the right-hand end of the
  heading's line, where and how the home screen puts its light/dark switch. Tapped, it
  opens `SearchLine`: the heading goes, the figures go, the unpriced filter goes,
  "Wstecz" goes, the bottom margin goes, and what is left is the line being typed into,
  the rows, and "Dodaj … i sprzedaj". On a phone half covered by the keyboard all of
  that was two and a half rows of the thing somebody was standing at a table hunting
  for, which is the friction rule losing to a control on the worst screen to lose it on.
  **The heading's job passes to the button.** A heading is there to say which of the two
  doors you came in by, and while the search is open the bottom of the screen says it
  better: the selling route has "Dodaj … i sprzedaj" under the list and the magazyn
  route has nothing at all. So the distinction survives in the thing you would actually
  press, and the line it used to take goes to the rows.
  The open line has **no border, no container and no fill** — a magnifier, the text, and
  a ×. A search box is chrome, and drawing a form field round it asks a question the
  screen is not asking. The × clears what is typed and closes the search when there is
  nothing to clear; the keyboard's own key is `ImeAction.Search` and puts the keyboard
  away, where it used to be a default that did nothing at all.
  **Closing throws the query away**, and has to: a list left narrowed by typing that is
  nowhere on screen is a list lying about what we have.
  What the margin gives back is real only because `ScreenColumn` takes the whole bottom
  edge from one place — see there.
  **One filter sits under that box, "Niewycenione przedmioty"**, and it is the one
  question the typing cannot ask: a thing we have not decided a price for has no
  name to type. It is what the magazyn is read for between giełdy — what still has
  to be settled before the next one — and it narrows *alongside* the search rather
  than instead of it, so a name typed with the filter on still answers about the
  unpriced ones. It is **drawn only while there is something for it to find**, and
  kept while it is on: pricing the last one would otherwise take the switch away
  with the list still narrowed to nothing. It goes with everything else while the
  search is open — at the stall the thing being looked for is already in somebody's
  hand, priced or not, and a chore for between giełdy is one of the lines in the way. With it on the second figure changes —
  "Mamy 3 przedmioty · Jeszcze ich nie wyceniliśmy" — because the asking total of
  things that are asked at nothing is 0,00 zł, which would be the app answering the
  very question it has just been told nobody can answer yet.
  **It is only offered from the magazyn card.** Opened to sell from, the thing being
  looked for is already in somebody's hand, and whether it has an asking price is
  something the sell dialog settles anyway — the filter is a chore for between
  giełdy, not a step in a sale.
  **The route changes the heading, one button and that filter, and nothing else.** Arriving from
  "Sprzedaj" adds the "Dodaj … i sprzedaj" button, pinned at the bottom above "Wstecz"
  rather than sitting under the search box where it used to shove the list down a line
  every time the typing stopped matching; from the magazyn card it is simply absent.
  It also heads the screen **"Co chcesz sprzedać?" rather than "Nasz magazyn"** — the
  same second person "Czego szukasz?" uses, and the same reason: the app is asking the
  person holding the phone, not saying what we did. That is what tells you the list is a
  step in selling rather than the magazyn arrived at, which matters most coming from a
  giełda — the one door where what you left is a day rather than a list, and where the
  sale you are about to make is recorded into that day. **While the search is open the
  button carries that on its own**, the heading not being drawn at all: one route has a
  primary button under the rows and the other has nothing, which is the same difference
  said by the thing you would press rather than by a line at the top. The list, its two
  figures and its rows are identical either way.
  **The item screen puts the facts above and the three buttons below** — Sprzedaj,
  Usuń, Wstecz — pinned, so what you can do about a thing is always in the same
  place under the thumb while what it is scrolls past.
  **The photo is the same camera target as on the buy form**, not a read-only view:
  a thing shot in a hurry at a stall is exactly the thing worth shooting again in
  better light. Retaking writes straight to the item — there is no draft here to
  hold it in — so backing out of the camera is the only way not to change it, and
  the bin drops the picture in one tap without a confirmation, a photo being
  supplementary rather than a number anything depends on.
  **The name is a field, and it is the heading** — "Nazwa", where the title used to
  be, exactly as a giełda names itself on its own screen. A thing is named
  one-handed while somebody waits to be paid, so it comes out as "lampa" or as a
  thumb's worth of nonsense, and it is the one field that is worth more than a
  figure: it is how the thing is found when it is finally sold, so a wrong one
  costs the sale rather than the arithmetic. A **heading with the field somewhere
  below it would be the name in two places**, disagreeing with itself while it is
  typed — the same reason the count's read-out goes when the count is a field.
  **A blank writes nothing**: a name is the item's identity rather than one of its
  unknowns, so there is no "Nie wiemy" to fall back to the way a cost has one, and
  a cleared field is a half-typed correction. The old name stands, and the field
  says so while it is empty.
  The read-outs come next, **the date at the top** — the one fact here that was
  never a choice — then what it has already taken. **Both prices are
  editable fields** below them, because both are still decisions: one gets mistyped
  or skipped in a hurry, the other changes every time a thing sits unsold. They
  **save half a second after the typing stops**, with no confirm button: Firestore
  takes the write locally anyway, and a price change that depends on remembering to
  press something is a price change that gets lost.
  **"Sprzedaj" here opens the sell dialog on the asking price**, and the price
  there is **a field, "Sprzedajemy za", pre-filled with what the item is asked at**
  rather than a sentence reading the number back. What a thing goes for is agreed
  across a table, and it is often not what we were asking; correcting the ask first
  and then selling is two motions for one moment, and the second of them is the one
  that gets forgotten. So the sale is where the price is finally said, and Sprzedaj
  / Anuluj still make it the deliberate answer a sale needs. **The price the dialog
  opens with is captured when the button is pressed**, not read again afterwards, so
  the field's half-second save cannot move it underneath the dialog — and the field's
  text is held by the screen rather than by the field, so a price typed and sold on
  in one motion opens the dialog at the new number instead of racing that save.
  **The button waits for nothing**: an item with no asking price is priced in the
  dialog like any other, and Sprzedaj there is what waits for a readable amount.
  **A giełda that has been and gone offers no "Sprzedaj" at all.** An item opened
  from a day's screen shows everything else — the photo, both prices, "Usuń" — but
  not that button: a sale started there would be dated today and counted in today's
  takings, not in the day being read. The route carries the flag (`StockItemRoute`'s
  `selling`, defaulting to true), the way the magazyn's two doors already do.
  **Today's giełda keeps the button**, because there the two days are the same one
  and that screen is the stall we are standing at — the day's own list is a perfectly
  good way to reach the next thing to sell. The test is
  `CurrentEventResolver.isCurrent`, and it is **id equality, not a matching date**:
  every write resolves to the dated id, so an extra event created by hand on today's
  date would never receive the sale, and offering to sell into it would put the
  proceeds in a day nobody was reading.
  **A lot asks more in that same dialog**: a piece goes at its own price, so the
  **count leads it** — a stepper starting at 1, reading "z 9" beside it — because
  the count is what the price depends on and the thing only somebody standing at
  the stall knows. A single thing sees none of it: one field and the two buttons.
  Since the list stopped selling from under the thumb, this screen is the only way
  to the dialog at all.
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
  ("Zostało 9 z 12 szt.") in the list and above the item alike.
  **How many there are is a field too, until the first piece goes** — "Sztuki", the
  buy form's own word, **sharing its line with what was paid** exactly as it shares
  one with the name over there: a narrow box at the end of a wide one, rather than a
  stub alone on a row of its own. The row is drawn whether or not the count is in it,
  a lone weighted field being a full line, so there is one paid field here and never
  two to keep in step. A count typed at a stall is
  wrong the same way a price is: a crate counted in a hurry, or a lot entered as the
  one thing it looked like, which is why one thing can become six here. It saves on
  the same half-second pause, and **a count that does not parse writes nothing** —
  a field caught halfway between 1 and 12 is not an answer.
  Sharing the line is what shortens the label beside it: **"Kupiliśmy po cenie za
  szt." on the item screens**, against the buy form's "za sztukę" where the field owns
  its width. The full phrase wants about 208 dp and the shared row leaves about 197,
  so this is the `rzeczy(n)` exception again — a width buying a shorter word, not a
  second name for the thing. The sold item screen carries the abbreviation too,
  being the same field later in the same thing's life. `FieldLabel` would fit the long
  one by shrinking it, so this is not what keeps the label whole; it is what keeps it
  **full size**, the short phrase clearing the line with room where the long one would
  have to give up a point or two of type to do it.
  **Nothing here explains what a lot is.** The buy form's "Sprzedaje się po kawałku"
  hint belongs where the lot is being created and the choice is still open; on a
  thing we already own it is a paragraph explaining a decision that was made weeks
  ago, above the fields somebody came here to correct.
  **What was paid follows it**, per invariant 5, so the price field's own number does
  not move: it holds what one piece cost, and that is exactly what a count correction
  leaves alone. Only the label above it and the total read back below it change. While it is a field the
  line under the name goes: one number in two places is one of them disagreeing
  while it is being typed. A lot with a sale behind it keeps that line and loses the
  field, per invariant 5.
  What was paid is the **buy's** price, not the item's, and the field says which it
  is editing: alone in its buy it reads "Kupiliśmy za", and with siblings it reads
  "Całą paczkę kupiliśmy za" with this item's share spelled out underneath as a
  guess. **A lot alone in its buy is typed by the piece**, in the buy form's own
  words — "Kupiliśmy po cenie za szt.", with "Kupiliśmy 3 sztuki za 90,00 zł" read
  back underneath — because that is the number somebody remembers paying, and one
  label may not mean the price of a plate on one screen and the price of a crate on
  the next. `Buy.price` is still what was handed over: the field multiplies on its way
  in and divides on its way out, and nothing below it stores a rate. The division is
  the reason a total that will not divide evenly loses the odd grosz to the *display*
  and never to the record — a price shown and left alone writes nothing at all. A lot
  out of a box is the exception: there the field is the box's price, and a box was
  paid for once whatever was in it. The sold item screen says the same thing in the
  same words, being the same field at a later point in the same thing's life. Typing a price into an item that had no buy **opens one holding only that
  item**, which is how a cost unknown at the point of sale becomes exact later;
  clearing that same field records nothing, because inventing an empty buy would
  turn an honest unknown into a claim that we paid zero. **That buy is filed under
  "Dawno temu"**, exactly where the shortcut sale files a stated price, and never on
  the day the price is typed: only a shortcut sale leaves a thing with no buy, and
  typed in at the stall it would list the thing in that giełda's "Co kupiliśmy" and
  add it to the day's spend. Buys an older build filed on a real day are refiled by
  the Firestore repository as the ledger arrives — `Ledger.misfiledShortcutBuys`
  finds them as a buy holding one thing written in the same instant as one of its
  sales, which only the shortcut sale ever does.
  **Removing lives here and nowhere else.** It used to sit inside the sell dialog,
  a thumb-width from the price field, where the one screen you reach by hunting
  for something to sell also offered the button that resolves an item with no
  proceeds. Both now confirm, and they are still not alike: selling asks about a
  number and deleting asks about the record itself, whose button is **red and says
  only "Usuń"** — it destroys something, so it must not read like the neutral way
  out directly beneath it.
  **A lot that has already sold some of itself gets a different button in that
  place**, and the red one is not offered at all: "Sprzedaliśmy już wszystko",
  outlined in the ordinary colour because it destroys nothing. It closes the lot —
  out of the magazyn, buy and sales untouched — where deleting would strand those
  sales and, per rule 3, quietly hand the day back a profit it never made. Its
  dialog says where the rest went and what stays: "Zostało 9 z 12 szt. Reszty nie
  sprzedamy — przedmiot zniknie z magazynu, a to, co już sprzedaliśmy, zostanie w
  rachunkach." The label is one of the few read-outs standing in for a button, and
  deliberately: it is the same sentence as the sell dialog's "Sprzedaliśmy już
  wszystkie", which is the fact being recorded either way.
  The detail screen leaves by itself the moment its item stops being `IN_STOCK` or
  stops existing, so a completed sale, a closing or a deletion lands back in the
  list; a lot sold in part stays put and shows the extra sale.
  **Those sales are lines, each with a red "Cofnij"** — the date, the pieces, the
  price, under "Sprzedaliśmy do tej pory". A lot sold in part never reaches the sold
  list, so a sale recorded against the wrong lot can only be taken back from here.
  With no sale left the count is a field again and "Usuń" is back. The label is
  "Cofnij" on these lines and "Cofnij sprzedaż" on the sold screen: a line reading as
  a sale already says what is undone, while a button alone under a price field and
  saying only "Cofnij" reads as undoing the typing.
- **Sold** — the mirror of the stock list, reached from the second home card:
  everything `SOLD`, newest sale first, **with the magazyn's search over it** — the
  same magnifier on the heading's line, the same line when it opens, and the same
  disappearing act while it is open. It shares that list's predicate — the name,
  case-insensitive, in memory — so the two can never answer differently about the same
  typing. With no button of its own there is nothing under the rows at all while it is
  being searched: what the typing found runs from the line to the keyboard. Over the box sit the two
  figures the list is for: "Sprzedaliśmy 12 przedmiotów za 806,00 zł" and
  "Zarobiliśmy ok. 240,00 zł", **both over everything sold rather than over what the
  search found** — the heading says what we have sold, and looking for one thing does
  not change it. They are **over the sales rather than over the rows**, out of
  `overallStats()` like the home card that opens this screen: a row is a thing wholly
  gone, and a lot of twelve plates is one of those while being twelve things sold, so
  counting rows made this line disagree with the giełdy and left a lot sold in part
  out altogether — pieces and money both, it being still in the magazyn. A lot's row
  says how many of it went ("Sprzedaliśmy 12 sztuk za 806,00 zł"), so the list can be
  read against the count above it. Everything sold is in that profit, including what we
  never recorded buying: with no cost against it, what it went for is what it made,
  and its row says as much on the left — "Nie wiemy, za ile kupiliśmy" over what we
  took.
  Each row answers "was it worth it" without
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
  half a second after the typing stops exactly as the magazyn's prices do.
  **The name is a field here too**, in the heading and in the same words as the
  magazyn's, being the same thing later in its life — and here it is what this list
  is searched by. A blank writes nothing there either. The profit
  sits at the top and is recomputed from them as they change, which is what says the
  correction landed; it is the only figure on the screen nobody entered.
  A **date is picked from a calendar, never typed** — it is the one value the
  keyboard offers no help with, and every separator is a chance to record a day that
  never happened. **The event never follows the date**: grouping is `eventId`'s alone,
  and a date put right weeks later must not silently move a sale into another day's
  takings. Correcting the buying date moves the **buy** with it only when that buy
  holds this item alone; a box was bought once, whatever one thing out of it turns
  out to be dated.
  **A single thing carries no headings** — four fields, each labelled, and a
  "Kupiliśmy"/"Sprzedaliśmy" divider above them would only name what the labels
  already say. A lot earns them back, and with several sales its selling heading
  carries the total — "Sprzedaliśmy 12 sztuk za 806,00 zł w 3 kawałkach" — which is
  why nothing adds the sales up again underneath them. With one sale it is a bare
  "Sprzedaliśmy", like "Kupiliśmy" above it, the sale's own field reading its total
  back.
  A lot that went in several sales gets a date and a price **per sale**, each
  happening on its own day for its own money. **A sale of several pieces is priced per
  piece**, in the paid field's own words — "Sprzedaliśmy po cenie za szt." over
  "Sprzedaliśmy 10 sztuk za 150,00 zł" — because ten rings went at one ring's price.
  `Sell.price` stays the total: the field multiplies on the way in and divides on the
  way out, exactly as the paid field does.
  There is **no "Usuń"** here — deleting belongs where a thing still exists to be got
  rid of, and erasing a sold item would only lose the proceeds it is the record of.
  **"Cofnij sprzedaż" is there** instead, for the one mistake no field can
  correct: a sale that should not exist at all — the wrong row tapped at the stall, or
  a buyer who changed their mind once the button had been pressed. **A thing that went
  in one sale gets it pinned above "Wstecz", drawn exactly as "Usuń" is on the
  magazyn's screen** — full width, outlined in red, both from one `DestructiveButton` —
  being the same kind of act in the same place under the thumb; a small link under a
  price field was easy to miss. A lot sold in parts gets a small one **under each
  sale** instead, since one button at the bottom could not say which sale it meant,
  and a lot sold in eight parts must not become a column of eight full-width alarms.
  It is red and it
  asks first, since it erases a record and that day's takings drop by it, and the
  dialog says both halves: "Przedmiot wróci do magazynu, a sprzedaż za 45,00 zł
  zniknie z naszych rachunków." **A sale of several pieces asks how many come back**,
  with the sell dialog's own stepper starting at all of them and stopping there — the
  wrong row tapped is the commoner mistake, the buyer returning three rings of ten the
  other. The text follows the count: "3 sztuki wrócą do magazynu, a z naszych
  rachunków zniknie 45,00 zł." Taking all of them deletes the sale; fewer shrinks it.
  The same dialog serves the magazyn's sale lines. The sale is **deleted rather than flagged** — every
  figure it fed is computed, so nothing else needs telling — and nothing is written
  into today. The item goes back into stock unless the sales that remain still close
  it, which `Ledger.statusAfterUndoing` decides so the two repositories cannot differ.
  A thing recorded at the point of sale comes back as well, buy and all: it never left
  our hands, so the magazyn is where it is. The screen leaves with it, exactly as the
  magazyn's does when a thing stops being in stock.
  The photo shows if there is one, with the bin but **no camera**: an empty capture
  target on something that is no longer ours would invite photographing somebody
  else's. The Google button stays, though — "what was that, and what do people ask
  for one" is a question that outlives the sale.
- **Giełdy** — the third home card, "Mamy za sobą 12 giełd", over a list of the
  events themselves, newest first. The other two lists answer questions about
  things; this one answers them about days, and an `Event` is the app's only notion
  of a day. **It is `sellingSessions()` rather than every event**, the same rule the
  card counts by, so the two cannot disagree about how many giełd there have been —
  a day we only bought on is not one, and it has a list of its own.
  **"Nasze zakupy" is that list, and it is the same screen**, over
  `buyingSessions()` and reached from the fourth card. One composable for the two,
  because they are the same question about the same kind of thing: a second copy of
  it would be two lists of days free to disagree about how a day is drawn. Only the
  heading, the source and the empty line differ.
  A row says what the day brought in and what it cost — "Sprzedaliśmy 5
  rzeczy za 244,00 zł" over "Kupiliśmy 17 rzeczy za 492,00 zł", **selling
  first**, a giełda being a day of selling that we also buy on — with **what we made
  kept apart from that pair**, because it is not the gap between them: it is each
  sale against what that thing cost. A day of sales always names a figure there,
  since a sale we know no cost for counts for the whole of what it took.
  **Each line appears only if that half of the day happened**: a day of only shopping
  says what we bought and nothing else, a nought beside a nought being a sentence
  with nothing in it — and on the list of those days, every row would carry one.
  **A day with no sale puts what it cost where the profit would go**, and the line on
  the left then drops the sum and keeps the count — "Kupiliśmy 17 rzeczy" over there,
  "492,00 zł" and "Wydaliśmy" over here. Such a day has no profit to name and must not
  claim a nought, and what it does have is the money that left our hands, which is the
  whole of what the day was: on "Nasze zakupy" the figure every row is read for sat at
  the tail of a sentence while the place the eye goes for it stood empty. One rule
  decides it for both halves, so the sum appears once — the figure and the line cannot
  each think the other is carrying it.
  One composable draws these figures for the list row and for the day's own screen,
  so the two cannot fall out of step.
  **Each of the pair is one line and stays one line**, ellipsised rather than wrapped:
  a count and a sum are one fact, and split across two lines they read as two — which
  is worst here, where the line underneath is the other half of the pair and four
  lines have no obvious order left. The width is not theirs to spend, either, the
  profit beside them taking what it needs first. That is what the shorter word is
  for: "rzeczy" is what makes the line fit rather than a trim of one that already did.
  It carries **the same search as the other two lists**, behind the same magnifier on
  its heading's line and in the same words, because typing is how anything is found in
  this app and days pile up the way things do. Open, it does what they do: the heading
  goes, "Wstecz" goes, and the days run to the keyboard. A giełda is matched on its name **and on its
  date as the row says it** — most are auto-created and never named, so the date is
  the whole of what such a row shows, and leaving it out would make the search blind
  to the majority of the list. "2026-08" therefore finds a month.
  The list has no total of its own above it: a giełda is a day, and the days do not
  add up to a day. Nothing above the box is computed over what the search found,
  because there is nothing above it to compute.
  **A row opens the day**, onto its own screen — the magazyn's list and the sold list
  narrowed to it, in two sections, with the same rows and the same wording, **except
  that "Co kupiliśmy" answers in cost rather than in ask**: the figure on the right of
  those rows is what the whole thing cost us — a lot of three at 30,00 zł reading
  90,00 zł, a share of a box still marked "ok." — under a "Kupiliśmy" saying which
  figure it is, and the line under the name goes, one number in two places on one row
  being a row read twice. A day is read for the money that left our hands, and the
  per-piece ask the magazyn shows there is a different question asked of the same
  thing; it is a tap away, on the thing itself. A row
  there opens the thing, in the magazyn's item screen or the sold one depending on
  where it is now — **minus the way to sell it**, since the day it would be sold
  into is not the day on screen. A day is a way *into* the records rather than a
  second reading of them, and correcting one is what it is for. A sale carries its
  own share of the cost, so a lot that went across three giełdy shows a third of
  itself at each; a sale whose item has been deleted reads "—" for the thing and
  opens nothing, its profit being the whole price like any other uncosted sale.
  **The same thing sold again is one line, not ten.** Ten rings rung up one at a time
  at 15,00 zł read "Pierścionek · 10 sztuk · Sprzedaliśmy po 15,00 zł za sztukę",
  with the profit summed beside it. Sales collapse on the name (trimmed, any case),
  what one piece cost and what one piece went for — both compared **exactly, as
  fractions**, so two for 30,00 zł joins one for 15,00 zł — and a photo that could
  tell them apart: none on either thing, or the very same item, a lot sold a piece at
  a time. The cost per piece is read off the item rather than each sale's share,
  since a lot's shares differ by a grosz on purpose and are still one purchase.
  **New sales like that are joined when they are written** (see invariant 5), so the
  line is usually one `Sell` already, and it opens one thing that corrects all ten.
  The collapsing on screen stays for what was written apart: records from before the
  join existed, two phones selling the same ring offline, and separately recorded
  things from the magazyn. There the line opens its newest sale's thing. A line of
  several pieces says its prices per piece where they divide into whole grosze, and
  as the total where they do not, rather than rounding into a price nobody paid.
  That screen carries **the same search as the three lists**, its magnifier on the line
  the day names itself on, and it narrows both sections at once — a thing bought and
  sold on one day is honestly in each, so one box has to find it in both. Open, it takes
  more away here than anywhere: the name field, the date, the day's two figure lines and
  the profit beside them, which between them are a third of the screen. A day is read
  for its figures and searched for one row, and those are not the same errand; they are
  all back the moment the search closes. Those figures are **the day's and stay the
  day's** either way, never recomputed over what the typing found: they are what the row
  in the list behind says as well, and the two must not disagree because somebody is
  looking for a lamp. The magazyn and the sold list keep their headings still for the
  same reason. A sale is matched
  on its item's name, so a deleted thing's sale has nothing left to match and drops
  out of a search; it is back the moment the box is cleared. The box only appears
  once the day holds something, there being nothing to search in an empty one.
  **Today's giełda carries one button of its own**, pinned above "Wstecz", and for
  the reason its rows keep "Sprzedaj": the day on screen is the one every write
  resolves to, so what it records lands in the takings being read. It goes while the
  search is open, along with "Wstecz" — this screen's search is over the day's own rows,
  and a button that leaves for the magazyn is not what a failed search here wants next. A day that has
  been and gone shows none — those sales would be dated today.
  **Nor does a day that has sold nothing yet.** Until the first sale it is a day of
  shopping — the screen "Nasze zakupy" opens, answering for what an afternoon cost —
  and a primary button offering to sell is the wrong instrument on a screen being read
  for that. The first sale of the day is made from the home screen's "Sprzedaj", which
  is where a sale is started from when there is no day on screen at all; from the next
  one on, the day is a giełda and the button is here, at the stall. The rows are
  unaffected: what they carry is which day a sale would land in, and that is still
  today's.
  It is **the home screen's "Sprzedaj"**, in the same word, opening the same
  searchable magazyn: these two sections are one day's own work, and what a buyer is
  holding was most likely bought at some other giełda entirely, so without it the way
  to sell from the stall we are standing at was back out through the home screen.
  **The thing that was never recorded is reached through it rather than beside it.**
  "Dodaj nowy przedmiot i sprzedaj" briefly sat outlined underneath, and it was the
  rarer moment given equal standing on a screen whose job is reading a day — the sell
  list it opens carries that button already, where the search that has just failed to
  find the thing is what puts it there. One button here, one door onward. What was
  typed into the day's box does not follow "Sprzedaj" across either: that box is
  searching the day and the list it opens is searching the magazyn, and the two are
  different questions.
- **Selling a thing that was never recorded is a first-class path**, not a fallback:
  nothing is in the app to begin with, and requiring everything to be entered before
  it can be sold is exactly the friction that gets a tracker abandoned. "Add new"
  offers the whole buy form — name, what was paid, pieces, photo — alongside
  the final price, and `recordBuyAndSell` writes the buy, the item and the sale in
  one batch. It gets **a screen of its own rather than a dialog**, sharing
  `SellViewModel` with the list behind it so the typed search seeds the name: it
  carries as many fields as the buy screen, and a dialog held them against the
  keyboard with the photo pushed off-screen. **What was paid is optional and empty is a real answer**: it leaves the
  item with no buy, so the cost stays unknown rather than becoming zero. A stated
  price opens a buy holding only that item, so its cost is exact and the allocator
  is never involved.
  **A count above one arrives with "Sprzedane w całości" already ticked.** The
  checkbox only shows for a lot, and a lot recorded here is a lot being handed over
  here — it was not in the app a moment ago, so there is no pile left behind for it
  to stay in the magazyn for. Unticking it is the rarer answer: we brought five, two
  went, and three are coming home.
  **On a lot both prices are typed per piece** — "Kupiliśmy po cenie za sztukę" and
  "Sprzedajemy po cenie za sztukę" — and the form multiplies them up: what was paid
  by the count, what it went for by the pieces actually going. Somebody standing at
  the table knows what one of them cost and what one of them is fetching; the totals
  are arithmetic done in your head while a buyer waits, which is where the wrong
  number gets written down. It also puts the asking price where invariant 1 says it
  lives, so the ask is written onto the item **whether the lot went whole or in
  part** — what stays in the magazyn is now asked at what a piece just fetched,
  where the old total-priced field could only leave it blank. Both totals are read
  back under their fields ("Kupiliśmy 3 sztuki za 90,00 zł"), because a pile's total
  typed into a per-piece field is otherwise invisible until the profit is wrong
  weeks later. It is laid out as the buy form is, count beside the name and what was
  paid on a line of its own, because the two are the same purchase and should not
  feel like different acts. **The buy screen now does the same thing in the same words**, so a lot's
  cost is typed per piece whichever door it came through and the one label cannot
  mean two things. Only "Kup paczkę" still asks for a total, under a label that says
  so outright — "Całą paczkę kupiliśmy za" — because a box is priced before anybody
  knows how many things are in it.
- **Buying splits in two, but there is only one item form.** "Kup" records one
  thing at one price and clears for the next, so its cost is exact and the
  allocator is not involved. "Kup paczkę" is a two-step wizard: the price first, which
  opens the buy, then *the same item screen* with the price field hidden and items
  appended to that buy. Unpacking a box is deliberately the same motion as buying
  things one at a time.
  **The price step is one sentence, and it says which door this is** — "Użyj tej
  opcji, jeżeli kupiłeś paczkę i nie znasz cen pojedynczych przedmiotów w środku." —
  because not knowing the individual prices is the whole difference between the two
  doors, and this is the last cheap moment to notice you took the wrong one. It
  replaced two paragraphs that between them explained the price field the label
  already names, described the step that follows before it has happened, and then
  repeated the choice from the other side at the bottom of the screen ("Sprzedajesz
  to jako jedną pozycję? Wróć i wybierz «Kup»"). Three explanations of one decision
  is how a screen stops being read at all; the one that survives is the one naming
  the condition somebody can check against what is in their hands.
- The box's buy is created **before** its contents are known, so each item is saved
  as it is unpacked. Accumulating drafts and writing them at the end would lose the
  lot if the user backed out.

## Two builds

The real books are kept by two people who are using this at a stall, and a change
worth trying is a change that might be wrong. So there are two Android builds, and
**a workspace each**:

| Build | applicationId | Label | Icon | Workspace |
|---|---|---|---|---|
| prod | `pl.starocie` | starocie | the stall | `starocie-prod` |
| test | `pl.starocie.test` | starocie (test) | the stall, marked with a T | `starocie` |

The test build inherits the original `starocie` workspace, everything entered
while the app was being built being exactly what a test build should have in it,
and the real one starts empty on `starocie-prod`.

**Separate workspaces, not a separate Firebase project.** The rules are already a
single per-workspace predicate, so one workspace is one closed set of books; a
second project would mean a second config file, a second CI secret and two
accounts per person, all to buy an isolation the workspace already gives.

**Different applicationIds are what make it worth having.** Both sit on the phone
at once, with their own launcher entries and their own theme preference, so trying
something out costs nothing and reaching for the real one is never ambiguous —
which is also why the test build's icon carries a mark of its own. Two identical
tiles a thumb apart is precisely the ambiguity two builds were meant to remove.

### A module each, not a flavour each

```
androidHost/          the manifest, MainActivity, every resource but the icon
androidApp/           prod:  pl.starocie      + starocie-prod + the plain icon
androidAppTest/       test:  pl.starocie.test + starocie      + the marked icon
```

Product flavours would be the obvious way to do this and are the wrong one,
because **Android Studio's ▶ takes a flavour from the Build Variants panel rather
than from the run configuration**. There is no variant to set on an Android App
configuration, so two of those would be the same button twice, and the real
choice would live in a panel — which means it stays wherever it was left. That is
exactly how you reach for the test build and get the real books. A *module* is
something a run configuration can name, so `.idea/runConfigurations/` holds
**"starocie (prod)" and "starocie (test)"**, each pinned to its module, each with
Studio's own device picker, debugger and logcat. They are the one thing under
`.idea/` that is not gitignored: a button that exists only on the machine it was
made on is a setting each of us has to rediscover.

**Two more buttons build the file rather than the phone** — "APK (prod)" and
"APK (test)", Gradle configurations running `:androidApp:assembleDebug` and
`:androidAppTest:assembleDebug`. The pair above them deploys over a cable to
whatever Studio's device picker is pointed at, which is the wrong shape when what
is wanted is the `.apk` itself: the other phone is not plugged in, and sending
somebody a build is sending them a file. They name a module the same way, so the
same rule holds — the button says which books it is for, and nothing in a panel
can change that. Each leaves its APK where the workflow picks its own up,
`<module>/build/outputs/apk/debug/<module>-debug.apk`, and it is a debug build
for the same reason CI's is: an unsigned release APK will not install at all.

A module is also something the IDE has to be told about: until Gradle is synced,
`:androidAppTest` is not in Studio's model and its button falls back to the app
Studio does know, which is the real one. A sync is the first thing to try when a
run configuration does something other than what its name says.

The price is two application modules, and it is kept small by their sharing
`androidHost/` through `sourceSets` — one manifest, one `MainActivity`, one
theme. Each module's own `res/` holds its launcher icon and nothing else, which
is also what keeps the two out of each other's way: the same resource under two
source dirs is a merge conflict, not an override.

Which workspace a build talks to is a **parameter, not a default** — `App()`
takes it, each module hands it its own `BuildConfig.WORKSPACE_ID`, and no screen
below can tell which it is in. Giving it a default would be giving a test build a
way to reach the real books by omission.

One shape the toolchain forces: **the google-services plugin fails a build whose
package name has no client** in the JSON, so a second applicationId would
normally mean registering a second Android app in the console.
`androidAppTest/build.gradle.kts` **derives** its `google-services.json` from the
real one next door instead — same project, same API key, only the package name
changed, which is all Firestore and e-mail sign-in ever read. It is gitignored
like the file it comes from, and it copies the real client across untouched the
moment that file carries one for `pl.starocie.test`, so registering that app
properly — which is what Google sign-in on the test build would need, being keyed
to package name and fingerprint together — is an upgrade rather than a conflict.

**A new workspace has to be let in on.** The first person to open the prod build
creates `workspaces/starocie-prod` with themselves as its only member, and the
second is then locked out — the rules resolve membership by reading that very
document. Both uids go into `members` from the Firebase console (Authentication →
Users for the uid), which is the same thing that was done for the original
workspace and the reason a phone can sit in a retry loop with "nie udało się
zsynchronizować" until it happens.

**iOS has no split yet.** `MainViewController` names `PROD_WORKSPACE_ID` outright,
so anything run there, simulator included, writes to the real books. The phones we
sell from are the Android ones; when that stops being true, the split belongs in
Xcode's build configurations, not in another parameter.

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

**The button appearing is not the same as it working**, and this is the trap: the
web client of step 3 belongs to the *project*, so enabling Google is enough to
draw the button, while step 2's fingerprint belongs to a *package and a signing
key*. Miss it and the account chooser opens, an account is picked, and then
nothing happens at all — Play services logs `DEVELOPER_ERROR` under its own tag
and hands Credential Manager back a `[16] Cancelled by user`, which is
indistinguishable from someone dismissing the sheet, so the screen says nothing.
`rememberGoogleSignIn` logs every failure under `starocie/google`, which is where
to look first.

Two fingerprints follow from all of that, and each has to be registered against
**the app whose package it signs**: `pl.starocie` and `pl.starocie.test` are
separate console entries, and an `oauth_client` of type 1 under a client is what
says one has been added. An APK from Actions is signed with the runner's own
generated debug keystore rather than this machine's, so Google sign-in fails on
the download while working on the phone you plugged in — the fix for that is to
give CI the same keystore, not to chase its fingerprint.

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

`.github/workflows/android-apk.yml` builds `:androidApp:assembleDebug` **and**
`:androidAppTest:assembleDebug` and attaches both APKs to a rolling `latest-apk` prerelease,
so installing is opening one URL on the phone rather than plugging it into a
laptop:

    https://github.com/MrLynx93/starocie/releases/tag/latest-apk

`starocie-<sha>.apk` is the real one and `starocie-test-<sha>.apk` its neighbour;
which of the two a file is has to be legible on a phone's download list, where
the name is the only thing showing. Both are built every time — building one and
not the other leaves the test APK a commit or two behind the thing it exists to
test.

**Debug, not release**, because the debug keystore is generated and an unsigned
release APK will not install at all. Every push to `main` or a `claude/**`
branch rebuilds it and moves that download on.

The same two APKs are a button away in Studio — "APK (prod)" and "APK (test)",
above — for the build that is wanted before it has been pushed anywhere.

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
