package com.kopontren.paylater.data.remote.dto

// SDD.md §3.3 — semua Repository membungkus hasil panggilan API ke tipe ini.
// UI Layer tidak pernah menangani exception mentah (CLAUDE.md §3.5).
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
    data class NetworkError(val throwable: Throwable) : ApiResult<Nothing>()
}
