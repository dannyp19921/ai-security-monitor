// backend/src/main/kotlin/com/securemonitor/mfa/service/MfaService.kt
package com.securemonitor.mfa.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.securemonitor.mfa.dto.*
import com.securemonitor.model.User
import com.securemonitor.repository.UserRepository
import com.securemonitor.service.AuditService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Service handling Multi-Factor Authentication (MFA) operations.
 *
 * Provides functionality for:
 * - MFA setup with TOTP
 * - Code verification during login
 * - Backup code management
 * - MFA disable with security verification
 *
 * Security considerations:
 * - Secrets are stored encrypted in production
 * - Backup codes are hashed before storage
 * - All operations are audit logged
 */
@Service
class MfaService(
    private val totpService: TotpService,
    private val userRepository: UserRepository,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper
) {

    companion object {
        const val ISSUER = "AI Security Monitor"
    }

    /**
     * Initiates MFA setup for a user.
     * Generates a new TOTP secret but does NOT enable MFA yet.
     * User must verify with a valid code to complete setup.
     *
     * @param userId The user's ID
     * @return Setup response with secret and QR code URI
     */
    fun initiateSetup(userId: Long): MfaSetupResponse {
        val user = findUserOrThrow(userId)

        if (user.mfaEnabled) {
            throw MfaException("MFA is already enabled for this account")
        }

        val secret = totpService.generateSecret()
        val qrCodeUri = totpService.generateTotpUri(secret, ISSUER, user.email)

        auditService.log(
            action = "MFA_SETUP_INITIATED",
            username = user.username,
            resourceType = "USER",
            resourceId = userId.toString(),
            details = "MFA setup initiated",
            success = true
        )

        return MfaSetupResponse(
            secret = secret,
            qrCodeUri = qrCodeUri,
            issuer = ISSUER,
            accountName = user.email
        )
    }

    /**
     * Completes MFA setup by verifying the first TOTP code.
     * If valid, enables MFA and generates backup codes.
     *
     * @param userId The user's ID
     * @param request Contains secret and verification code
     * @return Response with backup codes
     */
    @Transactional
    fun completeSetup(userId: Long, request: MfaSetupVerifyRequest): MfaSetupCompleteResponse {
        val user = findUserOrThrow(userId)

        if (user.mfaEnabled) {
            throw MfaException("MFA is already enabled for this account")
        }

        // Verify the code matches the provided secret
        if (!totpService.verifyCode(request.secret, request.code)) {
            auditService.log(
                action = "MFA_SETUP_FAILED",
                username = user.username,
                resourceType = "USER",
                resourceId = userId.toString(),
                details = "Invalid TOTP code during setup",
                success = false
            )
            throw MfaException("Invalid verification code")
        }

        // Generate backup codes
        val backupCodes = totpService.generateBackupCodes()
        val hashedBackupCodes = backupCodes.map { totpService.hashBackupCode(it) }
        val backupCodesJson = objectMapper.writeValueAsString(hashedBackupCodes)

        // Update user with MFA enabled
        val updatedUser = user.copy(
            mfaEnabled = true,
            mfaSecret = request.secret,
            mfaBackupCodes = backupCodesJson,
            mfaEnabledAt = Instant.now()
        )
        userRepository.save(updatedUser)

        auditService.log(
            action = "MFA_ENABLED",
            username = user.username,
            resourceType = "USER",
            resourceId = userId.toString(),
            details = "MFA enabled successfully",
            success = true
        )

        return MfaSetupCompleteResponse(
            mfaEnabled = true,
            backupCodes = backupCodes
        )
    }

    /**
     * Verifies a TOTP code for a user during login.
     *
     * @param userId The user's ID
     * @param code The 6-digit TOTP code
     * @return true if code is valid
     */
    fun verifyCode(userId: Long, code: String): Boolean {
        val user = findUserOrThrow(userId)
        val secret = user.mfaSecret

        if (!user.mfaEnabled || secret == null) {
            throw MfaException("MFA is not enabled for this account")
        }

        val isValid = totpService.verifyCode(secret, code)

        auditService.log(
            action = if (isValid) "MFA_VERIFY_SUCCESS" else "MFA_VERIFY_FAILED",
            username = user.username,
            resourceType = "USER",
            resourceId = userId.toString(),
            details = if (isValid) "TOTP code verified" else "Invalid TOTP code",
            success = isValid
        )

        return isValid
    }

    /**
     * Verifies a backup code and marks it as used.
     *
     * @param userId The user's ID
     * @param backupCode The backup code
     * @return Pair of (isValid, remainingCodes)
     */
    @Transactional
    fun verifyBackupCode(userId: Long, backupCode: String): Pair<Boolean, Int> {
        val user = findUserOrThrow(userId)
        val backupCodesJson = user.mfaBackupCodes

        if (!user.mfaEnabled || backupCodesJson == null) {
            throw MfaException("MFA is not enabled for this account")
        }

        val hashedCodes: MutableList<String> = objectMapper.readValue(backupCodesJson)
        val normalizedInput = backupCode.uppercase().replace("-", "")
        val hashedInput = totpService.hashBackupCode(normalizedInput)

        val matchIndex = hashedCodes.indexOfFirst { it == hashedInput }

        if (matchIndex == -1) {
            auditService.log(
                action = "MFA_BACKUP_CODE_FAILED",
                username = user.username,
                resourceType = "USER",
                resourceId = userId.toString(),
                details = "Invalid backup code",
                success = false
            )
            return Pair(false, hashedCodes.size)
        }

        // Remove used backup code
        hashedCodes.removeAt(matchIndex)
        val updatedBackupCodesJson = objectMapper.writeValueAsString(hashedCodes)

        val updatedUser = user.copy(mfaBackupCodes = updatedBackupCodesJson)
        userRepository.save(updatedUser)

        auditService.log(
            action = "MFA_BACKUP_CODE_USED",
            username = user.username,
            resourceType = "USER",
            resourceId = userId.toString(),
            details = "Backup code used, ${hashedCodes.size} remaining",
            success = true
        )

        return Pair(true, hashedCodes.size)
    }

    /**
     * Disables MFA for a user after verification.
     *
     * @param userId The user's ID
     * @param code TOTP code or backup code for verification
     * @return Response confirming MFA is disabled
     */
    @Transactional
    fun disableMfa(userId: Long, code: String): MfaDisableResponse {
        val user = findUserOrThrow(userId)
        val secret = user.mfaSecret
        val backupCodesJson = user.mfaBackupCodes

        if (!user.mfaEnabled) {
            throw MfaException("MFA is not enabled for this account")
        }

        // Try TOTP code first, then backup code
        val isValidTotp = secret?.let { totpService.verifyCode(it, code) } ?: false
        val isValidBackup = if (!isValidTotp && backupCodesJson != null) {
            val hashedCodes: List<String> = objectMapper.readValue(backupCodesJson)
            totpService.verifyBackupCode(code, hashedCodes)
        } else false

        if (!isValidTotp && !isValidBackup) {
            auditService.log(
                action = "MFA_DISABLE_FAILED",
                username = user.username,
                resourceType = "USER",
                resourceId = userId.toString(),
                details = "Invalid verification code",
                success = false
            )
            throw MfaException("Invalid verification code")
        }

        // Disable MFA
        val updatedUser = user.copy(
            mfaEnabled = false,
            mfaSecret = null,
            mfaBackupCodes = null,
            mfaEnabledAt = null
        )
        userRepository.save(updatedUser)

        auditService.log(
            action = "MFA_DISABLED",
            username = user.username,
            resourceType = "USER",
            resourceId = userId.toString(),
            details = "MFA disabled",
            success = true
        )

        return MfaDisableResponse()
    }

    /**
     * Generates new backup codes, invalidating previous ones.
     *
     * @param userId The user's ID
     * @param code Current TOTP code for verification
     * @return New backup codes
     */
    @Transactional
    fun regenerateBackupCodes(userId: Long, code: String): MfaBackupCodesResponse {
        val user = findUserOrThrow(userId)
        val secret = user.mfaSecret

        if (!user.mfaEnabled || secret == null) {
            throw MfaException("MFA is not enabled for this account")
        }

        // Verify current TOTP code
        if (!totpService.verifyCode(secret, code)) {
            auditService.log(
                action = "MFA_BACKUP_REGEN_FAILED",
                username = user.username,
                resourceType = "USER",
                resourceId = userId.toString(),
                details = "Invalid TOTP code",
                success = false
            )
            throw MfaException("Invalid verification code")
        }

        // Generate new backup codes
        val backupCodes = totpService.generateBackupCodes()
        val hashedBackupCodes = backupCodes.map { totpService.hashBackupCode(it) }
        val backupCodesJson = objectMapper.writeValueAsString(hashedBackupCodes)

        val updatedUser = user.copy(mfaBackupCodes = backupCodesJson)
        userRepository.save(updatedUser)

        auditService.log(
            action = "MFA_BACKUP_CODES_REGENERATED",
            username = user.username,
            resourceType = "USER",
            resourceId = userId.toString(),
            details = "Backup codes regenerated",
            success = true
        )

        return MfaBackupCodesResponse(backupCodes = backupCodes)
    }

    /**
     * Gets the MFA status for a user.
     *
     * @param userId The user's ID
     * @return MFA status
     */
    fun getMfaStatus(userId: Long): MfaStatusResponse {
        val user = findUserOrThrow(userId)
        val backupCodesJson = user.mfaBackupCodes

        val backupCodesRemaining = if (user.mfaEnabled && backupCodesJson != null) {
            val hashedCodes: List<String> = objectMapper.readValue(backupCodesJson)
            hashedCodes.size
        } else null

        return MfaStatusResponse(
            mfaEnabled = user.mfaEnabled,
            mfaEnabledAt = user.mfaEnabledAt?.toString(),
            backupCodesRemaining = backupCodesRemaining
        )
    }

    /**
     * Checks if a user has MFA enabled.
     *
     * @param userId The user's ID
     * @return true if MFA is enabled
     */
    fun isMfaEnabled(userId: Long): Boolean {
        return findUserOrThrow(userId).mfaEnabled
    }

    private fun findUserOrThrow(userId: Long): User {
        return userRepository.findById(userId)
            .orElseThrow { MfaException("User not found") }
    }
}

/**
 * Exception thrown for MFA-related errors.
 */
class MfaException(message: String) : RuntimeException(message)
