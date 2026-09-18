package com.example.myapplication.services

import android.content.Context
import android.util.Log
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.database.ContactDao
import com.example.myapplication.data.database.ContactMethodDao
import com.example.myapplication.data.database.MaterialDao
import com.example.myapplication.data.database.ObjectDao
import com.example.myapplication.data.database.ProjectDao
import com.example.myapplication.data.database.WorkItemDao
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.SyncMessage
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.utils.NetworkUtils
import com.example.myapplication.utils.UserPreferences
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class WebSocketService @Inject constructor(
    private val userPreferences: UserPreferences,
    private val projectDao: ProjectDao,
    private val materialDao: MaterialDao,
    private val workItemDao: WorkItemDao,
    private val contactDao: ContactDao,
    private val contactMethodDao: ContactMethodDao,
    private val objectDao: ObjectDao,
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    @Named("websocket") private val okHttpClient: OkHttpClient,
    private val networkUtils: NetworkUtils
) {
    private companion object {
        const val TAG = "WebSocketService"
        const val MAX_RECONNECT_ATTEMPTS = 20
        const val RECONNECT_BASE_DELAY_MS = 2_000L
        const val MAX_RECONNECT_DELAY_MS = 60_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null

    @Volatile private var isConnected = false
    @Volatile private var isReconnecting = false
    @Volatile private var currentUserId: String? = null

    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    private val reconnectGuard = AtomicBoolean(false)

    var onForceLogout: (() -> Unit)? = null

    fun connect(userId: String) {
        if (userId.isEmpty()) {
            Log.w(TAG, "connect: empty userId")
            return
        }

        if (isConnected) {
            Log.d(TAG, "Already connected, reconnecting")
            disconnect()
        }

        val deviceId = userPreferences.getDeviceId()
        if (deviceId.isNullOrEmpty()) {
            Log.w(TAG, "connect: no deviceId")
            return
        }

        currentUserId = userId
        Log.d(TAG, "Connecting WebSocket for user=$userId device=$deviceId")

        val url = "ws://192.168.0.109:8080/ws/estimates?userId=$userId&deviceId=$deviceId"

        webSocket = okHttpClient.newWebSocket(
            Request.Builder().url(url).build(),
            createListener(userId)
        )
    }

    private fun createListener(userId: String) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket opened for user=$userId")
            isConnected = true
            reconnectAttempts = 0
            isReconnecting = false
            reconnectGuard.set(false)
            reconnectJob?.cancel()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text == "ping") {
                webSocket.send("pong")
                return
            }
            handleSyncMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "WebSocket failure: ${t.message}")
            isConnected = false
            currentUserId?.let { startReconnect(it) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $reason (code=$code)")
            isConnected = false

            if (code == 1000) {
                reconnectAttempts = 0
                isReconnecting = false
                reconnectGuard.set(false)
            } else {
                currentUserId?.let { startReconnect(it) }
            }
        }
    }

    private fun startReconnect(userId: String) {
        if (!reconnectGuard.compareAndSet(false, true)) {
            Log.d(TAG, "Reconnect already scheduled")
            return
        }

        isReconnecting = true
        reconnectJob?.cancel()

        reconnectJob = scope.launch {
            try {
                while (isActive && !isConnected && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++

                    val delayMs = computeBackoffDelay(reconnectAttempts)
                    Log.d(TAG, "Reconnect attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS in ${delayMs}ms")
                    delay(delayMs)

                    if (isConnected) break
                    if (!networkUtils.isOnline(context)) {
                        Log.d(TAG, "No network, waiting...")
                        continue
                    }

                    Log.d(TAG, "Attempting reconnect")
                    disconnect()
                    connect(userId)
                }

                if (!isConnected) {
                    Log.w(TAG, "Max reconnect attempts reached, resetting counter")
                    reconnectAttempts = MAX_RECONNECT_ATTEMPTS / 2
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                isReconnecting = false
                reconnectGuard.set(false)
            }
        }
    }

    private fun computeBackoffDelay(attempt: Int): Long {
        val shift = minOf(attempt - 1, 5)
        return minOf(RECONNECT_BASE_DELAY_MS * (1L shl shift), MAX_RECONNECT_DELAY_MS)
    }

    private fun handleSyncMessage(message: String) {
        val syncMessage = runCatching { gson.fromJson(message, SyncMessage::class.java) }
            .getOrElse {
                Log.e(TAG, "Failed to parse message: ${it.message}")
                return
            }

        Log.d(TAG, "Message type=${syncMessage.type} entity=${syncMessage.entityType}")

        when (syncMessage.type) {
            "FORCE_LOGOUT" -> handleForceLogout()
            "CREATE", "UPDATE", "SHARE", "SHARE_ACCEPTED", "SHARE_DECLINED" ->
                handleDataMessage(syncMessage)
            "DELETE" -> handleDeleteMessage(syncMessage)
            else -> Log.w(TAG, "Unknown message type: ${syncMessage.type}")
        }
    }

    private fun handleForceLogout() {
        Log.w(TAG, "FORCE_LOGOUT received")
        scope.launch {
            runCatching {
                clearAllLocalData()
                disconnect()
            }.onFailure { Log.e(TAG, "ForceLogout cleanup failed: ${it.message}") }

            onForceLogout?.invoke()
        }
    }

    private fun handleDataMessage(syncMessage: SyncMessage) {
        val data = syncMessage.data ?: run {
            Log.w(TAG, "Message data is null")
            return
        }
        if (!data.isJsonObject) {
            Log.w(TAG, "Message data is not a JSON object: ${data.javaClass.simpleName}")
            return
        }

        val json = data.asJsonObject.toString()

        scope.launch {
            runCatching {
                when (syncMessage.entityType) {
                    "PROJECT" -> upsertProject(gson.fromJson(json, Project::class.java))
                    "OBJECT" -> upsertObject(gson.fromJson(json, ObjectModel::class.java))
                    "CONTACT" -> upsertContact(gson.fromJson(json, Contact::class.java))
                    "CONTACT_METHOD" -> upsertContactMethod(gson.fromJson(json, ContactMethod::class.java))
                    "MATERIAL" -> upsertMaterial(gson.fromJson(json, Material::class.java))
                    "WORK_ITEM" -> upsertWorkItem(gson.fromJson(json, WorkItem::class.java))
                    else -> Log.w(TAG, "Unknown entity: ${syncMessage.entityType}")
                }
            }.onFailure { Log.e(TAG, "handleDataMessage failed: ${it.message}") }
        }
    }

    private fun handleDeleteMessage(syncMessage: SyncMessage) {
        val entityId = syncMessage.entityId
        if (entityId.isNullOrEmpty()) {
            Log.w(TAG, "Delete message without entityId")
            return
        }

        scope.launch {
            runCatching {
                when (syncMessage.entityType) {
                    "PROJECT" -> projectDao.deleteProjectById(entityId)
                    "MATERIAL" -> materialDao.deleteMaterialById(entityId)
                    "WORK_ITEM" -> workItemDao.deleteWorkItemById(entityId)
                    "CONTACT" -> contactDao.deleteContactById(entityId)
                    "CONTACT_METHOD" -> contactMethodDao.deleteContactMethodById(entityId)
                    "OBJECT" -> objectDao.deleteObjectById(entityId)
                    else -> Log.w(TAG, "Unknown entity for delete: ${syncMessage.entityType}")
                }
            }.onFailure { Log.e(TAG, "handleDeleteMessage failed: ${it.message}") }
        }
    }

    private suspend fun upsertProject(project: Project) {
        if (projectDao.getProjectById(project.id) == null) projectDao.insertProject(project)
        else projectDao.updateProject(project)
    }

    private suspend fun upsertObject(obj: ObjectModel) {
        if (objectDao.getObjectById(obj.id) == null) objectDao.insertObject(obj)
        else objectDao.updateObject(obj)
    }

    private suspend fun upsertContact(contact: Contact) {
        if (contactDao.getContactById(contact.id) == null) contactDao.insertContact(contact)
        else contactDao.updateContact(contact)
    }

    private suspend fun upsertContactMethod(method: ContactMethod) {
        if (contactMethodDao.getContactMethodById(method.id) == null) contactMethodDao.insertContactMethod(method)
        else contactMethodDao.updateContactMethod(method)
    }

    private suspend fun upsertMaterial(material: Material) {
        if (materialDao.getMaterialById(material.id) == null) materialDao.insertMaterial(material)
        else materialDao.updateMaterial(material)
    }

    private suspend fun upsertWorkItem(workItem: WorkItem) {
        if (workItemDao.getWorkItemById(workItem.id) == null) workItemDao.insertWorkItem(workItem)
        else workItemDao.updateWorkItem(workItem)
    }

    private suspend fun clearAllLocalData() {
        Log.d(TAG, "Clearing local data (FORCE_LOGOUT)")
        projectDao.deleteAll()
        objectDao.deleteAll()
        contactDao.deleteAll()
        contactMethodDao.deleteAll()
        materialDao.deleteAll()
        workItemDao.deleteAll()
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectGuard.set(false)
        isReconnecting = false
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
        reconnectAttempts = 0
    }

    fun isConnected(): Boolean = isConnected
}