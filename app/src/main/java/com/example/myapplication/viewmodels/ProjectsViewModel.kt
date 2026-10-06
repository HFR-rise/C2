package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.ProjectMemberDto
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.data.repository.ContactRepository
import com.example.myapplication.data.repository.ProjectMemberRepository
import com.example.myapplication.data.repository.ProjectRepository
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.ContactPhoneExtractor
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

enum class ProjectFilterType {
    BY_NAME,
    BY_DESCRIPTION,
    BY_PARTICIPANT
}

sealed interface ContactCheckResult {
    data object Exists : ContactCheckResult
    data object NotRegistered : ContactCheckResult
    data class Error(val message: String) : ContactCheckResult
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val contactRepository: ContactRepository,
    private val memberRepository: ProjectMemberRepository,
    val syncManager: SyncManager,
    private val userPreferences: UserPreferences
) : BaseViewModel() {

    private companion object {
        const val TAG = "ProjectsViewModel"
        const val PARTICIPANT_WAIT_TIMEOUT_MS = 3_000L
    }

    private val userId: String? = userPreferences.getUserId()

    val projects: StateFlow<List<Project>> = if (userId != null) {
        repository.getProjectsForUser(userId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
    } else {
        MutableStateFlow<List<Project>>(emptyList()).asStateFlow()
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentFilter = MutableStateFlow(ProjectFilterType.BY_NAME)
    val currentFilter: StateFlow<ProjectFilterType> = _currentFilter.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _participantNamesByProject =
        MutableStateFlow<Map<String, List<String>>>(emptyMap())

    private val _duplicatesCache = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    private val _duplicatesVersion = MutableStateFlow(0)
    val duplicatesVersion: StateFlow<Int> = _duplicatesVersion.asStateFlow()

    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation.asStateFlow()

    private val _projectToDelete = MutableStateFlow<Project?>(null)
    val projectToDelete: StateFlow<Project?> = _projectToDelete.asStateFlow()

    val filteredProjects: StateFlow<List<Project>> = combine(
        projects,
        _searchQuery,
        _currentFilter,
        _participantNamesByProject
    ) { projectsList, query, filter, participantNames ->
        filterProjects(projectsList, query, filter, participantNames)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        loadContacts()
        refreshParticipantNames()
    }

    private fun loadContacts() {
        viewModelScope.launch {
            try {
                contactRepository.getAllContacts().collect { list ->
                    _contacts.value = list
                    updateDuplicatesCache()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadContacts: ${e.message}", e)
            }
        }
    }

    fun refreshParticipantNames() {
        viewModelScope.launch {
            userId ?: return@launch
            if (!syncManager.hasInternetConnection()) return@launch

            val currentProjects = try {
                withTimeoutOrNull(PARTICIPANT_WAIT_TIMEOUT_MS) {
                    projects.first { it.isNotEmpty() }
                } ?: projects.value
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "refreshParticipantNames: wait failed: ${e.message}", e)
                return@launch
            }

            if (currentProjects.isEmpty()) {
                Log.d(TAG, "refreshParticipantNames: no projects, skipping")
                return@launch
            }

            try {
                val allMembers = mutableListOf<ProjectMemberDto>()
                currentProjects.forEach { p ->
                    val response = memberRepository.getMembers(p.id)
                    if (response.isSuccessful) {
                        allMembers.addAll(response.body().orEmpty())
                    } else {
                        Log.w(TAG, "getMembers(${p.id}) code=${response.code()}")
                    }
                }
                _participantNamesByProject.value = buildParticipantNamesMap(allMembers)
                Log.d(
                    TAG,
                    "Participant names loaded: ${_participantNamesByProject.value.size} projects"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "refreshParticipantNames failed: ${e.message}", e)
            }
        }
    }

    private suspend fun buildParticipantNamesMap(
        members: List<ProjectMemberDto>
    ): Map<String, List<String>> {
        val contactsByPhone = buildContactsByPhoneMap()
        val result = mutableMapOf<String, MutableList<String>>()

        members.forEach { m ->
            val normalizedPhone = m.phoneNumber?.let { PhoneUtils.normalize(it) }
            val displayName = normalizedPhone
                ?.let { contactsByPhone[it] }
                ?: m.name
                ?: m.phoneNumber
                ?: m.userId

            result.getOrPut(m.projectId) { mutableListOf() }.add(displayName)
        }

        return result
    }

    private suspend fun buildContactsByPhoneMap(): Map<String, String> {
        val contacts = contactRepository.getAllContactsOnce()
        val result = mutableMapOf<String, String>()
        contacts.forEach { contact ->
            val methods = try {
                contactRepository.getContactMethodsOnce(contact.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            methods.forEach { m ->
                val type = m.methodType.lowercase()
                if (type.contains("телефон") || type.contains("phone")) {
                    val phone = PhoneUtils.normalize(m.value)
                    if (phone.isNotBlank()) {
                        result[phone] = contact.name
                    }
                }
            }
        }
        return result
    }

    fun hasDuplicates(contactId: String): Boolean =
        _duplicatesCache.value.containsKey(contactId)

    private fun updateDuplicatesCache() {
        viewModelScope.launch {
            try {
                val allMethods = contactRepository.getAllContactMethods().first()
                val byContact = allMethods.groupBy { it.contactId }
                val valueToContacts = buildValueToContacts(_contacts.value, byContact)
                _duplicatesCache.value = buildDuplicates(valueToContacts)
                _duplicatesVersion.update { it + 1 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error computing duplicates: ${e.message}", e)
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
        memberCustomer: Contact?,
        memberEstimator: Contact?,
        memberBuilders: List<Contact>,
        onResult: (CreateProjectResult) -> Unit = {}
    ) {
        safeLaunch(
            block = {
                val uid = userId
                    ?: throw IllegalStateException("Пользователь не авторизован")

                val project = Project(
                    name = name,
                    description = description,
                    objectId = normalizeObjectId(objectId),
                    userId = uid
                )

                val createdProject = repository.createProject(project)

                materials.forEach { material ->
                    repository.addMaterial(material.copy(projectId = createdProject.id))
                }
                workItems.forEach { work ->
                    repository.addWorkItem(work.copy(projectId = createdProject.id))
                }

                val synced = syncManager.syncProjectNow(createdProject.id)

                if (!synced) {
                    val warning = "Проект создан. Назначение участников требует интернета — " +
                            "попробуйте позже из экрана сметы."
                    Log.w(TAG, "syncProjectNow failed — members not assigned")
                    return@safeLaunch CreateProjectResult(
                        project = createdProject,
                        warning = warning
                    )
                }

                val memberResult = assignMembers(
                    projectId = createdProject.id,
                    customer = memberCustomer,
                    estimator = memberEstimator,
                    builders = memberBuilders
                )

                val warning = if (memberResult.isFailure) {
                    val msg = memberResult.exceptionOrNull()?.message
                        ?: "Не удалось назначить участников"
                    Log.w(TAG, "Member assignment failed: $msg")
                    msg
                } else null

                CreateProjectResult(project = createdProject, warning = warning)
            },
            onSuccess = { result ->
                Log.d(TAG, "Project created: ${result.project?.name}")
                refreshParticipantNames()
                onResult(result)
            },
            onError = { e ->
                val msg = e.message ?: "Ошибка создания"
                setError("Ошибка создания: $msg")
                onResult(CreateProjectResult(project = null, warning = null, error = msg))
            }
        )
    }

    data class CreateProjectResult(
        val project: Project?,
        val warning: String?,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = project != null
    }

    fun updateProject(
        projectId: String,
        name: String,
        description: String,
        materials: List<Material>,
        workItems: List<WorkItem>
    ) {
        safeLaunch(
            block = {
                val oldProject = getProjectByIdAsync(projectId)
                    ?: throw IllegalStateException("Проект не найден")

                val updatedProject = oldProject.copy(
                    name = name,
                    description = description,
                    updatedAt = Date()
                )

                repository.updateProject(updatedProject)
                replaceMaterials(projectId, materials)
                replaceWorkItems(projectId, workItems)

                val totalBudget = calculateBudget(materials, workItems)
                val finalProject = updatedProject.copy(totalBudget = totalBudget)
                repository.updateProject(finalProject)

                finalProject
            },
            onSuccess = {
                Log.d(TAG, "Project updated: $projectId")
                triggerSyncNow()
            },
            onError = { e -> setError("Ошибка обновления: ${e.message}") }
        )
    }

    fun updateProjectWithMembers(
        projectId: String,
        name: String,
        description: String,
        materials: List<Material>,
        workItems: List<WorkItem>,
        memberCustomer: Contact?,
        memberEstimator: Contact?,
        memberBuilders: List<Contact>,
        onResult: (warning: String?) -> Unit = {}
    ) {
        safeLaunch(
            block = {
                val oldProject = getProjectByIdAsync(projectId)
                    ?: throw IllegalStateException("Проект не найден")

                val updatedProject = oldProject.copy(
                    name = name,
                    description = description,
                    updatedAt = Date()
                )
                repository.updateProject(updatedProject)

                replaceMaterials(projectId, materials)
                replaceWorkItems(projectId, workItems)

                val totalBudget = calculateBudget(materials, workItems)
                repository.updateProject(updatedProject.copy(totalBudget = totalBudget))

                val myRole = oldProject.myRole
                val canManageMembers = myRole == null || myRole == "CUSTOMER"

                val warning = if (canManageMembers) {
                    updateMembers(
                        projectId = projectId,
                        customer = memberCustomer,
                        estimator = memberEstimator,
                        builders = memberBuilders
                    )
                } else {
                    Log.d(TAG, "updateProjectWithMembers: role=$myRole — skip members update")
                    null
                }

                warning
            },
            onSuccess = { warning ->
                Log.d(TAG, "Project updated with members: $projectId, warning=$warning")
                triggerSyncNow()
                refreshParticipantNames()
                onResult(warning)
            },
            onError = { e ->
                Log.e(TAG, "updateProjectWithMembers failed: ${e.message}", e)
                setError("Ошибка обновления: ${e.message}")
                onResult("Ошибка: ${e.message}")
            }
        )
    }

    private suspend fun updateMembers(
        projectId: String,
        customer: Contact?,
        estimator: Contact?,
        builders: List<Contact>
    ): String? {
        if (!syncManager.hasInternetConnection()) {
            return "Нет интернета — участники не обновлены, попробуйте позже"
        }

        val currentMembers = memberRepository.getMembers(projectId)
            .body()
            .orEmpty()

        val currentCustomerPhone = currentMembers
            .firstOrNull { it.role == "CUSTOMER" }?.phoneNumber
            ?.let { PhoneUtils.normalize(it) }

        val currentEstimatorPhone = currentMembers
            .firstOrNull { it.role == "ESTIMATOR" }?.phoneNumber
            ?.let { PhoneUtils.normalize(it) }

        val currentBuilderPhones = currentMembers
            .filter { it.role == "BUILDER" }
            .mapNotNull { it.phoneNumber?.let(PhoneUtils::normalize) }
            .toSet()

        val warnings = mutableListOf<String>()

        if (customer != null) {
            val newPhone = getPhoneForContact(customer)
            if (newPhone == null) {
                warnings.add("У заказчика нет телефона")
            } else if (newPhone != currentCustomerPhone) {
                Log.d(TAG, "updateMembers: changing CUSTOMER $currentCustomerPhone → $newPhone")
                val response = memberRepository.setCustomer(projectId, newPhone)
                if (!response.isSuccessful) {
                    warnings.add(describeMemberError("заказчика", customer, response.code()))
                }
            }
        }

        if (estimator != null) {
            val newPhone = getPhoneForContact(estimator)
            if (newPhone == null) {
                warnings.add("У сметчика нет телефона")
            } else if (newPhone != currentEstimatorPhone) {
                Log.d(TAG, "updateMembers: changing ESTIMATOR $currentEstimatorPhone → $newPhone")
                val response = memberRepository.setEstimator(projectId, newPhone)
                if (!response.isSuccessful) {
                    warnings.add(describeMemberError("сметчика", estimator, response.code()))
                }
            }
        }

        val newBuilderPhones = builders.mapNotNull { getPhoneForContact(it) }.toSet()
        val toAddPhones = newBuilderPhones - currentBuilderPhones

        if (toAddPhones.isNotEmpty()) {
            Log.d(TAG, "updateMembers: adding ${toAddPhones.size} builders")
            val response = memberRepository.addBuilders(projectId, toAddPhones.toList())
            if (!response.isSuccessful) {
                warnings.add("Не удалось добавить строителей (код ${response.code()})")
            }
        }

        val toRemovePhones = currentBuilderPhones - newBuilderPhones
        val toRemoveMembers = currentMembers.filter { m ->
            m.role == "BUILDER" && m.phoneNumber?.let(PhoneUtils::normalize) in toRemovePhones
        }
        for (member in toRemoveMembers) {
            Log.d(TAG, "updateMembers: removing builder ${member.userId}")
            val response = memberRepository.removeMember(projectId, member.userId)
            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to remove builder ${member.userId}: ${response.code()}")
                warnings.add("Не удалось удалить строителя")
            }
        }

        return warnings.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    suspend fun assignMembers(
        projectId: String,
        customer: Contact?,
        estimator: Contact?,
        builders: List<Contact>
    ): Result<Unit> = runCatching {
        if (!syncManager.hasInternetConnection()) {
            throw IllegalStateException(
                "Нет соединения — участники не назначены, попробуйте позже"
            )
        }

        if (customer != null) {
            val phone = getPhoneForContact(customer)
                ?: throw IllegalStateException("У заказчика нет номера телефона")
            val response = memberRepository.setCustomer(projectId, phone)
            if (!response.isSuccessful) {
                throw Exception(describeMemberError("заказчика", customer, response.code()))
            }
            Log.d(TAG, "Customer assigned: ${customer.name}")
        }

        if (estimator != null) {
            val phone = getPhoneForContact(estimator)
                ?: throw IllegalStateException("У сметчика нет номера телефона")
            val response = memberRepository.setEstimator(projectId, phone)
            if (!response.isSuccessful) {
                throw Exception(describeMemberError("сметчика", estimator, response.code()))
            }
            Log.d(TAG, "Estimator assigned: ${estimator.name}")
        }

        if (builders.isNotEmpty()) {
            val phones = builders.mapNotNull { getPhoneForContact(it) }
            if (phones.isEmpty()) {
                Log.w(TAG, "No builder phones — skipped")
            } else {
                val response = memberRepository.addBuilders(projectId, phones)
                if (!response.isSuccessful) {
                    throw Exception("Не удалось добавить строителей (код ${response.code()})")
                }
                Log.d(TAG, "Builders assigned: ${phones.size}")
            }
        }
    }

    private fun describeMemberError(role: String, contact: Contact, code: Int): String {
        val name = contact.name
        return when (code) {
            404 -> "Пользователь «$name» не зарегистрирован в приложении. " +
                    "Попросите его установить приложение и войти."
            403 -> "Недостаточно прав для назначения $role"
            409 -> "Этот участник уже добавлен в проект"
            else -> "Не удалось назначить $role (код $code)"
        }
    }

    fun setCustomer(projectId: String, contact: Contact) {
        safeLaunch(
            block = {
                val phone = getPhoneForContact(contact)
                    ?: throw IllegalStateException("У контакта нет номера телефона")
                val response = memberRepository.setCustomer(projectId, phone)
                if (!response.isSuccessful) {
                    throw Exception(describeMemberError("заказчика", contact, response.code()))
                }
            },
            onSuccess = {
                Log.d(TAG, "Customer updated")
                reloadProjectIfOnline(projectId)
                refreshParticipantNames()
            },
            onError = { e -> setError(e.message ?: "Ошибка") }
        )
    }

    fun setEstimator(projectId: String, contact: Contact) {
        safeLaunch(
            block = {
                val phone = getPhoneForContact(contact)
                    ?: throw IllegalStateException("У контакта нет номера телефона")
                val response = memberRepository.setEstimator(projectId, phone)
                if (!response.isSuccessful) {
                    throw Exception(describeMemberError("сметчика", contact, response.code()))
                }
            },
            onSuccess = {
                Log.d(TAG, "Estimator updated")
                reloadProjectIfOnline(projectId)
                refreshParticipantNames()
            },
            onError = { e -> setError(e.message ?: "Ошибка") }
        )
    }

    suspend fun checkContactExists(contact: Contact): ContactCheckResult {
        val phone = getPhoneForContact(contact)
            ?: return ContactCheckResult.Error("У контакта нет номера телефона")

        return try {
            val response = memberRepository.checkUserExistsRaw(phone)
            when (response) {
                true -> ContactCheckResult.Exists
                false -> ContactCheckResult.NotRegistered
                null -> ContactCheckResult.Error("Не удалось проверить. Проверьте соединение.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "checkContactExists: ${e.message}", e)
            ContactCheckResult.Error("Ошибка проверки: ${e.message}")
        }
    }

    fun addBuilders(projectId: String, contacts: List<Contact>) {
        if (contacts.isEmpty()) return
        safeLaunch(
            block = {
                val phones = contacts.mapNotNull { getPhoneForContact(it) }
                if (phones.isEmpty()) {
                    throw IllegalStateException("Ни у одного контакта нет телефона")
                }
                val response = memberRepository.addBuilders(projectId, phones)
                if (!response.isSuccessful) {
                    throw Exception("Не удалось добавить строителей (код ${response.code()})")
                }
            },
            onSuccess = {
                Log.d(TAG, "Builders added")
                reloadProjectIfOnline(projectId)
                refreshParticipantNames()
            },
            onError = { e -> setError(e.message ?: "Ошибка") }
        )
    }

    private fun reloadProjectIfOnline(projectId: String) {
        if (!syncManager.hasInternetConnection()) return
        viewModelScope.launch {
            try {
                syncManager.reloadProject(projectId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "reloadProject failed: ${e.message}", e)
            }
        }
    }

    private suspend fun getPhoneForContact(contact: Contact): String? {
        val methods = try {
            contactRepository.getContactMethodsOnce(contact.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

        return ContactPhoneExtractor.extractPhone(contact, methods)
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
            onSuccess = {
                Log.d(TAG, "Project deleted: ${project.name}")
                _participantNamesByProject.update { it - project.id }
            },
            onError = { e -> setError("Ошибка удаления: ${e.message}") }
        )
    }

    suspend fun getProjectByIdAsync(projectId: String): Project? {
        return try {
            repository.getProjectById(projectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error getting project: ${e.message}", e)
            null
        }
    }

    suspend fun getProjectWithMembers(
        projectId: String
    ): Pair<Project, List<ProjectMemberDto>>? {
        return try {
            val project = repository.getProjectById(projectId)
            if (project == null) {
                Log.w(TAG, "getProjectWithMembers: project $projectId not found")
                return null
            }

            val response = memberRepository.getMembers(projectId)
            val members = if (response.isSuccessful) {
                response.body().orEmpty()
            } else {
                Log.w(TAG, "getProjectWithMembers: getMembers code=${response.code()}")
                emptyList()
            }

            Log.d(TAG, "getProjectWithMembers: project=$projectId, members=${members.size}")
            members.forEach { m ->
                Log.d(TAG, "  member: userId=${m.userId}, role=${m.role}, " +
                        "name=${m.name}, phone=${m.phoneNumber}")
            }

            project to members
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "getProjectWithMembers failed: ${e.message}", e)
            null
        }
    }

    suspend fun findLocalContactNameByPhone(phone: String?): String? {
        if (phone.isNullOrBlank()) return null

        val targetPhone = PhoneUtils.normalize(phone)
        if (targetPhone.isBlank()) return null

        return try {
            val contacts = contactRepository.getAllContactsOnce()
            contacts.forEach { contact ->
                val methods = try {
                    contactRepository.getContactMethodsOnce(contact.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }

                val hasPhone = methods.any { m ->
                    val type = m.methodType.lowercase()
                    val isPhone = type.contains("телефон") || type.contains("phone")
                    if (!isPhone) return@any false

                    PhoneUtils.normalize(m.value) == targetPhone
                }

                if (hasPhone) {
                    Log.d(TAG, "findLocalContactNameByPhone($phone) → '${contact.name}'")
                    return contact.name
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "findLocalContactNameByPhone failed: ${e.message}", e)
            null
        }
    }

    suspend fun getMaterialsForProject(projectId: String): List<Material> {
        return try {
            repository.getMaterials(projectId).first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error getting materials: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getWorkItemsForProject(projectId: String): List<WorkItem> {
        return try {
            repository.getWorkItems(projectId).first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error getting work items: ${e.message}", e)
            emptyList()
        }
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
        participantNames: Map<String, List<String>>
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

            ProjectFilterType.BY_PARTICIPANT -> projects.filter { project ->
                val names = participantNames[project.id].orEmpty()
                names.any { name -> FuzzySearch.matches(name, query, maxDistance = 2) }
            }
        }
    }

    fun loadProjectsFromServer() {
        viewModelScope.launch {
            val uid = userId ?: return@launch
            if (syncManager.hasInternetConnection()) {
                syncManager.startPeriodicSync()
                syncManager.syncDataFromServer(uid, skipQueueCheck = true)
                refreshParticipantNames()
            }
        }
    }

    private fun triggerSyncNow() {
        val uid = userId ?: return
        if (!syncManager.hasInternetConnection()) return
        viewModelScope.launch {
            try {
                syncManager.syncIfQueueIsEmpty(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "triggerSyncNow failed: ${e.message}", e)
            }
        }
    }
}