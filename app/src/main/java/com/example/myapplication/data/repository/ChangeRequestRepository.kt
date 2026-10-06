package com.example.myapplication.data.repository

import com.example.myapplication.data.models.*
import com.example.myapplication.network.ApiService
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChangeRequestRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getInbox(): Response<List<ChangeRequestDto>> =
        apiService.getChangesInbox()

    suspend fun getForProject(projectId: String): Response<List<ChangeRequestDto>> =
        apiService.getProjectChanges(projectId)

    suspend fun getPendingEstimate(projectId: String): Response<ChangeRequestDto> =
        apiService.getPendingEstimateChange(projectId)

    suspend fun submitEstimate(
        projectId: String,
        snapshot: ProjectSnapshotDto
    ): Response<ChangeRequestDto> =
        apiService.submitEstimateChange(projectId, snapshot)

    suspend fun addComment(
        projectId: String,
        text: String,
        entityType: String? = null,
        entityId: String? = null
    ): Response<ChangeRequestDto> =
        apiService.addComment(projectId, CommentRequestDto(text, entityType, entityId))

    suspend fun approve(changeId: String, comment: String? = null): Response<ChangeRequestDto> =
        apiService.approveChange(changeId, mapOf("comment" to comment))

    suspend fun reject(changeId: String, comment: String): Response<ChangeRequestDto> =
        apiService.rejectChange(changeId, mapOf("comment" to comment))

    suspend fun deleteDraft(changeId: String): Response<Unit> =
        apiService.deleteDraft(changeId)

    suspend fun getMyRole(projectId: String): Response<Map<String, String>> =
        apiService.getMyRole(projectId)
}
