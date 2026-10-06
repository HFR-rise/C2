package com.example.myapplication.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "estimate_drafts")
data class EstimateDraftEntity(
    @PrimaryKey
    val projectId: String,
    val projectName: String,
    val projectDescription: String,
    val materialsJson: String,
    val workItemsJson: String,
    val comment: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val serverChangeId: String? = null,
    val serverStatus: String? = null
)