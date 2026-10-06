package com.example.myapplication.data.models

import com.google.gson.annotations.SerializedName

data class ChangeRequestDto(
    @SerializedName("id") val id: String,
    @SerializedName("projectId") val projectId: String,
    @SerializedName("projectName") val projectName: String?,
    @SerializedName("authorId") val authorId: String,
    @SerializedName("authorName") val authorName: String?,
    @SerializedName("authorPhone") val authorPhone: String?,
    @SerializedName("kind") val kind: String,
    @SerializedName("status") val status: String,
    @SerializedName("payloadJson") val payloadJson: String?,
    @SerializedName("comment") val comment: String?,
    @SerializedName("reviewComment") val reviewComment: String?,
    @SerializedName("reviewerId") val reviewerId: String?,
    @SerializedName("reviewerName") val reviewerName: String?,
    @SerializedName("reviewerPhone") val reviewerPhone: String?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("reviewedAt") val reviewedAt: String?,
    @SerializedName("version") val version: Long?
)