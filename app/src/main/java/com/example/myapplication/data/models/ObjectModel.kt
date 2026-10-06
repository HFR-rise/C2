package com.example.myapplication.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "objects",
    foreignKeys = [
        ForeignKey(
            entity = ObjectModel::class,
            parentColumns = ["id"],
            childColumns = ["parentObjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["parentObjectId"]),
        Index(value = ["userId"]),
        Index(value = ["needsSync"])
    ]
)
data class ObjectModel(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val street: String = "",
    val house: String = "",
    val building: String = "",
    val description: String = "",
    val parentObjectId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String = "",

    val needsSync: Boolean = false,

    val version: Long? = null
) {
    fun getFormattedAddress(): String = buildString {
        if (street.isNotBlank()) append("ул. $street")
        if (house.isNotBlank()) {
            if (isNotEmpty()) append(", ")
            append("д. $house")
        }
        if (building.isNotBlank()) {
            if (isNotEmpty()) append(", ")
            append("к. $building")
        }
    }
}