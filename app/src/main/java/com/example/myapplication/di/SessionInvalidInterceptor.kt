package com.example.myapplication.di

import android.util.Log
import com.example.myapplication.services.SyncManager
import com.example.myapplication.services.WebSocketService
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SessionInvalidInterceptor @Inject constructor(
    private val webSocketServiceProvider: Provider<WebSocketService>,
    private val syncManagerProvider: Provider<SyncManager>
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code == 401) {
            Log.w("SessionInvalidInterceptor", "401 — session invalid, forcing logout")
            runCatching { webSocketServiceProvider.get().onForceLogout?.invoke() }
            runCatching { syncManagerProvider.get().onForceLogout?.invoke() }
        }

        return response
    }
}