package com.kopontren.paylater.data.repository

import com.kopontren.paylater.data.local.Session
import com.kopontren.paylater.data.mapper.toDomain
import com.kopontren.paylater.data.remote.ApiService
import com.kopontren.paylater.data.remote.dto.ApiResult
import java.io.IOException
import javax.inject.Inject

// SDD.md §4.1 (pola umum repository) diterapkan untuk endpoint login (SRS.md §4.1).
class AuthRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun login(username: String, password: String): ApiResult<Session> = try {
        val response = api.login(username = username, password = password)
        if (response.status.equals("success", ignoreCase = true)) {
            ApiResult.Success(response.toDomain())
        } else {
            ApiResult.Error(response.message ?: "Username atau password salah")
        }
    } catch (e: IOException) {
        ApiResult.NetworkError(e)
    }
}
