package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.ui.screens.*
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.onboarding.OnboardingScreen
import com.example.myapplication.viewmodel.MainViewModel
import com.example.myapplication.viewmodel.OnboardingViewModel
import android.content.Context
import androidx.core.content.edit
import com.example.myapplication.billing.BillingManager

class MainActivity : ComponentActivity() {
    private val sharedPreferences by lazy {
        getSharedPreferences("aigen_prefs", Context.MODE_PRIVATE)
    }
    private val billingManager by lazy {
        BillingManager(this).also { it.startConnection() }
    }

    private val isFirstLaunch: Boolean
        get() = sharedPreferences.getBoolean("is_first_launch", true)

    private fun markOnboardingCompleted() {
        sharedPreferences.edit { putBoolean("is_first_launch", false) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val mainViewModel: MainViewModel = viewModel()
                    val onboardingViewModel: OnboardingViewModel = viewModel()

                    val currentUser by mainViewModel.currentUser.collectAsState()
                    val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState()

                    // Определение начального экрана
                    val startDestination = when {
                        isFirstLaunch -> "onboarding"        // ПЕРВЫЙ ЗАПУСК → Онбординг
                        currentUser == null -> "login"       // Не авторизован → Логин
                        !onboardingCompleted -> "onboarding" // Онбординг не пройден → Онбординг
                        else -> "profile"                    // Все готово → ЛИЧНЫЙ КАБИНЕТ
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        // Экран входа
                        composable("login") {
                            LoginScreen(
                                viewModel = mainViewModel,
                                onNavigateToRegister = { navController.navigate("register") },
                                onLoginSuccess = {
                                    // Загружаем статус онбординга с сервера перед переходом
                                    val token = mainViewModel.token
                                    if (token.isNotEmpty()) {
                                        onboardingViewModel.loadOnboardingStatus(token)
                                    }
                                    // После входа всегда переходим в личный кабинет
                                    navController.navigate("profile") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Экран регистрации
                        composable("register") {
                            RegisterScreen(
                                viewModel = mainViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onRegisterSuccess = {
                                    // проверяем статус онбординга с сервера
                                    val token = mainViewModel.token
                                    if (token.isNotEmpty()) {
                                        onboardingViewModel.loadOnboardingStatus(token)
                                    }
                                    // После регистрации переходим в личный кабинет
                                    navController.navigate("profile") {
                                        popUpTo("register") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Экран онбординга
                        composable("onboarding") {
                            OnboardingScreen(
                                mainViewModel = mainViewModel,
                                onOnboardingComplete = { selectedPlan ->
                                    when (selectedPlan) {
                                        "monthly", "yearly" -> {
                                            // Платная подписка: переход на экран регистрации и оплаты
                                            navController.navigate("register_and_payment/$selectedPlan") {
                                                popUpTo("onboarding") { inclusive = true }
                                            }
                                        }
                                        else -> {
                                            // Демо-версия: если пользователь не авторизован — на экран входа,
                                            // иначе — сразу в профиль
                                            if (currentUser == null) {
                                                navController.navigate("login") {
                                                    popUpTo("onboarding") { inclusive = true }
                                                }
                                            } else {
                                                navController.navigate("profile") {
                                                    popUpTo("onboarding") { inclusive = true }
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        // Экран регистрации для активации демо-версии
                        composable("register_for_demo") {
                            RegisterForDemoScreen(
                                mainViewModel = mainViewModel,
                                onboardingViewModel = onboardingViewModel,
                                onDemoActivated = {
                                    markOnboardingCompleted()
                                    navController.navigate("home") {
                                        popUpTo("register_for_demo") { inclusive = true }
                                    }
                                },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        // Экран регистрации и оплаты подписки
                        composable(
                            route = "register_and_payment/{plan}",
                            arguments = listOf(navArgument("plan") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val plan = backStackEntry.arguments?.getString("plan") ?: "monthly"
                            RegisterAndPaymentScreen(
                                plan = plan,
                                mainViewModel = mainViewModel,
                                billingManager = billingManager,
                                onPaymentSuccess = {
                                    markOnboardingCompleted()
                                    navController.navigate("profile") {
                                        popUpTo("register_and_payment/$plan") { inclusive = true }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }


                        // Главный экран
                        composable("home") {
                            HomeScreen(
                                onNavigateToCatalog = { navController.navigate("catalog") },
                                onNavigateToMyAgents = { navController.navigate("my_agents") },
                                onNavigateToCreateAgent = { navController.navigate("create_agent") },
                                onNavigateToInvoke = { navController.navigate("invoke") },
                                onNavigateToOrchestrate = { navController.navigate("orchestrate") },
                                onNavigateToProfile = { navController.navigate("profile") }
                            )
                        }

                        // Экран профиля
                        composable("profile") {
                            ProfileScreen(
                                viewModel = mainViewModel,
                                onBack = {
                                    navController.navigate("home") {
                                        popUpTo("profile") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onLoggedOut = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Каталог агентов
                        composable("catalog") {
                            AgentCatalogScreen(
                                viewModel = mainViewModel,
                                onBack = { navController.popBackStack() },
                                onAgentSelected = { agent ->
                                    mainViewModel.setSelectedAgent(agent)
                                    navController.navigate("invoke")
                                }
                            )
                        }

                        // Мои агенты
                        composable("my_agents") {
                            MyAgentsScreen(
                                viewModel = mainViewModel,
                                onBack = { navController.popBackStack() },
                                onEditAgent = { agent ->
                                    navController.navigate("edit_agent/${agent.agentId}")
                                }
                            )
                        }

                        // Редактирование агента
                        composable(
                            route = "edit_agent/{agentId}",
                            arguments = listOf(navArgument("agentId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val agentId = backStackEntry.arguments?.getString("agentId") ?: ""
                            val myAgents by mainViewModel.myAgents.collectAsState()
                            val agent = myAgents.find { it.agentId == agentId }

                            if (agent != null) {
                                EditAgentScreen(
                                    agent = agent,
                                    viewModel = mainViewModel,
                                    onBack = { navController.popBackStack() },
                                    onAgentUpdated = { navController.popBackStack() }
                                )
                            } else {
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            }
                        }

                        // Создание агента
                        composable("create_agent") {
                            CreateAgentScreen(
                                viewModel = mainViewModel,
                                onBack = { navController.popBackStack() },
                                onAgentCreated = { navController.popBackStack() }
                            )
                        }

                        // Вызов агента
                        composable("invoke") {
                            InvokeAgentScreen(
                                viewModel = mainViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // Оркестрация
                        composable("orchestrate") {
                            OrchestrationScreen(
                                viewModel = mainViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}