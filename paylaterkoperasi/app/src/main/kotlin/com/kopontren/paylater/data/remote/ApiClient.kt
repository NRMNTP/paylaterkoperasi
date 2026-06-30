package com.kopontren.paylater.data.remote

import com.kopontren.paylater.BuildConfig
import com.kopontren.paylater.util.Constants
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

// Konfigurasi Retrofit/OkHttp, base URL — SDD.md §3.1, §2.3.
object ApiClient {
    fun createOkHttpClient(): OkHttpClient {
        // Level BODY hanya di debug build — endpoint login mengirim password lewat query
        // string, jadi logging penuh tidak boleh aktif di release (risiko kebocoran ke logcat).
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    fun createRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    fun createApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
