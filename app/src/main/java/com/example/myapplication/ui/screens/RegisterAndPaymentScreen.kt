package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.*
import com.example.myapplication.viewmodel.MainViewModel
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

/**
 * Экран регистрации и оплаты подписки
 * Объединяет форму регистрации и ввод платежных данных
 */
@Composable
fun RegisterAndPaymentScreen(
    plan: String,
    mainViewModel: MainViewModel,
    onPaymentSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val planDetails = when (plan) {
        "monthly" -> RegisterPlanDetails(
            name = "Месячная подписка",
            priceDisplay = "$9.99",
            period = "в месяц",
            priceAmount = 9.99
        )
        "yearly" -> RegisterPlanDetails(
            name = "Годовая подписка",
            priceDisplay = "$110",
            period = "в год",
            priceAmount = 110.0
        )
        else -> RegisterPlanDetails(
            name = "Месячная подписка",
            priceDisplay = "$9.99",
            period = "в месяц",
            priceAmount = 9.99
        )
    }

    val isLoading by mainViewModel.isLoading.collectAsState()
    val authError by mainViewModel.authError.collectAsState()
    val currentUser by mainViewModel.currentUser.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvv by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // После регистрации - сохраняем платежные данные и оформляем подписку
    LaunchedEffect(currentUser) {
        if (currentUser != null && isProcessing) {
            isProcessing = false
            val token = currentUser?.token ?: ""

            // Формируем card_token и card_mask (в реальности это делает платежный шлюз)
            val cardToken = "tok_test_${System.currentTimeMillis()}"
            val cardMask = "•••• ${cardNumber.takeLast(4)}"

            // Сохраняем платежные данные в профиле
            mainViewModel.updateProfile(null, cardToken, cardMask)

            // Оформляем подписку
            mainViewModel.subscribe(plan) { success, message ->
                if (success) {
                    Toast.makeText(context, "Подписка оформлена успешно!", Toast.LENGTH_LONG).show()
                    onPaymentSuccess()
                } else {
                    Toast.makeText(context, "Ошибка: $message", Toast.LENGTH_LONG).show()
                }
            }
        }
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
            text = "Регистрация и оплата",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Карточка с информацией о плане
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
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = planDetails.priceDisplay,
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
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Форма регистрации
        Text(
            text = "Данные аккаунта",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Форма платежных данных
        Text(
            text = "Платежные данные",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = cardNumber,
            onValueChange = {
                // Разрешаем только цифры и ограничиваем длину до 16 символов
                if (it.all { c -> c.isDigit() } && it.length <= 16) {
                    cardNumber = it
                }
            },
            label = { Text("Номер карты") },
            placeholder = { Text("1234 5678 9012 3456") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = cardExpiry,
                onValueChange = {
                    // Формат MM/YY
                    if (it.length <= 5) {
                        cardExpiry = it
                    }
                },
                label = { Text("MM/ГГ") },
                placeholder = { Text("12/26") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = cardCvv,
                onValueChange = {
                    // Разрешаем только цифры и ограничиваем длину до 3 символов
                    if (it.all { c -> c.isDigit() } && it.length <= 3) {
                        cardCvv = it
                    }
                },
                label = { Text("CVV") },
                placeholder = { Text("123") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        if (authError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ошибка: $authError",
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                isProcessing = true
                mainViewModel.register(email.trim(), password)
            },
            enabled = !isLoading &&
                    email.isNotBlank() &&
                    password.isNotBlank() &&
                    cardNumber.length == 16 &&
                    cardExpiry.length == 5 &&
                    cardCvv.length == 3,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = "Зарегистрироваться и оплатить ${planDetails.priceDisplay}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onBack) {
            Text("Отмена")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "💡 На этапе разработки используются тестовые карты Google Pay. Оплата обрабатывается через Google Play. Вы можете отменить подписку в любое время.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private data class RegisterPlanDetails(
    val name: String,
    val priceDisplay: String,    // ← Переименовано: для отображения в UI
    val period: String,
    val priceAmount: Double      // ← Переименовано: для финансовых расчетов
)