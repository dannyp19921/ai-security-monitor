// backend/src/main/kotlin/com/securemonitor/model/Role.kt
package com.securemonitor.model

import jakarta.persistence.*

@Entity
@Table(name = "roles")
data class Role(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    val name: String,

    @Column
    val description: String? = null
)