// backend/src/main/kotlin/com/securemonitor/scim/controller/ScimUserController.kt
package com.securemonitor.scim.controller

import com.securemonitor.model.User
import com.securemonitor.repository.RoleRepository
import com.securemonitor.repository.UserRepository
import com.securemonitor.scim.dto.*
import com.securemonitor.service.AuditService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * SCIM 2.0 User Resource Controller.
 * 
 * Implements the SCIM protocol (RFC 7644) for user provisioning.
 * This enables automated user management from external systems like
 * HR systems, identity providers, and enterprise directories.
 * 
 * Endpoints:
 * - GET    /scim/v2/Users      - List/search users
 * - POST   /scim/v2/Users      - Create user
 * - GET    /scim/v2/Users/{id} - Get user by ID
 * - PUT    /scim/v2/Users/{id} - Replace user
 * - PATCH  /scim/v2/Users/{id} - Update user (partial)
 * - DELETE /scim/v2/Users/{id} - Delete user
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7644">RFC 7644 - SCIM Protocol</a>
 */
@RestController
@RequestMapping("/scim/v2/Users")
@PreAuthorize("hasRole('ADMIN')")
class ScimUserController(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val auditService: AuditService
) {
    
    private val log = LoggerFactory.getLogger(javaClass)
    
    companion object {
        private const val SCIM_MEDIA_TYPE = "application/scim+json"
        private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT
    }
    
    // =========================================================================
    // List Users - GET /scim/v2/Users
    // =========================================================================
    
    /**
     * List users with optional filtering and pagination.
     * 
     * Supports basic filtering via the 'filter' parameter:
     * - filter=userName eq "john"
     * - filter=emails.value eq "john@example.com"
     * 
     * @param filter SCIM filter expression (optional)
     * @param startIndex 1-based index for pagination (default: 1)
     * @param count Maximum results to return (default: 100)
     */
    @GetMapping(produces = [SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE])
    fun listUsers(
        @RequestParam(required = false) filter: String?,
        @RequestParam(defaultValue = "1") startIndex: Int,
        @RequestParam(defaultValue = "100") count: Int
    ): ResponseEntity<ScimListResponse<ScimUser>> {
        log.debug("SCIM listUsers: filter={}, startIndex={}, count={}", filter, startIndex, count)
        
        val users = if (filter != null) {
            parseAndApplyFilter(filter)
        } else {
            userRepository.findAll()
        }
        
        // Apply pagination (SCIM uses 1-based indexing)
        val startIdx = maxOf(0, startIndex - 1)
        val paginatedUsers = users.drop(startIdx).take(count)
        
        val scimUsers = paginatedUsers.map { toScimUser(it) }
        
        val response = ScimListResponse(
            totalResults = users.size,
            startIndex = startIndex,
            itemsPerPage = scimUsers.size,
            resources = scimUsers
        )
        
        return ResponseEntity.ok(response)
    }
    
    // =========================================================================
    // Get User - GET /scim/v2/Users/{id}
    // =========================================================================
    
    /**
     * Get a single user by ID.
     */
    @GetMapping("/{id}", produces = [SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE])
    fun getUser(@PathVariable id: Long): ResponseEntity<Any> {
        log.debug("SCIM getUser: id={}", id)
        
        val user = userRepository.findById(id).orElse(null)
            ?: return notFound("User with ID $id not found")
        
        return ResponseEntity.ok(toScimUser(user))
    }
    
    // =========================================================================
    // Create User - POST /scim/v2/Users
    // =========================================================================
    
    /**
     * Create a new user via SCIM.
     */
    @PostMapping(
        consumes = [SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE],
        produces = [SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE]
    )
    fun createUser(@RequestBody request: ScimUserCreateRequest): ResponseEntity<Any> {
        log.info("SCIM createUser: userName={}", request.userName)
        
        // Check for existing user
        if (userRepository.existsByUsername(request.userName)) {
            return conflict("User with userName '${request.userName}' already exists")
        }
        
        val email = request.emails?.firstOrNull()?.value ?: "${request.userName}@scim.local"
        if (userRepository.existsByEmail(email)) {
            return conflict("User with email '$email' already exists")
        }
        
        // Get default USER role
        val userRole = roleRepository.findByName("USER").orElseThrow {
            IllegalStateException("USER role not found")
        }
        
        // Create user
        val password = request.password ?: UUID.randomUUID().toString()
        val user = User(
            username = request.userName,
            email = email,
            passwordHash = passwordEncoder.encode(password),
            roles = mutableSetOf(userRole),
            enabled = request.active
        )
        
        val savedUser = userRepository.save(user)
        
        auditService.log(
            action = "SCIM_USER_CREATED",
            username = "SCIM",
            resourceType = "USER",
            resourceId = savedUser.id.toString(),
            details = "User '${savedUser.username}' created via SCIM"
        )
        
        val scimUser = toScimUser(savedUser)
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header("Location", "/scim/v2/Users/${savedUser.id}")
            .body(scimUser)
    }
    
    // =========================================================================
    // Replace User - PUT /scim/v2/Users/{id}
    // =========================================================================
    
    /**
     * Replace an existing user (full update).
     */
    @PutMapping(
        "/{id}",
        consumes = [SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE],
        produces = [SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE]
    )
    fun replaceUser(
        @PathVariable id: Long,
        @RequestBody request: ScimUserCreateRequest
    ): ResponseEntity<Any> {
        log.info("SCIM replaceUser: id={}, userName={}", id, request.userName)
        
        val existingUser = userRepository.findById(id).orElse(null)
            ?: return notFound("User with ID $id not found")
        
        val email = request.emails?.firstOrNull()?.value ?: existingUser.email
        
        // Check for conflicts with other users
        userRepository.findByUsername(request.userName).ifPresent { user ->
            if (user.id != id) {
                throw ScimConflictException("userName '${request.userName}' is already taken")
            }
        }
        
        userRepository.findByEmail(email).ifPresent { user ->
            if (user.id != id) {
                throw ScimConflictException("email '$email' is already taken")
            }
        }
        
        val updatedUser = existingUser.copy(
            username = request.userName,
            email = email,
            enabled = request.active,
            passwordHash = request.password?.let { passwordEncoder.encode(it) } 
                ?: existingUser.passwordHash
        )
        
        val savedUser = userRepository.save(updatedUser)
        
        auditService.log(
            action = "SCIM_USER_REPLACED",
            username = "SCIM",
            resourceType = "USER",
            resourceId = savedUser.id.toString(),
            details = "User '${savedUser.username}' replaced via SCIM"
        )
        
        return ResponseEntity.ok(toScimUser(savedUser))
    }
    
    // =========================================================================
    // Update User - PATCH /scim/v2/Users/{id}
    // =========================================================================
    
    /**
     * Partially update a user via SCIM PATCH operations.
     */
    @PatchMapping(
        "/{id}",
        consumes = [SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE],
        produces = [SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE]
    )
    fun patchUser(
        @PathVariable id: Long,
        @RequestBody patchOp: ScimPatchOp
    ): ResponseEntity<Any> {
        log.info("SCIM patchUser: id={}, operations={}", id, patchOp.operations.size)
        
        var user = userRepository.findById(id).orElse(null)
            ?: return notFound("User with ID $id not found")
        
        for (operation in patchOp.operations) {
            user = applyPatchOperation(user, operation)
        }
        
        val savedUser = userRepository.save(user)
        
        auditService.log(
            action = "SCIM_USER_PATCHED",
            username = "SCIM",
            resourceType = "USER",
            resourceId = savedUser.id.toString(),
            details = "User '${savedUser.username}' patched via SCIM (${patchOp.operations.size} operations)"
        )
        
        return ResponseEntity.ok(toScimUser(savedUser))
    }
    
    // =========================================================================
    // Delete User - DELETE /scim/v2/Users/{id}
    // =========================================================================
    
    /**
     * Delete a user.
     */
    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Any> {
        log.info("SCIM deleteUser: id={}", id)
        
        val user = userRepository.findById(id).orElse(null)
            ?: return notFound("User with ID $id not found")
        
        val username = user.username
        userRepository.delete(user)
        
        auditService.log(
            action = "SCIM_USER_DELETED",
            username = "SCIM",
            resourceType = "USER",
            resourceId = id.toString(),
            details = "User '$username' deleted via SCIM"
        )
        
        return ResponseEntity.noContent().build()
    }
    
    // =========================================================================
    // Helper Methods
    // =========================================================================
    
    /**
     * Convert internal User entity to SCIM User resource.
     */
    private fun toScimUser(user: User): ScimUser {
        return ScimUser(
            id = user.id.toString(),
            userName = user.username,
            displayName = user.username,
            emails = listOf(
                ScimEmail(value = user.email, primary = true)
            ),
            active = user.enabled,
            groups = user.roles.map { role ->
                ScimGroupRef(
                    value = role.id.toString(),
                    display = role.name,
                    ref = "/scim/v2/Groups/${role.id}"
                )
            },
            meta = ScimMeta(
                resourceType = "User",
                created = user.createdAt.atOffset(ZoneOffset.UTC).format(ISO_FORMATTER),
                lastModified = (user.lastLogin ?: user.createdAt).atOffset(ZoneOffset.UTC).format(ISO_FORMATTER),
                location = "/scim/v2/Users/${user.id}"
            )
        )
    }
    
    /**
     * Parse and apply a SCIM filter expression.
     * 
     * Supports basic filters:
     * - userName eq "value"
     * - emails.value eq "value"
     * - active eq true
     */
    private fun parseAndApplyFilter(filter: String): List<User> {
        val allUsers = userRepository.findAll()
        
        // Basic filter parsing (simplified)
        val eqMatch = Regex("""(\w+(?:\.\w+)?)\s+eq\s+"?([^"]+)"?""", RegexOption.IGNORE_CASE)
            .find(filter)
        
        if (eqMatch != null) {
            val (attribute, value) = eqMatch.destructured
            
            return allUsers.filter { user ->
                when (attribute.lowercase()) {
                    "username" -> user.username.equals(value, ignoreCase = true)
                    "emails.value" -> user.email.equals(value, ignoreCase = true)
                    "active" -> user.enabled == value.toBoolean()
                    "displayname" -> user.username.equals(value, ignoreCase = true)
                    else -> true
                }
            }
        }
        
        return allUsers
    }
    
    /**
     * Apply a single PATCH operation to a user.
     */
    private fun applyPatchOperation(user: User, operation: ScimPatchOperation): User {
        val op = operation.op.lowercase()
        val path = operation.path?.lowercase()
        val value = operation.value
        
        return when (op) {
            "replace" -> {
                when (path) {
                    "username" -> user.copy(username = value as String)
                    "active" -> user.copy(enabled = value as Boolean)
                    "displayname" -> user.copy(username = value as String)
                    "emails" -> {
                        @Suppress("UNCHECKED_CAST")
                        val emails = value as? List<Map<String, Any>>
                        val primaryEmail = emails?.firstOrNull()?.get("value") as? String
                        if (primaryEmail != null) user.copy(email = primaryEmail) else user
                    }
                    else -> user
                }
            }
            "add" -> {
                // For simplicity, treat "add" same as "replace" for single-valued attributes
                applyPatchOperation(user, operation.copy(op = "replace"))
            }
            "remove" -> {
                // Handle removal (set to default/null where applicable)
                user
            }
            else -> user
        }
    }
    
    // =========================================================================
    // Error Responses
    // =========================================================================
    
    private fun notFound(detail: String): ResponseEntity<Any> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ScimError(
                status = "404",
                scimType = ScimError.TYPE_NO_TARGET,
                detail = detail
            ))
    }
    
    private fun conflict(detail: String): ResponseEntity<Any> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ScimError(
                status = "409",
                scimType = ScimError.TYPE_UNIQUENESS,
                detail = detail
            ))
    }
    
    @ExceptionHandler(ScimConflictException::class)
    fun handleConflict(e: ScimConflictException): ResponseEntity<ScimError> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ScimError(
                status = "409",
                scimType = ScimError.TYPE_UNIQUENESS,
                detail = e.message
            ))
    }
}

class ScimConflictException(message: String) : RuntimeException(message)
