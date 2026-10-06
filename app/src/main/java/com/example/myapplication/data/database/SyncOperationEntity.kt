package com.example.myapplication.data.database

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "sync_operations",
    primaryKeys = ["entityType", "entityId"],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "updatedAt"]),
        Index(value = ["updatedAt"])
    ]
)
data class SyncOperationEntity(
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payload: String?,
    val updatedAt: Long,
    val userId: String,
    val attempts: Int = 0,
    val lastError: String? = null
)