// backend/src/main/kotlin/com/securemonitor/scim/controller/ScimGroupController.kt
package com.securemonitor.scim.controller

import com.securemonitor.repository.RoleRepository
import com.securemonitor.repository.UserRepository
import com.securemonitor.scim.dto.*
import com.securemonitor.service.AuditService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * SCIM 2.0 Group Resource Controller.
 * 
 * Maps internal Roles to SCIM Groups for standardized access control management.
 * 
 * Endpoints:
 * - GET    /scim/v2/Groups      - List groups
 * - GET    /scim/v2/Groups/{id} - Get group by ID
 * 
 * Note: Create/Update/Delete of groups is not implemented as roles are
 * managed internally. This is read-only for external systems.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7643#section-4.2">RFC 7643 Section 4.2 - Group</a>
 */
@RestController
@RequestMapping("/scim/v2/Groups")
@PreAuthorize("hasRole('ADMIN')")
class ScimGroupController(
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val auditService: AuditService
) {
    
    private val log = LoggerFactory.getLogger(javaClass)
    
    companion object {
        private const val SCIM_MEDIA_TYPE = "application/scim+json"
        private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT
    }
    
    // =========================================================================
    // List Groups - GET /scim/v2/Groups
    // =========================================================================
    
    /**
     * List all groups (roles) with optional filtering.
     */
    @GetMapping(produces = [SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE])
    fun listGroups(
        @RequestParam(required = false) filter: String?,
        @RequestParam(defaultValue = "1") startIndex: Int,
        @RequestParam(defaultValue = "100") count: Int
    ): ResponseEntity<ScimListResponse<ScimGroup>> {
        log.debug("SCIM listGroups: filter={}, startIndex={}, count={}", filter, startIndex, count)
        
        val roles = roleRepository.findAll()
        
        // Apply filter if provided
        val filteredRoles = if (filter != null) {
            val eqMatch = Regex("""displayName\s+eq\s+"?([^"]+)"?""", RegexOption.IGNORE_CASE)
                .find(filter)
            
            if (eqMatch != null) {
                val displayName = eqMatch.groupValues[1]
                roles.filter { it.name.equals(displayName, ignoreCase = true) }
            } else {
                roles
            }
        } else {
            roles
        }
        
        // Apply pagination
        val startIdx = maxOf(0, startIndex - 1)
        val paginatedRoles = filteredRoles.drop(startIdx).take(count)
        
        val scimGroups = paginatedRoles.map { role ->
            toScimGroup(role.id, role.name)
        }
        
        val response = ScimListResponse(
            totalResults = filteredRoles.size,
            startIndex = startIndex,
            itemsPerPage = scimGroups.size,
            resources = scimGroups
        )
        
        return ResponseEntity.ok(response)
    }
    
    // =========================================================================
    // Get Group - GET /scim/v2/Groups/{id}
    // =========================================================================
    
    /**
     * Get a single group by ID.
     */
    @GetMapping("/{id}", produces = [SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE])
    fun getGroup(@PathVariable id: Long): ResponseEntity<Any> {
        log.debug("SCIM getGroup: id={}", id)
        
        val role = roleRepository.findById(id).orElse(null)
            ?: return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ScimError(
                    status = "404",
                    scimType = ScimError.TYPE_NO_TARGET,
                    detail = "Group with ID $id not found"
                ))
        
        return ResponseEntity.ok(toScimGroup(role.id, role.name))
    }
    
    // =========================================================================
    // Helper Methods
    // =========================================================================
    
    /**
     * Convert internal Role to SCIM Group resource.
     */
    private fun toScimGroup(roleId: Long, roleName: String): ScimGroup {
        // Find all users with this role
        val usersWithRole = userRepository.findAll().filter { user ->
            user.roles.any { it.id == roleId }
        }
        
        return ScimGroup(
            id = roleId.toString(),
            displayName = roleName,
            members = usersWithRole.map { user ->
                ScimMemberRef(
                    value = user.id.toString(),
                    display = user.username,
                    ref = "/scim/v2/Users/${user.id}",
                    type = "User"
                )
            },
            meta = ScimMeta(
                resourceType = "Group",
                location = "/scim/v2/Groups/$roleId"
            )
        )
    }
}
