package com.kopontren.paylater.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kopontren.paylater.ui.theme.AbuTeks

// Placeholder QA — lihat catatan di DashboardPlaceholderViewModel.kt. Digantikan
// modul Dashboard sungguhan (SRS.md §5.2) saat dikerjakan.
@Composable
fun DashboardPlaceholderScreen(
    onLoggedOut: () -> Unit,
    viewModel: DashboardPlaceholderViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Login berhasil", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Kasir: ${session?.username.orEmpty()}")
            Text("Jabatan: ${session?.jabatan.orEmpty()}")
            Spacer(Modifier.height(24.dp))
            Text(
                "Modul Dashboard belum diimplementasikan — lihat SRS.md §5.2",
                color = AbuTeks,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { viewModel.onLogoutClicked(onLoggedOut) }) {
                Text("Logout (uji sesi)")
            }
        }
    }
}
