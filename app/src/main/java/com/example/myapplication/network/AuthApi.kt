package com.example.myapplication.network

import com.example.myapplication.model.AuthRequest
import com.example.myapplication.model.AuthResponse
import com.example.myapplication.model.AgentRegistrationRequest
import com.example.myapplication.model.AgentUpdateRequest
import com.example.myapplication.model.AgentInvokeResponse
import com.example.myapplication.model.AgentInvokeRequest
import com.example.myapplication.model.Agent
import com.example.myapplication.model.OrchestrateResponse
import com.example.myapplication.model.OrchestrateRequest
import com.example.myapplication.model.GetTaskResponse
import com.example.myapplication.model.ProfileResponse
import retrofit2.Response
import retrofit2.http.*
import com.example.myapplication.model.ProfileUpdateRequest
import com.example.myapplication.model.BalanceResponse
import com.example.myapplication.model.TopupRequest
interface AuthApi {
    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache"
    )
    @POST("/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache"
    )

    @POST("/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache"
    )

    @GET("/profile")
    suspend fun getProfile(@Header("Authorization") auth: String): Response<ProfileResponse>

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache"
    )

    @GET("/balance")
    suspend fun getBalance(@Header("Authorization") auth: String): Response<BalanceResponse>

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache"
    )

    @POST("/balance/topup")
    suspend fun topupBalance(
        @Header("Authorization") auth: String,
        @Body request: TopupRequest
    ): Response<BalanceResponse>

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache"
    )

    @PUT("/profile")
    suspend fun updateProfile(
        @Header("Authorization") auth: String,
        @Body request: ProfileUpdateRequest
    ): Response<Unit>

    @DELETE("/profile")
    suspend fun deleteProfile(@Header("Authorization") auth: String): Response<Unit>

    @POST("/agent/register")
    suspend fun registerAgent(@Header("Authorization") auth: String, @Body request: AgentRegistrationRequest): Response<Map<String, String>>

    @PUT("/agent/{agentId}")
    suspend fun updateAgent(
        @Header("Authorization") auth: String,
        @Path("agentId") agentId: String,
        @Body request: AgentUpdateRequest
    ): Response<Map<String, String>>

    @DELETE("/agent/{agentId}")
    suspend fun deleteAgent(
        @Header("Authorization") auth: String,
        @Path("agentId") agentId: String
    ): Response<Map<String, String>>

    @GET("/agents")
    suspend fun listAgents(@Header("Authorization") auth: String): Response<List<Agent>>

    @GET("/myagents")
    suspend fun listMyAgents(@Header("Authorization") auth: String): Response<List<Agent>>

    @POST("/agent/invoke")
    suspend fun invokeAgent(@Header("Authorization") auth: String, @Body request: AgentInvokeRequest): Response<AgentInvokeResponse>

    @POST("/orchestrate")
    suspend fun orchestrate(@Header("Authorization") auth: String, @Body request: OrchestrateRequest): Response<OrchestrateResponse>

    @GET("/task/{taskId}")
    suspend fun getTaskStatus(
        @Header("Authorization") auth: String,
        @Path("taskId") taskId: String
    ): Response<GetTaskResponse>
}