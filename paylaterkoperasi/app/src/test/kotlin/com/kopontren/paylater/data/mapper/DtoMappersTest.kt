package com.kopontren.paylater.data.mapper

import com.kopontren.paylater.data.remote.dto.LoginResponseDto
import org.junit.Assert.assertEquals
import org.junit.Test

// SRS.md §4.1 — mapping response login DTO mentah ke domain Session.
class DtoMappersTest {

    @Test
    fun `response sukses dengan field lengkap - termapping benar`() {
        val dto = LoginResponseDto(
            status = "success",
            username = "Nurman",
            jabatan = "Pengurus",
            message = "Login berhasil"
        )

        val session = dto.toDomain()

        assertEquals("Nurman", session.username)
        assertEquals("Pengurus", session.jabatan)
    }

    @Test
    fun `field null - default ke string kosong, tidak crash`() {
        val dto = LoginResponseDto(status = "success", username = null, jabatan = null)

        val session = dto.toDomain()

        assertEquals("", session.username)
        assertEquals("", session.jabatan)
    }
}
