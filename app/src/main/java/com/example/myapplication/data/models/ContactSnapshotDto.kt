package com.example.myapplication.data.models

import com.google.gson.annotations.SerializedName


data class ContactSnapshotDto(
    @SerializedName("name")
    val name: String?,

    @SerializedName("description")
    val description: String?,

    @SerializedName("methods")
    val methods: List<ContactMethodSnapshotDto>?
)

data class ContactMethodSnapshotDto(
    @SerializedName("id")
    val id: String?,

    @SerializedName("methodType")
    val methodType: String?,

    @SerializedName("value")
    val value: String?
)