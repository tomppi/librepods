/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.
*/

package me.kavishdevar.librepods.presentation.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.health.HealthConnectHeartRateWriter
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.presentation.viewmodel.HeartRatePoint
import me.kavishdevar.librepods.presentation.viewmodel.demoState
import kotlin.math.roundToInt

@Composable
fun HeartRateRoute(viewModel: AirPodsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val healthConnectPermissionsLauncher = rememberLauncherForActivityResult(
        contract = HealthConnectHeartRateWriter.requestPermissionContract()
    ) { grantedPermissions ->
        viewModel.setHeartRateHealthConnectSyncEnabled(
            grantedPermissions.containsAll(
                HealthConnectHeartRateWriter.HEART_RATE_WRITE_PERMISSIONS
            )
        )
    }

    LaunchedEffect(Unit) {
        viewModel.refreshHeartRateHealthConnectStatus()
    }

    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = if (m3eEnabled) 0.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = if (m3eEnabled) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        HeartRateScreen(
            enabled = state.heartRateStreamingEnabled,
            latestBpm = state.latestHeartRateBpm,
            samples = state.heartRateSamples,
            healthConnectSyncEnabled = state.heartRateHealthConnectSyncEnabled,
            healthConnectAvailable = state.heartRateHealthConnectAvailable,
            healthConnectStatus = state.heartRateHealthConnectStatus,
            topPadding = topPadding,
            bottomPadding = bottomPadding,
            setEnabled = viewModel::setHeartRateStreamingEnabled,
            setHealthConnectSyncEnabled = { enabled ->
                if (enabled) {
                    if (state.heartRateHealthConnectAvailable) {
                        healthConnectPermissionsLauncher.launch(
                            HealthConnectHeartRateWriter.HEART_RATE_WRITE_PERMISSIONS
                        )
                    } else {
                        viewModel.refreshHeartRateHealthConnectStatus()
                    }
                } else {
                    viewModel.setHeartRateHealthConnectSyncEnabled(false)
                }
            }
        )
    }
}

@Composable
fun HeartRateScreen(
    enabled: Boolean,
    latestBpm: Int?,
    samples: List<HeartRatePoint>,
    healthConnectSyncEnabled: Boolean,
    healthConnectAvailable: Boolean,
    healthConnectStatus: String,
    topPadding: Dp = 16.dp,
    bottomPadding: Dp = 16.dp,
    setEnabled: (Boolean) -> Unit,
    setHealthConnectSyncEnabled: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(topPadding))

        StyledToggle(
            label = "AirPods heart rate",
            description = "Uses Librepods' AACP socket. No Gadgetbridge bridge, no exported sample log.",
            checked = enabled,
            onCheckedChange = setEnabled
        )

        StyledToggle(
            label = "Sync to Health Connect",
            description = healthConnectStatus,
            checked = healthConnectSyncEnabled,
            enabled = healthConnectAvailable,
            onCheckedChange = setHealthConnectSyncEnabled
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = latestBpm?.let { "$it BPM" } ?: "No heart-rate sample yet",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Last 30 minutes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            HeartRateGraph(samples = samples)
        }

        StyledList(
            title = "Potential problems"
        ) {
            StyledListItem(
                name = "0x17 is shared with head tracking",
                description = "Short RTBuddy HEARTRATE frames are decoded before the normal head-tracking length check.",
                onClick = null
            )
            StyledListItem(
                name = "Graph is in-memory only",
                description = "The 30-minute history is lost if the app/service process is killed.",
                onClick = null
            )
            StyledListItem(
                name = "Health Connect writes only new samples",
                description = "Existing graph history is not backfilled; sync starts after permission is granted and the toggle is on.",
                onClick = null
            )
            StyledListItem(
                name = "No movement-byte rejection",
                description = "Every HEARTRATE(19) 18-byte payload is graphed; startup/transient spikes may appear.",
                onClick = null
            )
        }

        Spacer(modifier = Modifier.height(bottomPadding))
    }
}

@Composable
private fun HeartRateGraph(
    samples: List<HeartRatePoint>,
    modifier: Modifier = Modifier
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val redLine = Color.Red.copy(alpha = 0.28f)
    val gridValues = listOf(30, 50, 80, 100, 150, 200)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(surface, RoundedCornerShape(20.dp))
    ) {
        val leftPad = 44.dp.toPx()
        val rightPad = 8.dp.toPx()
        val topPad = 12.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val chartLeft = leftPad
        val chartRight = size.width - rightPad
        val chartTop = topPad
        val chartBottom = size.height - bottomPad
        val chartHeight = chartBottom - chartTop
        val chartWidth = chartRight - chartLeft
        val minBpm = 30f
        val maxBpm = 200f
        val now = System.currentTimeMillis()
        val cutoff = now - 30L * 60L * 1000L
        val visibleSamples = samples.filter { it.timestampMillis >= cutoff }

        fun yFor(bpm: Float): Float {
            val fraction = ((bpm - minBpm) / (maxBpm - minBpm)).coerceIn(0f, 1f)
            return chartBottom - fraction * chartHeight
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurface.copy(alpha = 0.65f).toArgb()
            textSize = 11.sp.toPx()
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        gridValues.forEach { bpm ->
            val y = yFor(bpm.toFloat())
            drawLine(
                color = redLine,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                bpm.toString(),
                chartLeft - 8.dp.toPx(),
                y + 4.dp.toPx(),
                labelPaint
            )
        }

        drawLine(
            color = onSurface.copy(alpha = 0.12f),
            start = Offset(chartLeft, chartBottom),
            end = Offset(chartRight, chartBottom),
            strokeWidth = 1.dp.toPx()
        )

        if (visibleSamples.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = onSurface.copy(alpha = 0.45f).toArgb()
                textSize = 14.sp.toPx()
                textAlign = Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                "Turn on heart rate to start graphing",
                size.width / 2f,
                size.height / 2f,
                emptyPaint
            )
            return@Canvas
        }

        val points = visibleSamples.map { sample ->
            val t = ((sample.timestampMillis - cutoff).toFloat() / (30f * 60f * 1000f)).coerceIn(0f, 1f)
            Offset(
                x = chartLeft + t * chartWidth,
                y = yFor(sample.bpm.toFloat())
            )
        }

        if (points.size == 1) {
            drawCircle(
                color = primary,
                radius = 4.dp.toPx(),
                center = points.first()
            )
        } else {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = path,
                color = primary,
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = primary,
                radius = 4.dp.toPx(),
                center = points.last()
            )
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurface.copy(alpha = 0.48f).toArgb()
            textSize = 11.sp.toPx()
            textAlign = Paint.Align.LEFT
        }
        drawContext.canvas.nativeCanvas.drawText(
            "30 min ago",
            chartLeft,
            size.height - 4.dp.toPx(),
            footerPaint
        )
        footerPaint.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(
            "now",
            chartRight,
            size.height - 4.dp.toPx(),
            footerPaint
        )
    }
}

@Preview(name = "Heart rate")
@Composable
private fun HeartRateScreenPreview() {
    LibrePodsTheme(m3eEnabled = false) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
            HeartRateScreen(
                enabled = true,
                latestBpm = demoState.latestHeartRateBpm,
                samples = demoState.heartRateSamples,
                healthConnectSyncEnabled = demoState.heartRateHealthConnectSyncEnabled,
                healthConnectAvailable = demoState.heartRateHealthConnectAvailable,
                healthConnectStatus = demoState.heartRateHealthConnectStatus,
                setEnabled = {},
                setHealthConnectSyncEnabled = {},
                bottomPadding = 16.dp
            )
        }
    }
}
