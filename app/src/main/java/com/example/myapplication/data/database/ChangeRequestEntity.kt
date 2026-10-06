package com.example.myapplication.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "change_requests",
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["authorId"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class ChangeRequestEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val projectName: String? = null,
    val authorId: String,
    val authorName: String? = null,
    val authorPhone: String? = null,
    val kind: String,
    val status: String,
    val payloadJson: String? = null,
    val comment: String? = null,
    val reviewComment: String? = null,
    val reviewerId: String? = null,
    val reviewerName: String? = null,
    val reviewerPhone: String? = null,
    val createdAt: Long,
    val reviewedAt: Long? = null,
    val version: Long? = null
)