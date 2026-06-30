package com.kopontren.paylater.ui.login

import com.kopontren.paylater.data.local.Session

// SDD.md §5.1 — UiState immutable, di-expose via StateFlow, hanya ViewModel yang mengubahnya.
data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loggedInSession: Session? = null
)
