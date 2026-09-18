package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.data.repository.ProjectRepository
import com.example.myapplication.utils.FuzzySearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val repo: ProjectRepository,
    savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private val TAG = "ProjectDetailViewModel"
    private val projectId: String = savedStateHandle["projectId"] ?: ""

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _materials = MutableStateFlow<List<Material>>(emptyList())
    val materials: StateFlow<List<Material>> = _materials.asStateFlow()

    private val _workItems = MutableStateFlow<List<WorkItem>>(emptyList())
    val workItems: StateFlow<List<WorkItem>> = _workItems.asStateFlow()

    private val _materialSearchQuery = MutableStateFlow("")
    val materialSearchQuery: StateFlow<String> = _materialSearchQuery.asStateFlow()

    private val _workSearchQuery = MutableStateFlow("")
    val workSearchQuery: StateFlow<String> = _workSearchQuery.asStateFlow()

    private val _showMaterialDialog = MutableStateFlow(false)
    val showMaterialDialog: StateFlow<Boolean> = _showMaterialDialog.asStateFlow()

    private val _showWorkDialog = MutableStateFlow(false)
    val showWorkDialog: StateFlow<Boolean> = _showWorkDialog.asStateFlow()

    private val _editingMaterial = MutableStateFlow<Material?>(null)
    val editingMaterial: StateFlow<Material?> = _editingMaterial.asStateFlow()

    private val _editingWorkItem = MutableStateFlow<WorkItem?>(null)
    val editingWorkItem: StateFlow<WorkItem?> = _editingWorkItem.asStateFlow()

    val filteredMaterials: StateFlow<List<Material>> = combine(
        _materials,
        _materialSearchQuery
    ) { materials, query ->
        filterByName(materials, query) { it.name }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredWorkItems: StateFlow<List<WorkItem>> = combine(
        _workItems,
        _workSearchQuery
    ) { workItems, query ->
        filterByName(workItems, query) { it.name }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun <T> filterByName(
        items: List<T>,
        query: String,
        nameExtractor: (T) -> String
    ): List<T> {
        if (query.isBlank()) return items
        return FuzzySearch.filter(
            items = items,
            query = query,
            textExtractor = nameExtractor,
            maxDistance = 2
        )
    }

    init {
        if (projectId.isNotEmpty()) {
            loadData()
        } else {
            Log.e(TAG, "projectId is empty!")
        }
    }

    private fun loadData() {
        setLoading(true)

        viewModelScope.launch {
            try {
                _project.value = repo.getProjectById(projectId)
                Log.d(TAG, "Project loaded: ${_project.value?.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading project: ${e.message}")
                setError("Ошибка загрузки проекта")
            }
        }

        viewModelScope.launch {
            try {
                combine(
                    repo.getMaterials(projectId),
                    repo.getWorkItems(projectId)
                ) { materials, workItems ->
                    materials to workItems
                }.collect { (materials, workItems) ->
                    _materials.value = materials
                    _workItems.value = workItems
                    setLoading(false)
                    Log.d(TAG, "Materials: ${materials.size}, WorkItems: ${workItems.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading data: ${e.message}")
                setError("Ошибка загрузки данных")
                setLoading(false)
            }
        }
    }

    fun updateMaterialSearchQuery(query: String) {
        _materialSearchQuery.value = query
    }

    fun updateWorkSearchQuery(query: String) {
        _workSearchQuery.value = query
    }

    fun showMaterialDialog() { _showMaterialDialog.value = true }
    fun hideMaterialDialog() { _showMaterialDialog.value = false }

    fun showWorkDialog() { _showWorkDialog.value = true }
    fun hideWorkDialog() { _showWorkDialog.value = false }

    fun startEditMaterial(material: Material) {
        _editingMaterial.value = material
    }

    fun clearEditMaterial() {
        _editingMaterial.value = null
    }

    fun startEditWorkItem(workItem: WorkItem) {
        _editingWorkItem.value = workItem
    }

    fun clearEditWorkItem() {
        _editingWorkItem.value = null
    }

    fun addMaterial(name: String, quantity: Double, unit: String, price: Double) {
        safeLaunch(
            block = {
                val material = Material(
                    projectId = projectId,
                    name = name,
                    quantity = quantity,
                    unit = unit.ifBlank { "шт" },
                    unitPrice = price
                )
                repo.addMaterial(material)
            },
            onSuccess = {
                _showMaterialDialog.value = false
                Log.d(TAG, "Material added: $name")
            },
            onError = { e ->
                setError("Ошибка добавления: ${e.message}")
                Log.e(TAG, "Error adding material: ${e.message}")
            }
        )
    }

    fun updateMaterial(material: Material) {
        safeLaunch(
            block = { repo.updateMaterial(material) },
            onSuccess = {
                _editingMaterial.value = null
                Log.d(TAG, "Material updated: ${material.name}")
            },
            onError = { e ->
                setError("Ошибка обновления: ${e.message}")
                Log.e(TAG, "Error updating material: ${e.message}")
            }
        )
    }

    fun deleteMaterial(material: Material) {
        safeLaunch(
            block = { repo.deleteMaterial(material) },
            onSuccess = {
                Log.d(TAG, "Material deleted: ${material.name}")
            },
            onError = { e ->
                setError("Ошибка удаления: ${e.message}")
                Log.e(TAG, "Error deleting material: ${e.message}")
            }
        )
    }

    fun addWorkItem(name: String, hours: Double, rate: Double, materialCost: Double) {
        safeLaunch(
            block = {
                val workItem = WorkItem(
                    projectId = projectId,
                    name = name,
                    laborHours = hours,
                    hourlyRate = rate,
                    materialCost = materialCost
                )
                repo.addWorkItem(workItem)
            },
            onSuccess = {
                _showWorkDialog.value = false
                Log.d(TAG, "Work item added: $name")
            },
            onError = { e ->
                setError("Ошибка добавления: ${e.message}")
                Log.e(TAG, "Error adding work item: ${e.message}")
            }
        )
    }

    fun updateWorkItem(workItem: WorkItem) {
        safeLaunch(
            block = { repo.updateWorkItem(workItem) },
            onSuccess = {
                _editingWorkItem.value = null
                Log.d(TAG, "Work item updated: ${workItem.name}")
            },
            onError = { e ->
                setError("Ошибка обновления: ${e.message}")
                Log.e(TAG, "Error updating work item: ${e.message}")
            }
        )
    }

    fun deleteWorkItem(workItem: WorkItem) {
        safeLaunch(
            block = { repo.deleteWorkItem(workItem) },
            onSuccess = {
                Log.d(TAG, "Work item deleted: ${workItem.name}")
            },
            onError = { e ->
                setError("Ошибка удаления: ${e.message}")
                Log.e(TAG, "Error deleting work item: ${e.message}")
            }
        )
    }
}