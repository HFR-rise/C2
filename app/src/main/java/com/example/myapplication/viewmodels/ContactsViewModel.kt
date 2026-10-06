package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.repository.ContactRepository
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.FuzzySearch
import com.example.myapplication.utils.PhoneUtils
import com.example.myapplication.utils.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

enum class SearchFilter(val displayName: String) {
    BY_NAME("По имени"),
    BY_DESCRIPTION("По описанию"),
    BY_PHONE("Телефон"),
    BY_TELEGRAM("Telegram"),
    BY_VK("VK"),
    BY_EMAIL("Email"),
    BY_OTHER("Другой способ связи")
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repo: ContactRepository,
    private val syncManager: SyncManager,
    private val userPreferences: UserPreferences
) : BaseViewModel() {

    private val TAG = "ContactsViewModel"

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentFilter = MutableStateFlow(SearchFilter.BY_NAME)
    val currentFilter: StateFlow<SearchFilter> = _currentFilter.asStateFlow()

    private val _contactMethodsCache = MutableStateFlow<Map<String, List<ContactMethod>>>(emptyMap())

    private val _duplicatesCache = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val duplicatesCache: StateFlow<Map<String, List<String>>> = _duplicatesCache.asStateFlow()

    private val _duplicatesVersion = MutableStateFlow(0)
    val duplicatesVersion: StateFlow<Int> = _duplicatesVersion.asStateFlow()

    private val _editingContact = MutableStateFlow<Contact?>(null)
    val editingContact: StateFlow<Contact?> = _editingContact.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    init {
        loadContacts()
        loadAllContactMethods()
    }

    val filteredContacts: StateFlow<List<Contact>> = combine(
        _contacts,
        _searchQuery,
        _currentFilter,
        _contactMethodsCache
    ) { contacts, query, filter, methodsCache ->
        filterContacts(contacts, query, filter, methodsCache)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun filterContacts(
        contacts: List<Contact>,
        query: String,
        filter: SearchFilter,
        methodsCache: Map<String, List<ContactMethod>>
    ): List<Contact> {
        if (query.isBlank()) return contacts

        val normalizedQuery = query.lowercase().trim()

        return when (filter) {
            SearchFilter.BY_NAME -> FuzzySearch.filter(
                items = contacts,
                query = query,
                textExtractor = { it.name },
                maxDistance = getMaxDistance(query)
            )
            SearchFilter.BY_DESCRIPTION -> FuzzySearch.filter(
                items = contacts,
                query = query,
                textExtractor = { it.description },
                maxDistance = getMaxDistance(query)
            )
            SearchFilter.BY_PHONE -> filterByPhone(contacts, normalizedQuery, methodsCache)
            SearchFilter.BY_TELEGRAM -> filterByMessenger(contacts, normalizedQuery, methodsCache, "telegram")
            SearchFilter.BY_VK -> filterByMessenger(contacts, normalizedQuery, methodsCache, "vk")
            SearchFilter.BY_EMAIL -> filterByEmail(contacts, normalizedQuery, methodsCache)
            SearchFilter.BY_OTHER -> filterByOther(contacts, normalizedQuery, methodsCache)
        }
    }

    private fun filterByPhone(
        contacts: List<Contact>,
        query: String,
        methodsCache: Map<String, List<ContactMethod>>
    ): List<Contact> {
        if (query.isEmpty()) return contacts

        if (query == "+") {
            return contacts.filter { contact ->
                methodsCache[contact.id]?.any { isPhoneMethod(it) } == true
            }
        }

        val normalizedQueryPhone = PhoneUtils.normalize(query)
        if (normalizedQueryPhone.isEmpty()) return emptyList()

        return contacts.filter { contact ->
            methodsCache[contact.id]?.any { method ->
                isPhoneMethod(method) &&
                        PhoneUtils.normalize(method.value).startsWith(normalizedQueryPhone)
            } == true
        }
    }

    private fun filterByMessenger(
        contacts: List<Contact>,
        query: String,
        methodsCache: Map<String, List<ContactMethod>>,
        messengerType: String
    ): List<Contact> {
        val queryWithoutAt = query.removePrefix("@")

        return contacts.filter { contact ->
            methodsCache[contact.id]?.any { method ->
                method.methodType.lowercase().contains(messengerType) &&
                        method.value.lowercase().removePrefix("@").contains(queryWithoutAt)
            } == true
        }
    }

    private fun filterByEmail(
        contacts: List<Contact>,
        query: String,
        methodsCache: Map<String, List<ContactMethod>>
    ): List<Contact> {
        return contacts.filter { contact ->
            methodsCache[contact.id]?.any { method ->
                val type = method.methodType.lowercase()
                (type.contains("email") || type.contains("почта")) &&
                        method.value.lowercase().contains(query)
            } == true
        }
    }

    private fun filterByOther(
        contacts: List<Contact>,
        query: String,
        methodsCache: Map<String, List<ContactMethod>>
    ): List<Contact> {
        return contacts.filter { contact ->
            methodsCache[contact.id]?.any { method ->
                val type = method.methodType.lowercase()
                !isPhoneMethod(method) &&
                        !type.contains("telegram") &&
                        !type.contains("vk") &&
                        !type.contains("email") &&
                        !type.contains("почта") &&
                        method.value.lowercase().removePrefix("@").contains(query)
            } == true
        }
    }

    private fun isPhoneMethod(method: ContactMethod): Boolean {
        val type = method.methodType.lowercase()
        return type.contains("телефон") || type.contains("phone")
    }

    private fun loadContacts() {
        viewModelScope.launch {
            try {
                repo.getAllContacts().collect { list ->
                    _contacts.value = list
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadContacts: ${e.message}", e)
            }
        }
    }

    private fun loadAllContactMethods() {
        viewModelScope.launch {
            try {
                repo.getAllContactMethods().collect { allMethods ->
                    _contactMethodsCache.value = allMethods.groupBy { it.contactId }
                    updateDuplicatesCache()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadAllContactMethods: ${e.message}", e)
            }
        }
    }

    private fun loadMethodsForContact(contactId: String) {
        viewModelScope.launch {
            try {
                val methods = repo.getContactMethodsOnce(contactId)
                val currentCache = _contactMethodsCache.value.toMutableMap()
                currentCache[contactId] = methods
                _contactMethodsCache.value = currentCache
                updateDuplicatesCache()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading methods for $contactId: ${e.message}", e)
            }
        }
    }

    private fun updateDuplicatesCache() {
        viewModelScope.launch {
            try {
                val methodsMap = _contactMethodsCache.value
                val contacts = _contacts.value

                val duplicates = withContext(Dispatchers.Default) {
                    val valueToContacts = mutableMapOf<String, MutableList<String>>()

                    contacts.forEach { contact ->
                        methodsMap[contact.id]?.forEach { method ->
                            val normalizedValue = if (isPhoneMethod(method)) {
                                PhoneUtils.normalize(method.value)
                            } else {
                                method.value.lowercase()
                            }

                            if (normalizedValue.isNotBlank()) {
                                valueToContacts.getOrPut(normalizedValue) { mutableListOf() }.add(contact.id)
                            }
                        }
                    }

                    val result = mutableMapOf<String, MutableList<String>>()
                    valueToContacts.filter { it.value.size > 1 }.forEach { (_, contactIds) ->
                        contactIds.forEach { contactId ->
                            val others = contactIds.filter { it != contactId }
                            if (others.isNotEmpty()) {
                                result.getOrPut(contactId) { mutableListOf() }.addAll(others)
                            }
                        }
                    }
                    result
                }

                _duplicatesCache.value = duplicates
                _duplicatesVersion.value += 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "updateDuplicatesCache: ${e.message}", e)
            }
        }
    }

    fun hasDuplicates(contactId: String): Boolean =
        _duplicatesCache.value.containsKey(contactId)

    fun getDuplicateContacts(contactId: String): List<String> =
        _duplicatesCache.value[contactId] ?: emptyList()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSearchFilter(filter: SearchFilter) {
        _currentFilter.value = filter
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    private fun getMaxDistance(query: String): Int = when (query.length) {
        1 -> 0
        2 -> 1
        else -> 2
    }

    fun addContactWithMethods(name: String, description: String, methods: List<ContactMethod>) {
        safeLaunch(
            block = {
                val userId = userPreferences.getUserId()
                    ?: throw IllegalStateException("Пользователь не авторизован")

                Log.d(TAG, "Adding contact: $name with ${methods.size} methods")

                val contact = Contact(
                    name = name,
                    description = description,
                    userId = userId
                )

                val contactId = repo.addContact(contact)

                methods.forEach { method ->
                    repo.addContactMethod(
                        ContactMethod(
                            contactId = contactId,
                            methodType = method.methodType,
                            value = method.value,
                            userId = userId
                        )
                    )
                }

                contactId
            },
            onSuccess = { contactId ->
                Log.d(TAG, "Contact created: $contactId")
                loadMethodsForContact(contactId)
                _showAddDialog.value = false
                triggerSyncNow()
            },
            onError = { e ->
                Log.e(TAG, "Error creating contact: ${e.message}", e)
                setError("Ошибка: ${e.message}")
            }
        )
    }

    fun addContactMethod(contactId: String, methodType: String, value: String) {
        safeLaunch(
            block = {
                val method = ContactMethod(
                    contactId = contactId,
                    methodType = methodType,
                    value = value,
                    userId = userPreferences.getUserId() ?: ""
                )
                repo.addContactMethod(method)
            },
            onSuccess = {
                loadMethodsForContact(contactId)
                triggerSyncNow()
            },
            onError = { e -> setError("Ошибка добавления способа связи: ${e.message}") }
        )
    }

    fun updateContactMethod(method: ContactMethod) {
        safeLaunch(
            block = { repo.updateContactMethod(method) },
            onSuccess = {
                loadMethodsForContact(method.contactId)
                triggerSyncNow()
            },
            onError = { e -> setError("Ошибка обновления: ${e.message}") }
        )
    }

    fun deleteContactMethod(method: ContactMethod) {
        safeLaunch(
            block = { repo.deleteContactMethod(method) },
            onSuccess = {
                loadMethodsForContact(method.contactId)
                triggerSyncNow()
            },
            onError = { e -> setError("Ошибка удаления: ${e.message}") }
        )
    }

    fun updateContact(contact: Contact) {
        safeLaunch(
            block = { repo.updateContact(contact) },
            onSuccess = {
                _editingContact.value = null
                triggerSyncNow()
            },
            onError = { e -> setError("Ошибка обновления: ${e.message}") }
        )
    }

    fun deleteContact(contact: Contact) {
        safeLaunch(
            block = { repo.deleteContact(contact) },
            onSuccess = {
                val currentCache = _contactMethodsCache.value.toMutableMap()
                currentCache.remove(contact.id)
                _contactMethodsCache.value = currentCache
                updateDuplicatesCache()
                triggerSyncNow()
            },
            onError = { e -> setError("Ошибка удаления: ${e.message}") }
        )
    }

    fun startEditing(contact: Contact) {
        _editingContact.value = contact
    }

    fun clearEditing() {
        _editingContact.value = null
    }

    fun showAddDialog() {
        _showAddDialog.value = true
    }

    fun hideAddDialog() {
        _showAddDialog.value = false
    }

    fun clearErrorMessage() {
        setError(null)
    }

    fun refreshData() {
        safeLaunch(
            showLoading = false,
            block = {
                val userId = userPreferences.getUserId()
                    ?: throw IllegalStateException("Пользователь не авторизован")

                if (syncManager.hasInternetConnection()) {
                    setRefreshing(true)
                    Log.d(TAG, "Refreshing contacts: push pending + pull from server")
                    syncManager.startPeriodicSync()

                    syncManager.syncIfQueueIsEmpty(userId)
                } else {
                    Log.d(TAG, "No internet, skipping refresh")
                }
            },
            onSuccess = { setRefreshing(false) },
            onError = { e ->
                Log.e(TAG, "Error refreshing: ${e.message}", e)
                setRefreshing(false)
            }
        )
    }

    fun getContactMethods(contactId: String): Flow<List<ContactMethod>> {
        return repo.getContactMethods(contactId)
    }

    private fun triggerSyncNow() {
        val userId = syncManager.currentUserId() ?: return
        if (!syncManager.hasInternetConnection()) return

        viewModelScope.launch {
            try {
                syncManager.syncIfQueueIsEmpty(userId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "triggerSyncNow failed: ${e.message}", e)
            }
        }
    }
}