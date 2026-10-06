package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.models.AddBuildersResponse
import com.example.myapplication.data.models.ProjectMemberDto
import com.example.myapplication.network.ApiService
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectMemberRepository @Inject constructor(
    private val apiService: ApiService
) {
    private companion object {
        const val TAG = "ProjectMemberRepo"
    }

    suspend fun getMembers(projectId: String): Response<List<ProjectMemberDto>> {
        return apiService.getProjectMembers(projectId)
    }

    suspend fun setCustomer(projectId: String, phoneNumber: String): Response<Unit> {
        Log.d(TAG, "setCustomer: $projectId ← ${mask(phoneNumber)}")
        return apiService.setCustomer(projectId, mapOf("phoneNumber" to phoneNumber))
    }

    suspend fun setEstimator(projectId: String, phoneNumber: String): Response<Unit> {
        Log.d(TAG, "setEstimator: $projectId ← ${mask(phoneNumber)}")
        return apiService.setEstimator(projectId, mapOf("phoneNumber" to phoneNumber))
    }

    suspend fun checkUserExists(phone: String): Boolean {
        return checkUserExistsRaw(phone) == true
    }

    suspend fun checkUserExistsRaw(phone: String): Boolean? {
        return try {
            val response = apiService.checkUserExists(phone)
            if (response.isSuccessful) {
                val exists = response.body()?.get("exists")
                Log.d(TAG, "checkUserExistsRaw(${mask(phone)}): $exists")
                exists
            } else {
                Log.w(TAG, "checkUserExistsRaw(${mask(phone)}): HTTP ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkUserExistsRaw(${mask(phone)}) failed: ${e.message}", e)
            null
        }
    }

    suspend fun changeRole(
        projectId: String,
        targetUserId: String,
        newRole: String
    ): Response<Unit> {
        Log.d(TAG, "changeRole: $projectId user=$targetUserId → $newRole")
        return apiService.changeMemberRole(
            projectId,
            targetUserId,
            mapOf("role" to newRole)
        )
    }

    suspend fun addBuilders(
        projectId: String,
        phoneNumbers: List<String>
    ): Response<AddBuildersResponse> {
        return apiService.addBuilders(projectId, ApiService.AddBuildersRequest(phoneNumbers))
    }

    suspend fun removeMember(projectId: String, targetUserId: String): Response<Unit> {
        return apiService.removeMember(projectId, targetUserId)
    }

    private fun mask(phone: String): String =
        if (phone.length > 4) phone.take(phone.length - 4) + "****" else "****"
}