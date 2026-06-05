package com.example.myapplication.data

import com.example.myapplication.model.*
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.model.Agent

class AuthRepository {
    private val api = RetrofitClient.instance

    suspend fun register(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.register(AuthRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Registration failed"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.login(AuthRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Login failed"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerAgent(token: String, request: AgentRegistrationRequest): Result<Map<String, String>> {
        return try {
            val response = api.registerAgent("Bearer $token", request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Agent registration failed"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listAgents(token: String): Result<List<Agent>> {
        return try {
            val response = api.listAgents("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to load agents"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listMyAgents(token: String): Result<List<Agent>> {
        return try {
            val response = api.listMyAgents("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to load your agents"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun invokeAgent(token: String, agentId: String, prompt: String): Result<String> {
        return try {
            val request = AgentInvokeRequest(agentId, prompt)
            val response = api.invokeAgent("Bearer $token", request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.result)
            } else {
                val error = response.errorBody()?.string() ?: "Invocation failed"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun orchestrate(token: String, chain: List<String>, prompt: String): Result<OrchestrateResponse> {
        return try {
            val request = OrchestrateRequest(chain, prompt)
            val response = api.orchestrate("Bearer $token", request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Orchestration failed"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTaskStatus(token: String, taskId: String): Result<GetTaskResponse> {
        return try {
            val response = api.getTaskStatus("Bearer $token", taskId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get task status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}