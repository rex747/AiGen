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
import com.example.myapplication.data.SkillRepository
import com.example.myapplication.model.Skill
import com.example.myapplication.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillCategoriesScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGenerated: () -> Unit
) {
    val categories = remember { SkillRepository.getSkillCategories() }
    val selectedSkillIds by viewModel.extraSkillIds.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выберите навыки") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Button(
                    onClick = {
                        viewModel.generateCode()
                        onNavigateToGenerated()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Добавить навык агенту")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            categories.forEach { category ->
                item {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(category.skills) { skill ->
                    val isChecked = selectedSkillIds.contains(skill.id)
                    ListItem(
                        headlineContent = { Text(skill.name) },
                        supportingContent = { Text(skill.description) },
                        leadingContent = {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.toggleExtraSkill(skill.id) }
                            )
                        }
                    )
                }
            }
        }
    }
}