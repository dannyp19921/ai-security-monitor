// backend/src/main/kotlin/com/securemonitor/service/AiService.kt
package com.securemonitor.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.http.MediaType

@Service
class AiService(
    private val auditService: AuditService
) {

    @Value("\${groq.api-key:}")
    private lateinit var apiKey: String

    @Value("\${groq.model:llama-3.1-8b-instant}")
    private lateinit var model: String

    private val webClient = WebClient.builder()
        .baseUrl("https://api.groq.com/openai/v1")
        .build()

    fun chat(prompt: String, username: String): String {
        if (apiKey.isBlank()) {
            return "AI service is not configured. Please set GROQ_API_KEY."
        }

        val systemPrompt = """
            You are a security analyst AI assistant for the AI Security Monitor system.
            You help users understand security events, audit logs, and provide guidance
            on security best practices. Keep responses concise and actionable.
            Focus on IAM (Identity and Access Management) topics when relevant.
        """.trimIndent()

        val requestBody = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to prompt)
            ),
            "max_tokens" to 1024,
            "temperature" to 0.7
        )

        return try {
            val response = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()

            @Suppress("UNCHECKED_CAST")
            val choices = response?.get("choices") as? List<Map<String, Any>>
            val message = choices?.firstOrNull()?.get("message") as? Map<String, Any>
            val content = message?.get("content") as? String ?: "No response from AI"

            auditService.log(
                action = "AI_CHAT",
                username = username,
                resourceType = "AI",
                details = "Prompt: ${prompt.take(100)}..."
            )

            content
        } catch (e: Exception) {
            auditService.log(
                action = "AI_CHAT_FAILED",
                username = username,
                resourceType = "AI",
                details = "Error: ${e.message}",
                success = false
            )
            "Error communicating with AI service: ${e.message}"
        }
    }
}