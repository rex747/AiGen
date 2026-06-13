package com.example.myapplication.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.*

/**
 * Индикатор прогресса онбординга (точки вверху экрана)
 */
@Composable
fun OnboardingProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val isCompleted = index < currentStep

            Box(
                modifier = Modifier
                    .size(if (isActive) 10.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isActive -> PrimaryLight
                            isCompleted -> SecondaryLight
                            else -> DividerLight
                        }
                    )
            )

            if (index < totalSteps - 1) {
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

/**
 * Заголовок карточки онбординга
 */
@Composable
fun OnboardingCardTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Контейнер для контента карточки
 */
@Composable
fun OnboardingCardContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

/**
 * Кнопка "Далее" для карточек онбординга
 */
@Composable
fun OnboardingNextButton(
    text: String = "Далее",
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryLight,
            contentColor = Color.White,
            disabledContainerColor = DividerLight
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Кнопка "Назад" для карточек онбординга
 */
@Composable
fun OnboardingBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(48.dp)
    ) {
        Text(
            text = "Назад",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

/**
 * Карточка с отзывом (для карточек 10-12)
 */
@Composable
fun ReviewCard(
    reviewerName: String,
    reviewText: String,
    rating: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Рейтинг (звезды)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                repeat(5) { index ->
                    Text(
                        text = if (index < rating.toInt()) "★" else "☆",
                        fontSize = 20.sp,
                        color = if (index < rating.toInt()) WarningLight else DividerLight
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format("%.1f", rating),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Текст отзыва
            Text(
                text = reviewText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Имя рецензента
            Text(
                text = reviewerName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Градиентный фон для карточек онбординга
 */
@Composable
fun OnboardingGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BackgroundLight,
                        BackgroundLight.copy(alpha = 0.95f)
                    )
                )
            ),
        content = content
    )
}

/**
 * Анимированный переход между карточками
 */
@Composable
fun AnimatedOnboardingCard(
    cardIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(Int) -> Unit // ИСПРАВЛЕНО: добавлен параметр Int
) {
    AnimatedContent(
        targetState = cardIndex,
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) + fadeIn() togetherWith
                    slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        },
        modifier = modifier
    ) { targetIndex ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            content(targetIndex) // ИСПРАВЛЕНО: передаём targetIndex в content
        }
    }
}