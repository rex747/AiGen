package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val selectedSkills by viewModel.selectedSkills.collectAsState()
    val premium by viewModel.isPremium.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выбор навыков") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(viewModel.availableSkills, key = { it.id }) { skill ->
                val isSelected = selectedSkills.any { it.id == skill.id }
                // Бесплатные навыки доступны всегда, премиум – только если premium == true
                val enabled = !skill.isPremium || premium

                ListItem(
                    headlineContent = { Text(skill.name) },
                    supportingContent = { Text(skill.description) },
                    leadingContent = {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { viewModel.toggleSkill(skill) },
                            enabled = enabled
                        )
                    },
                    trailingContent = {
                        if (skill.isPremium && !premium)
                            Icon(Icons.Default.Lock, contentDescription = "Premium")
                    }
                )
            }
        }
    }
}