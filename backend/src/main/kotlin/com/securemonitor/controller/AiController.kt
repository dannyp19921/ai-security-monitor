// backend/src/main/kotlin/com/securemonitor/controller/AiController.kt
package com.securemonitor.controller

import com.securemonitor.dto.ChatRequest
import com.securemonitor.dto.ChatResponse
import com.securemonitor.service.AiService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ai")
class AiController(
    private val aiService: AiService
) {

    @PostMapping("/chat")
    fun chat(@RequestBody request: ChatRequest): ResponseEntity<ChatResponse> {
        val username = SecurityContextHolder.getContext().authentication?.name ?: "anonymous"
        val response = aiService.chat(request.message, username)

        return ResponseEntity.ok(ChatResponse(response = response))
    }
}