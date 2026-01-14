// backend/src/main/kotlin/com/securemonitor/mfa/service/TotpService.kt
package com.securemonitor.mfa.service

import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP (Time-based One-Time Password) service implementing RFC 6238.
 * 
 * Provides functionality for:
 * - Generating TOTP secrets (Base32 encoded)
 * - Generating and verifying 6-digit TOTP codes
 * - Creating otpauth:// URIs for authenticator apps
 * - Generating and verifying backup codes
 * 
 * This implementation follows security best practices:
 * - Cryptographically secure random number generation
 * - Timing-safe comparisons to prevent timing attacks
 * - Clock drift tolerance for user convenience
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6238">RFC 6238 - TOTP</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc4226">RFC 4226 - HOTP</a>
 */
@Service
class TotpService {

    companion object {
        /** Base32 alphabet as defined in RFC 4648 */
        private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        
        /** Time step in seconds (standard is 30) */
        private const val TIME_STEP_SECONDS = 30L
        
        /** Number of digits in TOTP code */
        private const val CODE_DIGITS = 6
        
        /** Clock drift tolerance (number of time steps to check in each direction) */
        private const val CLOCK_DRIFT_TOLERANCE = 1
        
        /** Default number of backup codes to generate */
        private const val DEFAULT_BACKUP_CODE_COUNT = 10
        
        /** HMAC algorithm for TOTP */
        private const val HMAC_ALGORITHM = "HmacSHA1"
    }

    private val secureRandom = SecureRandom()

    /**
     * Generates a new TOTP secret.
     * 
     * @return Base32-encoded secret (32 characters = 160 bits)
     */
    fun generateSecret(): String {
        val bytes = ByteArray(20) // 160 bits
        secureRandom.nextBytes(bytes)
        return encodeBase32(bytes)
    }

    /**
     * Generates a TOTP code for the given secret at the current time.
     * 
     * @param secret Base32-encoded secret
     * @return 6-digit TOTP code
     */
    fun generateCode(secret: String): String {
        return generateCode(secret, Instant.now())
    }

    /**
     * Generates a TOTP code for the given secret at the specified time.
     * 
     * @param secret Base32-encoded secret
     * @param timestamp Time to generate code for
     * @return 6-digit TOTP code
     */
    fun generateCode(secret: String, timestamp: Instant): String {
        val counter = timestamp.epochSecond / TIME_STEP_SECONDS
        return generateHotp(secret, counter)
    }

    /**
     * Verifies a TOTP code against the secret at the current time.
     * 
     * @param secret Base32-encoded secret
     * @param code 6-digit code to verify
     * @return true if the code is valid
     */
    fun verifyCode(secret: String, code: String): Boolean {
        return verifyCode(secret, code, Instant.now())
    }

    /**
     * Verifies a TOTP code against the secret at the specified time.
     * Allows for clock drift by checking adjacent time windows.
     * 
     * @param secret Base32-encoded secret
     * @param code 6-digit code to verify
     * @param timestamp Time to verify against
     * @return true if the code is valid within the tolerance window
     */
    fun verifyCode(secret: String, code: String, timestamp: Instant): Boolean {
        val currentCounter = timestamp.epochSecond / TIME_STEP_SECONDS
        
        // Check current window and adjacent windows for clock drift
        for (offset in -CLOCK_DRIFT_TOLERANCE..CLOCK_DRIFT_TOLERANCE) {
            val expectedCode = generateHotp(secret, currentCounter + offset)
            if (timingSafeEquals(expectedCode, code)) {
                return true
            }
        }
        
        return false
    }

    /**
     * Generates an otpauth:// URI for use with authenticator apps.
     * 
     * The URI format follows the Google Authenticator Key URI Format:
     * otpauth://totp/ISSUER:ACCOUNT?secret=SECRET&issuer=ISSUER&algorithm=SHA1&digits=6&period=30
     * 
     * @param secret Base32-encoded secret
     * @param issuer Application/service name
     * @param accountName User's account identifier (usually email)
     * @return otpauth:// URI string
     */
    fun generateTotpUri(secret: String, issuer: String, accountName: String): String {
        val encodedIssuer = urlEncode(issuer)
        val encodedAccount = urlEncode(accountName)
        
        return buildString {
            append("otpauth://totp/")
            append(encodedIssuer)
            append(":")
            append(encodedAccount)
            append("?secret=").append(secret)
            append("&issuer=").append(encodedIssuer)
            append("&algorithm=SHA1")
            append("&digits=").append(CODE_DIGITS)
            append("&period=").append(TIME_STEP_SECONDS)
        }
    }

    /**
     * Generates backup codes for account recovery.
     * 
     * @param count Number of codes to generate (default: 10)
     * @return List of backup codes in format XXXX-XXXX
     */
    fun generateBackupCodes(count: Int = DEFAULT_BACKUP_CODE_COUNT): List<String> {
        return (1..count).map { generateBackupCode() }
    }

    /**
     * Verifies a backup code against a list of hashed codes.
     * 
     * @param code Backup code to verify (with or without hyphen)
     * @param hashedCodes List of hashed backup codes
     * @return true if the code matches any hashed code
     */
    fun verifyBackupCode(code: String, hashedCodes: List<String>): Boolean {
        val normalizedCode = normalizeBackupCode(code)
        val hashedInput = hashBackupCode(normalizedCode)
        
        return hashedCodes.any { timingSafeEquals(it, hashedInput) }
    }

    /**
     * Hashes a backup code for secure storage.
     * 
     * @param code Backup code to hash
     * @return SHA-256 hash of the normalized code (hex encoded)
     */
    fun hashBackupCode(code: String): String {
        val normalizedCode = normalizeBackupCode(code)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(normalizedCode.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    // ============================================
    // Private helper methods
    // ============================================

    /**
     * Generates HOTP (HMAC-based One-Time Password) as per RFC 4226.
     */
    private fun generateHotp(secret: String, counter: Long): String {
        val keyBytes = decodeBase32(secret)
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(keyBytes, HMAC_ALGORITHM))
        val hash = mac.doFinal(counterBytes)
        
        // Dynamic truncation as per RFC 4226
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                     ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                     ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                     (hash[offset + 3].toInt() and 0xFF)
        
        val otp = binary % Math.pow(10.0, CODE_DIGITS.toDouble()).toInt()
        
        return otp.toString().padStart(CODE_DIGITS, '0')
    }

    /**
     * Encodes bytes to Base32 string.
     */
    private fun encodeBase32(data: ByteArray): String {
        val result = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                result.append(BASE32_ALPHABET[index])
                bitsLeft -= 5
            }
        }
        
        // Handle remaining bits
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            result.append(BASE32_ALPHABET[index])
        }
        
        return result.toString()
    }

    /**
     * Decodes Base32 string to bytes.
     */
    private fun decodeBase32(encoded: String): ByteArray {
        val result = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        
        for (char in encoded.uppercase()) {
            val value = BASE32_ALPHABET.indexOf(char)
            if (value < 0) continue // Skip invalid characters
            
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            
            if (bitsLeft >= 8) {
                result.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        
        return result.toByteArray()
    }

    /**
     * Generates a single backup code in format XXXX-XXXX.
     */
    private fun generateBackupCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val part1 = (1..4).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
        val part2 = (1..4).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
        return "$part1-$part2"
    }

    /**
     * Normalizes backup code for comparison/hashing.
     * Removes hyphens and converts to uppercase.
     */
    private fun normalizeBackupCode(code: String): String {
        return code.uppercase().replace("-", "")
    }

    /**
     * URL-encodes a string for use in otpauth URI.
     */
    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    }

    /**
     * Timing-safe string comparison to prevent timing attacks.
     * 
     * Always compares all characters regardless of where a mismatch occurs,
     * making it resistant to timing-based side-channel attacks.
     */
    private fun timingSafeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) {
            // Still do a comparison to maintain constant time
            val dummy = "0".repeat(a.length)
            MessageDigest.isEqual(a.toByteArray(), dummy.toByteArray())
            return false
        }
        return MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
    }
}
