package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.data.repository.ProjectRepository
import com.example.myapplication.data.repository.ContactRepository
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.FuzzySearch
import com.example.myapplication.utils.PhoneUtils
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
import java.util.Date
import javax.inject.Inject

enum class ProjectFilterType {
    BY_NAME,
    BY_DESCRIPTION,
    BY_CUSTOMER,
    BY_FOREMAN,
    BY_MANAGER
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val contactRepository: ContactRepository,
    val syncManager: SyncManager,
    private val userPreferences: UserPreferences
) : BaseViewModel() {

    private companion object {
        const val TAG = "ProjectsViewModel"
    }

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentFilter = MutableStateFlow(ProjectFilterType.BY_NAME)
    val currentFilter: StateFlow<ProjectFilterType> = _currentFilter.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _duplicatesCache = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    private val _duplicatesVersion = MutableStateFlow(0)
    val duplicatesVersion: StateFlow<Int> = _duplicatesVersion.asStateFlow()

    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation.asStateFlow()

    private val _projectToDelete = MutableStateFlow<Project?>(null)
    val projectToDelete: StateFlow<Project?> = _projectToDelete.asStateFlow()

    val filteredProjects: StateFlow<List<Project>> = combine(
        _projects,
        _searchQuery,
        _currentFilter,
        _contacts
    ) { projects, query, filter, contacts ->
        filterProjects(projects, query, filter, contacts)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadProjects()
        loadContacts()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            val userId = userPreferences.getUserId()
            if (userId == null) {
                Log.e(TAG, "User not logged in")
                return@launch
            }

            runCatching {
                repository.getProjectsForUser(userId).collect { projects ->
                    _projects.value = projects.filter {
                        it.userId == userId || it.userId.isNullOrEmpty()
                    }
                }
            }.onFailure {
                Log.e(TAG, "Error loading projects: ${it.message}")
                setError("Ошибка загрузки проектов")
            }
        }
    }

    private fun loadContacts() {
        viewModelScope.launch {
            contactRepository.getAllContacts().collect { list ->
                _contacts.value = list
                updateDuplicatesCache()
            }
        }
    }

    fun hasDuplicates(contactId: String): Boolean =
        _duplicatesCache.value.containsKey(contactId)

    private fun updateDuplicatesCache() {
        viewModelScope.launch {
            runCatching {
                val allMethods = contactRepository.getAllContactMethods().first()
                val byContact = allMethods.groupBy { it.contactId }
                val valueToContacts = buildValueToContacts(_contacts.value, byContact)
                _duplicatesCache.value = buildDuplicates(valueToContacts)
                _duplicatesVersion.update { it + 1 }
            }.onFailure {
                Log.e(TAG, "Error computing duplicates: ${it.message}")
            }
        }
    }

    private fun buildValueToContacts(
        contacts: List<Contact>,
        methodsByContact: Map<String, List<ContactMethod>>
    ): Map<String, MutableList<String>> {
        val result = mutableMapOf<String, MutableList<String>>()

        contacts.forEach { contact ->
            methodsByContact[contact.id]?.forEach { method ->
                val normalized = normalizeMethodValue(method.methodType, method.value)
                if (normalized.isNotBlank()) {
                    result.getOrPut(normalized) { mutableListOf() }.add(contact.id)
                }
            }
        }
        return result
    }

    private fun buildDuplicates(
        valueToContacts: Map<String, List<String>>
    ): Map<String, List<String>> {
        val duplicates = mutableMapOf<String, List<String>>()

        valueToContacts.filter { it.value.size > 1 }.forEach { (_, contactIds) ->
            contactIds.forEach { id ->
                val others = contactIds.filter { it != id }
                if (others.isNotEmpty()) duplicates[id] = others
            }
        }
        return duplicates
    }

    private fun normalizeMethodValue(methodType: String, value: String): String {
        val type = methodType.lowercase()
        return if (type.contains("телефон") || type.contains("phone")) {
            PhoneUtils.normalize(value)
        } else {
            value.lowercase()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSearchFilter(filter: ProjectFilterType) {
        _currentFilter.value = filter
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _currentFilter.value = ProjectFilterType.BY_NAME
    }

    fun createProjectWithMaterialsAndWorks(
        name: String,
        description: String,
        objectId: String?,
        materials: List<Material>,
        workItems: List<WorkItem>,
        customerContactId: String?,
        foremanContactId: String?,
        managerContactId: String?,
        includeForeman: Boolean,
        includeManager: Boolean
    ) {
        safeLaunch(
            block = {
                val userId = userPreferences.getUserId()
                    ?: throw IllegalStateException("Пользователь не авторизован")

                val project = Project(
                    name = name,
                    description = description,
                    objectId = normalizeObjectId(objectId),
                    customerContactId = customerContactId,
                    foremanContactId = foremanContactId,
                    managerContactId = managerContactId,
                    includeForeman = includeForeman,
                    includeManager = includeManager,
                    userId = userId
                )

                val createdProject = repository.createProject(project)

                materials.forEach { material ->
                    repository.addMaterial(material.copy(projectId = createdProject.id))
                }
                workItems.forEach { work ->
                    repository.addWorkItem(work.copy(projectId = createdProject.id))
                }

                syncManager.syncProjectToServer(createdProject)
                createdProject
            },
            onSuccess = { createdProject ->
                Log.d(TAG, "Project created: ${createdProject.name}")
            },
            onError = { e -> setError("Ошибка создания: ${e.message}") }
        )
    }

    fun showDeleteProjectConfirmation(project: Project) {
        _projectToDelete.value = project
        _showDeleteConfirmation.value = true
    }

    fun hideDeleteProjectConfirmation() {
        _showDeleteConfirmation.value = false
        _projectToDelete.value = null
    }

    fun confirmDeleteProject() {
        _projectToDelete.value?.let { deleteProject(it) }
        hideDeleteProjectConfirmation()
    }

    private fun deleteProject(project: Project) {
        safeLaunch(
            block = {
                repository.deleteProject(project)

                if (syncManager.hasInternetConnection()) {
                    syncManager.syncProjectDeletion(project.id)
                }
            },
            onSuccess = { Log.d(TAG, "Project deleted: ${project.name}") },
            onError = { e -> setError("Ошибка удаления: ${e.message}") }
        )
    }

    suspend fun getProjectByIdAsync(projectId: String): Project? {
        return runCatching { repository.getProjectById(projectId) }
            .onFailure { Log.e(TAG, "Error getting project: ${it.message}") }
            .getOrNull()
    }

    suspend fun getMaterialsForProject(projectId: String): List<Material> {
        return runCatching { repository.getMaterials(projectId).first() }
            .onFailure { Log.e(TAG, "Error getting materials: ${it.message}") }
            .getOrDefault(emptyList())
    }

    suspend fun getWorkItemsForProject(projectId: String): List<WorkItem> {
        return runCatching { repository.getWorkItems(projectId).first() }
            .onFailure { Log.e(TAG, "Error getting work items: ${it.message}") }
            .getOrDefault(emptyList())
    }

    fun updateProject(
        projectId: String,
        name: String,
        description: String,
        materials: List<Material>,
        workItems: List<WorkItem>,
        customerContactId: String?,
        foremanContactId: String?,
        managerContactId: String?,
        includeForeman: Boolean,
        includeManager: Boolean
    ) {
        safeLaunch(
            block = {
                val oldProject = getProjectByIdAsync(projectId)
                    ?: throw IllegalStateException("Проект не найден")

                val updatedProject = oldProject.copy(
                    name = name,
                    description = description,
                    customerContactId = customerContactId,
                    foremanContactId = foremanContactId,
                    managerContactId = managerContactId,
                    includeForeman = includeForeman,
                    includeManager = includeManager,
                    updatedAt = Date()
                )

                repository.updateProject(updatedProject)
                replaceMaterials(projectId, materials)
                replaceWorkItems(projectId, workItems)

                val totalBudget = calculateBudget(materials, workItems)
                val finalProject = updatedProject.copy(totalBudget = totalBudget)
                repository.updateProject(finalProject)

                syncManager.syncProjectToServer(finalProject)
                finalProject
            },
            onSuccess = { Log.d(TAG, "Project updated: $projectId") },
            onError = { e -> setError("Ошибка обновления: ${e.message}") }
        )
    }

    private fun normalizeObjectId(objectId: String?): String = when (objectId) {
        null, "null", "none", "root", "" -> ""
        else -> objectId
    }

    private suspend fun replaceMaterials(projectId: String, newMaterials: List<Material>) {
        repository.getMaterialsOnce(projectId).forEach { repository.deleteMaterial(it) }
        newMaterials.forEach { repository.addMaterial(it.copy(projectId = projectId)) }
    }

    private suspend fun replaceWorkItems(projectId: String, newWorkItems: List<WorkItem>) {
        repository.getWorkItemsOnce(projectId).forEach { repository.deleteWorkItem(it) }
        newWorkItems.forEach { repository.addWorkItem(it.copy(projectId = projectId)) }
    }

    private fun calculateBudget(
        materials: List<Material>,
        workItems: List<WorkItem>
    ): Double {
        val materialCost = materials.sumOf { it.quantity * it.unitPrice }
        val workCost = workItems.sumOf { it.laborHours * it.hourlyRate + it.materialCost }
        return materialCost + workCost
    }

    private fun filterProjects(
        projects: List<Project>,
        query: String,
        filter: ProjectFilterType,
        contacts: List<Contact>
    ): List<Project> {
        if (query.isBlank()) return projects

        return when (filter) {
            ProjectFilterType.BY_NAME -> FuzzySearch.filter(
                items = projects,
                query = query,
                textExtractor = { it.name },
                maxDistance = 2
            )
            ProjectFilterType.BY_DESCRIPTION -> FuzzySearch.filter(
                items = projects,
                query = query,
                textExtractor = { it.description },
                maxDistance = 2
            )
            ProjectFilterType.BY_CUSTOMER ->
                filterByContact(projects, query, contacts) { it.customerContactId }
            ProjectFilterType.BY_FOREMAN ->
                filterByContact(projects, query, contacts) { it.foremanContactId }
            ProjectFilterType.BY_MANAGER ->
                filterByContact(projects, query, contacts) { it.managerContactId }
        }
    }

    private fun filterByContact(
        projects: List<Project>,
        query: String,
        contacts: List<Contact>,
        contactIdExtractor: (Project) -> String?
    ): List<Project> {
        return projects.filter { project ->
            contactIdExtractor(project)?.let { contactId ->
                contacts.find { it.id == contactId }?.let { contact ->
                    FuzzySearch.matches(contact.name, query, maxDistance = 2) ||
                            FuzzySearch.matches(contact.description, query, maxDistance = 2)
                } ?: false
            } ?: false
        }
    }

    fun loadProjectsFromServer() {
        viewModelScope.launch {
            val userId = userPreferences.getUserId() ?: return@launch
            if (syncManager.hasInternetConnection()) {
                syncManager.syncDataFromServer(userId)
            }
        }
    }
}