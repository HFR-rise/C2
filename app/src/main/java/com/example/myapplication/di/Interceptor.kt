package com.example.myapplication.di

import com.example.myapplication.utils.UserPreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val userPreferences: UserPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val userId = userPreferences.getUserId()
        val deviceId = userPreferences.getDeviceId()

        val request = chain.request().newBuilder()
            .apply {
                removeHeader("X-User-Id")
                removeHeader("X-Device-Id")
                if (!userId.isNullOrEmpty()) {
                    addHeader("X-User-Id", userId)
                }
                if (!deviceId.isNullOrEmpty()) {
                    addHeader("X-Device-Id", deviceId)
                }
            }
            .build()

        return chain.proceed(request)
    }
}