// backend/src/main/kotlin/com/securemonitor/controller/HealthController.kt
package com.securemonitor.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api")
class HealthController {

    @GetMapping("/health")
    fun health(): Map<String, Any> {
        return mapOf(
            "status" to "UP",
            "service" to "ai-security-monitor",
            "timestamp" to Instant.now().toString()
        )
    }
}