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
import pl.starocie.domain.parseMoney

data class SellUiState(
    val query: String = "",
    val inStock: List<Item> = emptyList(),
    val selected: Item? = null,
    val priceText: String = "",
    val note: String = "",
    val soldCompletely: Boolean = true,
) {
    val canConfirm: Boolean get() = selected != null && parseMoney(priceText) != null

    /** Offered when nothing matches — the on-the-fly path, cost left unknown. */
    val canCreateFromQuery: Boolean
        get() = query.isNotBlank() && inStock.none { it.matches(query) }
}

private fun Item.matches(query: String): Boolean =
    name?.contains(query, ignoreCase = true) == true ||
        note?.contains(query, ignoreCase = true) == true

class SellViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val local = MutableStateFlow(SellUiState())

    /**
     * Search runs in memory over the whole in-stock list, so it is instant and works
     * offline. An empty query shows everything — the grid is what makes nameless
     * items sellable, since you recognise the thing rather than its name.
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
            priceText = item.price?.let { p -> (p.minor / 100.0).toString() } ?: "",
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
            repository.recordSell(
                itemId = item.id,
                price = price,
                note = current.note,
                soldCompletely = current.soldCompletely,
            )
            dismiss()
        }
    }

    /** Creates the item and immediately selects it, so selling continues in one go. */
    fun createFromQuery() {
        val name = local.value.query.trim()
        if (name.isEmpty()) return

        viewModelScope.launch {
            val id = repository.createLooseItem(DraftItem(name = name))
            repository.ledger.value.itemById(id)?.let { created ->
                local.update {
                    it.copy(query = "", selected = created, priceText = "", soldCompletely = true)
                }
            }
        }
    }

    fun remove(item: Item) {
        viewModelScope.launch {
            repository.removeItem(item.id)
            dismiss()
        }
    }
}
