package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.MainViewModel
import com.example.myapplication.ui.screens.LoginScreen
import com.example.myapplication.ui.screens.RegisterScreen
import com.example.myapplication.ui.screens.AgentCatalogScreen
import com.example.myapplication.ui.screens.CreateAgentScreen
import com.example.myapplication.ui.screens.InvokeAgentScreen
import com.example.myapplication.ui.screens.OrchestrationScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val mainViewModel: MainViewModel = viewModel()

                    NavHost(navController = navController, startDestination = "login") {
                        composable("login") {
                            LoginScreen(
                                viewModel = mainViewModel,
                                onNavigateToRegister = { navController.navigate("register") },
                                onLoginSuccess = {
                                    navController.navigate("home") {
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
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                onNavigateToCatalog = { navController.navigate("catalog") },
                                onNavigateToCreateAgent = { navController.navigate("create_agent") },
                                onNavigateToInvoke = { navController.navigate("invoke") },
                                onNavigateToOrchestrate = { navController.navigate("orchestrate") }
                            )
                        }
                        composable("catalog") {
                            AgentCatalogScreen(
                                viewModel = mainViewModel,
                                onBack = { navController.popBackStack() },
                                onAgentSelected = { agent ->
                                    // Можно сразу перейти на экран вызова, передав agent
                                    mainViewModel.setSelectedAgent(agent)
                                    navController.navigate("invoke")
                                }
                            )
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