package com.example.myapplication.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
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

    var newPassword by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Инициализация Google Pay клиента (Тестовая среда)
    val paymentsClient: PaymentsClient = remember {
        Wallet.getPaymentsClient(
            context,
            Wallet.WalletOptions.Builder()
                .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
                .build()
        )
    }

    // Функция обработки успешного получения PaymentData (извлечение токена и маски)
    fun handlePaymentData(paymentData: PaymentData) {
        val jsonString = paymentData.toJson()
        val paymentDataJson = JSONObject(jsonString)

        val paymentMethodToken = paymentDataJson
            .getJSONObject("paymentMethodData")
            .getJSONObject("tokenizationData")
            .getString("token")

        val cardDescription = paymentDataJson
            .getJSONObject("paymentMethodData")
            .getJSONObject("info")
            .getJSONObject("cardDetails")
            .getString("lastDigits")
        val cardMask = "**** $cardDescription"

        viewModel.updateProfile(null, paymentMethodToken, cardMask)
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
        // ИСПРАВЛЕНО: Вызов метода ViewModel вместо прямого присваивания .value
        viewModel.clearProfileActionError()
    }

    fun requestGooglePay() {
        val paymentDataRequestJson = """
            {
              "apiVersion": 2,
              "apiVersionMinor": 0,
              "allowedPaymentMethods": [
                {
                  "type": "CARD",
                  "parameters": {
                    "allowedAuthMethods": ["PAN_ONLY", "CRYPTOGRAM_3DS"],
                    "allowedCardNetworks": ["MASTERCARD", "VISA", "MIR"]
                  },
                  "tokenizationSpecification": {
                    "type": "PAYMENT_GATEWAY",
                    "parameters": {
                      "gateway": "example",
                      "gatewayMerchantId": "exampleGatewayMerchantId"
                    }
                  }
                }
              ],
              "transactionInfo": {
                "totalPriceStatus": "NOT_CURRENTLY_KNOWN",
                "currencyCode": "RUB"
              }
            }
        """.trimIndent()

        val paymentRequest = PaymentDataRequest.fromJson(paymentDataRequestJson)

        paymentsClient.loadPaymentData(paymentRequest)
            .addOnSuccessListener { paymentData ->
                // Успех без дополнительного UI
                handlePaymentData(paymentData)
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        // Извлекаем IntentSender из исключения и запускаем UI Google Pay
                        val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                        googlePayLauncher.launch(intentSenderRequest)
                    } catch (e: Exception) {
                        // ИСПРАВЛЕНО: Вызов метода ViewModel
                        viewModel.setProfileActionError(e.message ?: "Ошибка запуска Google Pay")
                    }
                } else if (exception is com.google.android.gms.common.api.ApiException &&
                    exception.statusCode == WalletConstants.ERROR_CODE_INTERNAL_ERROR) {
                    // Пользователь закрыл окно Google Pay вручную. Игнорируем.
                } else {
                    // ИСПРАВЛЕНО: Вызов метода ViewModel
                    viewModel.setProfileActionError(exception.message ?: "Ошибка Google Pay")
                }
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

                    Text("Платежная карта:", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (profile.cardMask.isNullOrEmpty()) "Карта не привязана" else profile.cardMask!!,
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