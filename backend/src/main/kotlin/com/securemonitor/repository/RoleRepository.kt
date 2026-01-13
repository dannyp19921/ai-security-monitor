// backend/src/main/kotlin/com/securemonitor/repository/RoleRepository.kt
package com.securemonitor.repository

import com.securemonitor.model.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface RoleRepository : JpaRepository<Role, Long> {
    fun findByName(name: String): Optional<Role>
}