package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.repository.ObjectRepository
import com.example.myapplication.data.repository.ProjectRepository
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.FuzzySearch
import com.example.myapplication.utils.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ObjectFilterType {
    BY_NAME,
    BY_DESCRIPTION,
    BY_ADDRESS
}

@HiltViewModel
class ObjectsViewModel @Inject constructor(
    private val objectRepository: ObjectRepository,
    private val projectRepository: ProjectRepository,
    private val userPreferences: UserPreferences,
    private val syncManager: SyncManager,
    savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private companion object {
        const val TAG = "ObjectsViewModel"
        const val ROOT_OBJECT_NAME = "Без объекта"

        val ROOT_OBJECT_IDS = setOf("", "root", "null", "none")
    }

    private val _currentParentId = MutableStateFlow(
        savedStateHandle.get<String>("parentId")
    )
    val currentParentId: StateFlow<String?> = _currentParentId.asStateFlow()

    private val _showRootProjects = MutableStateFlow(true)
    private val _showCreateDialog = MutableStateFlow(false)
    private val _showCreateTypeDialog = MutableStateFlow(false)
    private val _showDeleteConfirmation = MutableStateFlow(false)
    private val _showInfoDialog = MutableStateFlow(false)
    private val _showEditDialog = MutableStateFlow(false)
    private val _showDeleteProjectConfirmation = MutableStateFlow(false)

    private val _objects = MutableStateFlow<List<ObjectModel>>(emptyList())
    private val _projectsInObject = MutableStateFlow<List<Project>>(emptyList())
    private val _rootLevelProjects = MutableStateFlow<List<Project>>(emptyList())

    private val _objectToDelete = MutableStateFlow<ObjectModel?>(null)
    private val _infoObject = MutableStateFlow<ObjectModel?>(null)
    private val _editingObject = MutableStateFlow<ObjectModel?>(null)
    private val _projectToDelete = MutableStateFlow<Project?>(null)
    private val _currentObjectName = MutableStateFlow<String?>(null)

    private val _searchQuery = MutableStateFlow("")
    private val _currentFilter = MutableStateFlow(ObjectFilterType.BY_NAME)

    val showRootProjects = _showRootProjects.asStateFlow()
    val showCreateDialog = _showCreateDialog.asStateFlow()
    val showCreateTypeDialog = _showCreateTypeDialog.asStateFlow()
    val showDeleteConfirmation = _showDeleteConfirmation.asStateFlow()
    val showInfoDialog = _showInfoDialog.asStateFlow()
    val showEditDialog = _showEditDialog.asStateFlow()
    val showDeleteProjectConfirmation = _showDeleteProjectConfirmation.asStateFlow()

    val objects = _objects.asStateFlow()
    val projectsInObject = _projectsInObject.asStateFlow()
    val rootLevelProjects = _rootLevelProjects.asStateFlow()

    val objectToDelete = _objectToDelete.asStateFlow()
    val infoObject = _infoObject.asStateFlow()
    val editingObject = _editingObject.asStateFlow()
    val projectToDelete = _projectToDelete.asStateFlow()

    val searchQuery = _searchQuery.asStateFlow()
    val currentFilter = _currentFilter.asStateFlow()

    val currentObjectName = _currentObjectName.asStateFlow()

    val filteredObjects: StateFlow<List<ObjectModel>> = combine(
        _objects,
        _searchQuery,
        _currentFilter
    ) { objects, query, filter ->
        filterObjects(objects, query, filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            setLoading(true)
            try {
                reloadLocalData()
            } finally {
                setLoading(false)
            }
        }

        observeCurrentObjectName()
        ensureRootObjectExists()
    }

    private fun filterObjects(
        objects: List<ObjectModel>,
        query: String,
        filter: ObjectFilterType
    ): List<ObjectModel> {
        val visible = objects.filter { it.name != ROOT_OBJECT_NAME }
        if (query.isBlank()) return visible

        return when (filter) {
            ObjectFilterType.BY_NAME -> FuzzySearch.filter(
                items = visible, query = query,
                textExtractor = { it.name }, maxDistance = 2
            )
            ObjectFilterType.BY_DESCRIPTION -> FuzzySearch.filter(
                items = visible, query = query,
                textExtractor = { it.description }, maxDistance = 2
            )
            ObjectFilterType.BY_ADDRESS -> FuzzySearch.filter(
                items = visible, query = query,
                textExtractor = { it.getFormattedAddress() }, maxDistance = 2
            )
        }
    }

    private fun ensureRootObjectExists() {
        viewModelScope.launch {
            runCatching {
                val userId = userPreferences.getUserId() ?: return@launch

                val rootObjects = objectRepository.getRootObjects().first()
                if (rootObjects.none { it.name == ROOT_OBJECT_NAME }) {
                    val rootObject = ObjectModel(
                        name = ROOT_OBJECT_NAME,
                        description = "Корневые сметы (автоматически создан)",
                        userId = userId
                    )
                    objectRepository.insertObject(rootObject)
                    Log.d(TAG, "Created root object: ${rootObject.id}")
                }
            }.onFailure { Log.e(TAG, "ensureRootObjectExists: ${it.message}") }
        }
    }

    private suspend fun reloadLocalData() {
        val parentId = _currentParentId.value

        runCatching {
            _objects.value = if (parentId != null) {
                objectRepository.getChildObjectsOnce(parentId)
            } else {
                objectRepository.getRootObjectsOnce()
                    .filter { it.name != ROOT_OBJECT_NAME }
            }

            if (parentId != null) {
                _projectsInObject.value =
                    projectRepository.getProjectsByObjectIdOnce(parentId)
                _rootLevelProjects.value = emptyList()
            } else {
                _projectsInObject.value = emptyList()
                val userId = userPreferences.getUserId()
                _rootLevelProjects.value = if (userId != null) {
                    projectRepository.getProjectsForUserOnce(userId)
                        .filter { isRootLevelProject(it) }
                } else {
                    emptyList()
                }
            }
        }.onFailure {
            Log.e(TAG, "reloadLocalData: ${it.message}")
            _objects.value = emptyList()
            _projectsInObject.value = emptyList()
            _rootLevelProjects.value = emptyList()
            setError("Ошибка загрузки данных")
        }
    }

    private fun observeCurrentObjectName() {
        viewModelScope.launch {
            _currentParentId.collect { parentId ->
                _currentObjectName.value = parentId?.let {
                    objectRepository.getObjectById(it)?.name
                }
            }
        }
    }

    fun loadDataFromLocalOnly() {
        viewModelScope.launch {
            setLoading(true)
            try {
                reloadLocalData()
            } finally {
                setLoading(false)
            }
        }
    }

    fun refreshAllData() {
        viewModelScope.launch {
            setRefreshing(true)
            try {
                val userId = userPreferences.getUserId()
                if (userId != null && syncManager.hasInternetConnection()) {
                    syncManager.syncDataFromServer(userId, skipQueueCheck = false)
                }
                reloadLocalData()
            } catch (e: Exception) {
                Log.e(TAG, "refreshAllData: ${e.message}")
                setError("Ошибка обновления: ${e.message}")
            } finally {
                setRefreshing(false)
            }
        }
    }

    private suspend fun refreshProjectsInternal() {
        val parentId = _currentParentId.value
        runCatching {
            if (parentId != null) {
                _projectsInObject.value =
                    projectRepository.getProjectsByObjectIdOnce(parentId)
            } else {
                val userId = userPreferences.getUserId()
                _rootLevelProjects.value = if (userId != null) {
                    projectRepository.getProjectsForUserOnce(userId)
                        .filter { isRootLevelProject(it) }
                } else {
                    emptyList()
                }
            }
        }.onFailure {
            Log.e(TAG, "refreshProjectsInternal: ${it.message}")
        }
    }

    fun updateParentId(newParentId: String?) {
        if (_currentParentId.value == newParentId) return
        Log.d(TAG, "updateParentId: ${_currentParentId.value} -> $newParentId")
        _currentParentId.value = newParentId
        loadDataFromLocalOnly()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSearchFilter(filter: ObjectFilterType) {
        _currentFilter.value = filter
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _currentFilter.value = ObjectFilterType.BY_NAME
    }

    fun toggleRootProjectsSection() {
        _showRootProjects.update { !it }
    }

    fun showCreateDialog() { _showCreateDialog.value = true }
    fun hideCreateDialog() { _showCreateDialog.value = false }

    fun showCreateTypeDialog() { _showCreateTypeDialog.value = true }
    fun hideCreateTypeDialog() { _showCreateTypeDialog.value = false }

    fun showDeleteConfirmation(obj: ObjectModel) {
        _objectToDelete.value = obj
        _showDeleteConfirmation.value = true
    }

    fun hideDeleteConfirmation() {
        _showDeleteConfirmation.value = false
        _objectToDelete.value = null
    }

    fun showInfoDialog(obj: ObjectModel) {
        _infoObject.value = obj
        _showInfoDialog.value = true
    }

    fun hideInfoDialog() {
        _showInfoDialog.value = false
        _infoObject.value = null
    }

    fun showDeleteProjectConfirmation(project: Project) {
        _projectToDelete.value = project
        _showDeleteProjectConfirmation.value = true
    }

    fun hideDeleteProjectConfirmation() {
        _showDeleteProjectConfirmation.value = false
        _projectToDelete.value = null
    }

    fun startEditing(obj: ObjectModel) {
        _editingObject.value = obj
        _showEditDialog.value = true
    }

    fun hideEditDialog() {
        _showEditDialog.value = false
        _editingObject.value = null
    }

    fun clearErrorMessage() {
        setError(null)
    }

    fun createObject(
        name: String,
        street: String,
        house: String,
        building: String,
        description: String
    ) {
        viewModelScope.launch {
            val userId = userPreferences.getUserId()
            if (userId == null) {
                setError("Пользователь не авторизован")
                _showCreateDialog.value = false
                return@launch
            }

            val obj = ObjectModel(
                name = name,
                street = street,
                house = house,
                building = building,
                description = description,
                parentObjectId = _currentParentId.value,
                userId = userId
            )

            try {
                objectRepository.insertObject(obj)
                syncManager.syncEntityToServer(obj)

                _showCreateDialog.value = false
            } catch (e: Exception) {
                Log.e(TAG, "createObject: ${e.message}")
                setError("Ошибка создания: ${e.message}")
                _showCreateDialog.value = false
            }
        }
    }

    fun updateObject(obj: ObjectModel) {
        viewModelScope.launch {
            try {
                val objToSave = if (obj.userId.isNullOrEmpty()) {
                    obj.copy(userId = userPreferences.getUserId().orEmpty())
                } else obj

                objectRepository.updateObject(objToSave)
                syncManager.syncEntityToServer(objToSave)

                hideEditDialog()
                reloadLocalData()
            } catch (e: Exception) {
                Log.e(TAG, "updateObject: ${e.message}")
                setError("Ошибка обновления: ${e.message}")
            }
        }
    }

    fun deleteObject(obj: ObjectModel) {
        viewModelScope.launch {
            setLoading(true)
            try {
                objectRepository.deleteObjectWithCascade(obj)

                syncManager.queueOperation("DELETE", "OBJECT", obj.id, obj)

                reloadLocalData()
            } catch (e: Exception) {
                Log.e(TAG, "deleteObject: ${e.message}")
                setError("Ошибка при удалении: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    fun confirmDeleteProject() {
        val project = _projectToDelete.value ?: run {
            hideDeleteProjectConfirmation()
            return
        }

        hideDeleteProjectConfirmation()

        viewModelScope.launch {
            try {
                objectRepository.deleteProjectWithAllData(project)

                syncManager.syncProjectDeletion(project.id)

                refreshProjectsInternal()
            } catch (e: Exception) {
                Log.e(TAG, "confirmDeleteProject: ${e.message}")
                setError("Ошибка удаления: ${e.message}")
            }
        }
    }

    fun moveProject(projectId: String, newObjectId: String) {
        viewModelScope.launch {
            try {
                val project = projectRepository.getProjectById(projectId)
                if (project == null) {
                    Log.e(TAG, "Project not found: $projectId")
                    setError("Смета не найдена")
                    return@launch
                }

                val updatedProject = project.copy(
                    objectId = normalizeObjectId(newObjectId)
                )
                projectRepository.updateProject(updatedProject)
                syncManager.syncEntityToServer(updatedProject)

                refreshProjectsInternal()
            } catch (e: Exception) {
                Log.e(TAG, "moveProject: ${e.message}")
                setError("Ошибка перемещения: ${e.message}")
            }
        }
    }

    private fun normalizeObjectId(objectId: String?): String =
        if (objectId in ROOT_OBJECT_IDS) "" else objectId.orEmpty()

    private fun isRootLevelProject(project: Project): Boolean =
        project.objectId in ROOT_OBJECT_IDS || project.objectId.isNullOrEmpty()
}