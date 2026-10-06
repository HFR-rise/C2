package com.example.myapplication.data.models

import com.google.gson.annotations.SerializedName

data class ProjectCreateRequest(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("objectId") val objectId: String? = null
)