package com.example.myapplication.services

import android.content.Context
import android.util.Log
import com.example.myapplication.utils.NetworkUtils
import com.example.myapplication.utils.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val userPreferences: UserPreferences,
    private val webSocketService: WebSocketService,
    private val syncManager: SyncManager,
    private val networkUtils: NetworkUtils
) {
    private companion object {
        const val TAG = "NetworkMonitor"
        const val POLL_INTERVAL_MS = 3_000L
        const val MAX_RECONNECT_ATTEMPTS = 3
        const val RECONNECT_DELAY_BASE_MS = 1_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitoringJob: Job? = null

    @Volatile private var isMonitoring = false
    @Volatile private var wasOnline = false

    private val recoveryInProgress = AtomicBoolean(false)

    init {
        wasOnline = networkUtils.isOnline(context)
        startMonitoring()
    }

    private fun startMonitoring() {
        if (monitoringJob?.isActive == true) return

        isMonitoring = true
        Log.d(TAG, "Network monitoring started")

        monitoringJob = scope.launch {
            var consecutiveErrors = 0

            while (isMonitoring && isActive) {
                try {
                    val isOnline = networkUtils.isOnline(context)

                    when {
                        !wasOnline && isOnline -> {
                            Log.d(TAG, "Network recovered")
                            handleNetworkRecovery()
                            consecutiveErrors = 0
                        }
                        wasOnline && !isOnline -> {
                            Log.d(TAG, "Network lost")
                        }
                    }

                    wasOnline = isOnline

                    delay(POLL_INTERVAL_MS)

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    consecutiveErrors++
                    Log.e(TAG, "Monitor loop error (${consecutiveErrors}x): ${e.message}")
                    delay(minOf(consecutiveErrors * 2_000L, 30_000L))
                }
            }
        }
    }

    fun stopMonitoring() {
        if (!isMonitoring && monitoringJob == null) return
        Log.d(TAG, "Stopping network monitoring")
        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null
        wasOnline = false
        recoveryInProgress.set(false)
    }

    private fun handleNetworkRecovery() {
        if (!recoveryInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Recovery already in progress, skipping")
            return
        }

        scope.launch {
            try {
                val userId = userPreferences.getUserId()
                if (userId == null) {
                    Log.d(TAG, "User not logged in, skipping recovery")
                    return@launch
                }

                reconnectWebSocket(userId)
                syncManager.handleOnlineRecovery()
                Log.d(TAG, "Recovery completed")

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recovery failed: ${e.message}")
            } finally {
                recoveryInProgress.set(false)
            }
        }
    }

    private suspend fun reconnectWebSocket(userId: String) {
        if (webSocketService.isConnected()) {
            Log.d(TAG, "WebSocket already connected")
            return
        }

        repeat(MAX_RECONNECT_ATTEMPTS) { attempt ->
            val attemptNumber = attempt + 1
            Log.d(TAG, "WS reconnect attempt $attemptNumber/$MAX_RECONNECT_ATTEMPTS")

            webSocketService.disconnect()
            webSocketService.connect(userId)

            delay(RECONNECT_DELAY_BASE_MS * attemptNumber)

            if (webSocketService.isConnected()) {
                Log.d(TAG, "WS reconnected on attempt $attemptNumber")
                return
            }
        }

        Log.w(TAG, "WS reconnect failed after $MAX_RECONNECT_ATTEMPTS attempts")
    }

    fun hasInternetConnection(): Boolean = networkUtils.isOnline(context)
}