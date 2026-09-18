package com.example.myapplication.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatterUtils {
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("ru", "RU"))
    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    fun formatCurrency(amount: Double): String = currencyFormatter.format(amount)

    fun formatCurrencyPlain(amount: Double): String =
        String.format(Locale.US, "%.2f", amount) + " ₽"

    fun formatDate(date: Date): String = dateFormatter.format(date)

    fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    fun formatQuantity(quantity: Double, unit: String): String =
        "${formatNumber(quantity)} $unit"
}