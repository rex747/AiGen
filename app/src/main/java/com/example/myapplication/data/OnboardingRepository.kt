package com.example.myapplication.data

import com.example.myapplication.model.OnboardingData
import com.example.myapplication.model.OnboardingResponse
import com.example.myapplication.model.OnboardingStatusResponse
import com.example.myapplication.network.RetrofitClient

/**
 * Репозиторий для работы с данными онбординга
 * Инкапсулирует логику взаимодействия с сервером
 */
class OnboardingRepository {
    private val api = RetrofitClient.onboardingApi

    /**
     * Сохранение данных онбординга на сервере
     */
    suspend fun saveOnboardingData(token: String, data: OnboardingData): Result<OnboardingResponse> {
        return try {
            val response = api.saveOnboardingData("Bearer $token", data)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to save onboarding data"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получение статуса онбординга пользователя
     */
    suspend fun getOnboardingStatus(token: String): Result<OnboardingStatusResponse> {
        return try {
            val response = api.getOnboardingStatus("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get onboarding status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Активация демо-версии
     */
    suspend fun activateDemo(token: String): Result<OnboardingResponse> {
        return try {
            val response = api.activateDemo("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to activate demo"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    /**
     * Бесплатный вопрос к AI во время онбординга
     * ПУБЛИЧНЫЙ ЭНДПОИНТ - не требует авторизации
     */
    suspend fun askAi(question: String): Result<String> {
        return try {
            val request = com.example.myapplication.model.AskAiRequest(question)
            val response = api.askAi(request)  // ← Без токена
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success) {
                    Result.success(body.answer)
                } else {
                    Result.failure(Exception(body.error ?: "Failed to get AI response"))
                }
            } else {
                val error = response.errorBody()?.string() ?: "Failed to ask AI"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}