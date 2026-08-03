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
    val splittable: Boolean = false,
    /** Names recorded in this sitting, newest first — the running tally. */
    val recorded: List<String> = emptyList(),
    val error: String? = null,
) {
    val paid: Money? get() = parseMoney(paidText)
    val canSave: Boolean get() = name.isNotBlank()
}

/**
 * One thing, one price, then straight into the next. Because each item is the sole
 * item of its own buy, its cost is exact — no allocation is involved in this flow.
 */
class BuyOneViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(BuyOneUiState())
    val state: StateFlow<BuyOneUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }

    fun onPaidChange(value: String) = _state.update { it.copy(paidText = value) }

    fun onAskingChange(value: String) = _state.update { it.copy(askingText = value) }

    fun onSplittableChange(value: Boolean) = _state.update { it.copy(splittable = value) }

    /** Saves, then clears the form so the next thing can be typed straight away. */
    fun save() {
        val current = _state.value
        if (!current.canSave) return

        viewModelScope.launch {
            runCatching {
                repository.recordBuy(
                    price = current.paid,
                    name = null,
                    items = listOf(
                        DraftItem(
                            name = current.name.trim(),
                            price = parseMoney(current.askingText),
                            splittable = current.splittable,
                        ),
                    ),
                )
            }.onSuccess {
                _state.update {
                    BuyOneUiState(recorded = listOf(current.name.trim()) + it.recorded)
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Nie zapisano") }
            }
        }
    }
}
