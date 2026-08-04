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
import pl.starocie.domain.toInputText

data class SellUiState(
    val query: String = "",
    val inStock: List<Item> = emptyList(),
    val selected: Item? = null,
    val priceText: String = "",
    val note: String = "",
    val soldCompletely: Boolean = true,
    val error: String? = null,
) {
    val canConfirm: Boolean get() = selected != null && parseMoney(priceText) != null

    /** Offered when nothing matches — the on-the-fly path, cost left unknown. */
    val canCreateFromQuery: Boolean
        get() = query.isNotBlank() && inStock.none { it.matches(query) }
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
                .onFailure { e -> local.update { it.copy(error = e.message ?: "Nie zapisano") } }
        }
    }

    /** Creates the item and immediately selects it, so selling continues in one go. */
    fun createFromQuery() {
        val name = local.value.query.trim()
        if (name.isEmpty()) return

        viewModelScope.launch {
            runCatching { repository.addItem(null, DraftItem(name = name)) }
                .onSuccess { id ->
                    repository.ledger.value.itemById(id)?.let { created ->
                        local.update {
                            it.copy(
                                query = "",
                                selected = created,
                                priceText = "",
                                soldCompletely = true,
                            )
                        }
                    }
                }
                .onFailure { e -> local.update { it.copy(error = e.message ?: "Nie zapisano") } }
        }
    }

    fun remove(item: Item) {
        viewModelScope.launch {
            runCatching { repository.removeItem(item.id) }
                .onSuccess { dismiss() }
                .onFailure { e -> local.update { it.copy(error = e.message ?: "Nie usunięto") } }
        }
    }
}
