package com.example.myapplication.data.models

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class SendCodeRequest(
    @SerializedName("phoneNumber")
    val phoneNumber: String
)

data class VerifyCodeRequest(
    @SerializedName("phoneNumber")
    val phoneNumber: String,
    @SerializedName("code")
    val code: String,
    @SerializedName("deviceId")
    val deviceId: String? = null
)

data class UserResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("phoneNumber")
    val phoneNumber: String,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("verified")
    val verified: Boolean = false,
    @SerializedName("createdAt")
    val createdAt: String? = null
)

data class SyncMessage(
    @SerializedName("type")
    val type: String,
    @SerializedName("entityType")
    val entityType: String,
    @SerializedName("entityId")
    val entityId: String,
    @SerializedName("data")
    val data: JsonElement?,
    @SerializedName("userId")
    val userId: String,
    @SerializedName("timestamp")
    val timestamp: String,
    @SerializedName("version")
    val version: Long?
)