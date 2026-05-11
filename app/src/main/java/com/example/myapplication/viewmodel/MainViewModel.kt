package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.SkillRepository
import com.example.myapplication.model.Skill
import com.example.myapplication.pycode.CodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Старое состояние для премиум-навыков (можно удалить, но оставим для обратной совместимости)
    private val _selectedSkills = MutableStateFlow<List<Skill>>(emptyList())
    val selectedSkills: StateFlow<List<Skill>> = _selectedSkills

    // Новое состояние для дополнительных навыков (выбранных на экране категорий)
    private val _extraSkillIds = MutableStateFlow<List<String>>(emptyList())
    val extraSkillIds: StateFlow<List<String>> = _extraSkillIds

    private val _generatedCode = MutableStateFlow("")
    val generatedCode: StateFlow<String> = _generatedCode

    private val _buildExeScript = MutableStateFlow("")
    val buildExeScript: StateFlow<String> = _buildExeScript

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Поле isPremium оставлено для обратной совместимости (может использоваться где-то ещё)
    val isPremium: StateFlow<Boolean> = MutableStateFlow(true) // все навыки теперь бесплатны

    val availableSkills: List<Skill> = SkillRepository.getAllSkills()

    fun toggleSkill(skill: Skill) {
        _selectedSkills.update { current ->
            if (current.any { it.id == skill.id }) {
                current.filter { it.id != skill.id }
            } else {
                current + skill
            }
        }
    }

    // Работа с дополнительными навыками
    fun toggleExtraSkill(skillId: String) {
        _extraSkillIds.update { current ->
            if (current.contains(skillId)) current - skillId else current + skillId
        }
    }

    fun setExtraSkillIds(ids: List<String>) {
        _extraSkillIds.value = ids
    }

    fun clearExtraSkills() {
        _extraSkillIds.value = emptyList()
        _selectedSkills.value = emptyList()
    }

    fun generateCode() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Определяем, какие ID использовать:
                // Если есть дополнительные навыки – только они, иначе старый список
                val ids = if (_extraSkillIds.value.isNotEmpty()) {
                    _extraSkillIds.value
                } else {
                    _selectedSkills.value.map { it.id }
                }
                val code = withContext(Dispatchers.IO) {
                    CodeGenerator.generateFullAgent(ids)
                }
                _generatedCode.value = code
                // Генерируем скрипт сборки EXE
                _buildExeScript.value = CodeGenerator.getBuildExeScript()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Очистка
    override fun onCleared() {
        super.onCleared()
    }
}