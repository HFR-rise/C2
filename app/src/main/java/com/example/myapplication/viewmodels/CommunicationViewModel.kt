package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.database.ChangeRequestDao
import com.example.myapplication.data.database.ChangeRequestEntity
import com.example.myapplication.data.models.ChangeRequestDto
import com.example.myapplication.data.models.ProjectSnapshotDto
import com.example.myapplication.data.repository.ChangeRequestRepository
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.UserPreferences
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommunicationViewModel @Inject constructor(
    private val changeRepo: ChangeRequestRepository,
    private val changeRequestDao: ChangeRequestDao,
    private val syncManager: SyncManager,
    private val userPreferences: UserPreferences,
    private val gson: Gson
) : BaseViewModel() {

    private companion object {
        const val TAG = "CommunicationVM"
    }

    val items: StateFlow<List<ChangeRequestEntity>> =
        changeRequestDao.observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    private val _processingIds = MutableStateFlow<Set<String>>(emptySet())
    val processingIds: StateFlow<Set<String>> = _processingIds.asStateFlow()

    private val _currentUserId = MutableStateFlow(userPreferences.getUserId())
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val activeOps = mutableMapOf<String, Job>()

    init {
        loadInbox()
    }

    fun loadInbox() {
        safeLaunch(
            showLoading = false,
            block = { changeRepo.getInbox() },
            onSuccess = { response ->
                if (!response.isSuccessful) {
                    setError("Ошибка загрузки: ${response.code()}")
                    return@safeLaunch
                }

                val dtos: List<ChangeRequestDto>? = response.body()
                val entities = dtos.orEmpty().map { it.toEntity() }

                viewModelScope.launch {
                    changeRequestDao.upsertAll(entities)
                    Log.d(TAG, "Loaded ${entities.size} items into Room")
                }
            },
            onError = { e -> setError("Ошибка сети: ${e.message}") }
        )
    }

    fun approve(changeId: String, comment: String? = null) {
        runOperation(changeId, "approve") {
            changeRepo.approve(changeId, comment)
        }
    }

    fun reject(changeId: String, comment: String) {
        if (comment.isBlank()) {
            setError("Введите причину отклонения")
            return
        }
        runOperation(changeId, "reject") {
            changeRepo.reject(changeId, comment)
        }
    }

    fun deleteDraft(changeId: String) {
        runOperation(changeId, "delete") {
            changeRepo.deleteDraft(changeId)
        }
    }

    fun parseSnapshot(entity: ChangeRequestEntity): ProjectSnapshotDto? {
        val json = entity.payloadJson ?: return null
        return runCatching {
            gson.fromJson(json, ProjectSnapshotDto::class.java)
        }.getOrNull()
    }

    private fun runOperation(
        changeId: String,
        name: String,
        block: suspend () -> retrofit2.Response<*>
    ) {
        if (activeOps.containsKey(changeId)) return

        _processingIds.update { it + changeId }

        val job = viewModelScope.launch {
            try {
                val before = changeRequestDao.getById(changeId)
                val projectId = before?.projectId

                val response = block()
                if (!response.isSuccessful) {
                    throw Exception("$name failed: ${response.code()}")
                }

                when (name) {
                    "delete" -> {
                        changeRequestDao.deleteById(changeId)
                        Log.d(TAG, "Draft deleted locally: $changeId")
                    }
                    else -> {
                        if (projectId != null) {
                            runCatching { syncManager.reloadProject(projectId) }
                                .onFailure {
                                    Log.e(TAG, "reloadProject($projectId) failed: ${it.message}", it)
                                }
                        }
                        loadInbox()
                    }
                }

                Log.d(TAG, "$name OK: $changeId")
            } catch (e: Exception) {
                Log.e(TAG, "$name error: ${e.message}", e)
                setError(e.message ?: "Ошибка")
            } finally {
                _processingIds.update { it - changeId }
                activeOps.remove(changeId)
            }
        }
        activeOps[changeId] = job
    }

    fun refresh() = loadInbox()
}

private fun ChangeRequestDto.toEntity(): ChangeRequestEntity = ChangeRequestEntity(
    id = id,
    projectId = projectId,
    projectName = projectName,
    authorId = authorId,
    authorName = authorName,
    authorPhone = authorPhone,
    kind = kind,
    status = status,
    payloadJson = payloadJson,
    comment = comment,
    reviewComment = reviewComment,
    reviewerId = reviewerId,
    reviewerName = reviewerName,
    reviewerPhone = reviewerPhone,
    createdAt = parseDate(createdAt),
    reviewedAt = reviewedAt?.let { parseDate(it) },
    version = version
)

private fun parseDate(value: String?): Long {
    if (value.isNullOrBlank()) return System.currentTimeMillis()
    return runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .parse(value)?.time
    }.getOrNull() ?: System.currentTimeMillis()
}