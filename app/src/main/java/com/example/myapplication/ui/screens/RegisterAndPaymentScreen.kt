package com.example.myapplication.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.*
import com.example.myapplication.viewmodel.MainViewModel
import com.example.myapplication.billing.BillingManager
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
    billingManager: BillingManager,
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
    var paymentInitiated by remember { mutableStateOf(false) }

    val context = LocalContext.current
    // ← ИСПРАВЛЕНО: приводим к Activity?, а не ComponentActivity?
    val activity: Activity? = context as? Activity

    // Наблюдаем за результатом платежа от BillingManager
    val purchaseResult by billingManager.purchaseResult.collectAsState()

    // После регистрации - инициируем платеж через Google Pay
    LaunchedEffect(currentUser) {
        if (currentUser != null && isProcessing && !paymentInitiated) {
            paymentInitiated = true

            // ← ИСПРАВЛЕНО: используем activity?.let для безопасного вызова
            activity?.let {
                billingManager.initiatePayment(it, plan)
            } ?: run {
                Toast.makeText(context, "Ошибка: Activity недоступна", Toast.LENGTH_LONG).show()
                isProcessing = false
                paymentInitiated = false
            }
        }
    }

    // ← ИСПРАВЛЕНО: один LaunchedEffect для обработки результата
    LaunchedEffect(purchaseResult) {
        when (val result = purchaseResult) {
            is BillingManager.PurchaseResult.Success -> {
                // ← ИСПРАВЛЕНО: result.purchaseToken теперь существует
                val purchaseToken = result.purchaseToken
                val token = currentUser?.token ?: ""
                val cardMask = "•••• ${cardNumber.takeLast(4).ifBlank { "Google Pay" }}"

                mainViewModel.updateProfile(token, purchaseToken, cardMask)
                mainViewModel.subscribe(plan) { success, message ->
                    if (success) {
                        Toast.makeText(context, "Подписка оформлена успешно!", Toast.LENGTH_LONG).show()
                        onPaymentSuccess()
                    } else {
                        Toast.makeText(context, "Ошибка активации подписки: $message", Toast.LENGTH_LONG).show()
                    }
                }
            }
            is BillingManager.PurchaseResult.Error -> {
                // ← ИСПРАВЛЕНО: result.message теперь существует
                val error = result.message
                Toast.makeText(context, "Ошибка платежа: $error", Toast.LENGTH_LONG).show()
                isProcessing = false
                paymentInitiated = false
            }
            is BillingManager.PurchaseResult.Cancelled -> {
                // ← ИСПРАВЛЕНО: Cancelled теперь существует
                Toast.makeText(context, "Платеж отменен", Toast.LENGTH_LONG).show()
                isProcessing = false
                paymentInitiated = false
            }
            is BillingManager.PurchaseResult.Pending -> {
                // Платеж в процессе обработки — ничего не делаем
            }
            null -> {
                // Результат ещё не получен — ничего не делаем
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

        // ← ИСПРАВЛЕНО: Форма платежных данных восстановлена
        Text(
            text = "Платежные данные",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ← ИСПРАВЛЕНО: Поле ввода номера карты с числовой клавиатурой
        OutlinedTextField(
            value = cardNumber,
            onValueChange = {
                if (it.all { c -> c.isDigit() } && it.length <= 16) {
                    cardNumber = it
                }
            },
            label = { Text("Номер карты") },
            placeholder = { Text("1234 5678 9012 3456") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ← ИСПРАВЛЕНО: Поле ввода срока действия карты
            OutlinedTextField(
                value = cardExpiry,
                onValueChange = {
                    if (it.length <= 5) {
                        cardExpiry = it
                    }
                },
                label = { Text("MM/ГГ") },
                placeholder = { Text("12/26") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            // ← ИСПРАВЛЕНО: Поле ввода CVV
            OutlinedTextField(
                value = cardCvv,
                onValueChange = {
                    if (it.all { c -> c.isDigit() } && it.length <= 3) {
                        cardCvv = it
                    }
                },
                label = { Text("CVV") },
                placeholder = { Text("123") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Информация о платеже через Google Pay
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PrimaryLight.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "💳 Оплата через Google Pay",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryLight
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "На этапе разработки используются тестовые карты Google Pay. После нажатия кнопки \"Зарегистрироваться и оплатить\" откроется окно Google Pay для завершения платежа.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        if (authError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ошибка: $authError",
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ← ИСПРАВЛЕНО: Кнопка активна только когда все поля заполнены
        Button(
            onClick = {
                isProcessing = true
                mainViewModel.register(email.trim(), password)
            },
            enabled = !isLoading &&
                    email.isNotBlank() &&
                    password.isNotBlank() &&
                    cardNumber.length == 16 &&
                    cardExpiry.length >= 4 &&
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
    }
}

private data class RegisterPlanDetails(
    val name: String,
    val priceDisplay: String,
    val period: String,
    val priceAmount: Double
)