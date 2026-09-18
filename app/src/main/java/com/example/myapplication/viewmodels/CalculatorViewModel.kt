package com.example.myapplication.viewmodels

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CalculatorInput(
    val unitPrice: String = "",
    val quantity: String = "",
    val laborHours: String = "",
    val hourlyRate: String = ""
) {
    val unitPriceValue: Double get() = unitPrice.toDoubleOrNull() ?: 0.0
    val quantityValue: Double get() = quantity.toDoubleOrNull() ?: 0.0
    val laborHoursValue: Double get() = laborHours.toDoubleOrNull() ?: 0.0
    val hourlyRateValue: Double get() = hourlyRate.toDoubleOrNull() ?: 0.0
}

data class CalculatorResult(
    val materialTotal: Double = 0.0,
    val laborTotal: Double = 0.0,
    val grandTotal: Double = 0.0
)

@HiltViewModel
class CalculatorViewModel @Inject constructor() : BaseViewModel() {

    private val _input = MutableStateFlow(CalculatorInput())
    val input: StateFlow<CalculatorInput> = _input.asStateFlow()

    val unitPrice: StateFlow<String> = _input.map { it.unitPrice }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val quantity: StateFlow<String> = _input.map { it.quantity }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val laborHours: StateFlow<String> = _input.map { it.laborHours }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val hourlyRate: StateFlow<String> = _input.map { it.hourlyRate }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val result: StateFlow<CalculatorResult> = _input
        .map { input ->
            val materialTotal = input.unitPriceValue * input.quantityValue
            val laborTotal = input.laborHoursValue * input.hourlyRateValue

            CalculatorResult(
                materialTotal = materialTotal,
                laborTotal = laborTotal,
                grandTotal = materialTotal + laborTotal
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CalculatorResult()
        )

    fun updateUnitPrice(value: String) {
        _input.value = _input.value.copy(unitPrice = value)
    }

    fun updateQuantity(value: String) {
        _input.value = _input.value.copy(quantity = value)
    }

    fun updateLaborHours(value: String) {
        _input.value = _input.value.copy(laborHours = value)
    }

    fun updateHourlyRate(value: String) {
        _input.value = _input.value.copy(hourlyRate = value)
    }

    fun reset() {
        _input.value = CalculatorInput()
    }

    @Deprecated("Используйте result.value.materialTotal")
    fun calculateMaterialTotal(): Double = result.value.materialTotal

    @Deprecated("Используйте result.value.laborTotal")
    fun calculateLaborTotal(): Double = result.value.laborTotal

    @Deprecated("Используйте result.value.grandTotal")
    fun calculateGrandTotal(): Double = result.value.grandTotal
}