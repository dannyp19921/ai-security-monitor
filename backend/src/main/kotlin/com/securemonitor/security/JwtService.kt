// backend/src/main/kotlin/com/securemonitor/security/JwtService.kt
package com.securemonitor.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService {

    @Value("\${jwt.secret:myDefaultSecretKeyThatIsAtLeast256BitsLong123456}")
    private lateinit var secretString: String

    @Value("\${jwt.expiration:86400000}")
    private var expiration: Long = 86400000 // 24 hours

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(secretString.toByteArray())
    }

    fun generateToken(username: String, roles: List<String>): String {
        val now = Date()
        val expiryDate = Date(now.time + expiration)

        return Jwts.builder()
            .subject(username)
            .claim("roles", roles)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            getClaims(token)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getUsername(token: String): String {
        return getClaims(token).subject
    }

    @Suppress("UNCHECKED_CAST")
    fun getRoles(token: String): List<String> {
        return getClaims(token)["roles"] as? List<String> ?: emptyList()
    }

    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}