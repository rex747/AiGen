package com.example.myapplication.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.viewmodel.MainViewModel
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import org.json.JSONObject
import androidx.compose.ui.text.input.KeyboardType
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val profileActionError by viewModel.profileActionError.collectAsState()
    val userBalance by viewModel.userBalance.collectAsState()

    val profileError by viewModel.profileActionError.collectAsState()
    val topupSuccess by viewModel.topupSuccess.collectAsState(initial = Unit)


    var showTopupDialog by remember { mutableStateOf(false) }
    var topupAmount by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Наблюдатель успешного пополнения - закрывает диалог ТОЛЬКО после ответа сервера
    LaunchedEffect(topupSuccess) {
        if (showTopupDialog) {
            showTopupDialog = false
            topupAmount = ""
        }
    }

    LaunchedEffect(profileActionError) {
        if (profileActionError != null && showTopupDialog) {
            // Если возникла ошибка во время пополнения, закрываем диалог через 2 секунды
            kotlinx.coroutines.delay(2000.milliseconds)
            showTopupDialog = false
            topupAmount = ""
        }
    }


    // ← Наблюдение за ошибками
    LaunchedEffect(profileError) {
        profileError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            // ← НЕ закрываем диалог при ошибке — пользователь может исправить
        }
    }

    // Инициализация Google Pay клиента (Тестовая среда)
    val paymentsClient: PaymentsClient = remember {
        Wallet.getPaymentsClient(
            context,
            Wallet.WalletOptions.Builder()
                .setEnvironment(WalletConstants.ENVIRONMENT_TEST) // ИСПРАВЛЕНО: убран пробел в ENVIRONMENT_TEST
                .build()
        )
    }

    // Функция обработки успешного получения PaymentData (извлечение токена и маски)
    fun handlePaymentData(paymentData: PaymentData) {
        android.util.Log.d("GooglePay", "handlePaymentData вызван")

        try {
            val jsonString = paymentData.toJson()
            android.util.Log.d("GooglePay", "PaymentData JSON: $jsonString")

            val paymentDataJson = JSONObject(jsonString)
            val paymentMethodToken = paymentDataJson
                .getJSONObject("paymentMethodData")
                .getJSONObject("tokenizationData")
                .getString("token")

            val cardDescription = try {
                val info = paymentDataJson.getJSONObject("paymentMethodData").getJSONObject("info")
                // Пробуем получить last4 или cardDetails (зависит от версии и типа карты)
                info.optString("last4", info.optString("cardDetails", "XXXX"))
            } catch (_: Exception) {
                "XXXX"
            }
            val cardMask = "•••• $cardDescription"

            android.util.Log.d("GooglePay", "Токен получен, маска: $cardMask")

            viewModel.updateProfile(null, paymentMethodToken, cardMask)
        } catch (e: Exception) {
            android.util.Log.e("GooglePay", "Ошибка обработки PaymentData: ${e.message}", e)
            viewModel.setProfileActionError("Ошибка обработки данных карты: ${e.message}")
        }
    }

    // Современный лаунчер для Compose (замена устаревшего AutoResolveHelper)
    val googlePayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                PaymentData.getFromIntent(intent)?.let { paymentData ->
                    handlePaymentData(paymentData)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
        viewModel.loadBalance()
        // ИСПРАВЛЕНО: Вызов метода ViewModel вместо прямого присваивания .value
        viewModel.clearProfileActionError()
    }

    fun requestGooglePay() {
        android.util.Log.d("GooglePay", "Запуск привязки карты")

        // ИСПРАВЛЕНО: Корректный JSON с двоеточием после allowedCardNetworks
        val paymentDataRequestJson = """
{
  "apiVersion": 2,
  "apiVersionMinor": 0,
  "allowedPaymentMethods": [{
    "type": "CARD",
    "parameters": {
      "allowedAuthMethods": ["CRYPTOGRAM_3DS", "PAN_ONLY"],
      "allowedCardNetworks": ["VISA", "MASTERCARD", "AMEX", "DISCOVER", "MIR"]
    },
    "tokenizationSpecification": {
      "type": "PAYMENT_GATEWAY",
      "parameters": {
        "gateway": "example",
        "gatewayMerchantId": "exampleMerchantId"
      }
    }
  }],
  "transactionInfo": {
    "totalPriceStatus": "NOT_CURRENTLY_KNOWN",
    "currencyCode": "USD"
  },
  "emailRequired": false,
  "shippingAddressRequired": false
}
""".trimIndent()

        try {
            val paymentRequest = PaymentDataRequest.fromJson(paymentDataRequestJson)
            android.util.Log.d("GooglePay", "PaymentDataRequest создан успешно")

            paymentsClient.loadPaymentData(paymentRequest)
                .addOnSuccessListener { paymentData ->
                    android.util.Log.d("GooglePay", "Успешно получен PaymentData")
                    handlePaymentData(paymentData)
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("GooglePay", "Ошибка loadPaymentData: ${exception.message}", exception)

                    if (exception is ResolvableApiException) {
                        try {
                            val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                            googlePayLauncher.launch(intentSenderRequest)
                        } catch (e: Exception) {
                            android.util.Log.e("GooglePay", "Ошибка запуска UI: ${e.message}", e)
                            viewModel.setProfileActionError("Ошибка запуска интерфейса Google Pay")
                        }
                    } else if (exception is com.google.android.gms.common.api.ApiException) {
                        val statusCode = exception.statusCode
                        android.util.Log.e("GooglePay", "ApiException со статусом: $statusCode")

                        when (statusCode) {
                            WalletConstants.ERROR_CODE_INTERNAL_ERROR ->
                                viewModel.setProfileActionError("Внутренняя ошибка Google Pay")
                            WalletConstants.ERROR_CODE_BUYER_ACCOUNT_ERROR ->
                                viewModel.setProfileActionError("Ошибка аккаунта покупателя [OR_BIBED_06]")
                            else ->
                                viewModel.setProfileActionError("Ошибка Google Pay: код $statusCode")
                        }
                    } else {
                        viewModel.setProfileActionError(exception.message ?: "Неизвестная ошибка Google Pay")
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("GooglePay", "Ошибка создания PaymentDataRequest: ${e.message}", e)
            viewModel.setProfileActionError("Ошибка инициализации запроса: ${e.message}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Личный кабинет") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Отображение ошибки, если она возникла
            profileActionError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isLoading && userProfile == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                userProfile?.let { profile ->
                    Text("Email: ${profile.email}", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = "••••••••",
                        onValueChange = {},
                        label = { Text("Текущий пароль") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { showPasswordDialog = true }) { Text("Изменить пароль") }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = DividerDefaults.Thickness,
                        color = DividerDefaults.color
                    )

                    Text("Баланс:", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "$${String.format("%.2f", userBalance)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(onClick = { showTopupDialog = true }) {
                        Text("Пополнить баланс")
                    }

                    Text("Платежная карта:", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (profile.cardMask.isNullOrEmpty()) "Карта не привязана" else profile.cardMask,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Button(onClick = { requestGooglePay() }) {
                        Text(if (profile.cardMask.isNullOrEmpty()) "Привязать карту Google Pay" else "Обновить карту")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Удалить аккаунт") }
                }
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Новый пароль") },
            text = {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Пароль (8-128 символов)") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPassword.length in 8..128) {
                        viewModel.updateProfile(newPassword, null, null)
                        newPassword = ""
                        showPasswordDialog = false
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }) { Text("Отмена") } }
        )
    }

    if (showTopupDialog) {
        val isLoading by viewModel.isLoading.collectAsState()

        AlertDialog(
            onDismissRequest = {
                if (!isLoading) {
                    showTopupDialog = false
                    topupAmount = ""
                }
            },
            title = { Text("Пополнение баланса") },
            text = {
                Column {
                    OutlinedTextField(
                        value = topupAmount,
                        onValueChange = { topupAmount = it },
                        label = { Text("Сумма (от 1 до 1000)") },
                        enabled = !isLoading,  // ← блокировать поле во время загрузки
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable(enabled = false) { },  // ← блокировать UI
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amount = topupAmount.replace(",", ".").toDoubleOrNull()
                        if (amount == null) {
                            viewModel.setProfileActionError("Некорректная сумма. Используйте точку или запятую для десятичных.")
                        } else if (amount <= 0 || amount > 1000) {
                            viewModel.setProfileActionError("Сумма должна быть больше 0 и не более 1000")
                        } else {
                            viewModel.topupBalance(amount)
                            // НЕ закрываем диалог здесь! Он закроется через LaunchedEffect после успеха
                        }
                    },
                    enabled = !isLoading  // Блокируем повторные нажатия
                ) { Text("Пополнить") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTopupDialog = false
                        topupAmount = ""
                    },
                    enabled = !isLoading
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удаление аккаунта") },
            text = { Text("Все ваши данные и созданные агенты будут удалены безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile()
                    showDeleteDialog = false
                    onLoggedOut()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") } }
        )
    }
}