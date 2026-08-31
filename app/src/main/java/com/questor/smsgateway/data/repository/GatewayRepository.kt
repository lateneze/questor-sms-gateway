package com.questor.smsgateway.data.repository

import com.questor.smsgateway.data.db.GatewayDatabase
import com.questor.smsgateway.data.db.entities.DeliveryReportEntity
import com.questor.smsgateway.data.db.entities.InboundMessageEntity
import com.questor.smsgateway.data.db.entities.OutboxMessageEntity
import kotlinx.coroutines.flow.Flow

class GatewayRepository(private val db: GatewayDatabase) {

    val pendingCountFlow: Flow<Int> = db.outboxDao().countPendingFlow()
    val sentCountFlow: Flow<Int> = db.outboxDao().countSentFlow()
    val deliveredCountFlow: Flow<Int> = db.outboxDao().countDeliveredFlow()
    val failedCountFlow: Flow<Int> = db.outboxDao().countFailedFlow()
    val inboundCountFlow: Flow<Int> = db.inboundDao().countTotalInboundFlow()

    val recentOutboxFlow: Flow<List<OutboxMessageEntity>> = db.outboxDao().getRecentMessagesFlow(100)
    val recentInboundFlow: Flow<List<InboundMessageEntity>> = db.inboundDao().getRecentInboundFlow(100)

    // Outbox Operations
    suspend fun enqueueOutboxMessage(message: OutboxMessageEntity) {
        db.outboxDao().insert(message)
    }

    suspend fun getOutboxMessage(messageId: String): OutboxMessageEntity? {
        return db.outboxDao().getById(messageId)
    }

    suspend fun updateOutboxMessage(message: OutboxMessageEntity) {
        db.outboxDao().update(message)
    }

    suspend fun getNextPendingOutboxMessage(): OutboxMessageEntity? {
        return db.outboxDao().getNextPending()
    }

    // Inbound Operations
    suspend fun recordInboundMessage(message: InboundMessageEntity) {
        db.inboundDao().insert(message)
    }

    suspend fun getUnacknowledgedInbound(limit: Int = 100): List<InboundMessageEntity> {
        return db.inboundDao().getUnacknowledged(limit)
    }

    suspend fun acknowledgeInbound(ids: List<String>) {
        db.inboundDao().markAcknowledged(ids)
    }

    // Delivery Report Operations
    suspend fun recordDeliveryReport(report: DeliveryReportEntity) {
        db.deliveryReportDao().insertOrUpdate(report)
        // Also update the outbox status if matching
        val outbox = db.outboxDao().getById(report.messageId)
        if (outbox != null) {
            val newStatus = if (report.status.equals("delivered", ignoreCase = true)) "DELIVERED" else "FAILED"
            db.outboxDao().update(
                outbox.copy(
                    status = newStatus,
                    deliveredAtUtc = report.updatedAtUtc,
                    errorMessage = if (newStatus == "FAILED") report.detail else outbox.errorMessage
                )
            )
        }
    }

    suspend fun getDeliveryReport(messageId: String): DeliveryReportEntity? {
        return db.deliveryReportDao().getByMessageId(messageId)
    }

    suspend fun getDeliveryReportsBatch(messageIds: List<String>): List<DeliveryReportEntity> {
        return db.deliveryReportDao().getByMessageIds(messageIds)
    }

    suspend fun acknowledgeDeliveryReports(messageIds: List<String>): Int {
        return db.deliveryReportDao().markAcknowledged(messageIds)
    }

    // Maintenance & Stats Reset Operations
    suspend fun clearCompletedHistory() {
        db.outboxDao().clearCompleted()
        db.inboundDao().clearAcknowledged()
        db.deliveryReportDao().clearAcknowledged()
    }

    suspend fun resetAllQueueStats() {
        db.outboxDao().clearAll()
        db.inboundDao().clearAll()
        db.deliveryReportDao().clearAll()
    }
}
