package com.questor.smsgateway.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.questor.smsgateway.data.db.dao.DeliveryReportDao
import com.questor.smsgateway.data.db.dao.GatewayLogDao
import com.questor.smsgateway.data.db.dao.InboundDao
import com.questor.smsgateway.data.db.dao.OutboxDao
import com.questor.smsgateway.data.db.entities.DeliveryReportEntity
import com.questor.smsgateway.data.db.entities.GatewayLogEntity
import com.questor.smsgateway.data.db.entities.InboundMessageEntity
import com.questor.smsgateway.data.db.entities.OutboxMessageEntity

@Database(
    entities = [
        OutboxMessageEntity::class,
        InboundMessageEntity::class,
        DeliveryReportEntity::class,
        GatewayLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GatewayDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun inboundDao(): InboundDao
    abstract fun deliveryReportDao(): DeliveryReportDao
    abstract fun gatewayLogDao(): GatewayLogDao

    companion object {
        @Volatile
        private var INSTANCE: GatewayDatabase? = null

        fun getInstance(context: Context): GatewayDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GatewayDatabase::class.java,
                    "questor_sms_gateway.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
