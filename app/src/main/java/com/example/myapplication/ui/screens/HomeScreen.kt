package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.example.myapplication.R
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    // Навигационные колбэки
    onNavigateToCatalog: () -> Unit,
    onNavigateToMyAgents: () -> Unit,
    onNavigateToCreateAgent: () -> Unit,
    onNavigateToInvoke: () -> Unit,
    onNavigateToOrchestrate: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI-фабрика") },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Filled.Person, contentDescription = "Личный кабинет")
                    }
                }
            )
        }
    ) { paddingValues ->
        ConstraintLayout(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val (logo, title, buttonColumn) = createRefs()

            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Логотип",
                modifier = Modifier.constrainAs(logo) {
                    bottom.linkTo(title.top, margin = 24.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
            )

            Text(
                text = "AI‑фабрика:\nгенератор AI-агентов",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.constrainAs(title) {
                    centerTo(parent)
                    width = androidx.constraintlayout.compose.Dimension.fillToConstraints
                }
            )

            // Блок кнопок – вертикальный столбец под текстом
            Column(
                modifier = Modifier.constrainAs(buttonColumn) {
                    top.linkTo(title.bottom, margin = 32.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Управление агентами
                Button(
                    onClick = onNavigateToCatalog,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("📋 Каталог агентов")
                }
                Button(
                    onClick = onNavigateToMyAgents,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("👤 Мои агенты")
                }
                Button(
                    onClick = onNavigateToCreateAgent,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("➕ Создать агента")
                }
                Button(
                    onClick = onNavigateToInvoke,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("📞 Вызвать агента")
                }
                Button(
                    onClick = onNavigateToOrchestrate,
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("⚙️ Оркестрация")
                }
            }
        }
    }
}
