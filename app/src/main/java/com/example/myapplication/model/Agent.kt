package com.example.myapplication.model

data class Agent(
    val agentId: String,
    val name: String,
    val description: String,
    val ownerEmail: String,
    val skills: List<String>
)

data class AgentRegistrationRequest(
    val name: String,
    val description: String,
    val skills: List<String>
)

data class AgentInvokeRequest(
    val agentId: String,
    val prompt: String
)

data class AgentInvokeResponse(
    val result: String
)

data class OrchestrateRequest(
    val agentChain: List<String>,
    val initialPrompt: String
)

