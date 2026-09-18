package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.SendCodeRequest
import com.example.myapplication.data.models.UserResponse
import com.example.myapplication.data.models.VerifyCodeRequest
import com.example.myapplication.network.ApiService
import com.example.myapplication.services.SyncManager
import com.example.myapplication.services.WebSocketService
import com.example.myapplication.utils.UserPreferences
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val apiService: ApiService,
    private val userPreferences: UserPreferences,
    private val webSocketService: WebSocketService,
    private val syncManager: SyncManager,
    private val gson: Gson
) : BaseViewModel() {

    private companion object {
        const val TAG = "AuthViewModel"
        const val MIN_CODE_LENGTH = 4
    }

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _verificationCode = MutableStateFlow("")
    val verificationCode: StateFlow<String> = _verificationCode.asStateFlow()

    private val _isCodeSent = MutableStateFlow(false)
    val isCodeSent: StateFlow<Boolean> = _isCodeSent.asStateFlow()

    private val _currentUser = MutableStateFlow<UserResponse?>(null)
    val currentUser: StateFlow<UserResponse?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _showAccountInUseError = MutableStateFlow(false)
    val showAccountInUseError: StateFlow<Boolean> = _showAccountInUseError.asStateFlow()

    init {
        setupForceLogoutCallbacks()
        restoreSessionIfLoggedIn()
    }

    override fun onCleared() {
        super.onCleared()
        syncManager.onForceLogout = null
        webSocketService.onForceLogout = null
        Log.d(TAG, "Callbacks removed")
    }

    private fun setupForceLogoutCallbacks() {
        syncManager.onForceLogout = {
            Log.w(TAG, "Force logout from SyncManager")
            viewModelScope.launch(Dispatchers.Main) { forceLogout() }
        }
        webSocketService.onForceLogout = {
            Log.w(TAG, "FORCE_LOGOUT from server")
            viewModelScope.launch(Dispatchers.Main) { forceLogout() }
        }
    }

    private fun restoreSessionIfLoggedIn() {
        val savedUserId = userPreferences.getUserId() ?: return
        if (!userPreferences.isLoggedIn()) return

        viewModelScope.launch {
            val isValid = try {
                syncManager.checkCurrentSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Session check failed: ${e.message}", e)
                false
            }

            if (!isValid) {
                Log.w(TAG, "Session invalid on restore — forcing logout")
                forceLogout()
                return@launch
            }

            try {
                syncManager.syncDataFromServer(savedUserId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Initial sync failed: ${e.message}", e)
            }

            try {
                webSocketService.connect(savedUserId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "WS connect failed: ${e.message}", e)
            }

            _isLoggedIn.value = true
        }
    }

    fun updatePhoneNumber(value: String) {
        _phoneNumber.value = value
        clearAllErrors()
    }

    fun updateVerificationCode(value: String) {
        _verificationCode.value = value
        clearAllErrors()
    }

    fun clearAccountInUseError() {
        _showAccountInUseError.value = false
    }

    private fun clearAllErrors() {
        setError(null)
        _showAccountInUseError.value = false
    }

    fun sendCode() {
        if (_phoneNumber.value.isBlank()) return

        safeLaunch(
            block = { apiService.sendCode(SendCodeRequest(_phoneNumber.value)) },
            onSuccess = { response ->
                if (response.isSuccessful) {
                    _isCodeSent.value = true
                    Log.d(TAG, "Code sent to ${_phoneNumber.value}")
                } else {
                    handleSendCodeError(response.code())
                }
            },
            onError = { e ->
                setError("Ошибка сети: ${e.message}")
            }
        )
    }

    private fun handleSendCodeError(code: Int) {
        setError(
            when (code) {
                400 -> "Неверный формат номера телефона"
                429 -> "Слишком много попыток. Попробуйте позже."
                else -> "Ошибка отправки кода ($code)"
            }
        )
        Log.e(TAG, "Send code failed: $code")
    }

    fun verifyCode() {
        if (_verificationCode.value.length < MIN_CODE_LENGTH) return

        safeLaunch(
            block = {
                val deviceId = getOrCreateDeviceId()
                val request = VerifyCodeRequest(
                    phoneNumber = _phoneNumber.value,
                    code = _verificationCode.value,
                    deviceId = deviceId
                )
                apiService.verifyCode(request) to deviceId
            },
            onSuccess = { (response, deviceId) ->
                handleVerifyResponse(response, deviceId)
            },
            onError = { e ->
                setError("Ошибка сети: ${e.message}")
            }
        )
    }

    private suspend fun handleVerifyResponse(
        response: Response<UserResponse>,
        deviceId: String
    ) {
        if (response.isSuccessful) {
            val user = response.body()
            if (user != null) {
                onLoginSuccess(user, deviceId)
            } else {
                setError("Сервер вернул пустой ответ")
            }
            return
        }

        when (response.code()) {
            409 -> handleConflictError(response)
            400 -> setError("Неверный или просроченный код подтверждения")
            404 -> setError("Пользователь не найден")
            else -> setError("Ошибка: ${response.code()} ${response.message()}")
        }
    }

    private suspend fun onLoginSuccess(user: UserResponse, deviceId: String) {
        _currentUser.value = user

        runCatching { syncManager.clearAllLocalData() }
            .onFailure { Log.e(TAG, "Local cleanup failed: ${it.message}") }

        userPreferences.apply {
            saveUserId(user.id)
            savePhoneNumber(user.phoneNumber)
            setLoggedIn(true)
            saveDeviceId(deviceId)
        }

        _showAccountInUseError.value = false
        _isLoggedIn.value = true

        viewModelScope.launch {
            try {
                syncManager.syncDataFromServer(user.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Initial sync failed: ${e.message}", e)
            }

            try {
                webSocketService.connect(user.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "WS connect failed: ${e.message}", e)
            }
        }

        Log.d(TAG, "✅ User logged in: ${user.id}")
    }

    private fun handleConflictError(response: Response<UserResponse>) {
        setError(parseErrorMessage(response) ?: "Аккаунт уже используется на другом устройстве")
        _showAccountInUseError.value = true
        _verificationCode.value = ""
        Log.w(TAG, "Login rejected — account in use")
    }

    private fun parseErrorMessage(response: Response<*>): String? {
        val body = response.errorBody()?.string() ?: return null
        return runCatching {
            gson.fromJson(body, ApiError::class.java)?.error?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private data class ApiError(val error: String?)

    fun resetToPhoneInput() {
        _isCodeSent.value = false
        _verificationCode.value = ""
        clearAllErrors()
    }

    fun retryWithSamePhone() {
        _verificationCode.value = ""
        clearAllErrors()
    }

    fun logout() = performLogout(sendToServer = true)

    fun forceLogout() = performLogout(sendToServer = false)

    private fun performLogout(sendToServer: Boolean) {
        viewModelScope.launch {
            val userId = userPreferences.getUserId()
            if (userId == null) {
                Log.w(TAG, "No user ID — skipping server logout")
                performLocalLogout()
                return@launch
            }

            Log.d(TAG, "Logout (sendToServer=$sendToServer) for $userId")

            if (sendToServer && syncManager.hasInternetConnection()) {
                runCatching { apiService.logout() }
                    .onFailure { Log.e(TAG, "Server logout failed: ${it.message}") }
            }

            performLocalLogout()
        }
    }

    private suspend fun performLocalLogout() {
        runCatching { syncManager.stopPeriodicSync() }
            .onFailure { Log.e(TAG, "Stop periodic sync failed: ${it.message}") }

        runCatching { webSocketService.disconnect() }
            .onFailure { Log.e(TAG, "WS disconnect failed: ${it.message}") }

        runCatching { syncManager.clearAllLocalData() }
            .onFailure { Log.e(TAG, "Local data clear failed: ${it.message}") }

        runCatching { userPreferences.clear() }
            .onFailure { Log.e(TAG, "Prefs clear failed: ${it.message}") }

        resetAllStates()
        Log.d(TAG, "✅ Logout complete")
    }

    private fun resetAllStates() {
        _currentUser.value = null
        _isCodeSent.value = false
        _phoneNumber.value = ""
        _verificationCode.value = ""
        _showAccountInUseError.value = false
        setError(null)
        _isLoggedIn.value = false
    }

    private fun getOrCreateDeviceId(): String =
        userPreferences.getDeviceId() ?: UUID.randomUUID().toString().also {
            userPreferences.saveDeviceId(it)
            Log.d(TAG, "Created deviceId: $it")
        }
}