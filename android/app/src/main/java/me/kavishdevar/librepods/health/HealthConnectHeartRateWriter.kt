/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.
*/

package me.kavishdevar.librepods.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneId

class HealthConnectHeartRateWriter(private val context: Context) {
    companion object {
        val HEART_RATE_WRITE_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(HeartRateRecord::class)
        )

        fun requestPermissionContract() = PermissionController.createRequestPermissionResultContract()
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasWritePermission(): Boolean {
        if (!isAvailable()) return false
        return client.permissionController.getGrantedPermissions()
            .containsAll(HEART_RATE_WRITE_PERMISSIONS)
    }

    suspend fun writeHeartRateSample(timestampMillis: Long, bpm: Int) {
        if (!isAvailable()) return
        if (bpm !in 1..300) return

        val start = Instant.ofEpochMilli(timestampMillis)
        val end = start.plusMillis(1)
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(start)

        val record = HeartRateRecord(
            startTime = start,
            startZoneOffset = zoneOffset,
            endTime = end,
            endZoneOffset = zoneOffset,
            samples = listOf(
                HeartRateRecord.Sample(
                    time = start,
                    beatsPerMinute = bpm.toLong()
                )
            ),
            metadata = Metadata.manualEntry()
        )

        client.insertRecords(listOf(record))
    }
}
