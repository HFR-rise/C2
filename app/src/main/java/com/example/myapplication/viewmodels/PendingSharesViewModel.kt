package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.repository.ProjectRepository
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class PendingSharesViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val userPreferences: UserPreferences,
    private val syncManager: SyncManager
) : BaseViewModel() {

    private companion object {
        const val TAG = "PendingShares"
    }

    private val _pendingProjects = MutableStateFlow<List<Project>>(emptyList())
    val pendingProjects: StateFlow<List<Project>> = _pendingProjects.asStateFlow()

    private val _processingIds = MutableStateFlow<Set<String>>(emptySet())
    val processingIds: StateFlow<Set<String>> = _processingIds.asStateFlow()

    private val activeOperations = mutableMapOf<String, Job>()

    fun loadPendingProjects() {
        safeLaunch(
            block = { fetchPendingProjects() },
            onSuccess = { projects ->
                _pendingProjects.value = projects
                Log.d(TAG, "Loaded ${projects.size} pending projects")
            },
            onError = { e ->
                Log.e(TAG, "Error loading pending projects: ${e.message}")
                _pendingProjects.value = emptyList()
            }
        )
    }

    private suspend fun fetchPendingProjects(): List<Project> {
        val userId = requireUserId()
        val response = repository.getPendingProjects(userId)

        if (!response.isSuccessful) {
            throw Exception("Ошибка загрузки: ${response.code()}")
        }
        return response.body() ?: emptyList()
    }

    fun acceptShare(projectId: String) {
        runShareOperation(
            projectId = projectId,
            action = { userId -> repository.acceptShare(projectId) },
            successMessage = "Share accepted",
            afterSuccess = { userId ->
                if (syncManager.hasInternetConnection()) {
                    syncManager.syncDataFromServer(userId)
                }
            }
        )
    }

    fun declineShare(projectId: String) {
        runShareOperation(
            projectId = projectId,
            action = { userId -> repository.declineShare(projectId) },
            successMessage = "Share declined",
            afterSuccess = { }
        )
    }

    private fun runShareOperation(
        projectId: String,
        action: suspend (userId: String) -> Response<Unit>,
        successMessage: String,
        afterSuccess: suspend (userId: String) -> Unit
    ) {
        if (activeOperations.containsKey(projectId)) {
            Log.d(TAG, "Operation for $projectId already in progress, skipping")
            return
        }

        _processingIds.update { it + projectId }

        val job = viewModelScope.launch {
            try {
                val userId = requireUserId()
                val response = action(userId)

                if (!response.isSuccessful) {
                    throw Exception(shareErrorMessage(response.code()))
                }

                _pendingProjects.update { list -> list.filter { it.id != projectId } }

                afterSuccess(userId)
                Log.d(TAG, "$successMessage: $projectId")

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Operation failed for $projectId: ${e.message}")
                setError(e.message ?: "Неизвестная ошибка")
            } finally {
                _processingIds.update { it - projectId }
                activeOperations.remove(projectId)
            }
        }

        activeOperations[projectId] = job
    }

    fun refresh() {
        loadPendingProjects()
    }

    private fun requireUserId(): String =
        userPreferences.getUserId()
            ?: throw IllegalStateException("Пользователь не авторизован")

    private fun shareErrorMessage(code: Int): String = when (code) {
        404 -> "Приглашение не найдено"
        403 -> "Нет доступа к этому приглашению"
        else -> "Ошибка операции: $code"
    }
}