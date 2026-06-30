package com.kopontren.paylater.util

/**
 * Sentralisasi kredensial & URL produksi (lihat CLAUDE.md §5, SRS.md §9.1).
 * Jangan d-hardcode tersebar di banyak file — selalu rujuk konstanta ini.
 */
object Constants {
    const val BASE_URL =
        "https://script.google.com/macros/s/AKfycbxfQTXiiIBi3uvMNTVe93gwV8EYLyQC5D_UAycPN05iLXvCM4-3h2O2NGQPU6RYjOwl/exec"
}
