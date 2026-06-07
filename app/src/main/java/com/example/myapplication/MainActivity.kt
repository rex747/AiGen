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
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.MainViewModel
import com.example.myapplication.ui.screens.LoginScreen
import com.example.myapplication.ui.screens.RegisterScreen
import com.example.myapplication.ui.screens.AgentCatalogScreen
import com.example.myapplication.ui.screens.CreateAgentScreen
import com.example.myapplication.ui.screens.InvokeAgentScreen
import com.example.myapplication.ui.screens.OrchestrationScreen
import com.example.myapplication.ui.screens.MyAgentsScreen
import com.example.myapplication.ui.screens.EditAgentScreen
import com.example.myapplication.ui.screens.ProfileScreen

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
                                onBack = { navController.popBackStack() },
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