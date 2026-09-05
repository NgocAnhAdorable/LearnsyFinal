package com.learnsypro.app.ui.quiz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class ConfettiParticle(
    var x: Float,
    var y: Float,
    val vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    var rotation: Float,
    val rotSpeed: Float
)

private val confettiColors = listOf(
    Color(0xFFF472B6), Color(0xFFA855F7), Color(0xFF6366F1),
    Color(0xFF06B6D4), Color(0xFF10B981), Color(0xFFFBBF24)
)

/**
 * ── ConfettiCanvas ──
 * Tương đương _runConfetti(canvas) trong quiz-player.jsx: canvas particle
 * rơi từ trên xuống có trọng lực + xoay, tự dọn sau ~2.2s.
 */
@Composable
fun ConfettiCanvas(active: Boolean, widthPx: Float, heightPx: Float) {
    if (!active || widthPx <= 0f || heightPx <= 0f) return

    var particles by remember(active) {
        mutableStateOf(
            List(60) {
                ConfettiParticle(
                    x = Random.nextFloat() * widthPx,
                    y = -Random.nextFloat() * heightPx * 0.3f,
                    vx = (Random.nextFloat() - 0.5f) * 4f,
                    vy = Random.nextFloat() * 3f + 2f,
                    color = confettiColors.random(),
                    size = Random.nextFloat() * 6f + 4f,
                    rotation = Random.nextFloat() * 360f,
                    rotSpeed = (Random.nextFloat() - 0.5f) * 10f
                )
            }
        )
    }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val endTime = System.currentTimeMillis() + 2200
        while (System.currentTimeMillis() < endTime) {
            particles = particles.map { p ->
                p.copy(
                    x = p.x + p.vx,
                    y = p.y + p.vy,
                    vy = p.vy + 0.12f, // gravity
                    rotation = p.rotation + p.rotSpeed
                )
            }
            delay(16)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { p ->
            if (p.y < heightPx + 20f) {
                rotate(p.rotation, pivot = Offset(p.x, p.y)) {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(p.x - p.size / 2, p.y - p.size / 2),
                        size = androidx.compose.ui.geometry.Size(p.size, p.size * 0.6f)
                    )
                }
            }
        }
    }
}
