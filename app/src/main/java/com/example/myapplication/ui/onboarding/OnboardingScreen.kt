package com.example.myapplication.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.viewmodel.OnboardingViewModel
import com.example.myapplication.viewmodel.MainViewModel

/**
 * Главный экран онбординга со свайпами
 * Управляет отображением 20 карточек через HorizontalPager
 * НАВИГАЦИЯ ОСУЩЕСТВЛЯЕТСЯ ТОЛЬКО СВАЙПАМИ (без кнопок "Далее"/"Назад")
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    mainViewModel: MainViewModel,
    onOnboardingComplete: (selectedPlan: String) -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    // Состояние HorizontalPager для 20 карточек (индексы 0-19)
    val pagerState = rememberPagerState(pageCount = { 20 })

    val onboardingData by viewModel.onboardingData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedPlan by viewModel.selectedPlan.collectAsState()
    val aiResponse by viewModel.aiResponse.collectAsState()

    // Синхронизация pagerState с viewModel (для отслеживания текущей карточки)
    LaunchedEffect(pagerState.currentPage) {
        viewModel.goToCard(pagerState.currentPage)
    }

    // Обработка завершения онбординга
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()
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
            // Индикатор прогресса (точки вверху)
            OnboardingProgressIndicator(
                currentStep = pagerState.currentPage,
                totalSteps = 20
            )

            // HorizontalPager для свайпов — ЕДИНСТВЕННЫЙ механизм навигации
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) { page ->
                when (page) {
                    // ====================================================================
                    // КАРТОЧКИ 1-3: КРАТКИЙ РАССКАЗ О ПРИЛОЖЕНИИ
                    // ====================================================================
                    0 -> OnboardingCard1_Introduction()
                    1 -> OnboardingCard2_Features()
                    2 -> OnboardingCard3_Value()

                    // ====================================================================
                    // КАРТОЧКИ 4-6: ИНФОРМАЦИЯ О КЛИЕНТЕ
                    // ====================================================================
                    3 -> OnboardingCard4_AiPurpose(
                        aiPurpose = onboardingData.aiPurpose,
                        onPurposeSelected = { purpose ->
                            viewModel.updateOnboardingData { data ->
                                data.copy(aiPurpose = purpose)
                            }
                        }
                    )
                    4 -> OnboardingCard5_Industry(
                        industry = onboardingData.industry,
                        onIndustrySelected = { industry ->
                            viewModel.updateOnboardingData { data ->
                                data.copy(industry = industry)
                            }
                        }
                    )
                    5 -> OnboardingCard6_RequiredSkills(
                        selectedSkills = onboardingData.requiredSkills,
                        onSkillsChanged = { skills ->
                            viewModel.updateOnboardingData { data ->
                                data.copy(requiredSkills = skills)
                            }
                        }
                    )

                    // ====================================================================
                    // КАРТОЧКИ 7-9: ВРЕМЯ РЕШЕНИЯ ЗАДАЧ
                    // ====================================================================
                    6 -> OnboardingCard7_CurrentDuration(
                        currentDuration = onboardingData.currentTaskDurationHours,
                        onDurationChanged = { duration ->
                            viewModel.updateOnboardingData { data ->
                                data.copy(currentTaskDurationHours = duration)
                            }
                        }
                    )
                    7 -> OnboardingCard8_EstimatedAiDuration(
                        estimatedDuration = onboardingData.estimatedAiDurationHours,
                        onDurationChanged = { duration ->
                            viewModel.updateOnboardingData { data ->
                                data.copy(estimatedAiDurationHours = duration)
                            }
                        }
                    )
                    8 -> OnboardingCard9_TimeComparisonChart(
                        currentDuration = onboardingData.currentTaskDurationHours,
                        estimatedDuration = onboardingData.estimatedAiDurationHours
                    )

                    // ====================================================================
                    // КАРТОЧКИ 10-12: ЭФФЕКТ ПЕРСОНАЛИЗАЦИИ
                    // ====================================================================
                    9 -> OnboardingCard10_AgentConnections()
                    10 -> OnboardingCard11_OrchestrationPlatform()
                    11 -> OnboardingCard12_PersonalConductor()

                    // ====================================================================
                    // КАРТОЧКИ 13-15: ВОПРОС AI-АГЕНТУ
                    // ====================================================================
                    12 -> OnboardingCard13_AskAiQuestion(
                        aiResponse = aiResponse,
                        isLoading = isLoading,
                        onAskQuestion = { question ->
                            val token = mainViewModel.token
                            viewModel.askAiAgent(token, question)
                        }
                    )
                    13 -> OnboardingCard14_AiResponse(
                        aiResponse = aiResponse,
                        isLoading = isLoading
                    )
                    14 -> OnboardingCard15_AiCapabilities()

                    // ====================================================================
                    // КАРТОЧКИ 16-18: ЧАСТОТА ИСПОЛЬЗОВАНИЯ
                    // ====================================================================
                    15 -> OnboardingCard16_UsageFrequency(
                        frequency = onboardingData.usageFrequency,
                        onFrequencySelected = { frequency ->
                            viewModel.updateOnboardingData { data ->
                                data.copy(usageFrequency = frequency)
                            }
                        }
                    )
                    16 -> OnboardingCard17_UsageTimeOfDay(
                        timeOfDay = onboardingData.usageTimeOfDay,
                        onTimeSelected = { time ->
                            viewModel.updateOnboardingData { data ->
                                data.copy(usageTimeOfDay = time)
                            }
                        }
                    )
                    17 -> OnboardingCard18_LoadAnalysis(
                        frequency = onboardingData.usageFrequency,
                        timeOfDay = onboardingData.usageTimeOfDay
                    )

                    // ====================================================================
                    // КАРТОЧКИ 19-20: РЕЗЮМЕ И ВЫБОР ПЛАНА
                    // ====================================================================
                    18 -> OnboardingCard19_ProgressSummary(
                        onboardingData = onboardingData
                    )
                    19 -> OnboardingCard20_PlanSelection(
                        selectedPlan = selectedPlan,
                        onPlanSelected = { plan -> viewModel.setSelectedPlan(plan) },
                        onComplete = {
                            val token = mainViewModel.token
                            viewModel.completeOnboarding(token)
                            onOnboardingComplete(viewModel.selectedPlan.value)
                        }
                    )
                }
            }

            // ВАЖНО: КНОПКИ "ДАЛЕЕ"/"НАЗАД" ПОЛНОСТЬЮ ОТСУТСТВУЮТ!
            // Навигация осуществляется ТОЛЬКО через свайпы по карточкам

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