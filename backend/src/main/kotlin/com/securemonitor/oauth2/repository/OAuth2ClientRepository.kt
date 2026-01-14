package com.securemonitor.oauth2.repository

import com.securemonitor.oauth2.model.OAuth2Client
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * Repository for managing OAuth 2.0 client registrations.
 */
@Repository
interface OAuth2ClientRepository : JpaRepository<OAuth2Client, Long> {
    
    /**
     * Find a client by its client ID.
     * 
     * @param clientId The unique client identifier
     * @return The client if found
     */
    fun findByClientId(clientId: String): Optional<OAuth2Client>
    
    /**
     * Check if a client with the given ID exists.
     * 
     * @param clientId The client identifier to check
     * @return true if the client exists
     */
    fun existsByClientId(clientId: String): Boolean
    
    /**
     * Find all enabled clients.
     * 
     * @return List of enabled clients
     */
    fun findByEnabledTrue(): List<OAuth2Client>
    
    /**
     * Find a client by name.
     * 
     * @param clientName The client name
     * @return The client if found
     */
    fun findByClientName(clientName: String): Optional<OAuth2Client>
}
