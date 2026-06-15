package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.viewmodel.MainViewModel
import com.example.myapplication.viewmodel.OnboardingViewModel
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

/**
 * Экран регистрации для активации демо-версии
 * После регистрации автоматически активирует 3-дневный пробный период
 */
@Composable
fun RegisterForDemoScreen(
    mainViewModel: MainViewModel,
    onboardingViewModel: OnboardingViewModel,
    onDemoActivated: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val isLoading by mainViewModel.isLoading.collectAsState()
    val authError by mainViewModel.authError.collectAsState()
    val currentUser by mainViewModel.currentUser.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // После регистрации - сохраняем данные онбординга и активируем демо
    LaunchedEffect(currentUser) {
        if (currentUser != null && isRegistering) {
            isRegistering = false
            val token = currentUser?.token ?: ""

            // Сохраняем данные онбординга
            onboardingViewModel.completeOnboarding(token)

            // Активируем демо-версию
            onboardingViewModel.activateDemoVersion(token) { success, message ->
                if (success) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    onDemoActivated()
                } else {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Активация демо-версии",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Зарегистрируйтесь, чтобы получить 3 дня бесплатного доступа",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (authError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ошибка: $authError",
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isRegistering = true
                mainViewModel.register(email.trim(), password)
            },
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = "Зарегистрироваться и активировать демо",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateBack) {
            Text("Назад")
        }
    }
}