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

    private val _packetCount = MutableStateFlow(0L)
    val packetCount = _packetCount.asStateFlow()

    private val _lastPacketAt = MutableStateFlow(0L)
    val lastPacketAt = _lastPacketAt.asStateFlow()

    private val _lastRawHex = MutableStateFlow("")
    val lastRawHex = _lastRawHex.asStateFlow()

    private val _lastParsed = MutableStateFlow("")
    val lastParsed = _lastParsed.asStateFlow()

    private val calibrationSamples = mutableListOf<Triple<Int, Int, Int>>()
    private var isCalibrated = false
    private var o1Neutral = 19000
    private var o2Neutral = 0
    private var o3Neutral = 0

    private const val CALIBRATION_SAMPLE_COUNT = 10
    private const val ORIENTATION_OFFSET = 5500

    // Sensor field offsets *inside the motion payload* as documented in
    // docs/AAP Definitions.md ("Received Head Tracking Sensor Data"). They are
    // relative to the payload located by parsing the SensorDataWX protobuf,
    // never absolute offsets into the whole AACP frame.
    private const val ORIENTATION_1_OFFSET = 0
    private const val ORIENTATION_2_OFFSET = 2
    private const val ORIENTATION_3_OFFSET = 4
    private const val ACCEL_HORIZONTAL_OFFSET = 8
    private const val ACCEL_VERTICAL_OFFSET = 10
    private const val SENSOR_PAYLOAD_MIN_SIZE = 12

    fun processPacket(packet: ByteArray) {
        // Parse the SensorDataWX protobuf to locate the motion payload, then read the
        // sensor values relative to it. No fixed frame offsets.
        val motion = RtBuddySensorData.parseMotionCommandPayloads(packet) ?: return

        _lastRawHex.value = packet.joinToString(" ") { "%02X".format(it) }
        _lastParsed.value = motion.payloads.joinToString("\n") { p ->
            "svc=${p.service} off=${p.frameOffset} len=${p.bytes.size} hex=${p.bytes.joinToString(" ") { "%02X".format(it) }}"
        }

        val payload = motion.payloads
            .filter { it.bytes.size >= SENSOR_PAYLOAD_MIN_SIZE }
            .firstOrNull()
            ?: return

        val data = payload.bytes
        if (data.size < SENSOR_PAYLOAD_MIN_SIZE) return

        _packetCount.value = _packetCount.value + 1
        _lastPacketAt.value = System.currentTimeMillis()

        val o1 = leInt16(data, ORIENTATION_1_OFFSET)
        val o2 = leInt16(data, ORIENTATION_2_OFFSET)
        val o3 = leInt16(data, ORIENTATION_3_OFFSET)

        val horizontalAccel = leInt16(data, ACCEL_HORIZONTAL_OFFSET).toFloat()
        val verticalAccel = leInt16(data, ACCEL_VERTICAL_OFFSET).toFloat()

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
        _packetCount.value = 0L
        _lastPacketAt.value = 0L
        _lastRawHex.value = ""
        _lastParsed.value = ""
    }
}
