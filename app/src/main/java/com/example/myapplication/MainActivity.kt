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

class MainActivity : ComponentActivity() {
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
                        currentUser == null -> "login" // Пользователь не авторизован
                        !onboardingCompleted -> "onboarding" // Онбординг не пройден
                        else -> "home" // Все готово, показываем главный экран
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
                                    // После входа проверяем, пройден ли онбординг
                                    if (!onboardingCompleted) {
                                        navController.navigate("onboarding") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("home") {
                                            popUpTo("login") { inclusive = true }
                                        }
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
                                    // После регистрации сразу показываем онбординг
                                    navController.navigate("onboarding") {
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
                                        "demo" -> {
                                            // Демо-версия: переход на главный экран
                                            navController.navigate("home") {
                                                popUpTo("onboarding") { inclusive = true }
                                            }
                                        }
                                        "monthly", "yearly" -> {
                                            // Платная подписка: переход на экран оплаты
                                            navController.navigate("payment/$selectedPlan") {
                                                popUpTo("onboarding") { inclusive = true }
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        // Экран оплаты
                        composable(
                            route = "payment/{plan}",
                            arguments = listOf(navArgument("plan") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val plan = backStackEntry.arguments?.getString("plan") ?: "monthly"
                            PaymentScreen(
                                plan = plan,
                                viewModel = mainViewModel,
                                onPaymentSuccess = {
                                    navController.navigate("home") {
                                        popUpTo("onboarding") { inclusive = true }
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
                                onBack = { navController.popBackStack() },
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