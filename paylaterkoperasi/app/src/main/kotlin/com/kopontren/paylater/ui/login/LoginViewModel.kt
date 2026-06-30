package com.kopontren.paylater.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kopontren.paylater.data.local.SessionManager
import com.kopontren.paylater.data.remote.dto.ApiResult
import com.kopontren.paylater.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// SDD.md §7.1 — alur login: validasi lokal -> AuthRepository.login() -> saveSession() -> navigasi.
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // SRS.md §5.1 AC4 — sesi tetap login setelah app ditutup-buka kembali.
        viewModelScope.launch {
            sessionManager.sessionFlow.firstOrNull()?.let { session ->
                _uiState.update { it.copy(loggedInSession = session) }
            }
        }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun onLoginClicked() {
        val state = _uiState.value
        val validationError = validateLoginInput(state.username, state.password)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.login(state.username.trim(), state.password)) {
                is ApiResult.Success -> {
                    sessionManager.saveSession(result.data.username, result.data.jabatan)
                    _uiState.update { it.copy(isLoading = false, loggedInSession = result.data) }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Tidak dapat terhubung ke server. Periksa koneksi internet.")
                }
            }
        }
    }
}

// Validasi lokal sebelum request dikirim (SRS.md §5.1 AC3) — fungsi murni, mudah diuji
// tanpa coroutine/DI (CLAUDE.md §3.6: validasi form ditulis bersamaan dengan implementasi).
internal fun validateLoginInput(username: String, password: String): String? {
    if (username.isBlank() || password.isBlank()) return "Username dan password wajib diisi"
    return null
}
