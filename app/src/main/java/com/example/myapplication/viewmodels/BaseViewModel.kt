package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _warningMessage = MutableStateFlow<String?>(null)
    val warningMessage: StateFlow<String?> = _warningMessage.asStateFlow()

    protected fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    protected fun setRefreshing(refreshing: Boolean) {
        _isRefreshing.value = refreshing
    }

    protected fun setError(message: String?) {
        _errorMessage.value = message
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun setWarning(message: String?) {
        _warningMessage.value = message
    }

    fun clearWarning() {
        _warningMessage.value = null
    }

    protected fun <T> safeLaunch(
        showLoading: Boolean = true,
        block: suspend () -> T,
        onSuccess: suspend (T) -> Unit = {},
        onError: suspend (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            clearError()
            if (showLoading) setLoading(true)

            try {
                val result = block()
                onSuccess(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(e.message ?: "Произошла ошибка")
                onError(e)
            } finally {
                if (showLoading) setLoading(false)
            }
        }
    }

    protected suspend fun <T> safeSuspend(
        block: suspend () -> T
    ): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setError(e.message ?: "Произошла ошибка")
            null
        }
    }
}