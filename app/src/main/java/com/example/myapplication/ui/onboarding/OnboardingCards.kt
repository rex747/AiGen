package com.example.myapplication.ui.onboarding

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.OnboardingData
import com.example.myapplication.ui.theme.*
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.myapplication.R
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing

// ============================================================================
// КАРТОЧКИ 1-3: КРАТКИЙ РАССКАЗ О ПРИЛОЖЕНИИ
// ============================================================================

/**
 * Карточка 1: Введение - B2B платформа для координации ИИ-агентов
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard1_Introduction() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Добавляем логотип над надписями
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Логотип AiGen",
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
        OnboardingCardTitle(
            title = "AiGen",
            subtitle = "B2B платформа для координации ИИ-агентов разных компаний"
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Объединяйте AI-агентов из разных организаций в единую экосистему для решения сложных бизнес-задач",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

/**
 * Карточка 2: Основные возможности платформы
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard2_Features() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingCardTitle(
            title = "Возможности платформы",
            subtitle = "Всё, что нужно для эффективной работы с AI"
        )

        Spacer(modifier = Modifier.height(32.dp))

        FeatureItem(
            icon = "🤖",
            title = "Маркетплейс агентов",
            description = "Выбирайте готовых AI-агентов или создавайте своих"
        )

        Spacer(modifier = Modifier.height(16.dp))

        FeatureItem(
            icon = "🔗",
            title = "Оркестрация процессов",
            description = "Связывайте агентов в цепочки для автоматизации сложных задач"
        )

        Spacer(modifier = Modifier.height(16.dp))

        FeatureItem(
            icon = "💡",
            title = "Навыки агентов",
            description = "Добавляйте специализированные навыки для расширения возможностей"
        )
    }
}

/**
 * Карточка 3: Ценность платформы
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard3_Value() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingCardTitle(
            title = "Почему AiGen?",
            subtitle = "Инфраструктурный проект нового поколения"
        )

        Spacer(modifier = Modifier.height(32.dp))

        ValueItem(
            value = "10x",
            label = "Ускорение бизнес-процессов"
        )

        Spacer(modifier = Modifier.height(24.dp))

        ValueItem(
            value = "24/7",
            label = "Автоматическая работа агентов"
        )

        Spacer(modifier = Modifier.height(24.dp))

        ValueItem(
            value = "100%",
            label = "Безопасный обмен данными"
        )
    }
}

// ============================================================================
// КАРТОЧКИ 4-6: ИНФОРМАЦИЯ О КЛИЕНТЕ
// ============================================================================

/**
 * Карточка 4: Для чего нужны AI-агенты
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard4_AiPurpose(
    aiPurpose: String,
    onPurposeSelected: (String) -> Unit
) {
    var selectedPurpose by remember { mutableStateOf(aiPurpose) }

    // Сохраняем выбор при изменении
    LaunchedEffect(selectedPurpose) {
        if (selectedPurpose.isNotBlank()) {
            onPurposeSelected(selectedPurpose)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingCardTitle(
            title = "Для чего вам нужны AI-агенты?",
            subtitle = "Выберите основную цель"
        )

        Spacer(modifier = Modifier.height(32.dp))

        val purposes = listOf(
            "Автоматизация рутинных задач",
            "Анализ данных и отчетность",
            "Обслуживание клиентов",
            "Маркетинг и продажи",
            "Управление проектами",
            "Другое"
        )

        purposes.forEach { purpose ->
            PurposeOption(
                text = purpose,
                selected = selectedPurpose == purpose,
                onClick = { selectedPurpose = purpose }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Карточка 5: Сфера применения
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard5_Industry(
    industry: String,
    onIndustrySelected: (String) -> Unit
) {
    var selectedIndustry by remember { mutableStateOf(industry) }

    LaunchedEffect(selectedIndustry) {
        if (selectedIndustry.isNotBlank()) {
            onIndustrySelected(selectedIndustry)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingCardTitle(
            title = "В какой сфере вы работаете?",
            subtitle = "Это поможет подобрать подходящих агентов"
        )

        Spacer(modifier = Modifier.height(32.dp))

        val industries = listOf(
            "Финансы и банкинг",
            "Ритейл и e-commerce",
            "Производство",
            "Логистика",
            "Здравоохранение",
            "Образование",
            "IT и технологии",
            "Другое"
        )

        industries.forEach { ind ->
            PurposeOption(
                text = ind,
                selected = selectedIndustry == ind,
                onClick = { selectedIndustry = ind }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Карточка 6: Необходимые навыки агентов
 * БЕЗ кнопок навигации — управление свайпами
 */
@SuppressLint("MutableCollectionMutableState")
@Composable
fun OnboardingCard6_RequiredSkills(
    selectedSkills: List<String>,
    onSkillsChanged: (List<String>) -> Unit
) {
    var skills by remember { mutableStateOf(selectedSkills.toSet()) }

    LaunchedEffect(skills) {
        onSkillsChanged(skills.toList())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingCardTitle(
            title = "Какие навыки нужны вашим агентам?",
            subtitle = "Выберите все подходящие варианты"
        )

        Spacer(modifier = Modifier.height(32.dp))

        val availableSkills = listOf(
            "Обработка естественного языка",
            "Компьютерное зрение",
            "Анализ данных",
            "Генерация контента",
            "Интеграция с API",
            "Машинное обучение",
            "Автоматизация процессов",
            "Работа с документами"
        )

        availableSkills.forEach { skill ->
            SkillCheckbox(
                text = skill,
                checked = skills.contains(skill),
                onCheckedChange = { checked ->
                    skills = if (checked) {
                        skills + skill   // ← Создаёт НОВЫЙ Set
                    } else {
                        skills - skill   // ← Создаёт НОВЫЙ Set
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ============================================================================
// КАРТОЧКИ 7-9: ВРЕМЯ РЕШЕНИЯ ЗАДАЧ
// ============================================================================

/**
 * Карточка 7: Текущее время решения задач
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard7_CurrentDuration(
    currentDuration: Int,
    onDurationChanged: (Int) -> Unit
) {
    var durationText by remember { mutableStateOf(if (currentDuration > 0) currentDuration.toString() else "") }

    // Парсинг и валидация введенного значения
    LaunchedEffect(durationText) {
        val parsedValue = durationText.toIntOrNull()
        if (parsedValue != null && parsedValue in 1..100) {
            onDurationChanged(parsedValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingCardTitle(
            title = "Сколько времени занимают ваши задачи?",
            subtitle = "Укажите среднее время выполнения типичной задачи (в часах)"
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = durationText,
            onValueChange = { newText ->
                // Разрешаем только цифры и ограничиваем длину
                if (newText.all { it.isDigit() } && newText.length <= 3) {
                    durationText = newText
                }
            },
            label = { Text("Количество часов") },
            placeholder = { Text("Например: 8") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Допустимый диапазон: от 1 до 100 часов",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        // Отображение текущего значения, если оно валидно
        val currentValue = durationText.toIntOrNull()
        if (currentValue != null && currentValue in 1..100) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "$currentValue часов",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryLight
            )
        }
    }
}

/**
 * Карточка 8: Ожидаемое время с AI-агентами
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard8_EstimatedAiDuration(
    estimatedDuration: Int,
    onDurationChanged: (Int) -> Unit
) {
    var durationText by remember { mutableStateOf(if (estimatedDuration > 0) estimatedDuration.toString() else "") }

    // Парсинг и валидация введенного значения
    LaunchedEffect(durationText) {
        val parsedValue = durationText.toIntOrNull()
        if (parsedValue != null && parsedValue in 1..100) {
            onDurationChanged(parsedValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingCardTitle(
            title = "Сколько времени это займет с AI?",
            subtitle = "Ожидаемое время выполнения с помощью AI-агентов (в часах)"
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = durationText,
            onValueChange = { newText ->
                // Разрешаем только цифры и ограничиваем длину
                if (newText.all { it.isDigit() } && newText.length <= 3) {
                    durationText = newText
                }
            },
            label = { Text("Количество часов") },
            placeholder = { Text("Например: 2") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "💡 AI-агенты обычно ускоряют процессы в 5-10 раз",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Допустимый диапазон: от 1 до 100 часов",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        // Отображение текущего значения, если оно валидно
        val currentValue = durationText.toIntOrNull()
        if (currentValue != null && currentValue in 1..100) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "$currentValue часов",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SecondaryLight
            )
        }
    }
}

/**
 * Карточка 9: График сравнения времени
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard9_TimeComparisonChart(
    currentDuration: Int,
    estimatedDuration: Int
) {
    val timeSaved = currentDuration - estimatedDuration
    val percentageSaved = if (currentDuration > 0) {
        ((timeSaved.toFloat() / currentDuration) * 100).toInt()
    } else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingCardTitle(
            title = "Экономия времени с AI",
            subtitle = "Сравнение текущего и ожидаемого времени"
        )

        Spacer(modifier = Modifier.height(48.dp))

        ComparisonBar(
            label = "Без AI",
            value = currentDuration,
            maxValue = currentDuration.coerceAtLeast(estimatedDuration),
            color = ErrorLight
        )

        Spacer(modifier = Modifier.height(24.dp))

        ComparisonBar(
            label = "С AI",
            value = estimatedDuration,
            maxValue = currentDuration.coerceAtLeast(estimatedDuration),
            color = SuccessLight
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SuccessLight.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Вы сэкономите",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$timeSaved часов ($percentageSaved%)",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = SuccessLight
                )
                Text(
                    text = "на каждой задаче",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ============================================================================
// КАРТОЧКИ 10-12: ЭФФЕКТ ПЕРСОНАЛИЗАЦИИ
// ============================================================================

/**
 * Карточка 10: Настройка связей AI-агентов
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard10_AgentConnections() {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        delay(500.milliseconds)
        progress = 1f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingCardTitle(
            title = "Настраиваем связи AI-агентов",
            subtitle = "Анализируем ваши задачи и подбираем оптимальную конфигурацию"
        )

        Spacer(modifier = Modifier.height(48.dp))

        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(120.dp),
            color = SuccessDark,
            strokeWidth = 8.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = BackgroundDark
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Алексей К.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black  // ← ЧЁРНЫЙ ЦВЕТ ИМЕНИ
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    repeat(5) { index ->
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Star,
                            contentDescription = "Star",
                            tint = if (index < 5) WarningLight else DividerLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Персонализация AI агентов заняла всего пару минут, но результат превзошел ожидания!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black  // ← ЧЁРНЫЙ ЦВЕТ ОТЗЫВА
                )
            }
        }
    }
}

/**
 * Карточка 11: Подготовка площадки оркестрации
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard11_OrchestrationPlatform() {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        delay(500.milliseconds)
        progress = 1f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingCardTitle(
            title = "Готовим площадку оркестрации",
            subtitle = "Создаем инфраструктуру для взаимодействия агентов"
        )

        Spacer(modifier = Modifier.height(48.dp))

        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(120.dp),
            color = SecondaryLight,
            strokeWidth = 8.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = SecondaryLight
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Мария С.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black  // ← ЧЁРНЫЙ ЦВЕТ ИМЕНИ
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    repeat(5) { index ->
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Star,
                            contentDescription = "Star",
                            tint = if (index < 5) WarningLight else DividerLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Оркестрация работает безупречно. Агенты взаимодействуют как единый механизм.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black  // ← ЧЁРНЫЙ ЦВЕТ ОТЗЫВА
                )
            }
        }
    }
}

/**
 * Карточка 12: Создание персонального дирижера
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard12_PersonalConductor() {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        delay(500.milliseconds)
        progress = 1f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingCardTitle(
            title = "Создаем персонального дирижера",
            subtitle = "Ваш личный AI-ассистент готов к работе"
        )

        Spacer(modifier = Modifier.height(48.dp))

        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(120.dp),
            color = TertiaryLight,
            strokeWidth = 8.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TertiaryLight
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Дмитрий В.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black  // ← ЧЁРНЫЙ ЦВЕТ ИМЕНИ
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    repeat(5) { index ->
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Star,
                            contentDescription = "Star",
                            tint = if (index < 5) WarningLight else DividerLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Персональный дирижер понимает мои задачи с полуслова. Невероятно удобно!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black  // ← ЧЁРНЫЙ ЦВЕТ ОТЗЫВА
                )
            }
        }
    }
}

// ============================================================================
// КАРТОЧКИ 13-15: ВОПРОС AI-АГЕНТУ
// ============================================================================

/**
 * Карточка 13: Задать вопрос AI-агенту
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard13_AskAiQuestion(
    aiResponse: String?,
    isLoading: Boolean,
    onAskQuestion: (String) -> Unit
) {
    var question by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingCardTitle(
            title = "Задайте вопрос AI-агенту",
            subtitle = "Попробуйте прямо сейчас без регистрации"
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Ваш вопрос") },
            placeholder = { Text("Например: Как автоматизировать обработку заказов?") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onAskQuestion(question) },
            enabled = question.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = SuccessLight
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Задать вопрос")
            }
        }

        if (aiResponse != null) {
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = PrimaryLight.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Ответ AI-агента:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Green
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = aiResponse,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Карточка 14: Ответ AI-агента (детальный просмотр)
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard14_AiResponse(
    aiResponse: String?,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingCardTitle(
            title = "Возможности AI-агента",
            subtitle = "Базовый агент готов помочь вам"
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(60.dp),
                color = PrimaryLight
            )
        } else if (aiResponse != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = CardBackground
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = aiResponse,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Black,
                        lineHeight = 24.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "💡 С платной версией вы получите доступ к специализированным агентам с расширенными навыками",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Карточка 15: Возможности AI-агентов
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard15_AiCapabilities() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingCardTitle(
            title = "Что умеют AI-агенты?",
            subtitle = "Широкий спектр возможностей для вашего бизнеса"
        )

        Spacer(modifier = Modifier.height(32.dp))

        CapabilityItem(
            icon = "📊",
            title = "Анализ данных",
            description = "Обработка больших объемов информации и выявление закономерностей"
        )

        Spacer(modifier = Modifier.height(16.dp))

        CapabilityItem(
            icon = "✍️",
            title = "Генерация контента",
            description = "Создание текстов, отчетов, презентаций"
        )

        Spacer(modifier = Modifier.height(16.dp))

        CapabilityItem(
            icon = "🔄",
            title = "Автоматизация процессов",
            description = "Выполнение рутинных задач без участия человека"
        )

        Spacer(modifier = Modifier.height(16.dp))

        CapabilityItem(
            icon = "🔍",
            title = "Поиск и исследование",
            description = "Быстрый поиск информации и анализ источников"
        )
    }
}

// ============================================================================
// КАРТОЧКИ 16-18: ЧАСТОТА ИСПОЛЬЗОВАНИЯ
// ============================================================================

/**
 * Карточка 16: Частота использования
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard16_UsageFrequency(
    frequency: String,
    onFrequencySelected: (String) -> Unit
) {
    var selectedFrequency by remember { mutableStateOf(frequency) }

    LaunchedEffect(selectedFrequency) {
        if (selectedFrequency.isNotBlank()) {
            onFrequencySelected(selectedFrequency)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingCardTitle(
            title = "Как часто вы планируете использовать AI-агентов?",
            subtitle = "Это поможет нам оптимизировать производительность"
        )

        Spacer(modifier = Modifier.height(32.dp))

        val frequencies = listOf(
            "Несколько раз в день",
            "Ежедневно",
            "Несколько раз в неделю",
            "Еженедельно",
            "Несколько раз в месяц",
            "По мере необходимости"
        )

        frequencies.forEach { freq ->
            PurposeOption(
                text = freq,
                selected = selectedFrequency == freq,
                onClick = { selectedFrequency = freq }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Карточка 17: Время суток использования
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard17_UsageTimeOfDay(
    timeOfDay: String,
    onTimeSelected: (String) -> Unit
) {
    var selectedTime by remember { mutableStateOf(timeOfDay) }

    LaunchedEffect(selectedTime) {
        if (selectedTime.isNotBlank()) {
            onTimeSelected(selectedTime)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingCardTitle(
            title = "В какое время суток вы будете работать с AI?",
            subtitle = "Для оптимального распределения ресурсов"
        )

        Spacer(modifier = Modifier.height(32.dp))

        val times = listOf(
            "Утро (6:00 - 12:00)",
            "День (12:00 - 18:00)",
            "Вечер (18:00 - 24:00)",
            "Ночь (0:00 - 6:00)",
            "Круглосуточно"
        )

        times.forEach { time ->
            PurposeOption(
                text = time,
                selected = selectedTime == time,
                onClick = { selectedTime = time }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Карточка 18: Анализ нагрузки
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard18_LoadAnalysis(
    frequency: String,
    timeOfDay: String
) {
    // Флаг запуска анимации
    var animationStarted by remember { mutableStateOf(false) }

    // Плавная анимация прогресса от 0 до 1 (0% до 100%)
    val progress by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(
            durationMillis = 2000,  // Длительность анимации: 2 секунды
            easing = LinearEasing   // Линейная анимация (равномерное заполнение)
        ),
        label = "load_analysis_progress"
    )

    // Запуск анимации после небольшой задержки
    LaunchedEffect(Unit) {
        delay(500.milliseconds)
        animationStarted = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingCardTitle(
            title = "Анализируем нагрузку",
            subtitle = "Оптимизируем систему под ваш график"
        )

        Spacer(modifier = Modifier.height(48.dp))

        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(120.dp),
            color = SuccessLight,  // ← ИЗМЕНЕНО: зеленый цвет индикатора
            strokeWidth = 8.dp,
            trackColor = DividerLight  // ← ДОБАВЛЕНО: цвет трека (фон индикатора)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = SuccessLight  // ← ИЗМЕНЕНО: зеленый цвет текста
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = SuccessLight.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Ваш профиль использования:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Частота: $frequency",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Время: $timeOfDay",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ============================================================================
// КАРТОЧКА 19: РЕЗЮМЕ ПРОГРЕССА
// ============================================================================

/**
 * Карточка 19: Резюме прогресса
 * БЕЗ кнопок навигации — управление свайпами
 */
@Composable
fun OnboardingCard19_ProgressSummary(
    onboardingData: OnboardingData
) {
    val timeSaved = onboardingData.currentTaskDurationHours - onboardingData.estimatedAiDurationHours

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Ваш персональный план",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = SuccessLight,  // ← ЗЕЛЕНЫЙ ЦВЕТ (#10B981)
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Прогресс при использовании AI-агентов",
            style = MaterialTheme.typography.bodyLarge,
            color = SuccessLight,  // ← ЗЕЛЕНЫЙ ЦВЕТ (#10B981)
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        SummaryCard(
            icon = "🎯",
            title = "Ваша цель",
            value = onboardingData.aiPurpose
        )

        Spacer(modifier = Modifier.height(16.dp))

        SummaryCard(
            icon = "🏢",
            title = "Сфера деятельности",
            value = onboardingData.industry
        )

        Spacer(modifier = Modifier.height(16.dp))

        SummaryCard(
            icon = "⏱️",
            title = "Экономия времени",
            value = "$timeSaved часов на задачу"
        )

        Spacer(modifier = Modifier.height(16.dp))

        SummaryCard(
            icon = "🛠️",
            title = "Необходимые навыки",
            value = "${onboardingData.requiredSkills.size} навыков выбрано"
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = SuccessLight.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Ожидаемый результат через 30 дней:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SuccessLight
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Автоматизация 80% рутинных задач",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ============================================================================
// КАРТОЧКА 20: ВЫБОР ПЛАНА
// ============================================================================

/**
 * Карточка 20: Выбор плана (демо, месячная, годовая подписка)
 * БЕЗ кнопки "Назад" — управление свайпами
 * Кнопка "Продолжить" ОСТАВЛЕНА — это не навигация, а завершение онбординга
 */
@Composable
fun OnboardingCard20_PlanSelection(
    selectedPlan: String,
    onPlanSelected: (String) -> Unit,
    onComplete: () -> Unit
) {
    var currentPlan by remember { mutableStateOf(selectedPlan) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingCardTitle(
            title = "Выберите подходящий план",
            subtitle = "Начните бесплатно или получите полный доступ"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Демо-версия
        PlanCard(
            title = "Демо-версия",
            price = "Бесплатно",
            period = "3 дня",
            features = listOf(
                "Доступ к базовому AI-агенту",
                "Без доступа к специализированным навыкам",
                "Ограничения на количество запросов"
            ),
            selected = currentPlan == "demo",
            onSelect = { currentPlan = "demo" },
            badge = "ПОПРОБОВАТЬ"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Месячная подписка
        PlanCard(
            title = "Месячная подписка",
            price = "$9.99",
            period = "в месяц",
            features = listOf(
                "Полный доступ ко всем агентам",
                "Все навыки и интеграции",
                "Приоритетная поддержка",
                "Неограниченные запросы"
            ),
            selected = currentPlan == "monthly",
            onSelect = { currentPlan = "monthly" }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Годовая подписка
        PlanCard(
            title = "Годовая подписка",
            price = "$110",
            period = "в год",
            features = listOf(
                "Всё из месячной подписки",
                "Экономия 8%",
                "Приоритетный доступ к новым функциям",
                "Персональный менеджер"
            ),
            selected = currentPlan == "yearly",
            onSelect = { currentPlan = "yearly" },
            badge = "ВЫГОДНО"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // КНОПКА "ПРОДОЛЖИТЬ" ОСТАВЛЕНА — это не навигация между карточками,
        // а завершение онбординга и переход к оплате/регистрации
        Button(
            onClick = {
                onPlanSelected(currentPlan)
                onComplete()
            },
            enabled = currentPlan.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryLight,
                contentColor = Color.White,
                disabledContainerColor = DividerLight
            )
        ) {
            Text(
                text = "Продолжить",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ============================================================================
// ВСПОМОГАТЕЛЬНЫЕ КОМПОНЕНТЫ
// ============================================================================

@Composable
private fun FeatureItem(
    icon: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ValueItem(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = SuccessLight
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun PurposeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) SecondaryLight.copy(alpha = 0.15f) else SurfaceLight
        ),
        border = if (selected) BorderStroke(width = 2.dp, color = SecondaryLight) else BorderStroke(width = 1.dp, color = DividerLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = SuccessLight,
                    unselectedColor = TertiaryLight.copy(alpha = 0.6f)
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) BackgroundDark else SurfaceDark
            )
        }
    }
}

@Composable
private fun SkillCheckbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) SuccessLight.copy(alpha = 0.15f) else CardBackground
        ),
        border = if (checked) BorderStroke(width = 2.dp, color = SuccessLight) else BorderStroke(width = 1.dp, color = DividerLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = SuccessLight,
                    uncheckedColor = OnBackgroundLight.copy(alpha = 0.6f),
                    checkmarkColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun ComparisonBar(
    label: String,
    value: Int,
    maxValue: Int,
    color: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$value часов",
                style = MaterialTheme.typography.labelLarge,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (maxValue > 0) value.toFloat() / maxValue else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            color = color,
            trackColor = DividerLight
        )
    }
}

@Composable
private fun CapabilityItem(
    icon: String,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black  // ← ИЗМЕНЕНО: цвет заголовка на черный
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black  // ← ИЗМЕНЕНО: цвет описания на черный
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    icon: String,
    title: String,
    value: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Black  // ← ИЗМЕНЕНО: цвет заголовка блока на черный
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black  // ← ДОБАВЛЕНО: цвет значения на черный
                )
            }
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    period: String,
    features: List<String>,
    selected: Boolean,
    onSelect: () -> Unit,
    badge: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) PrimaryLight.copy(alpha = 0.05f) else CardBackground
        ),
        border = if (selected) BorderStroke(width = 2.dp, color = PrimaryLight) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = price,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryLight
                        )
                        if (period != "Бесплатно") {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = period,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }

                badge?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (it == "ВЫГОДНО") SuccessLight else TertiaryLight
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            features.forEach { feature ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "✓",
                        color = SuccessLight,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) PrimaryLight else DividerLight,
                    contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(if (selected) "Выбрано" else "Выбрать")
            }
        }
    }
}