package com.kopontren.paylater.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kopontren.paylater.R
import com.kopontren.paylater.ui.components.FloatingCard
import com.kopontren.paylater.ui.components.GradientBackground
import com.kopontren.paylater.ui.theme.AbuTeks
import com.kopontren.paylater.ui.theme.HijauMuda
import com.kopontren.paylater.ui.theme.HijauUtama
import com.kopontren.paylater.ui.theme.MerahError

// DESIGN.md §3 — Login Screen. Card melayang di atas latar gradasi hijau.
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.loggedInSession) {
        if (uiState.loggedInSession != null) onLoginSuccess()
    }

    GradientBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_putih),
                    contentDescription = null,
                    modifier = Modifier.size(90.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text("PayLater Koperasi", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Koperasi Darul Ulum", fontSize = 13.sp, color = HijauMuda)
            }

            FloatingCard {
                Text("Selamat Datang 👋", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                Text("Username", fontSize = 13.sp, color = AbuTeks)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = viewModel::onUsernameChange,
                    placeholder = { Text("Masukkan username...") },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text("Password", fontSize = 13.sp, color = AbuTeks)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    placeholder = { Text("Masukkan password...") },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = viewModel::onLoginClicked,
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = HijauUtama),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("MASUK", fontWeight = FontWeight.Bold)
                    }
                }

                uiState.errorMessage?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(message, color = MerahError, fontSize = 13.sp)
                }
            }
        }
    }
}
