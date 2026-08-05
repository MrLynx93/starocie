package pl.starocie.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.starocie.domain.DraftItem
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.parseMoney

data class BuyOneUiState(
    val name: String = "",
    val paidText: String = "",
    val askingText: String = "",
    val quantityText: String = "1",
    /** Base64 JPEG of the thing in front of you, if a photo was taken. */
    val photo: String? = null,
    /** How many recorded in this sitting. */
    val recordedCount: Int = 0,
    /**
     * Set while filling a box: items attach to that buy and the price field is
     * hidden, because the price was paid once for the whole lot.
     */
    val buyId: String? = null,
    val error: String? = null,
) {
    val paid: Money? get() = parseMoney(paidText)

    /** Blank or nonsense means one, so a stray edit cannot lose the record. */
    val quantity: Int get() = quantityText.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1

    /** More than one is a lot that sells in parts — mirrors `Item.splittable`. */
    val splittable: Boolean get() = quantity > 1

    /** Only a standalone purchase asks what was paid; a box already knows. */
    val showPaid: Boolean get() = buyId == null

    val canSave: Boolean get() = name.isNotBlank()
}

/**
 * One thing, then straight into the next.
 *
 * Standalone, each item is the sole item of its own buy, so its cost is exact.
 * Attached to a box, items accumulate against that buy and the allocator splits
 * its price across them — the screen is identical either way, which is the point:
 * unpacking a box is the same motion as buying things one at a time.
 */
class BuyOneViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(BuyOneUiState())
    val state: StateFlow<BuyOneUiState> = _state.asStateFlow()

    /** Null for standalone purchases; a buy id when filling a box. */
    fun attachTo(buyId: String?) {
        if (_state.value.buyId != buyId) _state.update { it.copy(buyId = buyId) }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }

    fun onPaidChange(value: String) = _state.update { it.copy(paidText = value) }

    fun onAskingChange(value: String) = _state.update { it.copy(askingText = value) }

    fun onQuantityChange(value: String) = _state.update { it.copy(quantityText = value) }

    fun onPhotoCaptured(base64: String?) {
        if (base64 != null) _state.update { it.copy(photo = base64) }
    }

    fun clearPhoto() = _state.update { it.copy(photo = null) }

    /** Saves, then clears the form so the next thing can be typed straight away. */
    fun save() {
        val current = _state.value
        if (!current.canSave) return

        val draft = DraftItem(
            name = current.name.trim(),
            price = parseMoney(current.askingText),
            quantity = current.quantity,
            photo = current.photo,
        )

        viewModelScope.launch {
            runCatching {
                val buyId = current.buyId
                if (buyId == null) {
                    repository.recordBuy(current.paid, name = null, items = listOf(draft))
                } else {
                    repository.addItem(buyId, draft)
                }
            }.onSuccess {
                // Keep the box attachment and the tally; clear only the entry.
                _state.update {
                    BuyOneUiState(
                        buyId = it.buyId,
                        recordedCount = it.recordedCount + 1,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Nie udało się zapisać") }
            }
        }
    }
}
