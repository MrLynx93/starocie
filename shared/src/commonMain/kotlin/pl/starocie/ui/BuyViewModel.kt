package pl.starocie.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.starocie.domain.CostAllocator
import pl.starocie.domain.DraftItem
import pl.starocie.domain.Item
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.parseMoney

private val PREVIEW_DATE = LocalDate(2000, 1, 1)
private val PREVIEW_INSTANT = Instant.fromEpochSeconds(0)

private fun previewId(index: Int) = index.toString().padStart(4, '0')

data class BuyUiState(
    val totalText: String = "",
    val name: String = "",
    val drafts: List<DraftItem> = emptyList(),
    val saved: Boolean = false,
    val error: String? = null,
) {
    val total: Money? get() = parseMoney(totalText)

    val canSave: Boolean get() = total != null || drafts.isNotEmpty()

    /**
     * What each item would cost if saved now. Shown live so the split is visible
     * while typing rather than being a surprise afterwards.
     */
    val preview: List<Pair<DraftItem, Money?>>
        get() {
            val total = total
            if (total == null || drafts.isEmpty()) return drafts.map { it to null }

            // Ids are index-based and zero-padded so the allocator's id tie-break
            // matches the order shown on screen.
            val standIns = drafts.mapIndexed { index, draft ->
                Item(
                    id = previewId(index),
                    date = PREVIEW_DATE,
                    name = draft.name,
                    price = draft.price,
                    status = ItemStatus.IN_STOCK,
                    createdBy = "",
                    createdAt = PREVIEW_INSTANT,
                    updatedAt = PREVIEW_INSTANT,
                )
            }
            val shares = CostAllocator.allocate(total, standIns)
            return drafts.mapIndexed { index, draft -> draft to shares[previewId(index)] }
        }

    /** A single item's cost is exact; one of several is an estimate. */
    val previewIsEstimated: Boolean get() = drafts.size > 1
}

class BuyViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(BuyUiState())
    val state: StateFlow<BuyUiState> = _state.asStateFlow()

    fun onTotalChange(value: String) = _state.update { it.copy(totalText = value) }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }

    /** A name is required — it is how the item will be found when selling. */
    fun addDraft(name: String, priceText: String, splittable: Boolean) {
        if (name.isBlank()) return
        val draft = DraftItem(
            name = name.trim(),
            price = parseMoney(priceText),
            splittable = splittable,
        )
        _state.update { it.copy(drafts = it.drafts + draft) }
    }

    fun removeDraft(index: Int) = _state.update {
        it.copy(drafts = it.drafts.filterIndexed { i, _ -> i != index })
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return

        viewModelScope.launch {
            // A rejected write must not reach the dispatcher: an uncaught
            // PERMISSION_DENIED here kills the app instead of reporting itself.
            runCatching {
                repository.recordBuy(
                    price = current.total,
                    name = current.name,
                    // A buy with no items still records the spend; items can be
                    // attached later rather than blocking the save.
                    items = current.drafts,
                )
            }
                .onSuccess { _state.update { it.copy(saved = true) } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Nie zapisano") } }
        }
    }
}
