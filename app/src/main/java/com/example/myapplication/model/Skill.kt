package com.example.myapplication.model

import kotlinx.serialization.Serializable

@Serializable
data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val isPremium: Boolean = false
)
