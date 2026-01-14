// backend/src/main/kotlin/com/securemonitor/model/User.kt
package com.securemonitor.model

import jakarta.persistence.*
import java.time.Instant

/**
 * User entity representing an authenticated user in the system.
 *
 * Supports:
 * - Username/password authentication
 * - Role-based access control (RBAC)
 * - Multi-factor authentication (MFA/TOTP)
 * - OAuth2 federated login
 */
@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    val username: String,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(nullable = false)
    val passwordHash: String,

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")]
    )
    var roles: MutableSet<Role> = mutableSetOf(),

    @Column(nullable = false)
    val enabled: Boolean = true,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column
    val lastLogin: Instant? = null,

    // ============================================
    // MFA (Multi-Factor Authentication) fields
    // ============================================

    /**
     * Whether MFA is enabled for this user.
     * When true, users must provide a TOTP code after password authentication.
     */
    @Column(nullable = false)
    val mfaEnabled: Boolean = false,

    /**
     * Base32-encoded TOTP secret for generating/verifying codes.
     * Only set when MFA is enabled. Should be treated as highly sensitive.
     */
    @Column(length = 64)
    val mfaSecret: String? = null,

    /**
     * JSON array of hashed backup codes for account recovery.
     * Format: ["hash1", "hash2", ...]
     * Codes are SHA-256 hashed for secure storage.
     */
    @Column(length = 1024)
    val mfaBackupCodes: String? = null,

    /**
     * Timestamp when MFA was enabled.
     * Useful for audit logging and security analysis.
     */
    @Column
    val mfaEnabledAt: Instant? = null
)
