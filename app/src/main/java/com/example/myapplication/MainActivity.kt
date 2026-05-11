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
import com.example.myapplication.ui.screens.GeneratedCodeScreen
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.ui.screens.SkillCategoriesScreen
import com.example.myapplication.ui.screens.SkillsScreen       // можно оставить для совместимости
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val mainViewModel: MainViewModel = viewModel()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                viewModel = mainViewModel,
                                onNavigateToSkills = { navController.navigate("skills") },   // старый экран
                                onNavigateToCategories = { navController.navigate("categories") },
                                onNavigateToGenerated = {
                                    navController.navigate("generated")
                                }
                            )
                        }
                        composable("skills") {
                            SkillsScreen(
                                viewModel = mainViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("categories") {
                            SkillCategoriesScreen(
                                viewModel = mainViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToGenerated = {
                                    navController.navigate("generated")
                                }
                            )
                        }
                        composable("generated") {
                            GeneratedCodeScreen(
                                viewModel = mainViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}