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
import pl.starocie.domain.DraftItem
import pl.starocie.domain.Item
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
 */
data class NewItemForm(
    val name: String = "",
    val paidText: String = "",
    val priceText: String = "",
    val quantityText: String = "1",
    val note: String = "",
    val photo: String? = null,
    /** Only meaningful for a lot; a single thing is always sold in full. */
    val soldCompletely: Boolean = false,
) {
    /** What was paid for it, if that is known at all. */
    val paid: Money? get() = parseMoney(paidText)

    /** What it went for. Required — this is a sale. */
    val price: Money? get() = parseMoney(priceText)

    /** Blank or nonsense means one, so a stray edit cannot lose the record. */
    val quantity: Int get() = quantityText.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1

    val splittable: Boolean get() = quantity > 1

    /** A lot only closes when ticked; anything else is closed by its only sale. */
    val closesTheItem: Boolean get() = !splittable || soldCompletely

    val canConfirm: Boolean get() = name.isNotBlank() && price != null
}

data class SellUiState(
    val query: String = "",
    val inStock: List<Item> = emptyList(),
    val selected: Item? = null,
    val priceText: String = "",
    val note: String = "",
    val soldCompletely: Boolean = true,
    /** Non-null while the "never recorded" form is open. */
    val newItem: NewItemForm? = null,
    val error: String? = null,
) {
    val canConfirm: Boolean get() = selected != null && parseMoney(priceText) != null

    /**
     * Offered whenever the typed name is not already in stock — including with an
     * empty search, because at the start nothing is recorded and the on-the-fly
     * path is then the *only* path.
     */
    val canAddNew: Boolean
        get() = inStock.none { it.name.equals(query.trim(), ignoreCase = true) }
}

private fun Item.matches(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
        note?.contains(query, ignoreCase = true) == true

class SellViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val local = MutableStateFlow(SellUiState())

    /**
     * Search runs in memory over the whole in-stock list, so it is instant and works
     * offline. An empty query shows everything, so the list doubles as the browse
     * view when you cannot remember what a thing was called.
     */
    val state: StateFlow<SellUiState> = combine(local, repository.ledger) { ui, ledger ->
        val stock = ledger.itemsInStock()
        ui.copy(
            inStock = if (ui.query.isBlank()) stock else stock.filter { it.matches(ui.query) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SellUiState())

    fun onQueryChange(value: String) = local.update { it.copy(query = value) }

    fun select(item: Item) = local.update {
        it.copy(
            selected = item,
            // Pre-fill with the asking price: usually right, always editable.
            priceText = item.price?.toInputText() ?: "",
            note = "",
            soldCompletely = !item.splittable,
        )
    }

    fun dismiss() = local.update { it.copy(selected = null, priceText = "", note = "") }

    fun onPriceChange(value: String) = local.update { it.copy(priceText = value) }

    fun onNoteChange(value: String) = local.update { it.copy(note = value) }

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
                    note = current.note,
                    soldCompletely = current.soldCompletely,
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
                        // The ask is what it went for — but only when the whole thing
                        // went. Part of a lot says nothing about the rest of it.
                        price = price.takeIf { form.closesTheItem },
                        quantity = form.quantity,
                        note = form.note.takeIf { it.isNotBlank() },
                        photo = form.photo,
                    ),
                    price = price,
                    soldCompletely = form.closesTheItem,
                )
            }
                .onSuccess { local.update { it.copy(newItem = null, query = "") } }
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
