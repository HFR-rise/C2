package com.example.myapplication.data.models

import com.google.gson.annotations.SerializedName

data class ObjectSnapshotDto(
    @SerializedName("name")
    val name: String?,

    @SerializedName("street")
    val street: String?,

    @SerializedName("house")
    val house: String?,

    @SerializedName("building")
    val building: String?,

    @SerializedName("description")
    val description: String?,

    @SerializedName("parentObjectId")
    val parentObjectId: String?
)