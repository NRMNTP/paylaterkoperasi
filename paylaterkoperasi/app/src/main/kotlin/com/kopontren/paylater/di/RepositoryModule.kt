package com.kopontren.paylater.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val SESSION_DATASTORE_NAME = "session_prefs"
private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = SESSION_DATASTORE_NAME)

// AuthRepository & SessionManager memakai @Inject constructor langsung (SDD.md §4.1) —
// satu-satunya yang butuh provider manual di sini adalah DataStore itu sendiri.
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideSessionDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.sessionDataStore
}
