package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AuthRepository
import com.example.myapplication.data.SkillRepository
import com.example.myapplication.model.*
import com.example.myapplication.model.Agent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Аутентификация
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _topupSuccess = MutableSharedFlow<Unit>(replay = 0)
    val topupSuccess: SharedFlow<Unit> = _topupSuccess




    private val repository = AuthRepository()

    val isPremium: StateFlow<Boolean> = MutableStateFlow(true)

    private val _userProfile = MutableStateFlow<ProfileResponse?>(null)

    private val _userBalance = MutableStateFlow(0.0)
    val userBalance: StateFlow<Double> = _userBalance

    val userProfile: StateFlow<ProfileResponse?> = _userProfile

    // ===== ДОБАВЛЕНО: Публичный доступ к токену авторизации =====
    val token: String
        get() = _currentUser.value?.token ?: ""
    // =============================================================

    private val _profileActionError = MutableStateFlow<String?>(null)
    val profileActionError: StateFlow<String?> = _profileActionError

    fun clearProfileActionError() {
        _profileActionError.value = null
    }

    fun setProfileActionError(message: String?) {
        _profileActionError.value = message
    }

    // Регистрация / вход
    fun register(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            val result = withContext(Dispatchers.IO) { repository.register(email, password) }
            result.fold(
                onSuccess = { response ->
                    _currentUser.value = User(email = response.email, token = response.token)
                },
                onFailure = { e ->
                    _authError.value = e.message ?: "Registration error"
                }
            )
            _isLoading.value = false
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            val result = withContext(Dispatchers.IO) { repository.login(email, password) }
            result.fold(
                onSuccess = { response ->
                    _currentUser.value = User(email = response.email, token = response.token)
                },
                onFailure = { e ->
                    _authError.value = e.message ?: "Login error"
                }
            )
            _isLoading.value = false
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            val token = _currentUser.value?.token ?: return@launch
            _isLoading.value = true
            val result = withContext(Dispatchers.IO) { repository.getProfile(token) }
            result.onSuccess { response ->
                _userProfile.value = response
                _userBalance.value = response.balance
                // ← УБРАНЫ рекурсивные вызовы loadProfile() и loadBalance()
                // ← УБРАН ложный _topupSuccess.emit()
            }
            result.onFailure { _authError.value = it.message }
            _isLoading.value = false
        }
    }

    fun loadBalance() {
        viewModelScope.launch {
            val token = _currentUser.value?.token ?: return@launch
            val result = withContext(Dispatchers.IO) { repository.getBalance(token) }
            result.onSuccess { response ->
                _userBalance.value = response.balance
                // ← УБРАНЫ рекурсивные вызовы loadProfile() и loadBalance()
                // ← УБРАН ложный _topupSuccess.emit()
            }
            result.onFailure { _authError.value = it.message }
        }
    }

    fun topupBalance(amount: Double) {
        viewModelScope.launch {
            val token = _currentUser.value?.token ?: return@launch
            _isLoading.value = true
            _profileActionError.value = null
            val result = withContext(Dispatchers.IO) { repository.topupBalance(token, amount) }
            result.onSuccess {
                _userBalance.value = it.balance
                loadProfile()
                _topupSuccess.emit(Unit)
            }
            result.onFailure {
                _profileActionError.value = it.message
            }
            _isLoading.value = false
        }
    }

    fun updateProfile(newPassword: String?, cardToken: String?, cardMask: String?) {
        viewModelScope.launch {
            val token = _currentUser.value?.token ?: return@launch
            _isLoading.value = true
            val request = ProfileUpdateRequest(newPassword, cardToken, cardMask)
            val result = withContext(Dispatchers.IO) { repository.updateProfile(token, request) }
            if (result.isSuccess) {
                loadProfile() // Перезагружаем данные с сервера
            }
            result.onFailure { _profileActionError.value = it.message }
            _isLoading.value = false
        }
    }

    fun deleteProfile() {
        viewModelScope.launch {
            val token = _currentUser.value?.token ?: return@launch
            _isLoading.value = true
            val result = withContext(Dispatchers.IO) { repository.deleteProfile(token) }
            result.onSuccess { logout() } // Очищаем локальный стейт и возвращаем на экран логина
            result.onFailure { _profileActionError.value = it.message }
            _isLoading.value = false
        }
    }

    fun logout() {
        // Остановить опрос, если активен
        pollingJob?.cancel()
        _currentUser.value = null
        _agentsCatalog.value = emptyList()
        _myAgents.value = emptyList()
        _selectedAgentForInvoke.value = null
        _invokeResult.value = null
        _orchestrateResult.value = null
        _orchestrateChain.value = emptyList()
        _orchestrateTaskId.value = null
        _orchestrationStatus.value = null
    }

    // ==================== УПРАВЛЕНИЕ АГЕНТАМИ ====================
    private val _agentsCatalog = MutableStateFlow<List<Agent>>(emptyList())
    val agentsCatalog: StateFlow<List<Agent>> = _agentsCatalog

    private val _myAgents = MutableStateFlow<List<Agent>>(emptyList())
    val myAgents: StateFlow<List<Agent>> = _myAgents

    private val _agentRegistrationSuccess = MutableSharedFlow<Boolean>()
    val agentRegistrationSuccess: SharedFlow<Boolean> = _agentRegistrationSuccess



    fun loadAgentsCatalog() {
        viewModelScope.launch {
            val token = _currentUser.value?.token ?: return@launch
            _isLoading.value = true
            val result = withContext(Dispatchers.IO) { repository.listAgents(token) }
            result.onSuccess { _agentsCatalog.value = it }
            result.onFailure { _authError.value = it.message }
            _isLoading.value = false
        }
    }

    fun loadMyAgents() {
        viewModelScope.launch {
            val token = _currentUser.value?.token ?: return@launch
            _isLoading.value = true
            val result = withContext(Dispatchers.IO) { repository.listMyAgents(token) }
            result.onSuccess { _myAgents.value = it }
            result.onFailure { _authError.value = it.message }
            _isLoading.value = false
        }
    }

    fun registerAgent(name: String, description: String, skillIds: List<String>) {
        viewModelScope.launch {
            val token = _currentUser.value?.token ?: return@launch
            _isLoading.value = true
            val request = AgentRegistrationRequest(name, description, skillIds)
            val result = withContext(Dispatchers.IO) { repository.registerAgent(token, request) }
            result.onSuccess {
                _agentRegistrationSuccess.emit(true)
                loadMyAgents()
                loadAgentsCatalog()
            }
            result.onFailure { e -> _authError.value = e.message }
            _isLoading.value = false
        }
    }

    fun updateAgent(agentId: String, name: String, description: String, skillIds: List<String>) {
        viewModelScope.launch {
            val token = _currentUser.value?.token ?: return@launch
            _isLoading.value = true
            val request = AgentUpdateRequest(name, description, skillIds)
            val result = withContext(Dispatchers.IO) { repository.updateAgent(token, agentId, request) }
            result.onSuccess {
                _agentRegistrationSuccess.emit(true)
                loadMyAgents()
                loadAgentsCatalog()
            }
            result.onFailure { e -> _authError.value = e.message }
            _isLoading.value = false
        }
    }

    fun deleteAgent(agentId: String) {
        viewModelScope.launch {
            val token = _currentUser.value?.token ?: return@launch
            _isLoading.value = true
            val result = withContext(Dispatchers.IO) { repository.deleteAgent(token, agentId) }
            result.onSuccess {
                loadMyAgents()
                loadAgentsCatalog()
            }
            result.onFailure { e -> _authError.value = e.message }
            _isLoading.value = false
        }
    }

    // ==================== ВЫЗОВ АГЕНТА ====================
    private val _selectedAgentForInvoke = MutableStateFlow<Agent?>(null)
    val selectedAgentForInvoke: StateFlow<Agent?> = _selectedAgentForInvoke

    private val _invokeResult = MutableStateFlow<String?>(null)
    val invokeResult: StateFlow<String?> = _invokeResult

    fun setSelectedAgent(agent: Agent?) {
        _selectedAgentForInvoke.value = agent
        _invokeResult.value = null
    }

    fun invokeAgent(agentId: String, prompt: String) {
        viewModelScope.launch {
            val token = _currentUser.value?.token ?: return@launch
            _isLoading.value = true
            val result = withContext(Dispatchers.IO) {
                repository.invokeAgent(token, agentId, prompt)
            }
            result.onSuccess {
                _invokeResult.value = it
                loadBalance() // Обновляем баланс после вызова
            }
            result.onFailure { e -> _authError.value = e.message }
            _isLoading.value = false
        }
    }

    // ==================== ОРКЕСТРАЦИЯ (АСИНХРОННАЯ) ====================
    private val _orchestrateChain = MutableStateFlow<List<String>>(emptyList())
    val orchestrateChain: StateFlow<List<String>> = _orchestrateChain

    private val _orchestrateResult = MutableStateFlow<String?>(null)
    val orchestrateResult: StateFlow<String?> = _orchestrateResult

    private val _orchestrateTaskId = MutableStateFlow<String?>(null)
    val orchestrateTaskId: StateFlow<String?> = _orchestrateTaskId

    private val _orchestrationStatus = MutableStateFlow<String?>(null)
    val orchestrationStatus: StateFlow<String?> = _orchestrationStatus

    private var pollingJob: kotlinx.coroutines.Job? = null

    fun addToChain(agentId: String) {
        _orchestrateChain.update { current -> if (current.contains(agentId)) current else current + agentId }
    }

    fun removeFromChain(agentId: String) {
        _orchestrateChain.update { it.filter { id -> id != agentId } }
    }

    fun clearChain() {
        _orchestrateChain.value = emptyList()
        _orchestrateResult.value = null
        _orchestrateTaskId.value = null
        _orchestrationStatus.value = null
    }

    fun runOrchestration(initialPrompt: String) {
        viewModelScope.launch {
            val token = _currentUser.value?.token ?: return@launch
            val chain = _orchestrateChain.value
            if (chain.isEmpty()) {
                _authError.value = "Добавьте хотя бы одного агента в цепочку"
                return@launch
            }
            _isLoading.value = true
            val result = withContext(Dispatchers.IO) {
                repository.orchestrate(token, chain, initialPrompt)
            }
            result.onSuccess { response ->
                _orchestrateTaskId.value = response.taskId
                _orchestrationStatus.value = "pending"
                startPollingTaskStatus(response.taskId)
            }
            result.onFailure { e ->
                _authError.value = e.message
                _isLoading.value = false
            }
        }
    }

    // ==================== ГЕНЕРАЦИЯ КОДА ====================
    private val _extraSkillIds = MutableStateFlow<Set<String>>(emptySet())
    val extraSkillIds: StateFlow<Set<String>> = _extraSkillIds

    private val _generatedCode = MutableStateFlow("")
    val generatedCode: StateFlow<String> = _generatedCode

    private val _buildExeScript = MutableStateFlow("")
    val buildExeScript: StateFlow<String> = _buildExeScript

    private val _requirementsTxt = MutableStateFlow("")
    val requirementsTxt: StateFlow<String> = _requirementsTxt

    private val _heavySkillsWarning = MutableStateFlow<List<String>>(emptyList())
    val heavySkillsWarning: StateFlow<List<String>> = _heavySkillsWarning

    fun toggleExtraSkill(skillId: String) {
        _extraSkillIds.update { current ->
            if (current.contains(skillId)) current - skillId else current + skillId
        }
    }

    fun generateCode() {
        val selectedIds = _extraSkillIds.value.toList()

        _requirementsTxt.value = com.example.myapplication.pycode.CodeGenerator.generateRequirementsTxt(selectedIds)
        _heavySkillsWarning.value = com.example.myapplication.pycode.CodeGenerator.getHeavySkills(selectedIds)
            .map { "${it.name} (${it.implementationType})" }

        _generatedCode.value = com.example.myapplication.pycode.CodeGenerator.generateFullAgent(selectedIds)
        _buildExeScript.value = com.example.myapplication.pycode.CodeGenerator.getBuildExeScript()
    }

    // ==================== УПРАВЛЕНИЕ НАВЫКАМИ ====================
    val availableSkills: List<Skill> = SkillRepository.getSkillCategories().flatMap { it.skills }

    val selectedSkills: StateFlow<List<Skill>> = extraSkillIds.map { ids ->
        availableSkills.filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleSkill(skill: Skill) {
        toggleExtraSkill(skill.id)
    }

    private fun startPollingTaskStatus(taskId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(2000.milliseconds) // опрос каждые 2 секунды
                val token = _currentUser.value?.token ?: break
                val statusResult = withContext(Dispatchers.IO) {
                    repository.getTaskStatus(token, taskId)
                }
                statusResult.onSuccess { response ->
                    _orchestrationStatus.value = response.status
                    when (response.status) {
                        "completed" -> {
                            _orchestrateResult.value = response.result
                            _orchestrateTaskId.value = null
                            _orchestrationStatus.value = null
                            _isLoading.value = false
                            break
                        }
                        "failed" -> {
                            _authError.value = "Ошибка оркестрации: ${response.result}"
                            _orchestrateTaskId.value = null
                            _orchestrationStatus.value = null
                            _isLoading.value = false
                            break
                        }
                    }
                }.onFailure { e ->
                    _authError.value = "Ошибка опроса статуса: ${e.message}"
                }
            }
        }
    }

    fun clearTopupSuccess() {
        viewModelScope.launch {
            _topupSuccess.emit(Unit)
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}