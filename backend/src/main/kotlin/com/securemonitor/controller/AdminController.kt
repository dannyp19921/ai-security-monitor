// backend/src/main/kotlin/com/securemonitor/controller/AdminController.kt
package com.securemonitor.controller

import com.securemonitor.dto.UserResponse
import com.securemonitor.model.User
import com.securemonitor.repository.UserRepository
import com.securemonitor.repository.RoleRepository
import com.securemonitor.service.AuditService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val auditService: AuditService
) {

    @GetMapping("/users")
    fun getAllUsers(): ResponseEntity<List<UserResponse>> {
        val users = userRepository.findAll().map { it.toResponse() }
        
        auditService.log(
            action = "ADMIN_VIEW_USERS",
            username = SecurityContextHolder.getContext().authentication?.name ?: "unknown",
            resourceType = "USER",
            details = "Listed ${users.size} users"
        )
        
        return ResponseEntity.ok(users)
    }

    @PostMapping("/users/{userId}/roles/{roleName}")
    fun addRoleToUser(
        @PathVariable userId: Long,
        @PathVariable roleName: String
    ): ResponseEntity<UserResponse> {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        
        val role = roleRepository.findByName(roleName)
            .orElseThrow { IllegalArgumentException("Role not found") }
        
        user.roles = user.roles + role
        val savedUser = userRepository.save(user)
        
        auditService.log(
            action = "ADMIN_ADD_ROLE",
            username = SecurityContextHolder.getContext().authentication?.name ?: "unknown",
            resourceType = "USER",
            resourceId = userId.toString(),
            details = "Added role $roleName to user ${user.username}"
        )
        
        return ResponseEntity.ok(savedUser.toResponse())
    }

    @DeleteMapping("/users/{userId}/roles/{roleName}")
    fun removeRoleFromUser(
        @PathVariable userId: Long,
        @PathVariable roleName: String
    ): ResponseEntity<UserResponse> {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        
        val role = roleRepository.findByName(roleName)
            .orElseThrow { IllegalArgumentException("Role not found") }
        
        user.roles = user.roles - role
        val savedUser = userRepository.save(user)
        
        auditService.log(
            action = "ADMIN_REMOVE_ROLE",
            username = SecurityContextHolder.getContext().authentication?.name ?: "unknown",
            resourceType = "USER",
            resourceId = userId.toString(),
            details = "Removed role $roleName from user ${user.username}"
        )
        
        return ResponseEntity.ok(savedUser.toResponse())
    }

    private fun User.toResponse() = UserResponse(
        id = this.id,
        username = this.username,
        email = this.email,
        roles = this.roles.map { it.name },
        enabled = this.enabled,
        createdAt = this.createdAt.toString(),
        lastLogin = this.lastLogin?.toString()
    )
}