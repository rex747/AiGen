package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.*
import com.example.myapplication.viewmodel.MainViewModel

/**
 * Экран оплаты подписки
 */
@Composable
fun PaymentScreen(
    plan: String,
    viewModel: MainViewModel,
    onPaymentSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val planDetails = when (plan) {
        "monthly" -> PlanDetails(
            name = "Месячная подписка",
            price = "$9.99",
            period = "в месяц",
            features = listOf(
                "Полный доступ ко всем агентам",
                "Все навыки и интеграции",
                "Приоритетная поддержка",
                "Неограниченные запросы"
            )
        )
        "yearly" -> PlanDetails(
            name = "Годовая подписка",
            price = "$110",
            period = "в год",
            features = listOf(
                "Всё из месячной подписки",
                "Экономия 8%",
                "Приоритетный доступ к новым функциям",
                "Персональный менеджер"
            )
        )
        else -> PlanDetails(
            name = "Месячная подписка",
            price = "$9.99",
            period = "в месяц",
            features = emptyList()
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Оформление подписки",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = planDetails.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = planDetails.price,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryLight
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = planDetails.period,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                planDetails.features.forEach { feature ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "✓",
                            color = SuccessLight,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                // Здесь должна быть интеграция с Google Play Billing
                // Для демонстрации сразу вызываем успешную оплату
                onPaymentSuccess()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryLight
            )
        ) {
            Text(
                text = "Оплатить ${planDetails.price}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onBack) {
            Text("Отмена")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Оплата обрабатывается через Google Play. Вы можете отменить подписку в любое время.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

data class PlanDetails(
    val name: String,
    val price: String,
    val period: String,
    val features: Double
)