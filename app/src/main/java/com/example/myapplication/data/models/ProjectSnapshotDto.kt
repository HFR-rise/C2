package com.example.myapplication.data.models

import com.google.gson.annotations.SerializedName

data class ProjectSnapshotDto(
    @SerializedName("name") val name: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("objectId") val objectId: String? = null,
    @SerializedName("materials") val materials: List<MaterialSnapshotDto>?,
    @SerializedName("workItems") val workItems: List<WorkItemSnapshotDto>?,
    @SerializedName("totalBudget") val totalBudget: Double?,
    @SerializedName("comment") val comment: String?
)

data class MaterialSnapshotDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("quantity") val quantity: Double?,
    @SerializedName("unit") val unit: String?,
    @SerializedName("unitPrice") val unitPrice: Double?,
    @SerializedName("category") val category: String?,
    @SerializedName("notes") val notes: String?
)

data class WorkItemSnapshotDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("stage") val stage: Int?,
    @SerializedName("laborHours") val laborHours: Double?,
    @SerializedName("hourlyRate") val hourlyRate: Double?,
    @SerializedName("materialCost") val materialCost: Double?,
    @SerializedName("isCompleted") val isCompleted: Boolean?,
    @SerializedName("notes") val notes: String?
)

data class CommentRequestDto(
    @SerializedName("text") val text: String,
    @SerializedName("entityType") val entityType: String? = null,
    @SerializedName("entityId") val entityId: String? = null
)