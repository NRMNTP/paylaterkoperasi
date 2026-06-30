package com.kopontren.paylater.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kopontren.paylater.data.local.Session
import com.kopontren.paylater.data.local.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Sementara — placeholder QA untuk verifikasi modul Login (sesi tersimpan + logout)
// sebelum modul Dashboard sungguhan (SRS.md §5.2) dibangun.
@HiltViewModel
class DashboardPlaceholderViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    val session: StateFlow<Session?> = sessionManager.sessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onLogoutClicked(onDone: () -> Unit) {
        viewModelScope.launch {
            sessionManager.clearSession()
            onDone()
        }
    }
}
