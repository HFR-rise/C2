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
        val digits = normalize(phone)
        if (digits.isEmpty()) return ""
        val number = if (digits.length == 10) "7$digits" else digits
        return when (number.length) {
            1 -> "+7"
            2 -> "+7 (${number[1]}"
            3 -> "+7 (${number.substring(1, 3)}"
            4 -> "+7 (${number.substring(1, 4)}"
            5 -> "+7 (${number.substring(1, 4)}) ${number[4]}"
            6 -> "+7 (${number.substring(1, 4)}) ${number.substring(4, 6)}"
            7 -> "+7 (${number.substring(1, 4)}) ${number.substring(4, 7)}"
            8 -> "+7 (${number.substring(1, 4)}) ${number.substring(4, 7)}-${number[7]}"
            9 -> "+7 (${number.substring(1, 4)}) ${number.substring(4, 7)}-${number.substring(7, 9)}"
            10 -> "+7 (${number.substring(1, 4)}) ${number.substring(4, 7)}-${number.substring(7, 10)}"
            11 -> "+7 (${number.substring(1, 4)}) ${number.substring(4, 7)}-${number.substring(7, 11)}"
            else -> "+$number"
        }
    }

    fun isValid(phone: String): Boolean {
        val digits = normalize(phone)
        return digits.length in 10..11 && digits.startsWith("7")
    }
}