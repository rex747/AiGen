package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.SkillRepository
import com.example.myapplication.model.Agent
import com.example.myapplication.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAgentScreen(
    agent: Agent,
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit,
    onAgentUpdated: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val isLoading by viewModel.isLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()

    var name by remember { mutableStateOf(agent.name) }
    var description by remember { mutableStateOf(agent.description) }
    var selectedSkillIds by remember { mutableStateOf(agent.skills.toSet()) }

    val categories = remember { SkillRepository.getSkillCategories() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактировать агента") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя агента") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
            item {
                Text("Выберите навыки:", style = MaterialTheme.typography.titleMedium)
            }
            categories.forEach { category ->
                item {
                    Text(category.name, style = MaterialTheme.typography.titleSmall)
                }
                items(category.skills) { skill ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(skill.name, modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = selectedSkillIds.contains(skill.id),
                            onCheckedChange = { isChecked ->
                                selectedSkillIds = if (isChecked) {
                                    selectedSkillIds + skill.id
                                } else {
                                    selectedSkillIds - skill.id
                                }
                            }
                        )
                    }
                }
            }
            item {
                if (authError != null) {
                    Text(text = authError!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.updateAgent(agent.agentId, name, description, selectedSkillIds.toList())
                            scope.launch {
                                kotlinx.coroutines.delay(500.milliseconds)
                                onAgentUpdated()
                            }
                        }
                    },
                    enabled = !isLoading && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text("Сохранить изменения")
                }
            }
        }
    }
}