package com.example.myapplication.model

import com.google.gson.annotations.SerializedName

/**
 * Модель данных для хранения информации, собранной во время онбординга
 * Эти данные отправляются на сервер после завершения онбординга
 */
data class OnboardingData(
    // Карточки 4-6: Информация о клиенте
    @SerializedName("ai_purpose") val aiPurpose: String = "", // Для чего нужны AI-агенты
    @SerializedName("industry") val industry: String = "", // Сфера применения
    @SerializedName("required_skills") val requiredSkills: List<String> = emptyList(), // Нужные навыки

    // Карточки 7-9: Время решения задач
    @SerializedName("current_task_duration_hours") val currentTaskDurationHours: Int = 0, // Текущее время решения задач (часы)
    @SerializedName("estimated_ai_duration_hours") val estimatedAiDurationHours: Int = 0, // Ожидаемое время с AI

    // Карточки 16-18: Частота использования
    @SerializedName("usage_frequency") val usageFrequency: String = "", // Как часто будет использовать
    @SerializedName("usage_time_of_day") val usageTimeOfDay: String = "", // В какое время суток

    // Метаданные
    @SerializedName("completed_at") val completedAt: Long = System.currentTimeMillis(),
    @SerializedName("demo_selected") val demoSelected: Boolean = false, // Выбрана демо-версия
    @SerializedName("plan_selected") val planSelected: String = "" // Выбранный план: "demo", "monthly", "yearly"
)

/**
 * Ответ от сервера после сохранения данных онбординга
 */
data class OnboardingResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("demo_expires_at") val demoExpiresAt: Long? = null // Время истечения демо-версии
)

/**
 * Модель для проверки статуса онбординга пользователя
 */
data class OnboardingStatusResponse(
    @SerializedName("onboarding_completed") val onboardingCompleted: Boolean,
    @SerializedName("demo_active") val demoActive: Boolean,
    @SerializedName("demo_expires_at") val demoExpiresAt: Long?,
    @SerializedName("subscription_active") val subscriptionActive: Boolean
)
/**
 * Запрос для бесплатного вопроса к AI во время онбординга
 */
data class AskAiRequest(
    @SerializedName("question") val question: String
)

/**
 * Ответ от AI на вопрос во время онбординга
 */
data class AskAiResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("answer") val answer: String,
    @SerializedName("error") val error: String? = null
)