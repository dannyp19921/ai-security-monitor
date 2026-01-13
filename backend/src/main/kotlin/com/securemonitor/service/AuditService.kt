// backend/src/main/kotlin/com/securemonitor/service/AuditService.kt
package com.securemonitor.service

import com.securemonitor.model.AuditLog
import com.securemonitor.repository.AuditLogRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AuditService(
    private val auditLogRepository: AuditLogRepository
) {

    fun log(
        action: String,
        username: String,
        resourceType: String? = null,
        resourceId: String? = null,
        ipAddress: String? = null,
        details: String? = null,
        success: Boolean = true
    ): AuditLog {
        val auditLog = AuditLog(
            action = action,
            username = username,
            resourceType = resourceType,
            resourceId = resourceId,
            ipAddress = ipAddress,
            details = details,
            success = success
        )
        return auditLogRepository.save(auditLog)
    }

    fun getRecentLogs(): List<AuditLog> {
        return auditLogRepository.findTop100ByOrderByTimestampDesc()
    }

    fun getLogsByUsername(username: String): List<AuditLog> {
        return auditLogRepository.findByUsernameOrderByTimestampDesc(username)
    }

    fun getLogsByAction(action: String): List<AuditLog> {
        return auditLogRepository.findByActionOrderByTimestampDesc(action)
    }

    fun getLogsByTimeRange(start: Instant, end: Instant): List<AuditLog> {
        return auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end)
    }
}