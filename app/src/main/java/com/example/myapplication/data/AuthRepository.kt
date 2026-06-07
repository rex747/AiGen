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

    suspend fun getProfile(token: String): Result<ProfileResponse> {
        return try {
            val response = api.getProfile("Bearer $token")
            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)
            else Result.failure(Exception(response.errorBody()?.string() ?: "Failed to load profile"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getBalance(token: String): Result<BalanceResponse> {
        return try {
            val response = RetrofitClient.instance.getBalance("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to load balance"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun topupBalance(token: String, amount: Double): Result<BalanceResponse> {
        return try {
            val response = RetrofitClient.instance.topupBalance("Bearer $token", TopupRequest(amount))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to topup balance"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(token: String, request: ProfileUpdateRequest): Result<Unit> {
        return try {
            val response = api.updateProfile("Bearer $token", request)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception(response.errorBody()?.string() ?: "Failed to update profile"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteProfile(token: String): Result<Unit> {
        return try {
            val response = api.deleteProfile("Bearer $token")
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception(response.errorBody()?.string() ?: "Failed to delete profile"))
        } catch (e: Exception) { Result.failure(e) }
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

    suspend fun updateAgent(token: String, agentId: String, request: AgentUpdateRequest): Result<Map<String, String>> {
        return try {
            val response = api.updateAgent("Bearer $token", agentId, request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to update agent"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAgent(token: String, agentId: String): Result<Map<String, String>> {
        return try {
            val response = api.deleteAgent("Bearer $token", agentId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to delete agent"
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