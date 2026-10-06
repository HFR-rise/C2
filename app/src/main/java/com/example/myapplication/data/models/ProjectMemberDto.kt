package com.example.myapplication.data.models

import com.google.gson.annotations.SerializedName

data class ProjectMemberDto(
    @SerializedName("id") val id: String,
    @SerializedName("projectId") val projectId: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("phoneNumber") val phoneNumber: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("role") val role: String,
    @SerializedName("addedBy") val addedBy: String? = null,
    @SerializedName("addedAt") val addedAt: String? = null
) {
    fun displayName(): String = name
        ?: phoneNumber
        ?: userId

    fun isCurrent(userId: String): Boolean = this.userId == userId
}
