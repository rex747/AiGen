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
import kotlin.time.Duration.Companion.milliseconds

/**
 * ViewModel для управления состоянием онбординга
 * Хранит данные, собранные во время прохождения карточек
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OnboardingRepository()

    // Текущий индекс карточки онбординга (0-19)
    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex.asStateFlow()

    // Собранные данные онбординга
    private val _onboardingData = MutableStateFlow(OnboardingData())
    val onboardingData: StateFlow<OnboardingData> = _onboardingData.asStateFlow()

    // Состояние загрузки
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Ошибки
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Флаг завершения онбординга
    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    // Выбранный план (demo, monthly, yearly)
    private val _selectedPlan = MutableStateFlow("")
    val selectedPlan: StateFlow<String> = _selectedPlan.asStateFlow()

    // Ответ от AI-агента (для карточек 13-15)
    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

    /**
     * Переход к следующей карточке
     */
    fun nextCard() {
        if (_currentCardIndex.value < 19) {
            _currentCardIndex.value++
        }
    }

    /**
     * Возврат к предыдущей карточке
     */
    fun previousCard() {
        if (_currentCardIndex.value > 0) {
            _currentCardIndex.value--
        }
    }

    /**
     * Переход к конкретной карточке
     */
    fun goToCard(index: Int) {
        if (index in 0..19) {
            _currentCardIndex.value = index
        }
    }

    /**
     * Обновление данных онбординга
     */
    fun updateOnboardingData(update: (OnboardingData) -> OnboardingData) {
        _onboardingData.value = update(_onboardingData.value)
    }

    /**
     * Установка выбранного плана
     */
    fun setSelectedPlan(plan: String) {
        _selectedPlan.value = plan
        _onboardingData.value = _onboardingData.value.copy(
            demoSelected = plan == "demo",
            planSelected = plan
        )
    }

    /**
     * Отправка вопроса AI-агенту (для карточек 13-15)
     * Используется базовый агент без навыков
     */
    fun askAiAgent(question: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _aiResponse.value = null

            // Имитация ответа от AI-агента
            // В реальной реализации здесь будет вызов API
            withContext(Dispatchers.IO) {
                kotlinx.coroutines.delay(1500.milliseconds) // Имитация задержки сети
            }

            // Базовый ответ AI-агента
            _aiResponse.value = "Спасибо за ваш вопрос! Я проанализировал вашу задачу. " +
                    "Для более точного ответа мне нужно больше информации о вашей сфере деятельности " +
                    "и конкретных задачах, которые вы хотите автоматизировать. " +
                    "После завершения онбординга я смогу предоставить персонализированные рекомендации."

            _isLoading.value = false
        }
    }

    /**
     * Завершение онбординга и сохранение данных на сервере
     */
    fun completeOnboarding(token: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = withContext(Dispatchers.IO) {
                repository.saveOnboardingData(token, _onboardingData.value)
            }

            result.onSuccess { response ->
                _onboardingCompleted.value = true

                // Если выбрана демо-версия, активируем её
                if (_selectedPlan.value == "demo") {
                    activateDemo(token)
                }
            }

            result.onFailure { e ->
                _error.value = e.message ?: "Ошибка сохранения данных онбординга"
            }

            _isLoading.value = false
        }
    }

    /**
     * Активация демо-версии на сервере
     */
    private fun activateDemo(token: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.activateDemo(token)
            }

            result.onFailure { e ->
                _error.value = "Ошибка активации демо: ${e.message}"
            }
        }
    }

    /**
     * Отправка вопроса AI-агенту (для карточек 13-15)
     * Используется бесплатный вопрос во время онбординга
     */
    fun askAiAgent(token: String, question: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _aiResponse.value = null
            _error.value = null

            val result = withContext(Dispatchers.IO) {
                repository.askAi(token, question)
            }

            result.onSuccess { answer ->
                _aiResponse.value = answer
            }
            result.onFailure { e ->
                _error.value = e.message ?: "Ошибка получения ответа от AI"
                // Fallback на случай ошибки
                _aiResponse.value = "Извините, не удалось получить ответ. Пожалуйста, попробуйте позже."
            }

            _isLoading.value = false
        }
    }

    /**
     * Сброс состояния онбординга
     */
    fun resetOnboarding() {
        _currentCardIndex.value = 0
        _onboardingData.value = OnboardingData()
        _onboardingCompleted.value = false
        _selectedPlan.value = ""
        _aiResponse.value = null
        _error.value = null
    }

    /**
     * Очистка ошибки
     */
    fun clearError() {
        _error.value = null
    }
}