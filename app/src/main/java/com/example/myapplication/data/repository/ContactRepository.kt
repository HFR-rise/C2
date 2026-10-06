package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.database.ContactDao
import com.example.myapplication.data.database.ContactMethodDao
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.network.ApiService
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.UserPreferences
import kotlinx.coroutines.flow.Flow
import retrofit2.Response
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val contactMethodDao: ContactMethodDao,
    private val apiService: ApiService,
    private val syncManager: SyncManager,
    private val userPreferences: UserPreferences
) {

    private companion object {
        const val TAG = "ContactRepository"
    }

    fun getAllContacts(): Flow<List<Contact>> = contactDao.getAllContacts()

    fun getAllContactMethods(): Flow<List<ContactMethod>> =
        contactMethodDao.getAllContactMethods()

    fun getContactMethods(contactId: String): Flow<List<ContactMethod>> =
        contactMethodDao.getContactMethods(contactId)

    suspend fun getAllContactsOnce(): List<Contact> =
        contactDao.getAllContactsOnce()

    suspend fun getContactMethodsOnce(contactId: String): List<ContactMethod> =
        contactMethodDao.getContactMethodsOnce(contactId)

    suspend fun getAllContactsSync(): List<Contact> =
        contactDao.getAllContactsOnce()

    suspend fun getContactsCount(): Int = contactDao.getContactsCount()

    suspend fun addContact(contact: Contact): String {
        val contactWithId = ensureId(contact)
        val withPending = contactWithId.copy(needsSync = true)

        contactDao.insertContact(withPending)
        Log.d(TAG, "Contact saved locally (pending sync): ${withPending.id}")

        return withPending.id
    }

    suspend fun updateContact(contact: Contact) {
        contactDao.updateContact(contact.copy(needsSync = true))
        Log.d(TAG, "Contact updated (pending sync): ${contact.id}")
    }

    suspend fun addContactMethod(method: ContactMethod): String {
        val methodWithId = ensureId(method)

        contactMethodDao.insertContactMethod(methodWithId)
        contactDao.markAsPending(methodWithId.contactId)

        Log.d(TAG, "ContactMethod saved (parent pending): ${methodWithId.id}")
        return methodWithId.id
    }

    suspend fun updateContactMethod(method: ContactMethod) {
        contactMethodDao.updateContactMethod(method)
        contactDao.markAsPending(method.contactId)
        Log.d(TAG, "ContactMethod updated (parent pending): ${method.id}")
    }

    suspend fun deleteContactMethod(method: ContactMethod) {
        contactMethodDao.deleteContactMethod(method)
        contactDao.markAsPending(method.contactId)
        Log.d(TAG, "ContactMethod deleted (parent pending): ${method.id}")
    }

    suspend fun deleteContact(contact: Contact) {
        contactDao.deleteContact(contact)

        syncToServer(
            operation = "DELETE",
            entityType = "CONTACT",
            entity = contact,
            call = { apiService.deleteContact(it.id) }
        )
    }

    suspend fun deleteAllContacts() {
        contactDao.deleteAll()
        contactMethodDao.deleteAll()
    }

    suspend fun getPendingContacts(): List<Contact> =
        contactDao.getPendingContacts()

    suspend fun getPendingCount(): Int =
        contactDao.getPendingCount()

    suspend fun markAsSynced(contactId: String) {
        contactDao.markAsSynced(contactId)
    }

    suspend fun markAsPending(contactId: String) {
        contactDao.markAsPending(contactId)
    }

    suspend fun upsertContact(contact: Contact) {
        contactDao.upsertContact(contact.copy(description = contact.description ?: ""))
    }

    private fun <T : Any> ensureId(entity: T): T = when (entity) {
        is Contact -> if (entity.id.isEmpty()) entity.copy(id = UUID.randomUUID().toString()) else entity
        is ContactMethod -> if (entity.id.isEmpty()) entity.copy(id = UUID.randomUUID().toString()) else entity
        else -> entity
    } as T

    private suspend fun <T : Any> syncToServer(
        operation: String,
        entityType: String,
        entity: T,
        call: suspend (T) -> Response<*>,
        onSuccess: suspend (T) -> Unit = {}
    ) {
        if (userPreferences.getUserId() == null) {
            Log.d(TAG, "No user — local only")
            return
        }

        if (!syncManager.hasInternetConnection()) {
            Log.d(TAG, "No network — queued $operation $entityType")
            queueOperation(operation, entityType, entity)
            return
        }

        try {
            val response = call(entity)
            if (response.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                response.body()?.let { body -> onSuccess(body as T) }
                Log.d(TAG, "$operation $entityType synced")
            } else {
                Log.w(TAG, "Sync failed (${response.code()}) — queued")
                queueOperation(operation, entityType, entity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync exception: ${e.message} — queued")
            queueOperation(operation, entityType, entity)
        }
    }

    private suspend fun <T : Any> queueOperation(operation: String, entityType: String, entity: T) {
        val entityId = when (entity) {
            is Contact -> entity.id
            is ContactMethod -> entity.id
            else -> return
        }
        syncManager.queueOperation(operation, entityType, entityId, entity)
    }
}