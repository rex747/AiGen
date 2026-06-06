package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.model.Agent
import com.example.myapplication.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAgentsScreen(
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit,
    onEditAgent: (Agent) -> Unit
) {
    val myAgents by viewModel.myAgents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadMyAgents()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои агенты") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                isLoading && myAgents.isEmpty() -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                authError != null -> {
                    Text(
                        text = "Ошибка: $authError",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                myAgents.isEmpty() -> Text(
                    text = "У вас пока нет созданных агентов",
                    modifier = Modifier.padding(16.dp)
                )
                else -> {
                    LazyColumn {
                        items(myAgents) { agent ->
                            MyAgentCard(
                                agent = agent,
                                onEditClick = { onEditAgent(agent) },
                                onDeleteClick = { viewModel.deleteAgent(agent.agentId) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyAgentCard(
    agent: Agent,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = agent.name, style = MaterialTheme.typography.titleLarge)
            Text(text = agent.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Навыки: ${agent.skills.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}