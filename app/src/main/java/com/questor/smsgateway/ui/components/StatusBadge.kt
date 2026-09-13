package com.questor.smsgateway.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.questor.smsgateway.ui.theme.DangerRed
import com.questor.smsgateway.ui.theme.DangerRedSubtle
import com.questor.smsgateway.ui.theme.SuccessGreen
import com.questor.smsgateway.ui.theme.SuccessGreenSubtle
import com.questor.smsgateway.ui.theme.WarningYellow
import com.questor.smsgateway.ui.theme.WarningYellowSubtle

@Composable
fun StatusBadge(
    isServiceRunning: Boolean,
    isConnected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        !isServiceRunning -> DangerRedSubtle
        isConnected -> SuccessGreenSubtle
        else -> WarningYellowSubtle
    }

    val dotColor = when {
        !isServiceRunning -> DangerRed
        isConnected -> SuccessGreen
        else -> WarningYellow
    }

    val textColor = when {
        !isServiceRunning -> DangerRed
        isConnected -> SuccessGreen
        else -> Color(0xFF856404)
    }

    val text = when {
        !isServiceRunning -> "SERVICE STOPPED"
        isConnected -> "CONNECTED"
        else -> "NOT CONNECTED / LISTENING"
    }

    // Pulsing halo animation for Connected state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isServiceRunning && isConnected) {
                    // Pulsing green halo aura
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer {
                                scaleX = glowScale
                                scaleY = glowScale
                                alpha = glowAlpha
                            }
                            .clip(CircleShape)
                            .background(SuccessGreen)
                    )
                }
                // Solid core dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
