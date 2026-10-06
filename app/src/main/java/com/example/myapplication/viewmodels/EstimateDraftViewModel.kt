package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.*
import com.example.myapplication.data.repository.ChangeRequestRepository
import com.example.myapplication.data.repository.DraftData
import com.example.myapplication.data.repository.EstimateDraftRepository
import com.example.myapplication.data.repository.ProjectRepository
import com.example.myapplication.services.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EstimateDraftViewModel @Inject constructor(
    private val draftRepo: EstimateDraftRepository,
    private val projectRepo: ProjectRepository,
    private val changeRepo: ChangeRequestRepository,
    private val syncManager: SyncManager,
    savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private val TAG = "EstimateDraftVM"
    private val projectId: String = savedStateHandle["projectId"] ?: ""

    private val _draft = MutableStateFlow<DraftData?>(null)
    val draft: StateFlow<DraftData?> = _draft.asStateFlow()

    private val _isReadOnly = MutableStateFlow(false)
    val isReadOnly: StateFlow<Boolean> = _isReadOnly.asStateFlow()

    init {
        if (projectId.isNotEmpty()) loadDraft()
    }

    private fun loadDraft() {
        viewModelScope.launch {
            setLoading(true)
            try {
                if (syncManager.hasInternetConnection()) {
                    val response = changeRepo.getPendingEstimate(projectId)
                    if (response.isSuccessful && response.body() != null) {
                        _isReadOnly.value = true
                        setError("Черновик уже на согласовании у заказчика")
                        return@launch
                    }
                }

                val existing = draftRepo.getDraft(projectId)

                _draft.value = existing ?: buildDraftFromProject()

                _draft.value?.let { draftRepo.saveDraft(it) }
            } catch (e: Exception) {
                Log.e(TAG, "loadDraft: ${e.message}", e)
                setError("Ошибка загрузки: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private suspend fun buildDraftFromProject(): DraftData? {
        val project = projectRepo.getProjectById(projectId) ?: return null
        val materials = projectRepo.getMaterialsOnce(projectId)
        val workItems = projectRepo.getWorkItemsOnce(projectId)

        return DraftData(
            projectId = projectId,
            projectName = project.name,
            projectDescription = project.description,
            materials = materials,
            workItems = workItems
        )
    }

    fun updateName(name: String) = updateDraft { it.copy(projectName = name) }
    fun updateDescription(desc: String) = updateDraft { it.copy(projectDescription = desc) }
    fun updateComment(comment: String) = updateDraft { it.copy(comment = comment) }

    fun addMaterial(material: Material) = updateDraft {
        it.copy(materials = it.materials + material.copy(projectId = projectId))
    }

    fun updateMaterial(material: Material) = updateDraft {
        it.copy(materials = it.materials.map { m -> if (m.id == material.id) material else m })
    }

    fun deleteMaterial(materialId: String) = updateDraft {
        it.copy(materials = it.materials.filter { m -> m.id != materialId })
    }

    fun addWorkItem(workItem: WorkItem) = updateDraft {
        it.copy(workItems = it.workItems + workItem.copy(projectId = projectId))
    }

    fun updateWorkItem(workItem: WorkItem) = updateDraft {
        it.copy(workItems = it.workItems.map { w -> if (w.id == workItem.id) workItem else w })
    }

    fun deleteWorkItem(workItemId: String) = updateDraft {
        it.copy(workItems = it.workItems.filter { w -> w.id != workItemId })
    }

    private fun updateDraft(transform: (DraftData) -> DraftData) {
        val current = _draft.value ?: return
        val updated = transform(current)
        _draft.value = updated
        viewModelScope.launch { draftRepo.saveDraft(updated) }
    }

    fun submitForApproval() {
        val current = _draft.value ?: return
        if (current.projectName.isBlank()) {
            setError("Введите название сметы")
            return
        }

        safeLaunch(
            block = {
                val snapshot = current.toSnapshot()
                val response = changeRepo.submitEstimate(projectId, snapshot)

                if (!response.isSuccessful) {
                    throw Exception("Ошибка отправки: ${response.code()}")
                }

                draftRepo.deleteDraft(projectId)
                response.body()
            },
            onSuccess = {
                setError(null)
                Log.d(TAG, "Draft submitted: ${it?.id}")
                _isReadOnly.value = true
            },
            onError = { e ->
                Log.e(TAG, "submitForApproval: ${e.message}", e)
                setError(e.message ?: "Ошибка отправки")
            }
        )
    }

    fun discardDraft() {
        viewModelScope.launch {
            draftRepo.deleteDraft(projectId)
            _draft.value = null
        }
    }

    private fun DraftData.toSnapshot() = ProjectSnapshotDto(
        name = projectName,
        description = projectDescription,
        materials = materials.map {
            MaterialSnapshotDto(
                id = it.id,
                name = it.name,
                quantity = it.quantity,
                unit = it.unit,
                unitPrice = it.unitPrice,
                category = it.category,
                notes = it.notes
            )
        },
        workItems = workItems.map {
            WorkItemSnapshotDto(
                id = it.id,
                name = it.name,
                stage = it.stage,
                laborHours = it.laborHours,
                hourlyRate = it.hourlyRate,
                materialCost = it.materialCost,
                isCompleted = it.isCompleted,
                notes = it.notes
            )
        },
        totalBudget = materials.sumOf { it.quantity * it.unitPrice } +
                workItems.sumOf { it.laborHours * it.hourlyRate + it.materialCost },
        comment = comment
    )
}