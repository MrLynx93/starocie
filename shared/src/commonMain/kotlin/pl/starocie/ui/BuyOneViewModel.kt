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
    /** What one of them cost — the field is per piece, as the asking price is. */
    val paidPerPiece: Money? get() = parseMoney(paidText)

    /** Blank or nonsense means one, so a stray edit cannot lose the record. */
    val quantity: Int get() = quantityText.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1

    /** More than one is a lot that sells in parts — mirrors `Item.splittable`. */
    val splittable: Boolean get() = quantity > 1

    /**
     * The buy's price, which is a total: one piece's cost taken as many times as
     * there are pieces. `Buy.price` is what was handed over for the lot, so the
     * multiplication happens here rather than anywhere the cost is later read.
     */
    val paid: Money? get() = paidPerPiece?.let { it * quantity }

    /** Only a standalone purchase asks what was paid; a box already knows. */
    val showPaid: Boolean get() = buyId == null

    /**
     * A name and, when one is asked for, what was paid. The price is required here
     * because at the moment of buying you know it — a blank would not be an honest
     * unknown, just a field skipped. The shortcut sale still takes an empty price,
     * which is where a genuinely unknown cost comes from.
     */
    val canSave: Boolean get() = name.isNotBlank() && (!showPaid || paidPerPiece != null)
}

/**
 * One thing, then straight into the next.
 *
 * Standalone, each item is the sole item of its own buy, so its cost is exact.
 * Attached to a box, items accumulate against that buy and the allocator splits
 * its price across them — the screen is identical either way, which is the point:
 * unpacking a box is the same motion as buying things one at a time.
 *
 * **A lot is priced by the piece, both times**, exactly as it is on the shortcut
 * sale's form: what one of them cost and what one of them is to go for. That is what
 * the person holding the thing knows, and the lot's total is the multiplication they
 * would otherwise be doing in their head at somebody else's table.
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

    /**
     * Buys and leaves. It no longer doubles as the way out of an empty form — the
     * back button is that — so it does nothing at all when there is nothing to buy.
     */
    fun saveAndLeave(onLeave: () -> Unit) = save(onSaved = onLeave)

    /** Saves, then clears the form so the next thing can be typed straight away. */
    fun save(onSaved: () -> Unit = {}) {
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
                    // `paid` is the lot's total, multiplied up from the piece price
                    // that was typed — a buy holds what was handed over, not a rate.
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
                onSaved()
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Nie udało się zapisać") }
            }
        }
    }
}
