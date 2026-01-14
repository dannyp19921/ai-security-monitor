package com.securemonitor.oauth2.repository

import com.securemonitor.oauth2.model.AuthorizationCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional

/**
 * Repository for managing OAuth 2.0 authorization codes.
 * 
 * Authorization codes are critical security tokens and should be:
 * - Retrieved and invalidated atomically
 * - Cleaned up after expiration
 * - Audited when used
 */
@Repository
interface AuthorizationCodeRepository : JpaRepository<AuthorizationCode, Long> {
    
    /**
     * Find an authorization code by its value.
     * 
     * @param code The authorization code string
     * @return The authorization code if found
     */
    fun findByCode(code: String): Optional<AuthorizationCode>
    
    /**
     * Find an unused, non-expired authorization code.
     * This is the primary method for validating codes during token exchange.
     * 
     * @param code The authorization code string
     * @param now Current timestamp for expiration check
     * @return The valid authorization code if found
     */
    @Query("""
        SELECT ac FROM AuthorizationCode ac 
        WHERE ac.code = :code 
        AND ac.used = false 
        AND ac.expiresAt > :now
    """)
    fun findValidCode(
        @Param("code") code: String, 
        @Param("now") now: Instant
    ): Optional<AuthorizationCode>
    
    /**
     * Find all codes for a specific user.
     * Useful for revoking all codes when a user logs out.
     * 
     * @param userId The user ID
     * @return List of authorization codes for the user
     */
    fun findByUserId(userId: Long): List<AuthorizationCode>
    
    /**
     * Find all codes for a specific client.
     * Useful for revoking all codes when a client is disabled.
     * 
     * @param clientId The client ID
     * @return List of authorization codes for the client
     */
    fun findByClientId(clientId: String): List<AuthorizationCode>
    
    /**
     * Delete all expired authorization codes.
     * This should be run periodically as a cleanup job.
     * 
     * @param now Current timestamp
     * @return Number of deleted codes
     */
    @Modifying
    @Query("DELETE FROM AuthorizationCode ac WHERE ac.expiresAt < :now")
    fun deleteExpiredCodes(@Param("now") now: Instant): Int
    
    /**
     * Delete all codes for a specific user.
     * Used when revoking all sessions for a user.
     * 
     * @param userId The user ID
     * @return Number of deleted codes
     */
    @Modifying
    fun deleteByUserId(userId: Long): Int
    
    /**
     * Delete all codes for a specific client.
     * Used when disabling or deleting a client.
     * 
     * @param clientId The client ID
     * @return Number of deleted codes
     */
    @Modifying
    fun deleteByClientId(clientId: String): Int
    
    /**
     * Count unused codes for a user within a time window.
     * Used for rate limiting authorization requests.
     * 
     * @param userId The user ID
     * @param since Start of time window
     * @return Number of codes issued in the window
     */
    @Query("""
        SELECT COUNT(ac) FROM AuthorizationCode ac 
        WHERE ac.userId = :userId 
        AND ac.issuedAt > :since
    """)
    fun countRecentCodesForUser(
        @Param("userId") userId: Long, 
        @Param("since") since: Instant
    ): Long
}
