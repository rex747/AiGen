package com.example.myapplication.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.viewmodel.OnboardingViewModel
import com.example.myapplication.viewmodel.MainViewModel

/**
 * Главный экран онбординга
 * Управляет отображением 19 карточек и навигацией между ними
 */
@Composable
fun OnboardingScreen(
    mainViewModel: MainViewModel,
    onOnboardingComplete: (selectedPlan: String) -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val currentCardIndex by viewModel.currentCardIndex.collectAsState()
    val onboardingData by viewModel.onboardingData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()
    val selectedPlan by viewModel.selectedPlan.collectAsState()

    // Обработка завершения онбординга
    LaunchedEffect(onboardingCompleted) {
        if (onboardingCompleted) {
            onOnboardingComplete(selectedPlan)
        }
    }

    OnboardingGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Индикатор прогресса
            OnboardingProgressIndicator(
                currentStep = currentCardIndex,
                totalSteps = 20
            )

            // Контент карточки
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedOnboardingCard(cardIndex = currentCardIndex) {
                    when (currentCardIndex) {
                        // Карточки 1-3: Краткий рассказ о приложении
                        0 -> OnboardingCard1_Introduction(
                            onNext = { viewModel.nextCard() }
                        )
                        1 -> OnboardingCard2_Features(
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )
                        2 -> OnboardingCard3_Value(
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )

                        // Карточки 4-6: Информация о клиенте
                        3 -> OnboardingCard4_AiPurpose(
                            aiPurpose = onboardingData.aiPurpose,
                            onPurposeSelected = { purpose ->
                                viewModel.updateOnboardingData { data -> data.copy(aiPurpose = purpose) }
                            },
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )
                        4 -> OnboardingCard5_Industry(
                            industry = onboardingData.industry,
                            onIndustrySelected = { industry ->
                                viewModel.updateOnboardingData { data -> data.copy(industry = industry) }
                            },
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )
                        5 -> OnboardingCard6_RequiredSkills(
                            selectedSkills = onboardingData.requiredSkills,
                            onSkillsChanged = { skills ->
                                viewModel.updateOnboardingData { data -> data.copy(requiredSkills = skills) }
                            },
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )

                        // Карточки 7-9: Время решения задач
                        6 -> OnboardingCard7_CurrentDuration(
                            currentDuration = onboardingData.currentTaskDurationHours,
                            onDurationChanged = { duration ->
                                viewModel.updateOnboardingData { data -> data.copy(currentTaskDurationHours = duration) }
                            },
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )
                        7 -> OnboardingCard8_EstimatedAiDuration(
                            estimatedDuration = onboardingData.estimatedAiDurationHours,
                            onDurationChanged = { duration ->
                                viewModel.updateOnboardingData { data -> data.copy(estimatedAiDurationHours = duration) }
                            },
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )
                        8 -> OnboardingCard9_TimeComparisonChart(
                            currentDuration = onboardingData.currentTaskDurationHours,
                            estimatedDuration = onboardingData.estimatedAiDurationHours,
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )

                        // Карточки 10-12: Эффект персонализации
                        9 -> OnboardingCard10_AgentConnections(
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )
                        10 -> OnboardingCard11_OrchestrationPlatform(
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )
                        11 -> OnboardingCard12_PersonalConductor(
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )

                        // Карточки 13-15: Вопрос AI-агенту
                        12 -> OnboardingCard13_AskAiQuestion(
                            aiResponse = null,
                            isLoading = isLoading,
                            onAskQuestion = { question -> viewModel.askAiAgent(question) },
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )
                        13 -> OnboardingCard14_AiResponse(
                            aiResponse = viewModel.aiResponse.collectAsState().value,
                            isLoading = isLoading,
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )
                        14 -> OnboardingCard15_AiCapabilities(
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )

                        // Карточки 16-18: Частота использования
                        15 -> OnboardingCard16_UsageFrequency(
                            frequency = onboardingData.usageFrequency,
                            onFrequencySelected = { frequency ->
                                viewModel.updateOnboardingData { data -> data.copy(usageFrequency = frequency) }
                            },
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )
                        16 -> OnboardingCard17_UsageTimeOfDay(
                            timeOfDay = onboardingData.usageTimeOfDay,
                            onTimeSelected = { time ->
                                viewModel.updateOnboardingData { data -> data.copy(usageTimeOfDay = time) }
                            },
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )
                        17 -> OnboardingCard18_LoadAnalysis(
                            frequency = onboardingData.usageFrequency,
                            timeOfDay = onboardingData.usageTimeOfDay,
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )

                        // Карточка 19: Резюме прогресса
                        18 -> OnboardingCard19_ProgressSummary(
                            onboardingData = onboardingData,
                            onNext = { viewModel.nextCard() },
                            onBack = { viewModel.previousCard() }
                        )

                        // Карточка 20: Выбор плана
                        19 -> OnboardingCard20_PlanSelection(
                            selectedPlan = selectedPlan,
                            onPlanSelected = { viewModel.setSelectedPlan(it) },
                            onComplete = {
                                val token = mainViewModel.token
                                viewModel.completeOnboarding(token)
                            },
                            onBack = { viewModel.previousCard() }
                        )
                    }
                }
            }

            // Отображение ошибок
            error?.let { errorMessage ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(errorMessage)
                }
            }
        }
    }
}