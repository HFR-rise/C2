package com.example.myapplication.utils

import android.util.Log
import org.apache.commons.text.similarity.LevenshteinDistance

object FuzzySearch {

    private val levenshtein = LevenshteinDistance()

    fun normalizePhone(phone: String): String {
        val digitsOnly = phone.replace(Regex("[^\\d]"), "")

        return when {
            digitsOnly.startsWith("8") && digitsOnly.length == 11 -> {
                "7" + digitsOnly.substring(1)
            }
            digitsOnly.startsWith("7") && digitsOnly.length == 11 -> {
                digitsOnly
            }
            digitsOnly.length == 10 -> {
                "7" + digitsOnly
            }
            digitsOnly.length > 11 -> {
                val last11 = digitsOnly.takeLast(11)
                if (last11.startsWith("7") || last11.startsWith("8")) {
                    normalizePhone(last11)
                } else {
                    "7" + last11
                }
            }
            digitsOnly.startsWith("8") && digitsOnly.length < 11 -> {
                "7" + digitsOnly.substring(1)
            }
            else -> digitsOnly
        }
    }

    fun matches(text: String, query: String, maxDistance: Int = 2): Boolean {
        if (query.isBlank()) return true
        if (text.isBlank()) return false

        val textLower = text.lowercase()
        val queryLower = query.lowercase()

        if (textLower.contains(queryLower)) return true

        val textNormalized = normalizePhone(textLower)
        val queryNormalized = normalizePhone(queryLower)

        if (textNormalized.isNotEmpty() && queryNormalized.isNotEmpty()) {
            if (textNormalized == queryNormalized) {
                Log.d("FuzzySearch", "Phone exact match: $textNormalized == $queryNormalized")
                return true
            }
            if (textNormalized.contains(queryNormalized)) {
                Log.d("FuzzySearch", "Phone contains match: $textNormalized contains $queryNormalized")
                return true
            }
            if (queryNormalized.length >= 5) {
                val distance = levenshtein.apply(textNormalized, queryNormalized)
                if (distance <= maxDistance) {
                    Log.d("FuzzySearch", "Phone fuzzy match: $textNormalized vs $queryNormalized, distance=$distance")
                    return true
                }
            }
        }

        if (queryLower.length < 3) {
            return textLower == queryLower || textLower.contains(queryLower)
        }

        val words = textLower.split(" ")
        for (word in words) {
            if (word.length >= queryLower.length - maxDistance) {
                val distance = levenshtein.apply(word, queryLower)
                if (distance <= maxDistance) return true
            }
        }

        if (queryLower.length >= 3) {
            val totalDistance = levenshtein.apply(textLower, queryLower)
            if (totalDistance <= maxDistance) return true
        }

        return false
    }

    fun <T> filter(
        items: List<T>,
        query: String,
        textExtractor: (T) -> String,
        maxDistance: Int = 2
    ): List<T> {
        if (query.isBlank()) return items
        return items.filter { item ->
            matches(textExtractor(item), query, maxDistance)
        }
    }
}