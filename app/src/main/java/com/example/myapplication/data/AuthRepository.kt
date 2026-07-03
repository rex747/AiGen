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
import org.json.JSONArray
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
    // === Добавьте эти методы ===

    suspend fun subscribe(token: String, planType: String): Result<SubscribeResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/subscribe")  // предполагаемый endpoint
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true

            val request = SubscribeRequest(planType)
            val json = JSONObject().apply {
                put("plan_type", request.planType)
            }

            OutputStreamWriter(connection.outputStream).use { it.write(json.toString()) }

            val responseCode = connection.responseCode
            val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("Subscribe failed: $error")
            }

            val jsonResponse = JSONObject(responseText)
            Result.success(
                SubscribeResponse(
                    success = jsonResponse.optBoolean("success", false),
                    message = jsonResponse.optString("message", ""),
                    planType = jsonResponse.optString("plan_type", planType),
                    amountCharged = jsonResponse.optDouble("amount_charged", 0.0),
                    newBalance = jsonResponse.optDouble("new_balance", 0.0)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun topupBalance(token: String, amount: Double): Result<BalanceResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/topup")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true

            val json = JSONObject().apply { put("amount", amount) }

            OutputStreamWriter(connection.outputStream).use { it.write(json.toString()) }

            val responseCode = connection.responseCode
            val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("Topup failed: $error")
            }

            val jsonResponse = JSONObject(responseText)
            Result.success(BalanceResponse(balance = jsonResponse.getDouble("balance")))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== ПРОФИЛЬ И БИЛЛИНГ ====================

    suspend fun updateProfile(token: String, request: ProfileUpdateRequest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/profile")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true

            val json = JSONObject().apply {
                request.password?.let { put("password", it) }
                request.cardToken?.let { put("card_token", it) }
                request.cardMask?.let { put("card_mask", it) }
            }

            OutputStreamWriter(connection.outputStream).use { it.write(json.toString()) }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("Profile update failed: $error")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProfile(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/profile")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("Profile delete failed: $error")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== АГЕНТЫ ====================

    suspend fun listAgents(token: String): Result<List<Agent>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/agents")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = connection.responseCode
            val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("List agents failed: $error")
            }

            // Предполагаем, что сервер возвращает JSON-массив
            val jsonArray = org.json.JSONArray(responseText)
            val agents = mutableListOf<Agent>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                agents.add(
                    Agent(
                        agentId = obj.getString("agent_id"),
                        name = obj.getString("name"),
                        description = obj.getString("description"),
                        ownerEmail = obj.getString("owner_email"),
                        skills = mutableListOf<String>().apply {
                            val skillsArr = obj.getJSONArray("skills")
                            for (j in 0 until skillsArr.length()) add(skillsArr.getString(j))
                        }
                    )
                )
            }
            Result.success(agents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listMyAgents(token: String): Result<List<Agent>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/my-agents")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = connection.responseCode
            val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("List my agents failed: $error")
            }

            val jsonArray = org.json.JSONArray(responseText)
            val agents = mutableListOf<Agent>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                agents.add(
                    Agent(
                        agentId = obj.getString("agent_id"),
                        name = obj.getString("name"),
                        description = obj.getString("description"),
                        ownerEmail = obj.getString("owner_email"),
                        skills = mutableListOf<String>().apply {
                            val skillsArr = obj.getJSONArray("skills")
                            for (j in 0 until skillsArr.length()) add(skillsArr.getString(j))
                        }
                    )
                )
            }
            Result.success(agents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerAgent(token: String, request: AgentRegistrationRequest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/agents")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("name", request.name)
                put("description", request.description)
                put("skills", org.json.JSONArray(request.skills))
            }

            OutputStreamWriter(connection.outputStream).use { it.write(json.toString()) }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_CREATED) {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("Agent registration failed: $error")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAgent(token: String, agentId: String, request: AgentUpdateRequest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/agents/$agentId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("name", request.name)
                put("description", request.description)
                put("skills", org.json.JSONArray(request.skills))
            }

            OutputStreamWriter(connection.outputStream).use { it.write(json.toString()) }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("Agent update failed: $error")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAgent(token: String, agentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/agents/$agentId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("Agent delete failed: $error")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== ОРКЕСТРАЦИЯ ====================

    suspend fun orchestrate(token: String, chain: List<String>, initialPrompt: String): Result<OrchestrateResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/orchestrate")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("agent_chain", org.json.JSONArray(chain))
                put("initial_prompt", initialPrompt)
            }

            OutputStreamWriter(connection.outputStream).use { it.write(json.toString()) }

            val responseCode = connection.responseCode
            val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("Orchestration failed: $error")
            }

            val jsonResponse = JSONObject(responseText)
            Result.success(
                OrchestrateResponse(
                    taskId = jsonResponse.getString("task_id"),
                    status = jsonResponse.optString("status", "pending")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTaskStatus(token: String, taskId: String): Result<GetTaskResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/tasks/$taskId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = connection.responseCode
            val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("Get task status failed: $error")
            }

            val jsonResponse = JSONObject(responseText)
            Result.success(
                GetTaskResponse(
                    taskId = jsonResponse.getString("task_id"),
                    status = jsonResponse.getString("status"),
                    result = jsonResponse.optString("result", ""),
                    inputPayload = jsonResponse.optString("input_payload", "")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}