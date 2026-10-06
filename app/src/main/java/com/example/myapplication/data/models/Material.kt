package com.example.myapplication.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "materials",
    indices = [Index(value = ["projectId"])],
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Material(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val name: String,
    val quantity: Double = 0.0,
    val unit: String = "шт",
    val unitPrice: Double = 0.0,
    val category: String = "",
    val notes: String = "",
    val userId: String = "",
    val version: Long? = null
) {
    val totalPrice: Double
        get() = quantity * unitPrice
}