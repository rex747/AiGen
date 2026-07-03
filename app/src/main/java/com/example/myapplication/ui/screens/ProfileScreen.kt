package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val userBalance by viewModel.userBalance.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    // === ИСПРАВЛЕНИЕ: Принудительная загрузка профиля при входе на экран ===
    LaunchedEffect(Unit) {
        if (userProfile == null) {
            viewModel.loadProfile()
        }
        if (userBalance == 0.0) {
            viewModel.loadBalance()
        }
    }
    // ======================================================================

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Личный кабинет") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && userProfile == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Email пользователя
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Email",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = currentUser?.email ?: "Не указан",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // Баланс
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Баланс",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "%.2f ₽".format(userBalance),
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                    }

                    // Профиль
                    userProfile?.let { profile ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Информация о профиле",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Подписка: ${if (profile.isPremium) "Премиум" else "Базовая"}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Статус онбординга: ${if (profile.onboardingCompleted) "Пройден" else "Не пройден"}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Кнопки действий
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { /* TODO: Navigate to topup */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Пополнить")
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.logout()
                                onLoggedOut()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Выйти")
                        }
                    }
                }
            }
        }
    }
}