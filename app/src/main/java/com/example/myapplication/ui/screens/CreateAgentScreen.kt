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
import com.example.myapplication.data.SkillRepository
import com.example.myapplication.model.Skill
import com.example.myapplication.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAgentScreen(
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit,
    onAgentCreated: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val isLoading by viewModel.isLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedSkillIds by remember { mutableStateOf(setOf<String>()) }

    val categories = remember { SkillRepository.getSkillCategories() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Создать агента") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
                            viewModel.registerAgent(name, description, selectedSkillIds.toList())
                            scope.launch {
                                // Ждём успешной регистрации (можно через SharedFlow, но для простоты задержка)
                                kotlinx.coroutines.delay(500)
                                onAgentCreated()
                            }
                        }
                    },
                    enabled = !isLoading && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text("Зарегистрировать агента")
                }
            }
        }
    }
}
