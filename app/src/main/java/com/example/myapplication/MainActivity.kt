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

    private val isOnboardingCompleted: Boolean
        get() = sharedPreferences.getBoolean("is_onboarding_completed", false)

    private fun markOnboardingCompleted() {
        sharedPreferences.edit {
            putBoolean("is_first_launch", false)
            putBoolean("is_onboarding_completed", true)
        }
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

                    LaunchedEffect(Unit) {
                        val savedToken = sharedPreferences.getString("auth_token", null)
                        val savedEmail = sharedPreferences.getString("user_email", null)
                        val savedExpiresIn = sharedPreferences.getLong("saved_expires_in", 0L)

                        if (!savedToken.isNullOrEmpty() && !savedEmail.isNullOrEmpty()) {
                            mainViewModel.restoreSession(savedEmail, savedToken, savedExpiresIn)
                            onboardingViewModel.loadOnboardingStatus(savedToken)
                        }
                    }

                    val startDestination = when {
                        isFirstLaunch -> "onboarding"
                        currentUser == null -> "login"
                        !onboardingCompleted -> "onboarding"
                        else -> "profile"
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable("login") {
                            LoginScreen(
                                viewModel = mainViewModel,
                                onNavigateToRegister = { navController.navigate("register") },
                                onLoginSuccess = {
                                    val token = mainViewModel.token
                                    if (token.isNotEmpty()) {
                                        // === ИСПРАВЛЕНИЕ: Если онбординг пройден локально, сохраняем его на сервере ===
                                        if (onboardingViewModel.onboardingCompleted.value) {
                                            onboardingViewModel.completeOnboarding(token)
                                        } else {
                                            onboardingViewModel.loadOnboardingStatus(token)
                                        }
                                    }
                                    navController.navigate("profile") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("register") {
                            RegisterScreen(
                                viewModel = mainViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onRegisterSuccess = {
                                    val token = mainViewModel.token
                                    if (token.isNotEmpty()) {
                                        // === ИСПРАВЛЕНИЕ: Аналогичная логика для регистрации ===
                                        if (onboardingViewModel.onboardingCompleted.value) {
                                            onboardingViewModel.completeOnboarding(token)
                                        } else {
                                            onboardingViewModel.loadOnboardingStatus(token)
                                        }
                                    }
                                    navController.navigate("profile") {
                                        popUpTo("register") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("onboarding") {
                            OnboardingScreen(
                                mainViewModel = mainViewModel,
                                onOnboardingComplete = { selectedPlan ->
                                    markOnboardingCompleted()

                                    when (selectedPlan) {
                                        "monthly", "yearly" -> {
                                            navController.navigate("register_and_payment/$selectedPlan") {
                                                popUpTo("onboarding") { inclusive = true }
                                            }
                                        }
                                        else -> {
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

                        composable("my_agents") {
                            MyAgentsScreen(
                                viewModel = mainViewModel,
                                onBack = { navController.popBackStack() },
                                onEditAgent = { agent ->
                                    navController.navigate("edit_agent/${agent.agentId}")
                                }
                            )
                        }

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

                        composable("create_agent") {
                            CreateAgentScreen(
                                viewModel = mainViewModel,
                                onBack = { navController.popBackStack() },
                                onAgentCreated = { navController.popBackStack() }
                            )
                        }

                        composable("invoke") {
                            InvokeAgentScreen(
                                viewModel = mainViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

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