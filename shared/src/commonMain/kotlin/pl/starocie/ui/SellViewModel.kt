package pl.starocie.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import pl.starocie.domain.DraftItem
import pl.starocie.domain.Item
import pl.starocie.domain.ItemStats
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.parseMoney
import pl.starocie.domain.toInputText

/**
 * A thing that was never recorded, being bought and sold in one motion.
 *
 * Everything except the name and the final price is optional: this exists because
 * a stall does not wait for bookkeeping. A blank [paidText] means the cost really
 * is unknown and must stay that way.
 *
 * **Both prices are typed per piece**, and both are multiplied up from there. A lot
 * is entered here as "these, at this much each, going for this much each" — which is
 * how it is actually being sold across the table — where the totals are arithmetic
 * nobody should be doing in their head while somebody waits. It also puts the asking
 * price where invariant 1 says it lives: [pricePerPiece] is what one goes for, so it
 * can be written onto the item as it stands.
 */
data class NewItemForm(
    val name: String = "",
    val paidText: String = "",
    val priceText: String = "",
    val quantityText: String = "1",
    val photo: String? = null,
    /**
     * Only meaningful for a lot; a single thing is always sold in full.
     *
     * It starts ticked, because a lot entered *here* is a lot being handed over
     * here: nothing was in the app a moment ago, so there is no pile left behind
     * to come back to. Unticking it is the rarer answer — we brought five, sold
     * two, and the other three are going home with us.
     */
    val soldCompletely: Boolean = true,
) {
    /** What one of them cost, if that is known at all. */
    val paidPerPiece: Money? get() = parseMoney(paidText)

    /** What one of them goes for. Required — this is a sale. */
    val pricePerPiece: Money? get() = parseMoney(priceText)

    /** Blank or nonsense means one, so a stray edit cannot lose the record. */
    val quantity: Int get() = quantityText.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1

    val splittable: Boolean get() = quantity > 1

    /** A lot only closes when ticked; anything else is closed by its only sale. */
    val closesTheItem: Boolean get() = !splittable || soldCompletely

    /** How many pieces are changing hands now: the whole lot, or one out of it. */
    val soldQuantity: Int get() = if (closesTheItem) quantity else 1

    /**
     * The buy's price, which is a total: one piece's cost taken as many times as
     * there are pieces. `Buy.price` is what was handed over for the lot, so the
     * multiplication happens here rather than anywhere the cost is later read.
     */
    val paid: Money? get() = paidPerPiece?.let { it * quantity }

    /** What this sale is for — the pieces going now, at the price of one. */
    val price: Money? get() = pricePerPiece?.let { it * soldQuantity }

    val canConfirm: Boolean get() = name.isNotBlank() && pricePerPiece != null
}

/**
 * One row of the stock list.
 *
 * The stats travel with the item because the row shows what the thing cost, and
 * that is not on the item — it is a share of its buy, worked out over the whole
 * ledger. Computing it row by row inside the list would redo that join on every
 * frame of a scroll.
 */
data class StockEntry(val item: Item, val stats: ItemStats, val piecesLeft: Int)

data class SellUiState(
    val query: String = "",
    val inStock: List<StockEntry> = emptyList(),
    val selected: Item? = null,
    val priceText: String = "",
    /** How many of a lot's pieces this sale takes. One thing is always one. */
    val sellQuantity: Int = 1,
    /**
     * What the price field held when the dialog opened, read as the price of one
     * piece. Stepping the count multiplies it back up; null when there was no
     * asking price to start from, and then the field is left alone.
     */
    val unitPrice: Money? = null,
    /** How many we think are left. Not a ceiling — see [correctedQuantity]. */
    val piecesLeft: Int = 1,
    /** How many have already gone, so an overshoot can be turned into a new total. */
    val soldSoFar: Int = 0,
    val soldCompletely: Boolean = true,
    /** Non-null while the "never recorded" form is open. */
    val newItem: NewItemForm? = null,
    val error: String? = null,
) {
    val canConfirm: Boolean get() = selected != null && parseMoney(priceText) != null

    /**
     * True once the count on its own finishes the lot. The tick then follows it and
     * stops being a choice: there is nothing left for it to write off.
     */
    val quantityClosesTheItem: Boolean get() = sellQuantity >= piecesLeft

    /**
     * The lot's real size, when selling more pieces than we recorded proves there
     * were more in it than we wrote down. Null while the count still fits.
     *
     * Counting a box at the stall is exactly the sort of thing that comes out one
     * short, and finding out is selling the piece that should not have existed. The
     * sale is the correction, so the item's count moves to meet it rather than the
     * sale being refused for disagreeing with a number we already know is wrong.
     */
    val correctedQuantity: Int?
        get() = selected?.let { (soldSoFar + sellQuantity).takeIf { total -> total > it.quantity } }

    /**
     * Offered whenever the typed name is not already in stock — including with an
     * empty search, because at the start nothing is recorded and the on-the-fly
     * path is then the *only* path.
     */
    val canAddNew: Boolean
        get() = inStock.none { it.item.name.equals(query.trim(), ignoreCase = true) }
}

/** One search rule for both lists: the magazyn's and the sold one's. */
internal fun Item.matchesQuery(query: String): Boolean =
    name.contains(query, ignoreCase = true)

class SellViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val local = MutableStateFlow(SellUiState())

    /**
     * Search runs in memory over the whole in-stock list, so it is instant and works
     * offline. An empty query shows everything, which is what makes one screen serve
     * both questions: "where is the thing I am holding" and "what have we still got".
     *
     * Newest first, because the thing you are least sure about is usually the thing
     * you bought last.
     */
    val state: StateFlow<SellUiState> = combine(local, repository.ledger) { ui, ledger ->
        val stock = ledger.itemsInStock()
            .filter { ui.query.isBlank() || it.matchesQuery(ui.query) }
            .sortedByDescending { it.createdAt }
            .map { StockEntry(it, ledger.itemStats(it), ledger.piecesLeft(it)) }
        ui.copy(inStock = stock)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SellUiState())

    fun onQueryChange(value: String) = local.update { it.copy(query = value) }

    /**
     * Opens the sell dialog on an item.
     *
     * [priceText] is what the caller already has on screen — the item screen holds
     * the asking price in a field that saves half a second after the typing stops,
     * so a price typed and sold on in one motion must not be overwritten here by the
     * older number still on the record.
     */
    fun select(item: Item, priceText: String? = null) = local.update {
        // Pre-fill with the asking price: usually right, always editable. On a lot
        // that ask is what one piece goes for, which is what makes it multipliable.
        val seeded = priceText ?: item.price?.toInputText() ?: ""
        it.copy(
            selected = item,
            priceText = seeded,
            unitPrice = parseMoney(seeded),
            // One piece, because one is what a sale usually is even out of a lot of
            // twenty — and it is the number that needs no tap to accept.
            sellQuantity = 1,
            piecesLeft = repository.ledger.value.piecesLeft(item),
            soldSoFar = repository.ledger.value.itemStats(item).soldQuantity,
            soldCompletely = !item.splittable,
        )
    }

    fun dismiss() = local.update {
        it.copy(
            selected = null,
            priceText = "",
            sellQuantity = 1,
            piecesLeft = 1,
            soldSoFar = 0,
            unitPrice = null,
        )
    }

    /**
     * Only ever floored at one — there is no ceiling, because the number we wrote
     * down is not evidence against the pieces in your hand. Going past it corrects
     * the lot instead of being refused; see [SellUiState.correctedQuantity].
     *
     * The price follows the count up from the price of one piece, because three
     * plates at the asking price is three times it — retyping that is arithmetic
     * the app already knows how to do. It multiplies the price the dialog *opened*
     * with rather than whatever is in the field, so stepping up and back down lands
     * on the number it started from instead of compounding. A price typed by hand
     * stands until the count moves again, which is then a deliberate restart.
     */
    fun onSellQuantityChange(value: Int) = local.update {
        val pieces = value.coerceAtLeast(1)
        it.copy(
            sellQuantity = pieces,
            priceText = it.unitPrice?.let { unit -> (unit * pieces).toInputText() } ?: it.priceText,
        )
    }

    fun onPriceChange(value: String) = local.update { it.copy(priceText = value) }

    fun onSoldCompletelyChange(value: Boolean) = local.update { it.copy(soldCompletely = value) }

    fun confirm() {
        val current = local.value
        val item = current.selected ?: return
        val price = parseMoney(current.priceText) ?: return

        viewModelScope.launch {
            runCatching {
                repository.recordSell(
                    itemId = item.id,
                    price = price,
                    quantity = current.sellQuantity,
                    // Taking the last pieces closes the lot on its own, so the tick
                    // only ever has to say "and the rest is not coming back".
                    soldCompletely = current.soldCompletely || current.quantityClosesTheItem,
                )
            }
                .onSuccess { dismiss() }
                .onFailure { e -> local.update { it.copy(error = e.message ?: "Nie udało się zapisać") } }
        }
    }

    /** Opens the "never recorded" form, seeded with whatever was typed to find it. */
    fun startNewItem() = local.update {
        it.copy(newItem = NewItemForm(name = it.query.trim()), error = null)
    }

    fun onNewItemChange(form: NewItemForm) = local.update { it.copy(newItem = form, error = null) }

    fun onNewPhotoCaptured(base64: String?) {
        if (base64 == null) return
        local.update { it.copy(newItem = it.newItem?.copy(photo = base64)) }
    }

    fun clearNewPhoto() = local.update { it.copy(newItem = it.newItem?.copy(photo = null)) }

    fun cancelNewItem() = local.update { it.copy(newItem = null, error = null) }

    /**
     * Records the purchase and the sale together, so a thing that was never entered
     * can still be sold without leaving the screen.
     */
    fun confirmNewItem() {
        val form = local.value.newItem ?: return
        if (!form.canConfirm) return
        val price = form.price ?: return

        viewModelScope.launch {
            runCatching {
                repository.recordBuyAndSell(
                    paid = form.paid,
                    draft = DraftItem(
                        name = form.name.trim(),
                        // The ask is the price of one piece, which is exactly what was
                        // typed — so it stands whether the lot went whole or in part,
                        // and what stays in the magazyn is asked at what one just
                        // fetched rather than at nothing at all.
                        price = form.pricePerPiece,
                        quantity = form.quantity,
                        photo = form.photo,
                    ),
                    // The pieces going now, at that price each. The repository sells
                    // the whole lot or one of it on the same flag, so the two counts
                    // are the same count.
                    price = price,
                    soldCompletely = form.closesTheItem,
                )
            }
                .onSuccess { local.update { it.copy(newItem = null, query = "") } }
                .onFailure { e -> local.update { it.copy(error = e.message ?: "Nie udało się zapisać") } }
        }
    }

    /**
     * The asking price, edited on the item screen. There is no save button — the
     * screen writes what has been typed once the typing stops, because a price
     * marked down at a stall must not depend on remembering to confirm it.
     */
    fun setAskingPrice(itemId: String, text: String) {
        viewModelScope.launch {
            runCatching { repository.setAskingPrice(itemId, parseMoney(text)) }
                .onFailure { e -> local.update { it.copy(error = e.message ?: "Nie udało się zapisać") } }
        }
    }

    /**
     * A new photo for a thing already in stock, or none. Written straight through:
     * there is nothing to confirm about a picture that was just taken.
     */
    fun setPhoto(itemId: String, photo: String?) {
        viewModelScope.launch {
            runCatching { repository.setPhoto(itemId, photo) }
                .onFailure { e -> local.update { it.copy(error = e.message ?: "Nie udało się zapisać") } }
        }
    }

    /** What was paid, corrected the same way — it is the item's buy that changes. */
    fun setPaidPrice(itemId: String, text: String) {
        viewModelScope.launch {
            runCatching { repository.setPaidPrice(itemId, parseMoney(text)) }
                .onFailure { e -> local.update { it.copy(error = e.message ?: "Nie udało się zapisać") } }
        }
    }

    /**
     * The day we bought it, corrected on the sold screen. The buy forms never ask
     * for one, so a thing entered in the evening carries the day it was typed in
     * until somebody notices.
     */
    fun setBoughtDate(itemId: String, date: LocalDate) {
        viewModelScope.launch {
            runCatching { repository.setBoughtDate(itemId, date) }
                .onFailure { e -> local.update { it.copy(error = e.message ?: "Nie udało się zapisać") } }
        }
    }

    /**
     * What a sale went for, corrected after the fact — the same half-second save as
     * the prices on the item screen, and for the same reason.
     *
     * An unreadable amount writes nothing: a sale happened for *some* price, and
     * blanking the field is a half-typed number rather than an answer.
     */
    fun setSellPrice(sellId: String, text: String) {
        val price = parseMoney(text) ?: return
        viewModelScope.launch {
            runCatching { repository.setSellPrice(sellId, price) }
                .onFailure { e -> local.update { it.copy(error = e.message ?: "Nie udało się zapisać") } }
        }
    }

    fun setSellDate(sellId: String, date: LocalDate) {
        viewModelScope.launch {
            runCatching { repository.setSellDate(sellId, date) }
                .onFailure { e -> local.update { it.copy(error = e.message ?: "Nie udało się zapisać") } }
        }
    }

    fun remove(item: Item) {
        viewModelScope.launch {
            runCatching { repository.removeItem(item.id) }
                .onSuccess { dismiss() }
                .onFailure { e -> local.update { it.copy(error = e.message ?: "Nie udało się usunąć") } }
        }
    }
}
