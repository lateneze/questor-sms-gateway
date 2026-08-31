package com.questor.smsgateway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.questor.smsgateway.ui.theme.DangerRed
import com.questor.smsgateway.ui.theme.DangerRedSubtle
import com.questor.smsgateway.ui.theme.SuccessGreen
import com.questor.smsgateway.ui.theme.SuccessGreenSubtle

@Composable
fun StatusBadge(
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isRunning) SuccessGreenSubtle else DangerRedSubtle
    val dotColor = if (isRunning) SuccessGreen else DangerRed
    val text = if (isRunning) "RUNNING / LISTENING" else "SERVICE STOPPED"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = dotColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
