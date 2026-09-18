package com.example.myapplication.network.models

data class SessionCheckResponse(
    val isValid: Boolean,
    val message: String? = null
)