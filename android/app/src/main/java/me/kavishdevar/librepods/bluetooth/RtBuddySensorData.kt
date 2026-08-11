/*
    LibrePods - AirPods liberated from Apple's ecosystem
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

package me.kavishdevar.librepods.bluetooth

/**
 * RTBuddy / SensorDataWX protocol helpers.
 *
 * Field and service numbers mirror the RTBuddy dissectors published in the
 * pabloaul/apple-wireshark repository (plugins/rtbuddy/rtbuddy.proto and
 * plugins/rtbuddy/rtbuddy.lua).
 *
 * Head tracking, heart rate and other live sensor data all ride the same
 * "BuddyCommand" (AACP opcode 0x17) frames that carry the SensorDataWX
 * protobuf (RTBuddy descriptor 0x00100000). The Accessory streams a
 * `Command` sub-message per sensor service (ACCEL, GYRO, CMA, DEVMOTION6,
 * ACTIVITY, HEARTRATE, ...) whose payload holds the raw sensor samples.
 */
object RtBuddySensorData {

    const val AACP_HEADER_LENGTH = 4
    const val BUDDY_COMMAND_OPCODE = 0x17
    const val BUDDY_COMMAND_HEADER_LENGTH = 2
    const val RTBUDDY_HEADER_LENGTH = 6 // descriptor (4) + length (2)
    // AACP (4) + BuddyCommand opcode (2) + descriptor (4) + payload length (2)
    const val AACP_RTBUDDY_HEADER_LENGTH =
        AACP_HEADER_LENGTH + BUDDY_COMMAND_HEADER_LENGTH + RTBUDDY_HEADER_LENGTH

    const val MAX_PAYLOAD_LENGTH = 16 * 1024

    const val DESCRIPTOR_SENSOR = 0x00000800L
    const val DESCRIPTOR_SENSOR_DATA_WX = 0x00100000L

    // ServiceType from rtbuddy.proto
    const val SERVICE_DRVCOMM = 1
    const val SERVICE_TTR = 2
    const val SERVICE_ANALYTICS = 3
    const val SERVICE_ACTIN = 4
    const val SERVICE_KADABRA = 5
    const val SERVICE_MANDO = 6
    const val SERVICE_IED = 7
    const val SERVICE_NEO = 8
    const val SERVICE_NEORELAY = 9
    const val SERVICE_COMM = 10
    const val SERVICE_ACCEL = 11
    const val SERVICE_GYRO = 12
    const val SERVICE_PDR = 13
    const val SERVICE_ACTIVITY = 14
    const val SERVICE_CMA = 15
    const val SERVICE_DEVMOTION6 = 16
    const val SERVICE_SPL0 = 17
    const val SERVICE_HOSTLIBHID = 18
    const val SERVICE_HEARTRATE = 19

    val MOTION_SERVICES = setOf(
        SERVICE_ACCEL,
        SERVICE_GYRO,
        SERVICE_PDR,
        SERVICE_ACTIVITY,
        SERVICE_CMA,
        SERVICE_DEVMOTION6
    )

    // Fields of SensorDataWX (rtbuddy.proto)
    const val FIELD_SEQUENCE = 1
    const val FIELD_LOG_TYPE = 2
    const val FIELD_ANOTHER_SENSOR_STREAM = 3
    const val FIELD_REQUEST_ALL_DESCRIPTORS = 4
    const val FIELD_DESCRIPTOR = 5
    const val FIELD_CMD = 7
    const val FIELD_SERVICE_SETTINGS = 8
    const val FIELD_IDK_START_ACK = 9
    const val FIELD_CMD_ACK = 12

    // Fields of Command / ServiceSetting sub-messages
    const val FIELD_SERVICE = 1
    const val FIELD_TWO = 2
    const val FIELD_CONFIGURATION = 3
    const val FIELD_COMMAND_PAYLOAD = 3

    const val WIRE_VARINT = 0
    const val WIRE_FIXED64 = 1
    const val WIRE_LENGTH_DELIMITED = 2
    const val WIRE_FIXED32 = 5

    // type=0x0004, service=0x0004, opcode=0x0017, descriptor=0x00100000
    val RTBUDDY_FRAME_PREFIX = byteArrayOf(
        0x04, 0x00, 0x04, 0x00,
        0x17, 0x00,
        0x00, 0x00, 0x10, 0x00
    )

    /** True when [data] is a plausible RTBuddy SensorDataWX frame. */
    fun isSensorDataWxFrame(data: ByteArray, frameOffset: Int = 0): Boolean {
        if (data.size - frameOffset < AACP_HEADER_LENGTH + BUDDY_COMMAND_HEADER_LENGTH) return false
        if (!data.startsWith(RTBUDDY_FRAME_PREFIX, frameOffset)) return false
        if (frameDescriptor(data, frameOffset) != DESCRIPTOR_SENSOR_DATA_WX) return false
        val payloadLength = framePayloadLength(data, frameOffset) ?: return false
        if (payloadLength <= 0 || payloadLength > MAX_PAYLOAD_LENGTH) return false
        return frameOffset + AACP_HEADER_LENGTH + BUDDY_COMMAND_HEADER_LENGTH +
            RTBUDDY_HEADER_LENGTH + payloadLength <= data.size
    }

    /** Little-endian 32-bit RTBuddy descriptor at bytes [frameOffset+6..frameOffset+9]. */
    fun frameDescriptor(data: ByteArray, frameOffset: Int = 0): Long? {
        val start = frameOffset + AACP_HEADER_LENGTH + BUDDY_COMMAND_HEADER_LENGTH
        if (start + 4 > data.size) return null
        return data.readLeUint32(start).toLong()
    }

    /** Little-endian 16-bit RTBuddy payload length at bytes [frameOffset+10..frameOffset+11]. */
    fun framePayloadLength(data: ByteArray, frameOffset: Int = 0): Int? {
        val start = frameOffset + AACP_HEADER_LENGTH + BUDDY_COMMAND_HEADER_LENGTH + 4
        if (start + 2 > data.size) return null
        return data.readLe16(start)
    }

    /**
     * Parses the SensorDataWX protobuf of a frame and returns every length-delimited
     * payload candidate along with where it lives inside the frame, plus the frame's
     * sequence and log type. Sub-messages are traversed to the same bounded depth used
     * by the heart-rate decoder because some firmware variants wrap data in nested
     * messages. Sensor values are then read relative to a located payload rather than
     * from fixed offsets in the frame.
     */
    fun parseMotionCommandPayloads(data: ByteArray, frameOffset: Int = 0): MotionData? {
        if (!isSensorDataWxFrame(data, frameOffset)) return null
        val protoStart = frameOffset + AACP_RTBUDDY_HEADER_LENGTH
        val protoEnd = data.size

        // Some frames carry two trailing bytes when log_type has bit 0x4 set
        // (see rtbuddy.lua). Detect them by parsing with the trailer included and,
        // if that yields a service field, also try without it.
        val topLevel = parseProtoMessage(data, protoStart, protoEnd) ?: return null

        val sequence = topLevel.firstVarint(FIELD_SEQUENCE)?.toInt() ?: -1
        val logType = topLevel.firstVarint(FIELD_LOG_TYPE)?.toInt() ?: -1

        val payloads = mutableListOf<MotionPayload>()
        topLevel.fields.forEach { field ->
            if (field.wireType == WIRE_LENGTH_DELIMITED &&
                payloads.size < MAX_PAYLOADS
            ) {
                collectPayloads(
                    data = data,
                    start = field.valueStart,
                    end = field.valueEnd,
                    depth = 0,
                    payloads = payloads
                )
            }
        }

        // Fallback: if the first parse didn't traverse (e.g. nested envelope), retry the
        // whole protobuf once more allowing the two-byte trailer stripped.
        if (payloads.isEmpty()) {
            val retried = parseProtoMessage(data, protoStart, protoEnd - 2) ?: return null
            retried.fields.forEach { field ->
                if (field.wireType == WIRE_LENGTH_DELIMITED &&
                    payloads.size < MAX_PAYLOADS
                ) {
                    collectPayloads(
                        data = data,
                        start = field.valueStart,
                        end = field.valueEnd,
                        depth = 0,
                        payloads = payloads
                    )
                }
            }
        }

        return MotionData(
            sequence = sequence,
            logType = logType,
            payloads = payloads
        )
    }

    private fun collectPayloads(
        data: ByteArray,
        start: Int,
        end: Int,
        depth: Int,
        payloads: MutableList<MotionPayload>
    ) {
        if (depth > MAX_COMMAND_ENVELOPE_DEPTH || payloads.size >= MAX_PAYLOADS) return
        val message = parseProtoMessage(data, start, end) ?: return
        val service = message.firstVarint(FIELD_SERVICE)?.toInt()

        message.fields.forEach { field ->
            if (field.wireType == WIRE_LENGTH_DELIMITED) {
                // Capture the payload bytes and their absolute position in the frame.
                val bytes = data.copyOfRange(field.valueStart, field.valueEnd)
                val payload = MotionPayload(
                    bytes = bytes,
                    frameOffset = field.valueStart,
                    service = service
                )
                if (payloads.none { it.bytes.contentEquals(bytes) }) {
                    payloads += payload
                }
                if (payloads.size >= MAX_PAYLOADS) return
            }
        }

        if (depth == MAX_COMMAND_ENVELOPE_DEPTH) return
        message.fields.forEach { field ->
            if (field.wireType == WIRE_LENGTH_DELIMITED &&
                payloads.size < MAX_PAYLOADS
            ) {
                collectPayloads(
                    data = data,
                    start = field.valueStart,
                    end = field.valueEnd,
                    depth = depth + 1,
                    payloads = payloads
                )
            }
        }
    }

    private fun parseProtoMessage(data: ByteArray, start: Int, end: Int): ProtoMessage? {
        if (start < 0 || end < start || end > data.size) return null
        val fields = mutableListOf<ProtoField>()
        var index = start
        while (index < end) {
            if (fields.size >= MAX_PROTO_FIELDS) return null
            val key = readVarint(data, index, end) ?: return null
            index = key.nextIndex
            val fieldNumber = key.value ushr 3
            if (fieldNumber <= 0 || fieldNumber > MAX_PROTO_FIELD_NUMBER) return null
            val wireType = (key.value and 0x07).toInt()

            when (wireType) {
                WIRE_VARINT -> {
                    val value = readVarint(data, index, end) ?: return null
                    fields += ProtoField(
                        number = fieldNumber.toInt(),
                        wireType = wireType,
                        varintValue = value.value,
                        valueStart = index,
                        valueEnd = value.nextIndex
                    )
                    index = value.nextIndex
                }

                WIRE_LENGTH_DELIMITED -> {
                    val length = readVarint(data, index, end) ?: return null
                    if (length.value > Int.MAX_VALUE) return null
                    val valueEnd = length.nextIndex + length.value.toInt()
                    if (valueEnd < length.nextIndex || valueEnd > end) return null
                    fields += ProtoField(
                        number = fieldNumber.toInt(),
                        wireType = wireType,
                        valueStart = length.nextIndex,
                        valueEnd = valueEnd
                    )
                    index = valueEnd
                }

                WIRE_FIXED64 -> {
                    if (end - index < 8) return null
                    fields += ProtoField(
                        number = fieldNumber.toInt(),
                        wireType = wireType,
                        valueStart = index,
                        valueEnd = index + 8
                    )
                    index += 8
                }

                WIRE_FIXED32 -> {
                    if (end - index < 4) return null
                    fields += ProtoField(
                        number = fieldNumber.toInt(),
                        wireType = wireType,
                        valueStart = index,
                        valueEnd = index + 4
                    )
                    index += 4
                }

                else -> return null
            }
        }
        return ProtoMessage(fields)
    }

    private fun readVarint(data: ByteArray, start: Int, end: Int): VarintRead? {
        var value = 0L
        var shift = 0
        var index = start
        while (index < end && shift < 64) {
            val byte = data[index++].toInt().and(0xFF)
            value = value or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return VarintRead(value, index)
            shift += 7
        }
        return null
    }

    private data class ProtoMessage(val fields: List<ProtoField>) {
        fun firstVarint(fieldNumber: Int): Long? = fields.firstOrNull {
            it.number == fieldNumber && it.wireType == WIRE_VARINT
        }?.varintValue
    }

    private data class ProtoField(
        val number: Int,
        val wireType: Int,
        val varintValue: Long? = null,
        val valueStart: Int,
        val valueEnd: Int
    )

    private data class VarintRead(val value: Long, val nextIndex: Int)

    data class MotionData(
        val sequence: Int,
        val logType: Int,
        val payloads: List<MotionPayload>
    )

    data class MotionPayload(
        val bytes: ByteArray,
        /** Absolute offset of the payload's first byte inside the full AACP frame. */
        val frameOffset: Int = -1,
        /** RTBuddy service this payload belongs to, if the parent message carried one. */
        val service: Int? = null
    )

    private const val MAX_COMMAND_ENVELOPE_DEPTH = 3
    private const val MAX_PAYLOADS = 16
    private const val MAX_PROTO_FIELDS = 96
    private const val MAX_PROTO_FIELD_NUMBER = 4_096L

    private fun ByteArray.startsWith(prefix: ByteArray, offset: Int): Boolean {
        if (offset < 0 || offset + prefix.size > size) return false
        for (i in prefix.indices) {
            if (this[offset + i] != prefix[i]) return false
        }
        return true
    }

    private fun ByteArray.readLe16(offset: Int): Int =
        this[offset].toInt().and(0xFF) or (this[offset + 1].toInt().and(0xFF) shl 8)

    private fun ByteArray.readLeUint32(offset: Int): Int =
        this[offset].toInt().and(0xFF) or
            (this[offset + 1].toInt().and(0xFF) shl 8) or
            (this[offset + 2].toInt().and(0xFF) shl 16) or
            (this[offset + 3].toInt().and(0xFF) shl 24)
}
