package com.kopontren.paylater.data.remote

import com.kopontren.paylater.data.remote.dto.LoginResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

// Kontrak Retrofit — satu method per endpoint (SDD.md §3.1).
// Hanya endpoint yang modulnya sudah diimplementasikan yang didefinisikan di sini;
// endpoint lain ditambah saat modul terkait dikerjakan (CLAUDE.md §2.6).
interface ApiService {
    @GET(".")
    suspend fun login(
        @Query("action") action: String = "login",
        @Query("username") username: String,
        @Query("password") password: String
    ): LoginResponseDto
}
