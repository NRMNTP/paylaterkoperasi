package com.kopontren.paylater.data.remote.dto

import kotlinx.serialization.Serializable

// Bentuk JSON mentah dari endpoint login — lihat SRS.md §4.1.
// Response sukses: { "status": "success", "username": "...", "jabatan": "...", "message": "..." }
// Response gagal:  { "status": "error", "message": "Username atau password salah" }
@Serializable
data class LoginResponseDto(
    val status: String,
    val username: String? = null,
    val jabatan: String? = null,
    val message: String? = null
)
