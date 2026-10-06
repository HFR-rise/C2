package com.example.myapplication.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["objectId"]),
        Index(value = ["myRole"]),
        Index(value = ["needsSync"])
    ]
)
data class Project(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val shareStatus: ShareStatus = ShareStatus.PENDING,
    val objectId: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val totalBudget: Double = 0.0,
    val totalSpent: Double = 0.0,
    val userId: String = "",
    val version: Long? = null,


    val state: String = "DRAFT",

    val hasPendingChanges: Boolean = false,

    val myRole: String? = null,

    val needsSync: Boolean = false
)

enum class ProjectStatus {
    ACTIVE, COMPLETED, ARCHIVED
}

enum class ShareStatus {
    PENDING, ACCEPTED, DECLINED
}