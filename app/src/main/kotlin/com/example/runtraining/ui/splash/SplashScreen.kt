package com.example.runtraining.ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen branded launch screen: the timeline-bars logo, the app name, and
 * the tagline on the brand-blue gradient. Shown for ~2 s on cold start.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1E88E5), Color(0xFF0D47A1)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(Modifier.size(120.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                text = "FitToGym",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Bring YOUR workouts from the road to the gym",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }
    }
}

/** Four rounded bars + baseline (geometry from ic_launcher_foreground, 108 vp). */
@Composable
private fun BrandMark(modifier: Modifier) {
    Canvas(modifier) {
        val scale = size.minDimension / 108f
        val bars = listOf(
            listOf(28f, 46f, 12f, 22f),
            listOf(44f, 36f, 12f, 32f),
            listOf(60f, 28f, 12f, 40f),
            listOf(76f, 42f, 12f, 26f),
        )
        for (bar in bars) {
            val (x, y, w, h) = bar
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(x * scale, y * scale),
                size = Size(w * scale, h * scale),
                cornerRadius = CornerRadius(3f * scale, 3f * scale),
            )
        }
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(22f * scale, 72f * scale),
            size = Size(70f * scale, 4f * scale),
            cornerRadius = CornerRadius(1.5f * scale, 1.5f * scale),
        )
    }
}
