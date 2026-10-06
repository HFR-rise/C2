package com.example.myapplication.data.models

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProjectDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("shareStatus") val shareStatus: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("hasPendingChanges") val hasPendingChanges: Boolean? = null,
    @SerializedName("objectId") val objectId: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("totalBudget") val totalBudget: Double? = null,
    @SerializedName("totalSpent") val totalSpent: Double? = null,
    @SerializedName("createdBy") val createdBy: String? = null,
    @SerializedName("lastModifiedBy") val lastModifiedBy: String? = null,
    @SerializedName("version") val version: Long? = null,
    @SerializedName("members") val members: List<ProjectMemberDto>? = null,
    @SerializedName("myRole") val myRole: String? = null,

    @SerializedName("materials") val materials: List<Material>? = null,
    @SerializedName("workItems") val workItems: List<WorkItem>? = null
) {

    fun toEntity(): Project = Project(
        id = id,
        name = name,
        description = description.orEmpty(),
        shareStatus = parseShareStatus(shareStatus),
        objectId = objectId,
        createdAt = parseDate(createdAt) ?: Date(),
        updatedAt = parseDate(updatedAt) ?: Date(),
        status = parseProjectStatus(status),
        totalBudget = totalBudget ?: 0.0,
        totalSpent = totalSpent ?: 0.0,
        userId = createdBy.orEmpty(),
        version = version,
        state = state ?: "DRAFT",
        hasPendingChanges = hasPendingChanges ?: false,
        myRole = myRole
    )

    private fun parseShareStatus(value: String?): ShareStatus =
        runCatching { ShareStatus.valueOf(value ?: "PENDING") }
            .getOrDefault(ShareStatus.PENDING)

    private fun parseProjectStatus(value: String?): ProjectStatus =
        runCatching { ProjectStatus.valueOf(value ?: "ACTIVE") }
            .getOrDefault(ProjectStatus.ACTIVE)

    private fun parseDate(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(value)
        }.getOrNull()
    }
}