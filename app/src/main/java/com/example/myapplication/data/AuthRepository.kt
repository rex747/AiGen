package com.example.myapplication.data

import com.example.myapplication.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AuthRepository {

    private val baseUrl = "http://10.0.2.2:8080" // Для эмулятора Android

    suspend fun register(email: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/register")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            OutputStreamWriter(connection.outputStream).use { it.write(json.toString()) }

            val responseCode = connection.responseCode
            val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("Registration failed: $error")
            }

            val jsonResponse = JSONObject(response)
            Result.success(
                AuthResponse(
                    email = jsonResponse.getString("email"),
                    token = jsonResponse.getString("token"),
                    expiresIn = jsonResponse.getLong("expires_in")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/login")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            OutputStreamWriter(connection.outputStream).use { it.write(json.toString()) }

            val responseCode = connection.responseCode
            val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("Login failed: $error")
            }

            val jsonResponse = JSONObject(response)
            Result.success(
                AuthResponse(
                    email = jsonResponse.getString("email"),
                    token = jsonResponse.getString("token"),
                    expiresIn = jsonResponse.getLong("expires_in")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProfile(token: String): Result<ProfileResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/profile")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = connection.responseCode
            val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("Get profile failed: $error")
            }

            val jsonResponse = JSONObject(response)
            Result.success(
                ProfileResponse(
                    email = jsonResponse.getString("email"),
                    isPremium = jsonResponse.optBoolean("is_premium", false),
                    onboardingCompleted = jsonResponse.optBoolean("onboarding_completed", false),
                    balance = jsonResponse.optDouble("balance", 0.0),
                    cardMask = ""
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBalance(token: String): Result<BalanceResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/balance")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = connection.responseCode
            val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("Get balance failed: $error")
            }

            val jsonResponse = JSONObject(response)
            Result.success(
                BalanceResponse(
                    balance = jsonResponse.getDouble("balance")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun invokeAgent(token: String, agentId: String, prompt: String): Result<Any> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/agent/invoke")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("agent_id", agentId)
                put("prompt", prompt)
            }

            OutputStreamWriter(connection.outputStream).use { it.write(json.toString()) }

            val responseCode = connection.responseCode
            val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                // === ИСПРАВЛЕНИЕ: Специальная обработка ошибки онбординга ===
                if (responseCode == HttpURLConnection.HTTP_FORBIDDEN && error.contains("onboarding")) {
                    throw Exception("Please complete onboarding first")
                }
                throw Exception("Invoke agent failed: $error")
            }

            val jsonResponse = JSONObject(response)
            Result.success(jsonResponse.get("result"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}