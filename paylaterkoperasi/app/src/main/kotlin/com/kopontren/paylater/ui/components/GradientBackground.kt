package com.kopontren.paylater.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import com.kopontren.paylater.ui.theme.HijauGelap
import com.kopontren.paylater.ui.theme.HijauTerang
import com.kopontren.paylater.ui.theme.HijauUtama

// Pengganti bg_gradasi.png — SDD.md §6.1. Gradient native, bukan PNG statis.
@Composable
fun GradientBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(HijauGelap, HijauUtama, HijauTerang)
                )
            ),
        content = content
    )
}
