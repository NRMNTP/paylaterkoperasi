package com.kopontren.paylater.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// Pengganti TinyDB — SDD.md §3.4. Menyimpan sesi kasir yang login agar bertahan
// antar sesi aplikasi (SRS.md §5.1 AC4), sampai logout eksplisit memanggil clearSession().
class SessionManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val keyUsername = stringPreferencesKey("username")
    private val keyJabatan = stringPreferencesKey("jabatan")

    val sessionFlow: Flow<Session?> = dataStore.data.map { prefs ->
        val username = prefs[keyUsername] ?: return@map null
        val jabatan = prefs[keyJabatan] ?: ""
        Session(username, jabatan)
    }

    suspend fun saveSession(username: String, jabatan: String) {
        dataStore.edit {
            it[keyUsername] = username
            it[keyJabatan] = jabatan
        }
    }

    suspend fun clearSession() {
        dataStore.edit { it.clear() }
    }
}

data class Session(val username: String, val jabatan: String)
