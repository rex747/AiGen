package com.example.myapplication.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.model.Agent
import com.example.myapplication.viewmodel.MainViewModel
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvokeAgentScreen(
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit
) {
    val agents by viewModel.agentsCatalog.collectAsState()
    val selectedAgent by viewModel.selectedAgentForInvoke.collectAsState()
    val invokeResult by viewModel.invokeResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    var prompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState() // контроль состояния прокрутки

    LaunchedEffect(Unit) {
        viewModel.loadAgentsCatalog()
    }

    // автоматическая прокрутка к результату при его появлении
    LaunchedEffect(invokeResult, isLoading) {
        if (invokeResult != null && !isLoading) {
            // Небольшая задержка необходима для того, чтобы LazyColumn
            // успел пересчитать свой layoutInfo после добавления нового item
            kotlinx.coroutines.delay(100.milliseconds)
            // Прокручиваем к последнему элементу (результату)
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Вызвать агента") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        // ИСПРАВЛЕНИЕ: Заменяем Column с verticalScroll на LazyColumn
        // Это устраняет конфликт вложенной прокрутки (nested scrolling conflict)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Заголовок списка агентов
            item {
                Text(
                    text = "Выберите агента:",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Список агентов (теперь это часть основного LazyColumn)
            items(agents, key = { it.agentId }) { agent ->
                AgentRadioItem(
                    agent = agent,
                    isSelected = selectedAgent?.agentId == agent.agentId,
                    onSelect = { viewModel.setSelectedAgent(agent) }
                )
            }

            if (agents.isEmpty()) {
                item {
                    Text(
                        text = "Нет доступных агентов. Зарегистрируйте агента в каталоге.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Поле ввода запроса
            item {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Запрос к агенту") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }

            // Кнопка выполнения
            item {
                Button(
                    onClick = {
                        selectedAgent?.let { viewModel.invokeAgent(it.agentId, prompt) }
                    },
                    enabled = !isLoading && selectedAgent != null && prompt.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Выполняется...")
                    } else {
                        Text("Выполнить запрос")
                    }
                }
            }

            // Результат (теперь корректно прокручивается до самого конца)
            if (!invokeResult.isNullOrBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Результат:", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            // invokeResult теперь String, поэтому обращаемся к нему напрямую
                            Text(
                                text = invokeResult!!,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            // Блок с биллинговой информацией удален,
                            // так как сервер не передает skills_count и total_cost в ответе на /agent/invoke.
                            // Списание средств происходит на сервере, а новый баланс
                            // пользователь увидит в своем Личном кабинете (ProfileScreen).
                        }
                    }
                }
            }

            // Ошибки аутентификации
            if (authError != null) {
                item {
                    Text(
                        text = authError!!,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AgentRadioItem(
    agent: Agent,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Владелец: ${agent.ownerEmail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = agent.description.take(80),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
        }
    }
}