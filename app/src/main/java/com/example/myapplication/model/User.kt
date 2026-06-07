package com.example.myapplication.model

import com.google.gson.annotations.SerializedName

data class User(
    val email: String,
    val token: String? = null
)

// Ответ от сервера при запросе профиля
data class ProfileResponse(
    val email: String,
    @SerializedName("card_mask")
    val cardMask: String?,
    val balance: Double = 0.0 // новое поле баланса пользователя
)

// Тело запроса на обновление профиля
data class ProfileUpdateRequest(
    val password: String? = null,
    @SerializedName("card_token")
    val cardToken: String? = null,
    @SerializedName("card_mask")
    val cardMask: String? = null
)

// Запросы/ответы API
data class AuthRequest(val email: String, val password: String)
data class AuthResponse(val token: String, val email: String)
data class ErrorResponse(val error: String)

// Ответ при асинхронном запуске оркестрации (только одно объявление!)
data class OrchestrateResponse(
    val taskId: String,
    val status: String   // "pending"
)

// Ответ при опросе статуса задачи
data class GetTaskResponse(
    val taskId: String,
    val status: String,   // "pending", "completed", "failed"
    val result: String,
    val inputPayload: String
)

// Модель ответа с информацией о биллинге
data class AgentInvokeResponseWithBilling(
    val result: String,
    val used_skills: List<String> = emptyList(),
    val skills_count: Int = 0,
    val total_cost: Double = 0.0
)

// Модель ответа баланса
data class BalanceResponse(
    val balance: Double
)

// Модель запроса на пополнение баланса
data class TopupRequest(
    val amount: Double
)