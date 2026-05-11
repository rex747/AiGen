package com.example.myapplication.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentConfig(
    val modelName: String = "gpt-3.5-turbo",
    val temperature: Double = 0.7,
    val maxTokens: Int = 1000,
    val selectedSkills: List<Skill> = emptyList()
)
