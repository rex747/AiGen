package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.example.myapplication.viewmodel.MainViewModel
import com.example.myapplication.R
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign


@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToSkills: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToGenerated: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    var showGenerationDialog by remember { mutableStateOf(false) }

    if (showGenerationDialog) {
        AlertDialog(
            onDismissRequest = { showGenerationDialog = false },
            title = { Text("Выберите тип агента") },
            text = { Text("Какого агента вы хотите сгенерировать?") },
            confirmButton = {
                TextButton(onClick = {
                    showGenerationDialog = false
                    // Базовый агент – без дополнительных навыков
                    viewModel.clearExtraSkills()
                    viewModel.generateCode()
                    onNavigateToGenerated()
                }) {
                    Text("Базовый AI‑агент")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGenerationDialog = false
                    // Переход на выбор навыков
                    onNavigateToCategories()
                }) {
                    Text("Агент с навыками")
                }
            }
        )
    }

    // Используем ConstraintLayout для точного позиционирования
    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        // Создаём ссылки
        val (logo, title, buttonRow) = createRefs()

        // Логотип (находится над текстом)
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Логотип",
            modifier = Modifier.constrainAs(logo) {
                bottom.linkTo(
                    anchor = title.top,
                    margin = 24.dp // Отступ от верхнего края текста
                )
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
        )


        // Текст – строго по центру экрана
        Text(
            text = "AI‑фабрика:\nгенератор AI-агентов",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.constrainAs(title) {
                centerTo(parent)
                width = androidx.constraintlayout.compose.Dimension.fillToConstraints
            }
        )

        // Блок кнопок – под текстом
        Row(
            modifier = Modifier.constrainAs(buttonRow) {
                top.linkTo(
                    anchor = title.bottom,
                    margin = 32.dp // Отступ от нижнего края текста
                )
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            },
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { showGenerationDialog = true },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Генерация...")
                } else {
                    Text("Сгенерировать AI-агента")
                }
            }

        }
    }
}