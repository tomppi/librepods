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

package me.kavishdevar.librepods.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.kavishdevar.librepods.bluetooth.RtBuddySensorData
import kotlin.math.roundToInt

data class Orientation(val pitch: Float = 0f, val yaw: Float = 0f)
data class Acceleration(val vertical: Float = 0f, val horizontal: Float = 0f)

object HeadTracking {
    private val _orientation = MutableStateFlow(Orientation())
    val orientation = _orientation.asStateFlow()

    private val _acceleration = MutableStateFlow(Acceleration())
    val acceleration = _acceleration.asStateFlow()

    private val calibrationSamples = mutableListOf<Triple<Int, Int, Int>>()
    private var isCalibrated = false
    private var o1Neutral = 19000
    private var o2Neutral = 0
    private var o3Neutral = 0

    private const val CALIBRATION_SAMPLE_COUNT = 10
    private const val ORIENTATION_OFFSET = 5500

    // Sensor field offsets inside the full AACP frame as documented in
    // docs/AAP Definitions.md ("Received Head Tracking Sensor Data").
    private const val ORIENTATION_1_OFFSET = 43
    private const val ORIENTATION_2_OFFSET = 45
    private const val ORIENTATION_3_OFFSET = 47
    private const val ACCEL_HORIZONTAL_OFFSET = 51
    private const val ACCEL_VERTICAL_OFFSET = 53

    fun processPacket(packet: ByteArray) {
        // Gate extraction behind proper RTBuddy/SensorDataWX validation so only real
        // motion-sensor frames are interpreted as head-tracking data.
        val motion = RtBuddySensorData.parseMotionCommandPayloads(packet) ?: return
        if (motion.payloads.isEmpty()) return
        if (packet.size <= ACCEL_VERTICAL_OFFSET + 1) return

        val o1 = leInt16(packet, ORIENTATION_1_OFFSET)
        val o2 = leInt16(packet, ORIENTATION_2_OFFSET)
        val o3 = leInt16(packet, ORIENTATION_3_OFFSET)

        val horizontalAccel = leInt16(packet, ACCEL_HORIZONTAL_OFFSET).toFloat()
        val verticalAccel = leInt16(packet, ACCEL_VERTICAL_OFFSET).toFloat()

        if (!isCalibrated) {
            calibrationSamples.add(Triple(o1, o2, o3))
            if (calibrationSamples.size >= CALIBRATION_SAMPLE_COUNT) {
                calibrate()
            }
            return
        }

        val orientation = calculateOrientation(o1, o2, o3)
        _orientation.value = orientation

        _acceleration.value = Acceleration(verticalAccel, horizontalAccel)
    }

    private fun calibrate() {
        if (calibrationSamples.size < 3) return

        // Add offset during calibration
        o1Neutral = calibrationSamples.map { it.first + ORIENTATION_OFFSET }.average().roundToInt()
        o2Neutral = calibrationSamples.map { it.second + ORIENTATION_OFFSET }.average().roundToInt()
        o3Neutral = calibrationSamples.map { it.third + ORIENTATION_OFFSET }.average().roundToInt()

        isCalibrated = true
    }

    @Suppress("UnusedVariable")
    private fun calculateOrientation(o1: Int, o2: Int, o3: Int): Orientation {
        if (!isCalibrated) return Orientation()

        val o1Norm = (o1 + ORIENTATION_OFFSET) - o1Neutral
        val o2Norm = (o2 + ORIENTATION_OFFSET) - o2Neutral
        val o3Norm = (o3 + ORIENTATION_OFFSET) - o3Neutral

        val pitch = (o2Norm + o3Norm) / 2f / 32000f * 180f
        val yaw = (o2Norm - o3Norm) / 2f / 32000f * 180f

        return Orientation(pitch, yaw)
    }

    private fun leInt16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or (data[offset + 1].toInt() shl 8)

    fun reset() {
        calibrationSamples.clear()
        isCalibrated = false
        _orientation.value = Orientation()
        _acceleration.value = Acceleration()
    }
}
