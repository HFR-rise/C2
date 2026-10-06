package com.example.myapplication.utils

import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod

object ContactPhoneExtractor {

    fun extractPhone(
        contact: Contact,
        methods: List<ContactMethod>
    ): String? {
        val phoneMethod = methods
            .filter { it.contactId == contact.id }
            .firstOrNull { isPhoneMethod(it) }
            ?: return null

        val normalized = PhoneUtils.normalize(phoneMethod.value)
        return normalized.takeIf { it.length >= 11 }
    }

    fun isPhoneMethod(method: ContactMethod): Boolean {
        val type = method.methodType.lowercase()
        return type.contains("телефон") || type.contains("phone")
    }
}