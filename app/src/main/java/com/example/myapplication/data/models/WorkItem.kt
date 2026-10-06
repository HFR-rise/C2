package com.example.myapplication.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "work_items",
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
data class WorkItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val name: String,
    val stage: Int = 1,
    val laborHours: Double = 0.0,
    val hourlyRate: Double = 0.0,
    val materialCost: Double = 0.0,
    val isCompleted: Boolean = false,
    val completedAt: Date? = null,
    val notes: String = "",
    val userId: String = "",
    val version: Long? = null
) {
    val laborCost: Double get() = laborHours * hourlyRate

    val totalCost: Double get() = laborCost + materialCost
}