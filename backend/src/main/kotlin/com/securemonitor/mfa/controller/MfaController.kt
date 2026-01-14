// backend/src/main/kotlin/com/securemonitor/mfa/controller/MfaController.kt
package com.securemonitor.mfa.controller

import com.securemonitor.mfa.dto.*
import com.securemonitor.mfa.service.MfaException
import com.securemonitor.mfa.service.MfaService
import com.securemonitor.repository.UserRepository
import com.securemonitor.security.JwtService
import com.securemonitor.service.AuditService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.time.Instant

/**
 * REST controller for Multi-Factor Authentication (MFA) operations.
 *
 * Endpoints:
 * - POST /api/auth/mfa/setup - Initiate MFA setup
 * - POST /api/auth/mfa/setup/verify - Complete MFA setup
 * - POST /api/auth/mfa/verify - Verify TOTP code
 * - POST /api/auth/mfa/backup - Verify backup code
 * - POST /api/auth/mfa/disable - Disable MFA
 * - POST /api/auth/mfa/backup-codes - Regenerate backup codes
 * - GET  /api/auth/mfa/status - Get MFA status
 */
@RestController
@RequestMapping("/api/auth/mfa")
class MfaController(
    private val mfaService: MfaService,
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val auditService: AuditService
) {

    /**
     * Initiates MFA setup for the current user.
     * Returns secret and QR code URI for authenticator app.
     */
    @PostMapping("/setup")
    fun initiateSetup(): ResponseEntity<MfaSetupResponse> {
        val userId = getCurrentUserId()
        val response = mfaService.initiateSetup(userId)
        return ResponseEntity.ok(response)
    }

    /**
     * Completes MFA setup by verifying the first TOTP code.
     * Returns backup codes on success.
     */
    @PostMapping("/setup/verify")
    fun completeSetup(
        @Valid @RequestBody request: MfaSetupVerifyRequest
    ): ResponseEntity<MfaSetupCompleteResponse> {
        val userId = getCurrentUserId()
        val response = mfaService.completeSetup(userId, request)
        return ResponseEntity.ok(response)
    }

    /**
     * Verifies a TOTP code during MFA login.
     * Returns a full JWT token on success.
     */
    @PostMapping("/verify")
    fun verifyCode(
        @Valid @RequestBody request: MfaVerifyRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Map<String, Any>> {
        val username = getCurrentUsername()
        val user = userRepository.findByUsername(username)
            .orElseThrow { MfaException("User not found") }
        
        val isValid = mfaService.verifyCode(user.id, request.code)
        val ipAddress = getClientIp(httpRequest)

        return if (isValid) {
            // Update last login
            userRepository.save(user.copy(lastLogin = Instant.now()))
            
            // Generate full token (not MFA pending)
            val roles = user.roles.map { it.name }
            val token = jwtService.generateToken(user.username, roles)
            
            auditService.log(
                action = "MFA_LOGIN_SUCCESS",
                username = username,
                resourceType = "USER",
                resourceId = user.id.toString(),
                ipAddress = ipAddress
            )
            
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "MFA verification successful",
                "token" to token
            ))
        } else {
            auditService.log(
                action = "MFA_LOGIN_FAILED",
                username = username,
                details = "Invalid TOTP code",
                ipAddress = ipAddress,
                success = false
            )
            
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "message" to "Invalid verification code"
            ))
        }
    }

    /**
     * Verifies a backup code during MFA login.
     * Returns a full JWT token on success.
     */
    @PostMapping("/backup")
    fun verifyBackupCode(
        @Valid @RequestBody request: MfaBackupCodeRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Map<String, Any>> {
        val username = getCurrentUsername()
        val user = userRepository.findByUsername(username)
            .orElseThrow { MfaException("User not found") }
        
        val (isValid, remainingCodes) = mfaService.verifyBackupCode(user.id, request.backupCode)
        val ipAddress = getClientIp(httpRequest)

        return if (isValid) {
            // Update last login
            userRepository.save(user.copy(lastLogin = Instant.now()))
            
            // Generate full token
            val roles = user.roles.map { it.name }
            val token = jwtService.generateToken(user.username, roles)
            
            auditService.log(
                action = "MFA_LOGIN_BACKUP_CODE",
                username = username,
                resourceType = "USER",
                resourceId = user.id.toString(),
                ipAddress = ipAddress,
                details = "Backup code used, $remainingCodes remaining"
            )
            
            val response = mutableMapOf<String, Any>(
                "success" to true,
                "message" to "Backup code verified",
                "token" to token,
                "remainingBackupCodes" to remainingCodes
            )
            if (remainingCodes <= 2) {
                response["warning"] = "You have only $remainingCodes backup codes remaining. Consider generating new ones."
            }
            ResponseEntity.ok(response.toMap())
        } else {
            auditService.log(
                action = "MFA_LOGIN_FAILED",
                username = username,
                details = "Invalid backup code",
                ipAddress = ipAddress,
                success = false
            )
            
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "success" to false,
                "message" to "Invalid backup code"
            ))
        }
    }

    /**
     * Disables MFA for the current user.
     * Requires verification with TOTP or backup code.
     */
    @PostMapping("/disable")
    fun disableMfa(
        @Valid @RequestBody request: MfaDisableRequest
    ): ResponseEntity<MfaDisableResponse> {
        val userId = getCurrentUserId()
        // Note: Password verification should be done here in production
        val response = mfaService.disableMfa(userId, request.code)
        return ResponseEntity.ok(response)
    }

    /**
     * Regenerates backup codes.
     * Requires verification with current TOTP code.
     * Invalidates all previous backup codes.
     */
    @PostMapping("/backup-codes")
    fun regenerateBackupCodes(
        @Valid @RequestBody request: MfaVerifyRequest
    ): ResponseEntity<MfaBackupCodesResponse> {
        val userId = getCurrentUserId()
        val response = mfaService.regenerateBackupCodes(userId, request.code)
        return ResponseEntity.ok(response)
    }

    /**
     * Gets the current MFA status for the user.
     */
    @GetMapping("/status")
    fun getMfaStatus(): ResponseEntity<MfaStatusResponse> {
        val userId = getCurrentUserId()
        val response = mfaService.getMfaStatus(userId)
        return ResponseEntity.ok(response)
    }

    /**
     * Gets the current authenticated user's ID.
     */
    private fun getCurrentUserId(): Long {
        val username = getCurrentUsername()
        return userRepository.findByUsername(username)
            .orElseThrow { MfaException("User not found") }
            .id
    }
    
    /**
     * Gets the current authenticated username.
     */
    private fun getCurrentUsername(): String {
        return SecurityContextHolder.getContext().authentication?.name
            ?: throw MfaException("Not authenticated")
    }
    
    /**
     * Gets client IP address from request.
     */
    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (xForwardedFor != null) {
            xForwardedFor.split(",")[0].trim()
        } else {
            request.remoteAddr
        }
    }

    /**
     * Exception handler for MFA-related errors.
     */
    @ExceptionHandler(MfaException::class)
    fun handleMfaException(ex: MfaException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.badRequest().body(mapOf(
            "success" to false,
            "error" to (ex.message ?: "MFA error")
        ))
    }
}
