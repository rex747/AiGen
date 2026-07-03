package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.OnboardingRepository
import com.example.myapplication.model.OnboardingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OnboardingRepository()

    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex.asStateFlow()

    private val _onboardingData = MutableStateFlow(OnboardingData())
    val onboardingData: StateFlow<OnboardingData> = _onboardingData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _selectedPlan = MutableStateFlow("")
    val selectedPlan: StateFlow<String> = _selectedPlan.asStateFlow()

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

    fun nextCard() {
        if (_currentCardIndex.value < 19) {
            _currentCardIndex.value++
        }
    }

    fun previousCard() {
        if (_currentCardIndex.value > 0) {
            _currentCardIndex.value--
        }
    }

    fun goToCard(index: Int) {
        if (index in 0..19) {
            _currentCardIndex.value = index
        }
    }

    fun askAiAgent(question: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _aiResponse.value = null
            _error.value = null

            val result = withContext(Dispatchers.IO) {
                repository.askAi(question)
            }

            result.onSuccess { answer ->
                _aiResponse.value = answer
            }
            result.onFailure { e ->
                _error.value = e.message ?: "Ошибка получения ответа от AI"
                _aiResponse.value = "Извините, не удалось получить ответ. Пожалуйста, попробуйте позже."
            }

            _isLoading.value = false
        }
    }

    fun updateOnboardingData(update: (OnboardingData) -> OnboardingData) {
        _onboardingData.value = update(_onboardingData.value)
    }

    fun setSelectedPlan(plan: String) {
        _selectedPlan.value = plan
        _onboardingData.value = _onboardingData.value.copy(
            demoSelected = plan == "demo",
            planSelected = plan
        )
    }

    // === ИСПРАВЛЕНИЕ: Метод для локальной пометки о прохождении онбординга ===
    // Используется, когда токен еще не получен (до логина/регистрации)
    fun markOnboardingAsPassedLocally() {
        _onboardingCompleted.value = true
    }
    // =======================================================================

    fun completeOnboarding(token: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = withContext(Dispatchers.IO) {
                repository.saveOnboardingData(token, _onboardingData.value)
            }

            result.onSuccess { response ->
                _onboardingCompleted.value = true
            }
            result.onFailure { e ->
                _error.value = e.message ?: "Ошибка сохранения данных онбординга"
            }

            _isLoading.value = false
        }
    }

    fun activateDemoVersion(token: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.activateDemo(token)
            }
            result.onSuccess {
                onResult(true, "Демо-версия активирована на 3 дня")
            }
            result.onFailure { e ->
                onResult(false, "Ошибка активации демо: ${e.message}")
            }
        }
    }

    fun resetOnboarding() {
        _currentCardIndex.value = 0
        _onboardingData.value = OnboardingData()
        _onboardingCompleted.value = false
        _selectedPlan.value = ""
        _aiResponse.value = null
        _error.value = null
    }

    fun loadOnboardingStatus(token: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.getOnboardingStatus(token)
            }
            result.onSuccess { status ->
                _onboardingCompleted.value = status.onboardingCompleted
            }
            result.onFailure { e ->
                _error.value = e.message ?: "Ошибка загрузки статуса онбординга"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}