
package com.example.myapplication.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_operations")
data class SyncOperationEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val entityType: String,
    val entityId: String,
    val data: String,
    val timestamp: Long,
    val userId: String
)