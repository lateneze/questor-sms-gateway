package com.questor.smsgateway.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gateway_logs")
data class GatewayLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampUtc: Long = System.currentTimeMillis(),
    val level: String, // INFO, WARN, ERROR
    val tag: String,
    val message: String
)
