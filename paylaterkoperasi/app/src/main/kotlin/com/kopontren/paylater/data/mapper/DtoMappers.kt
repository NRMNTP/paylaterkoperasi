package com.kopontren.paylater.data.mapper

import com.kopontren.paylater.data.local.Session
import com.kopontren.paylater.data.remote.dto.LoginResponseDto

// DTO -> Domain Model, mapping eksplisit (CLAUDE.md §3.3 / SDD.md §5.1).
fun LoginResponseDto.toDomain(): Session = Session(
    username = username.orEmpty(),
    jabatan = jabatan.orEmpty()
)
