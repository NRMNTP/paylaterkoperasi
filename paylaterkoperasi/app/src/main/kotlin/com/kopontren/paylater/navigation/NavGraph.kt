package com.kopontren.paylater.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// Skema rute sesuai SDD.md §8 — destinasi screen akan diisi modul demi modul.
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object SantriList : Screen("santri_list")
    object SantriDetail : Screen("santri_detail/{nama}") {
        fun createRoute(nama: String) = "santri_detail/$nama"
    }
    object TransaksiList : Screen("transaksi_list")
    object FormTransaksi : Screen("form_transaksi")
    object Tagihan : Screen("tagihan")
    object Laporan : Screen("laporan")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            // TODO: LoginScreen — belum diimplementasikan, menunggu konfirmasi modul.
            ScaffoldPlaceholder("Kerangka proyek siap — modul Login belum diimplementasikan")
        }
    }
}

@Composable
private fun ScaffoldPlaceholder(label: String) {
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(label)
        }
    }
}
