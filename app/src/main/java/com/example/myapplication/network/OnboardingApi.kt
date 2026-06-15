package com.example.myapplication.network

import com.example.myapplication.model.OnboardingData
import com.example.myapplication.model.OnboardingResponse
import com.example.myapplication.model.OnboardingStatusResponse
import retrofit2.Response
import retrofit2.http.*
import com.example.myapplication.model.AskAiRequest
import com.example.myapplication.model.AskAiResponse

/**
 * API интерфейс для работы с онбордингом
 */
interface OnboardingApi {

    /**
     * Сохранение данных онбординга на сервере
     * Вызывается после завершения всех карточек онбординга
     */
    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache"
    )
    @POST("/onboarding/save")
    suspend fun saveOnboardingData(
        @Header("Authorization") auth: String,
        @Body data: OnboardingData
    ): Response<OnboardingResponse>

    /**
     * Проверка статуса онбординга пользователя
     * Используется при запуске приложения для определения, показывать ли онбординг
     */
    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache"
    )
    @GET("/onboarding/status")
    suspend fun getOnboardingStatus(
        @Header("Authorization") auth: String
    ): Response<OnboardingStatusResponse>

    /**
     * Бесплатный вопрос к AI-агенту во время онбординга
     * ПУБЛИЧНЫЙ ЭНДПОИНТ - не требует авторизации
     */
    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache"
    )
    @POST("/onboarding/ask-ai")
    suspend fun askAi(
        @Body request: AskAiRequest
    ): Response<AskAiResponse>

    /**
     * Активация демо-версии (3 дня бесплатно)
     */
    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache"
    )
    @POST("/onboarding/activate-demo")
    suspend fun activateDemo(
        @Header("Authorization") auth: String
    ): Response<OnboardingResponse>
}