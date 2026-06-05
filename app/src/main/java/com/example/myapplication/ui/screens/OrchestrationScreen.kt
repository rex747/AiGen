package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.model.Agent
import com.example.myapplication.viewmodel.MainViewModel
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrchestrationScreen(
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit
) {
    val agents by viewModel.agentsCatalog.collectAsState()
    val chain by viewModel.orchestrateChain.collectAsState()
    val orchestrateResult by viewModel.orchestrateResult.collectAsState()
    val orchestrationStatus by viewModel.orchestrationStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var initialPrompt by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadAgentsCatalog()
        viewModel.clearChain()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Оркестрация агентов") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Button(
                    onClick = { viewModel.runOrchestration(initialPrompt) },
                    enabled = !isLoading && chain.isNotEmpty() && initialPrompt.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text("Запустить цепочку")
                }
            }
        }
    ) { paddingValues ->
        // Основной контейнер с вертикальной прокруткой для всего содержимого
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Левая панель: доступные агенты (LazyColumn с фиксированной высотой)
            Text("Доступные агенты", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
            ) {
                items(agents) { agent ->
                    AgentSelectionCard(
                        agent = agent,
                        isInChain = chain.contains(agent.agentId),
                        onAdd = { viewModel.addToChain(agent.agentId) }
                    )
                }
                if (agents.isEmpty()) {
                    item {
                        Text("Нет зарегистрированных агентов", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Правая панель: цепочка агентов
            Text("Цепочка агентов", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                items(chain) { agentId ->
                    val agent = agents.find { it.agentId == agentId }
                    if (agent != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(agent.name, style = MaterialTheme.typography.bodyLarge)
                                Text(agent.ownerEmail, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.removeFromChain(agentId) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                            }
                        }
                        HorizontalDivider()
                    }
                }
                if (chain.isEmpty()) {
                    item {
                        Text("Цепочка пуста. Добавьте агентов из списка слева.")
                    }
                }
            }

            // Начальный запрос
            OutlinedTextField(
                value = initialPrompt,
                onValueChange = { initialPrompt = it },
                label = { Text("Начальный запрос") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5
            )

            // Статус выполнения (опционально)
            if (orchestrationStatus == "pending") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выполняется оркестрация...")
                }
            }

            // РЕЗУЛЬТАТ ОРКЕСТРАЦИИ С ПРОКРУТКОЙ
            if (orchestrateResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Результат оркестрации:", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        // Прокручиваемая область для длинного текста
                        Text(
                            text = orchestrateResult!!,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }

            if (viewModel.authError.collectAsState().value != null) {
                Text(viewModel.authError.collectAsState().value!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AgentSelectionCard(agent: Agent, isInChain: Boolean, onAdd: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(agent.name, style = MaterialTheme.typography.titleMedium)
                Text(agent.description.take(60), style = MaterialTheme.typography.bodySmall)
                Text(agent.ownerEmail, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onAdd,
                enabled = !isInChain
            ) {
                Text(if (isInChain) "Добавлен" else "Добавить")
            }
        }
    }
}