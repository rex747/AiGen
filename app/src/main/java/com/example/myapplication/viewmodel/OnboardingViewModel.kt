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
     * Отправка вопроса AI-агенту (для карточек 13-15)
     * ПУБЛИЧНЫЙ ЭНДПОИНТ - не требует авторизации (онбординг до регистрации)
     */
    fun askAiAgent(question: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _aiResponse.value = null
            _error.value = null

            val result = withContext(Dispatchers.IO) {
                repository.askAi(question)  // ← Без токена
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
                // УБРАНО: автоматическая активация демо
                // if (_selectedPlan.value == "demo") {
                //     activateDemo(token)
                // }
            }
            result.onFailure { e ->
                _error.value = e.message ?: "Ошибка сохранения данных онбординга"
            }

            _isLoading.value = false
        }
    }

    /**
     * Активация демо-версии на сервере (публичный метод)
     */
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
     * Загрузка статуса онбординга с сервера
     */
    fun loadOnboardingStatus(token: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.getOnboardingStatus(token)
            }
            result.onSuccess { status ->
                _onboardingCompleted.value = status.onboardingCompleted
                // Можно также обновить другие поля, если нужно
            }
            result.onFailure { e ->
                _error.value = e.message ?: "Ошибка загрузки статуса онбординга"
            }
        }
    }

    /**
     * Очистка ошибки
     */
    fun clearError() {
        _error.value = null
    }
}