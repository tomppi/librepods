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

package me.kavishdevar.librepods.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel

@Composable
fun HeadTrackingScreen(viewModel: AirPodsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    var result by remember { mutableStateOf<Boolean?>(null) }
    var testing by remember { mutableStateOf(false) }

    // Keep the stream running while the user is testing head tracking.
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
            description = "Keep the sensor stream running while testing",
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
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = when {
                        testing -> "Listening… nod or shake your head"
                        result == true -> "Yes"
                        result == false -> "No"
                        else -> "Tap test, then nod or shake"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (result) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurface
                    }
                )

                StyledButton(
                    onClick = {
                        result = null
                        testing = true
                        scope.launch {
                            val accepted = viewModel.testHeadTracking()
                            result = accepted
                            testing = false
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    maxScale = 0.05f
                ) {
                    Text(
                        text = if (testing) "Testing…" else "Test head tracking",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(bottomPadding))
    }
}
