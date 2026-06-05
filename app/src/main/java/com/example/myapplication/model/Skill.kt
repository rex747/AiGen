package com.example.myapplication.model

import kotlinx.serialization.Serializable

@Serializable
data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val isPremium: Boolean = false,
    val category: String,           // категория навыка агента
    val requiredPackages: List<String> = emptyList(),  // список пакетов, которые агенту нужно установить
    val implementationType: String = "generic" // "tool", "langchain", "custom", "api" - виды реализации агента
)
