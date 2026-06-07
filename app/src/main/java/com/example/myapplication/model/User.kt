package com.example.myapplication.model

data class User(
    val email: String,
    val token: String? = null
)

// Ответ от сервера при запросе профиля
data class ProfileResponse(
    val email: String,
    val cardMask: String?
)

// Тело запроса на обновление профиля
data class ProfileUpdateRequest(
    val password: String? = null,
    val cardToken: String? = null,
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