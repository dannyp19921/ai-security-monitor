// backend/src/main/kotlin/com/securemonitor/model/AuditLog.kt
package com.securemonitor.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "audit_logs")
data class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val timestamp: Instant = Instant.now(),

    @Column(nullable = false)
    val action: String,

    @Column(nullable = false)
    val username: String,

    @Column
    val resourceType: String? = null,

    @Column
    val resourceId: String? = null,

    @Column
    val ipAddress: String? = null,

    @Column(length = 1000)
    val details: String? = null,

    @Column(nullable = false)
    val success: Boolean = true
)