package com.kopontren.paylater.ui.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// SRS.md §5.1 AC3 — submit form kosong wajib dicegah di sisi klien sebelum request API.
class LoginValidationTest {

    @Test
    fun `username dan password terisi - tidak ada error`() {
        assertNull(validateLoginInput("kasir1", "rahasia"))
    }

    @Test
    fun `username kosong - error`() {
        assertEquals("Username dan password wajib diisi", validateLoginInput("", "rahasia"))
    }

    @Test
    fun `password kosong - error`() {
        assertEquals("Username dan password wajib diisi", validateLoginInput("kasir1", ""))
    }

    @Test
    fun `username hanya whitespace - dianggap kosong`() {
        assertEquals("Username dan password wajib diisi", validateLoginInput("   ", "rahasia"))
    }

    @Test
    fun `keduanya kosong - error`() {
        assertEquals("Username dan password wajib diisi", validateLoginInput("", ""))
    }
}
