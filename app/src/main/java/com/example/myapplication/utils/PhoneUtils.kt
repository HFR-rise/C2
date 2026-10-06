package com.example.myapplication.utils

object PhoneUtils {
    private val DIGITS_REGEX = Regex("[^\\d]")

    fun normalize(phone: String): String {
        val digits = phone.replace(DIGITS_REGEX, "")
        return when {
            digits.startsWith("8") && digits.length == 11 -> "7" + digits.substring(1)
            digits.startsWith("7") && digits.length == 11 -> digits
            digits.length == 10 -> "7" + digits
            digits.length > 11 -> normalize(digits.takeLast(11))
            else -> digits
        }
    }

    fun format(phone: String): String {
        val rawDigits = phone.replace(DIGITS_REGEX, "").take(11)
        if (rawDigits.isEmpty()) return ""

        val userDigits = when {
            rawDigits.startsWith("8") -> rawDigits.substring(1)
            rawDigits.startsWith("7") -> rawDigits.substring(1)
            else -> rawDigits
        }.take(10)

        if (userDigits.isEmpty()) {
            return "+7 ("
        }

        return buildString {
            append("+7 (")
            userDigits.forEachIndexed { index, digit ->
                when (index) {
                    3 -> append(") ")
                    6 -> append("-")
                    8 -> append("-")
                }
                append(digit)
            }
        }
    }

    fun isValid(phone: String): Boolean {
        val digits = normalize(phone)
        return digits.length == 11 && digits.startsWith("7")
    }

    fun cursorPositionAfterDigit(formatted: String, digitIndex: Int): Int {
        if (digitIndex <= 0) return 0
        var seen = 0
        for (i in formatted.indices) {
            if (formatted[i].isDigit()) {
                seen++
                if (seen == digitIndex) return i + 1
            }
        }
        return formatted.length
    }
}