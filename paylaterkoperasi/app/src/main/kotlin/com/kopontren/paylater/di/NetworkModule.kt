package com.kopontren.paylater.di

import com.kopontren.paylater.data.remote.ApiClient
import com.kopontren.paylater.data.remote.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

// Hilt — provide Retrofit, OkHttp (SDD.md §2.3).
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = ApiClient.createOkHttpClient()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = ApiClient.createRetrofit(okHttpClient)

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = ApiClient.createApiService(retrofit)
}
