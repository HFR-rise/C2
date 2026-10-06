package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.AddBuildersResponse
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.models.ProjectMemberDto
import com.example.myapplication.data.repository.ContactRepository
import com.example.myapplication.data.repository.ProjectMemberRepository
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.ContactPhoneExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class ProjectMembersViewModel @Inject constructor(
    private val memberRepository: ProjectMemberRepository,
    private val contactRepository: ContactRepository,
    private val syncManager: SyncManager,
    savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private companion object {
        const val TAG = "ProjectMembersVM"
    }

    private val projectId: String = savedStateHandle["projectId"] ?: ""

    private val _members = MutableStateFlow<List<ProjectMemberDto>>(emptyList())
    val members: StateFlow<List<ProjectMemberDto>> = _members.asStateFlow()

    private val _myRole = MutableStateFlow<String?>(null)
    val myRole: StateFlow<String?> = _myRole.asStateFlow()

    private val _showContactSelector = MutableStateFlow<MemberRole?>(null)
    val showContactSelector: StateFlow<MemberRole?> = _showContactSelector.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    init {
        Log.d(TAG, "init: projectId=$projectId")
        if (projectId.isNotEmpty()) {
            loadMembers()
        } else {
            Log.e(TAG, "projectId is empty")
        }
    }

    suspend fun checkContactExists(contact: Contact): ContactCheckResult {
        Log.d(TAG, "checkContactExists: START contact.id=${contact.id}, name='${contact.name}'")

        val phone = getPhoneForContact(contact)
        if (phone == null) {
            Log.w(TAG, "checkContactExists: NO PHONE for contact.id=${contact.id}, name='${contact.name}'")
            return ContactCheckResult.Error("У контакта нет номера телефона")
        }

        Log.d(TAG, "checkContactExists: phone=$phone, calling server...")

        return try {
            val response = memberRepository.checkUserExistsRaw(phone)
            Log.d(TAG, "checkContactExists: server response=$response")

            when (response) {
                true -> {
                    Log.d(TAG, "checkContactExists: EXISTS")
                    ContactCheckResult.Exists
                }
                false -> {
                    Log.d(TAG, "checkContactExists: NOT_REGISTERED")
                    ContactCheckResult.NotRegistered
                }
                null -> {
                    Log.w(TAG, "checkContactExists: NULL (network error?)")
                    ContactCheckResult.Error("Не удалось проверить. Проверьте соединение.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "checkContactExists: EXCEPTION", e)
            ContactCheckResult.Error("Ошибка проверки: ${e.message}")
        }
    }

    fun showMessage(message: String) {
        _operationMessage.value = message
    }

    fun clearMessage() {
        _operationMessage.value = null
    }

    fun loadMembers() {
        Log.d(TAG, "loadMembers: START projectId=$projectId")
        safeLaunch(
            block = {
                val response = memberRepository.getMembers(projectId)
                Log.d(TAG, "loadMembers: response code=${response.code()}")
                if (!response.isSuccessful) {
                    throw Exception("Ошибка загрузки: ${response.code()}")
                }
                response.body().orEmpty()
            },
            onSuccess = { list ->
                _members.value = list
                _myRole.value = list.firstOrNull { it.userId == currentUserId() }?.role
                Log.d(TAG, "loadMembers: OK, loaded=${list.size}, myRole=${_myRole.value}")
                list.forEach { m ->
                    Log.d(TAG, "  member: userId=${m.userId}, role=${m.role}, name=${m.name}, phone=${m.phoneNumber}")
                }
            },
            onError = { e ->
                Log.e(TAG, "loadMembers: FAILED", e)
                setError(e.message ?: "Ошибка загрузки участников")
            }
        )
    }

    fun changeRole(targetUserId: String, newRole: String) {
        Log.d(TAG, "changeRole: target=$targetUserId, newRole=$newRole")
        safeLaunch(
            block = {
                val response = memberRepository.changeRole(projectId, targetUserId, newRole)
                Log.d(TAG, "changeRole: response code=${response.code()}")
                if (!response.isSuccessful) {
                    throw Exception(describeError("сменить роль", response.code()))
                }
            },
            onSuccess = {
                Log.d(TAG, "changeRole: OK")
                _operationMessage.value = "Роль изменена"
                loadMembers()
                refreshProject()
            },
            onError = { e ->
                Log.e(TAG, "changeRole: FAILED", e)
                _operationMessage.value = e.message
                setError(e.message)
            }
        )
    }

    fun setCustomer(contact: Contact) {
        Log.d(TAG, "setCustomer: START contact.id=${contact.id}, name='${contact.name}'")
        safeLaunch(
            block = {
                val phone = getPhoneForContact(contact)
                Log.d(TAG, "setCustomer: resolved phone=$phone")
                if (phone == null) {
                    throw IllegalStateException("У контакта нет телефона")
                }
                val response = memberRepository.setCustomer(projectId, phone)
                Log.d(TAG, "setCustomer: response code=${response.code()}")
                if (!response.isSuccessful) {
                    throw Exception(describeMemberError("заказчика", contact, response.code()))
                }
            },
            onSuccess = {
                Log.d(TAG, "setCustomer: OK for '${contact.name}'")
                _operationMessage.value = "Заказчик назначен"
                loadMembers()
                refreshProject()
            },
            onError = { e ->
                Log.e(TAG, "setCustomer: FAILED for '${contact.name}'", e)
                _operationMessage.value = e.message
                setError(e.message)
            }
        )
    }

    fun setEstimator(contact: Contact) {
        Log.d(TAG, "setEstimator: START contact.id=${contact.id}, name='${contact.name}'")
        safeLaunch(
            block = {
                val phone = getPhoneForContact(contact)
                Log.d(TAG, "setEstimator: resolved phone=$phone")
                if (phone == null) {
                    throw IllegalStateException("У контакта нет телефона")
                }
                val response = memberRepository.setEstimator(projectId, phone)
                Log.d(TAG, "setEstimator: response code=${response.code()}")
                if (!response.isSuccessful) {
                    throw Exception(describeMemberError("сметчика", contact, response.code()))
                }
            },
            onSuccess = {
                Log.d(TAG, "setEstimator: OK for '${contact.name}'")
                _operationMessage.value = "Сметчик назначен"
                loadMembers()
                refreshProject()
            },
            onError = { e ->
                Log.e(TAG, "setEstimator: FAILED for '${contact.name}'", e)
                _operationMessage.value = e.message
                setError(e.message)
            }
        )
    }

    fun addBuilders(contacts: List<Contact>) {
        Log.d(TAG, "addBuilders: START, contacts=${contacts.size}")
        contacts.forEach { c ->
            Log.d(TAG, "  input contact: id=${c.id}, name='${c.name}'")
        }

        if (contacts.isEmpty()) {
            Log.w(TAG, "addBuilders: empty contacts, abort")
            return
        }

        safeLaunch(
            block = {
                Log.d(TAG, "addBuilders: resolving phones for ${contacts.size} contacts...")

                val phones = contacts.mapNotNull { contact ->
                    Log.d(TAG, "  resolving phone for contact.id=${contact.id}, name='${contact.name}'")
                    val phone = getPhoneForContact(contact)
                    Log.d(TAG, "  resolved phone=$phone for contact.id=${contact.id}")
                    phone
                }

                Log.d(TAG, "addBuilders: resolved phones=$phones (${phones.size} of ${contacts.size})")

                if (phones.isEmpty()) {
                    Log.e(TAG, "addBuilders: no phones resolved — THROWING")
                    throw IllegalStateException("Ни у одного контакта нет телефона")
                }

                Log.d(TAG, "addBuilders: sending to server, projectId=$projectId, phones=$phones")
                val response = memberRepository.addBuilders(projectId, phones)
                Log.d(TAG, "addBuilders: server response code=${response.code()}, successful=${response.isSuccessful}")
                Log.d(TAG, "addBuilders: server response body=${response.body()}")

                if (!response.isSuccessful) {
                    val errBody = try { response.errorBody()?.string() } catch (e: Exception) { null }
                    Log.e(TAG, "addBuilders: server error body=$errBody")
                    throw Exception(describeError("добавить строителей", response.code()))
                }
                response.body() ?: AddBuildersResponse()
            },
            onSuccess = { result ->
                val added = result.addedCount
                val notFound = result.notFoundCount
                val already = result.alreadyCount

                Log.d(TAG, "addBuilders: SUCCESS, added=$added, already=$already, notFound=$notFound")

                val message = buildString {
                    if (added > 0) append("Добавлено: $added. ")
                    if (already > 0) append("Уже участники: $already. ")
                    if (notFound > 0) append("Не найдено: $notFound.")
                }.trim()

                _operationMessage.value = message.ifEmpty { "Готово" }
                loadMembers()
                refreshProject()
            },
            onError = { e ->
                Log.e(TAG, "addBuilders: FAILED", e)
                _operationMessage.value = e.message
                setError(e.message)
            }
        )
    }

    fun removeMember(targetUserId: String) {
        Log.d(TAG, "removeMember: target=$targetUserId")
        safeLaunch(
            block = {
                val response = memberRepository.removeMember(projectId, targetUserId)
                Log.d(TAG, "removeMember: response code=${response.code()}")
                if (!response.isSuccessful) {
                    throw Exception(describeError("удалить участника", response.code()))
                }
            },
            onSuccess = {
                Log.d(TAG, "removeMember: OK")
                _operationMessage.value = "Участник удалён"
                loadMembers()
                refreshProject()
            },
            onError = { e ->
                Log.e(TAG, "removeMember: FAILED", e)
                _operationMessage.value = e.message
                setError(e.message)
            }
        )
    }

    fun openContactSelector(role: MemberRole) {
        Log.d(TAG, "openContactSelector: role=$role")
        _showContactSelector.value = role
    }

    fun closeContactSelector() {
        Log.d(TAG, "closeContactSelector")
        _showContactSelector.value = null
    }

    private fun currentUserId(): String? {
        return syncManager.currentUserId()
    }

    private fun refreshProject() {
        if (syncManager.hasInternetConnection()) {
            viewModelScope.launch {
                try {
                    Log.d(TAG, "refreshProject: reloading $projectId")
                    syncManager.reloadProject(projectId)
                    Log.d(TAG, "refreshProject: OK")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "refreshProject failed: ${e.message}", e)
                }
            }
        } else {
            Log.d(TAG, "refreshProject: no internet, skipped")
        }
    }

    private suspend fun getPhoneForContact(contact: Contact): String? {
        Log.d(TAG, "getPhoneForContact: START contact.id=${contact.id}, name='${contact.name}'")

        val methods: List<ContactMethod> = try {
            val m = contactRepository.getContactMethodsOnce(contact.id)
            Log.d(TAG, "getPhoneForContact: loaded ${m.size} methods from Room for contact.id=${contact.id}")
            m
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "getPhoneForContact: methods load failed for contact.id=${contact.id}", e)
            emptyList()
        }

        methods.forEachIndexed { i, m ->
            Log.d(TAG, "  method[$i]: id=${m.id}, contactId=${m.contactId}, type='${m.methodType}', value='${m.value}'")
        }

        val phone = ContactPhoneExtractor.extractPhone(contact, methods)
        Log.d(TAG, "getPhoneForContact: extractPhone → $phone")

        if (phone == null) {
            Log.w(TAG, "getPhoneForContact: NULL — no matching phone method found")
        }

        return phone
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

    private fun describeError(action: String, code: Int): String {
        return when (code) {
            403 -> "Недостаточно прав, чтобы $action"
            404 -> "Не найдено — возможно, участник уже удалён"
            409 -> "Конфликт: действие уже выполнено или невозможно"
            else -> "Не удалось $action (код $code)"
        }
    }
}

enum class MemberRole(val displayName: String, val selectorTitle: String) {
    CUSTOMER("Заказчик", "Выберите заказчика"),
    ESTIMATOR("Сметчик", "Выберите сметчика"),
    BUILDER("Строитель", "Выберите строителя")
}