package pl.starocie.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.parseMoney

data class BuyBoxUiState(
    val date: LocalDate = today(),
    val totalText: String = "",
    val name: String = "",
    /** Set once the buy exists; the screen then hands off to item entry. */
    val openedBuyId: String? = null,
    val error: String? = null,
) {
    val total: Money? get() = parseMoney(totalText)
    val canOpen: Boolean get() = total != null
}

/**
 * Step one of the box: record what was paid and open the buy. Its contents are
 * then entered on the ordinary item screen, which appends to this buy.
 *
 * Opening the buy before its contents are known is what lets each item be saved as
 * it is unpacked, rather than held in memory until a final "save all" that backing
 * out would discard.
 */
class BuyBoxViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(BuyBoxUiState())
    val state: StateFlow<BuyBoxUiState> = _state.asStateFlow()

    fun onDateChange(value: LocalDate) = _state.update { it.copy(date = value) }

    fun onTotalChange(value: String) = _state.update { it.copy(totalText = value, error = null) }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }

    fun open() {
        val current = _state.value
        if (!current.canOpen) return

        viewModelScope.launch {
            runCatching { repository.createBuy(current.total, current.name, current.date) }
                .onSuccess { id -> _state.update { it.copy(openedBuyId = id) } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Nie udało się zapisać") } }
        }
    }
}
