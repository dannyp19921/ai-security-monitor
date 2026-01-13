// backend/src/main/kotlin/com/securemonitor/controller/AuditController.kt
package com.securemonitor.controller

import com.securemonitor.model.AuditLog
import com.securemonitor.service.AuditService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/audit")
class AuditController(
    private val auditService: AuditService
) {

    @GetMapping("/logs")
    fun getRecentLogs(): ResponseEntity<List<AuditLog>> {
        return ResponseEntity.ok(auditService.getRecentLogs())
    }

    @GetMapping("/logs/user/{username}")
    fun getLogsByUsername(@PathVariable username: String): ResponseEntity<List<AuditLog>> {
        return ResponseEntity.ok(auditService.getLogsByUsername(username))
    }

    @GetMapping("/logs/action/{action}")
    fun getLogsByAction(@PathVariable action: String): ResponseEntity<List<AuditLog>> {
        return ResponseEntity.ok(auditService.getLogsByAction(action))
    }
}