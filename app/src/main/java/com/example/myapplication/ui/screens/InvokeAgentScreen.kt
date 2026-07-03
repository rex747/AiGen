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
fun InvokeAgentScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val selectedAgent by viewModel.selectedAgentForInvoke.collectAsState()
    val invokeResult by viewModel.invokeResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    var prompt by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // === ИСПРАВЛЕНИЕ: Проверка статуса онбординга ===
    LaunchedEffect(Unit) {
        if (userProfile == null) {
            viewModel.loadProfile()
        }
    }
    // ================================================

    // Обработка ошибок
    LaunchedEffect(authError) {
        authError?.let { error ->
            errorMessage = error
            showErrorDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Вызов агента") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Информация об агенте
            selectedAgent?.let { agent ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = agent.name,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = agent.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } ?: run {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Агент не выбран",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Поле ввода промпта
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Запрос к агенту") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                maxLines = 10
            )

            // Кнопка вызова
            Button(
                onClick = {
                    // === ИСПРАВЛЕНИЕ: Проверка онбординга перед вызовом ===
                    if (userProfile?.onboardingCompleted != true) {
                        errorMessage = "Пожалуйста, завершите онбординг перед использованием агентов"
                        showErrorDialog = true
                        return@Button
                    }
                    // =====================================================

                    selectedAgent?.let { agent ->
                        viewModel.invokeAgent(agent.agentId, prompt)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && selectedAgent != null && prompt.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Выполнить")
            }

            // Результат выполнения
            invokeResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Результат",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.toString(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }

    // Диалог ошибок
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = {
                showErrorDialog = false
                viewModel.clearProfileActionError()
            },
            title = { Text("Ошибка") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showErrorDialog = false
                        viewModel.clearProfileActionError()
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}