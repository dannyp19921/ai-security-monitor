// backend/src/main/kotlin/com/securemonitor/repository/AuditLogRepository.kt
package com.securemonitor.repository

import com.securemonitor.model.AuditLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    fun findByUsernameOrderByTimestampDesc(username: String): List<AuditLog>
    fun findByActionOrderByTimestampDesc(action: String): List<AuditLog>
    fun findByTimestampBetweenOrderByTimestampDesc(start: Instant, end: Instant): List<AuditLog>
    fun findTop100ByOrderByTimestampDesc(): List<AuditLog>
}