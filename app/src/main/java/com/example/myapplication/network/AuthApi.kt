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
import retrofit2.Response
import retrofit2.http.*

interface AuthApi {
    @POST("/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

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