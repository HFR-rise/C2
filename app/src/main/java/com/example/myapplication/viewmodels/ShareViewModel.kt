package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.services.ShareException
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.PhoneUtils
import com.example.myapplication.utils.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val syncManager: SyncManager,
    private val userPreferences: UserPreferences
) : BaseViewModel() {

    private companion object {
        const val TAG = "ShareViewModel"
        const val MIN_PHONE_LENGTH = 11
    }

    private val _sharingState = MutableStateFlow<SharingState>(SharingState.Idle)
    val sharingState: StateFlow<SharingState> = _sharingState.asStateFlow()

    private var shareJob: Job? = null

    fun shareProjectWithContact(
        projectId: String,
        contact: Contact,
        contactMethods: List<ContactMethod>
    ) {
        if (shareJob?.isActive == true) {
            Log.d(TAG, "Share already in progress, skipping")
            return
        }

        shareJob = viewModelScope.launch {
            _sharingState.value = SharingState.Loading

            try {
                val phoneNumber = extractPhoneNumber(contact, contactMethods)
                    ?: throw ShareException.InvalidPhone()

                val userId = userPreferences.getUserId()
                    ?: throw ShareException.NoPermission()

                Log.d(TAG, "Sharing project $projectId with phone: ${maskPhone(phoneNumber)}")

                val result = syncManager.shareProject(projectId, phoneNumber)

                result.fold(
                    onSuccess = {
                        Log.d(TAG, "✅ Project shared")
                        _sharingState.value = SharingState.Success(contact)
                    },
                    onFailure = { e ->
                        val errorType = when (e) {
                            is ShareException.UserNotFound -> ShareErrorType.USER_NOT_FOUND
                            is ShareException.InvalidPhone -> ShareErrorType.INVALID_PHONE
                            is ShareException.NoPermission -> ShareErrorType.NO_PERMISSION
                            else -> ShareErrorType.OTHER
                        }
                        Log.e(TAG, "Share failed: ${e.message}")
                        _sharingState.value = SharingState.Error(errorType, e.message)
                    }
                )

            } catch (e: ShareException.UserNotFound) {
                _sharingState.value = SharingState.Error(ShareErrorType.USER_NOT_FOUND, e.message)
            } catch (e: ShareException.InvalidPhone) {
                _sharingState.value = SharingState.Error(ShareErrorType.NO_PHONE, "У контакта нет номера телефона")
            } catch (e: ShareException.NoPermission) {
                _sharingState.value = SharingState.Error(ShareErrorType.OTHER, "Пользователь не авторизован")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error: ${e.message}", e)
                _sharingState.value = SharingState.Error(ShareErrorType.OTHER, "Ошибка: ${e.message}")
            }
        }
    }

    private fun extractPhoneNumber(
        contact: Contact,
        methods: List<ContactMethod>
    ): String? {
        val phoneMethod = methods
            .filter { it.contactId == contact.id }
            .firstOrNull { isPhoneMethod(it) }
            ?: return null

        val normalized = PhoneUtils.normalize(phoneMethod.value)
        return normalized.takeIf { it.length >= MIN_PHONE_LENGTH }
    }

    private fun isPhoneMethod(method: ContactMethod): Boolean {
        val type = method.methodType.lowercase()
        return type.contains("телефон") || type.contains("phone")
    }

    private fun maskPhone(phone: String): String =
        if (phone.length > 4) phone.take(phone.length - 4) + "****" else "****"

    fun resetState() {
        shareJob?.cancel()
        shareJob = null
        _sharingState.value = SharingState.Idle
    }
}

sealed class SharingState {
    data object Idle : SharingState()
    data object Loading : SharingState()
    data class Success(val contact: Contact) : SharingState()

    data class Error(
        val type: ShareErrorType,
        val message: String?
    ) : SharingState()
}

enum class ShareErrorType {
    NO_PHONE,
    INVALID_PHONE,
    UNAUTHORIZED,
    USER_NOT_FOUND,
    NO_PERMISSION,
    OTHER
}