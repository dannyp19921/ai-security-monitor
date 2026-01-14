// backend/src/main/kotlin/com/securemonitor/mfa/dto/MfaDtos.kt
package com.securemonitor.mfa.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * Response when initiating MFA setup.
 * Contains everything needed to display QR code and complete setup.
 */
data class MfaSetupResponse(
    /** Base32-encoded secret (display to user for manual entry) */
    val secret: String,
    
    /** otpauth:// URI for QR code generation */
    val qrCodeUri: String,
    
    /** Issuer name shown in authenticator app */
    val issuer: String,
    
    /** Account name shown in authenticator app */
    val accountName: String
)

/**
 * Request to verify MFA setup with initial TOTP code.
 */
data class MfaSetupVerifyRequest(
    /** The secret that was provided during setup */
    @field:NotBlank(message = "Secret is required")
    val secret: String,
    
    /** 6-digit TOTP code from authenticator app */
    @field:NotBlank(message = "Code is required")
    @field:Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits")
    val code: String
)

/**
 * Response after successfully enabling MFA.
 * Includes backup codes that user must save.
 */
data class MfaSetupCompleteResponse(
    /** Confirmation that MFA is now enabled */
    val mfaEnabled: Boolean = true,
    
    /** One-time display of backup codes (user must save these) */
    val backupCodes: List<String>,
    
    /** Message for the user */
    val message: String = "MFA enabled successfully. Save your backup codes in a safe place."
)

/**
 * Request to verify TOTP code during login.
 */
data class MfaVerifyRequest(
    /** 6-digit TOTP code from authenticator app */
    @field:NotBlank(message = "Code is required")
    @field:Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits")
    val code: String
)

/**
 * Request to verify using a backup code.
 */
data class MfaBackupCodeRequest(
    /** Backup code in format XXXX-XXXX or XXXXXXXX */
    @field:NotBlank(message = "Backup code is required")
    @field:Pattern(regexp = "^[A-Za-z0-9]{4}-?[A-Za-z0-9]{4}$", message = "Invalid backup code format")
    val backupCode: String
)

/**
 * Response after successful MFA verification.
 */
data class MfaVerifyResponse(
    /** JWT token for authenticated session */
    val token: String,
    
    /** Username of authenticated user */
    val username: String,
    
    /** User's roles */
    val roles: List<String>,
    
    /** Whether a backup code was used (user should generate new ones) */
    val backupCodeUsed: Boolean = false,
    
    /** Number of remaining backup codes (if backup code was used) */
    val remainingBackupCodes: Int? = null
)

/**
 * Request to disable MFA.
 * Requires current password for security.
 */
data class MfaDisableRequest(
    /** Current password to confirm identity */
    @field:NotBlank(message = "Password is required")
    val password: String,
    
    /** TOTP code OR backup code for verification */
    @field:NotBlank(message = "Verification code is required")
    val code: String
)

/**
 * Response after disabling MFA.
 */
data class MfaDisableResponse(
    /** Confirmation that MFA is now disabled */
    val mfaEnabled: Boolean = false,
    
    /** Message for the user */
    val message: String = "MFA has been disabled for your account."
)

/**
 * Response with new backup codes.
 */
data class MfaBackupCodesResponse(
    /** New backup codes (invalidates previous codes) */
    val backupCodes: List<String>,
    
    /** Message for the user */
    val message: String = "New backup codes generated. Previous codes are now invalid."
)

/**
 * General MFA status response.
 */
data class MfaStatusResponse(
    /** Whether MFA is enabled */
    val mfaEnabled: Boolean,
    
    /** When MFA was enabled (null if not enabled) */
    val mfaEnabledAt: String? = null,
    
    /** Number of remaining backup codes */
    val backupCodesRemaining: Int? = null
)
