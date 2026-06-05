package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.model.Agent
import com.example.myapplication.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentCatalogScreen(
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit,
    onAgentSelected: (Agent) -> Unit = {}
) {
    val agents by viewModel.agentsCatalog.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAgentsCatalog()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Каталог агентов") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                authError != null -> {
                    Text(
                        text = "Ошибка: $authError",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                agents.isEmpty() -> Text(
                    text = "Нет зарегистрированных агентов",
                    modifier = Modifier.padding(16.dp)
                )
                else -> {
                    LazyColumn {
                        items(agents) { agent ->
                            AgentCard(agent = agent, onCardClick = { onAgentSelected(agent) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentCard(agent: Agent, onCardClick: () -> Unit) {
    Card(
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = agent.name, style = MaterialTheme.typography.titleLarge)
            Text(text = agent.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Владелец: ${agent.ownerEmail}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Навыки: ${agent.skills.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}