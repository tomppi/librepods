/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

@file:OptIn(ExperimentalEncodingApi::class)

package me.kavishdevar.librepods.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.utils.HeadTracking
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
fun HeadTrackingScreen(viewModel: AirPodsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val orientation by HeadTracking.orientation.collectAsState()
    val acceleration by HeadTracking.acceleration.collectAsState()
    val packetCount by HeadTracking.packetCount.collectAsState()
    val lastPacketAt by HeadTracking.lastPacketAt.collectAsState()

    // Keep the stream running while the user is inspecting the live indicator.
    DisposableEffect(Unit) {
        viewModel.startHeadTracking()
        onDispose {
            viewModel.stopHeadTracking()
        }
    }

    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    val topPadding =
        if (m3eEnabled) 0.dp
        else WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding =
        if (m3eEnabled) 0.dp
        else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp

    val receiving = state.headTrackingActive &&
        (System.currentTimeMillis() - lastPacketAt) < RECEIVING_WINDOW_MILLIS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(topPadding))

        StyledToggle(
            label = "Head tracking stream",
            checked = state.headTrackingActive,
            onCheckedChange = { if (it) viewModel.startHeadTracking() else viewModel.stopHeadTracking() },
            description = if (receiving) "Receiving head tracking data now" else "Waiting for head tracking data",
            header = true
        )

        StyledToggle(
            label = "Head gestures for calls",
            checked = state.headGesturesEnabled,
            onCheckedChange = viewModel::setHeadGesturesEnabled,
            description = "Nod to accept, shake to reject an incoming call"
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (receiving) "Live" else "No data yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (receiving) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SensorValue("Packets", packetCount.toString())
                    SensorValue("Pitch", orientation.pitch.round())
                    SensorValue("Yaw", orientation.yaw.round())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SensorValue("Accel H", acceleration.horizontal.round())
                    SensorValue("Accel V", acceleration.vertical.round())
                    SensorValue("", "")
                }
            }
        }

        Spacer(modifier = Modifier.height(bottomPadding))
    }
}

@Composable
private fun SensorValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private const val RECEIVING_WINDOW_MILLIS = 2_000L

private fun Float.round(): String = String.format("%.1f", this)
