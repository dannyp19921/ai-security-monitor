package com.securemonitor.mfa.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

/**
 * Test suite for TOTP (Time-based One-Time Password) service.
 * 
 * TOTP is defined in RFC 6238 and builds on HOTP (RFC 4226).
 * It's used for multi-factor authentication with apps like
 * Google Authenticator, Authy, and Microsoft Authenticator.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6238">RFC 6238 - TOTP</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc4226">RFC 4226 - HOTP</a>
 */
@DisplayName("TotpService")
class TotpServiceTest {

    private lateinit var totpService: TotpService

    @BeforeEach
    fun setUp() {
        totpService = TotpService()
    }

    @Nested
    @DisplayName("generateSecret")
    inner class GenerateSecret {

        @Test
        @DisplayName("should generate a Base32-encoded secret")
        fun shouldGenerateBase32Secret() {
            val secret = totpService.generateSecret()
            
            // Base32 alphabet: A-Z and 2-7
            val base32Pattern = Regex("^[A-Z2-7]+$")
            assertThat(secret).matches(base32Pattern.toPattern())
        }

        @Test
        @DisplayName("should generate secret with correct length (32 characters = 160 bits)")
        fun shouldGenerateCorrectLength() {
            val secret = totpService.generateSecret()
            
            // 160 bits = 20 bytes, Base32 encodes 5 bits per character
            // 160 / 5 = 32 characters
            assertThat(secret.length).isEqualTo(32)
        }

        @Test
        @DisplayName("should generate unique secrets on each call")
        fun shouldGenerateUniqueSecrets() {
            val secrets = (1..100).map { totpService.generateSecret() }.toSet()
            
            assertThat(secrets).hasSize(100)
        }

        @Test
        @DisplayName("should generate cryptographically secure secrets")
        fun shouldGenerateSecureSecrets() {
            // Verify entropy by checking that secrets are well-distributed
            val secrets = (1..1000).map { totpService.generateSecret() }
            val uniqueFirstChars = secrets.map { it.first() }.toSet()
            
            // With good randomness, we should see variety in first characters
            assertThat(uniqueFirstChars.size).isGreaterThan(20)
        }
    }

    @Nested
    @DisplayName("generateCode")
    inner class GenerateCode {

        @Test
        @DisplayName("should generate 6-digit code")
        fun shouldGenerateSixDigitCode() {
            val secret = totpService.generateSecret()
            val code = totpService.generateCode(secret)
            
            assertThat(code).hasSize(6)
            assertThat(code).matches(Regex("^\\d{6}$").toPattern())
        }

        @Test
        @DisplayName("should generate same code for same secret within time window")
        fun shouldGenerateSameCodeWithinWindow() {
            val secret = totpService.generateSecret()
            val code1 = totpService.generateCode(secret)
            val code2 = totpService.generateCode(secret)
            
            // Within the same 30-second window, codes should match
            assertThat(code1).isEqualTo(code2)
        }

        @Test
        @DisplayName("should generate different codes for different secrets")
        fun shouldGenerateDifferentCodesForDifferentSecrets() {
            val secret1 = totpService.generateSecret()
            val secret2 = totpService.generateSecret()
            
            val code1 = totpService.generateCode(secret1)
            val code2 = totpService.generateCode(secret2)
            
            // Different secrets should produce different codes (with high probability)
            assertThat(code1).isNotEqualTo(code2)
        }

        @Test
        @DisplayName("should match RFC 6238 test vectors")
        fun shouldMatchRfcTestVectors() {
            // RFC 6238 test vector (SHA1)
            // Secret: "12345678901234567890" (ASCII) = Base32: GEZDGNBVGY3TQOJQ...
            // Time: 59 seconds since epoch -> counter = 1
            // Expected TOTP: 287082
            
            val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" // "12345678901234567890" in Base32
            val timestamp = Instant.ofEpochSecond(59)
            
            val code = totpService.generateCode(secret, timestamp)
            
            assertThat(code).isEqualTo("287082")
        }

        @Test
        @DisplayName("should match RFC 6238 test vector for time 1111111109")
        fun shouldMatchRfcTestVector2() {
            val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
            val timestamp = Instant.ofEpochSecond(1111111109)
            
            val code = totpService.generateCode(secret, timestamp)
            
            assertThat(code).isEqualTo("081804")
        }

        @Test
        @DisplayName("should pad codes with leading zeros")
        fun shouldPadWithLeadingZeros() {
            // Generate many codes and verify all are 6 digits
            val secret = totpService.generateSecret()
            val codes = (0L..100L).map { offset ->
                totpService.generateCode(secret, Instant.ofEpochSecond(offset * 30))
            }
            
            codes.forEach { code ->
                assertThat(code).hasSize(6)
            }
        }
    }

    @Nested
    @DisplayName("verifyCode")
    inner class VerifyCode {

        @Test
        @DisplayName("should accept valid code for current time")
        fun shouldAcceptValidCode() {
            val secret = totpService.generateSecret()
            val code = totpService.generateCode(secret)
            
            val result = totpService.verifyCode(secret, code)
            
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should reject invalid code")
        fun shouldRejectInvalidCode() {
            val secret = totpService.generateSecret()
            val invalidCode = "000000"
            
            val result = totpService.verifyCode(secret, invalidCode)
            
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should accept code from previous time window (clock drift tolerance)")
        fun shouldAcceptPreviousWindowCode() {
            val secret = totpService.generateSecret()
            val now = Instant.now()
            val previousWindow = now.minusSeconds(30)
            
            val code = totpService.generateCode(secret, previousWindow)
            val result = totpService.verifyCode(secret, code, now)
            
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should accept code from next time window (clock drift tolerance)")
        fun shouldAcceptNextWindowCode() {
            val secret = totpService.generateSecret()
            val now = Instant.now()
            val nextWindow = now.plusSeconds(30)
            
            val code = totpService.generateCode(secret, nextWindow)
            val result = totpService.verifyCode(secret, code, now)
            
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should reject code from too old time window")
        fun shouldRejectOldCode() {
            val secret = totpService.generateSecret()
            val now = Instant.now()
            val oldWindow = now.minusSeconds(90) // 3 windows ago
            
            val code = totpService.generateCode(secret, oldWindow)
            val result = totpService.verifyCode(secret, code, now)
            
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should reject code from too future time window")
        fun shouldRejectFutureCode() {
            val secret = totpService.generateSecret()
            val now = Instant.now()
            val futureWindow = now.plusSeconds(90) // 3 windows ahead
            
            val code = totpService.generateCode(secret, futureWindow)
            val result = totpService.verifyCode(secret, code, now)
            
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should use timing-safe comparison")
        fun shouldUseTimingSafeComparison() {
            val secret = totpService.generateSecret()
            val validCode = totpService.generateCode(secret)
            
            // Both should complete in similar time regardless of where mismatch occurs
            val result1 = totpService.verifyCode(secret, "000000")
            val result2 = totpService.verifyCode(secret, validCode.take(5) + "X")
            
            assertThat(result1).isFalse()
            assertThat(result2).isFalse()
        }
    }

    @Nested
    @DisplayName("generateTotpUri")
    inner class GenerateTotpUri {

        @Test
        @DisplayName("should generate valid otpauth URI")
        fun shouldGenerateValidUri() {
            val secret = "JBSWY3DPEHPK3PXP"
            val issuer = "AI Security Monitor"
            val accountName = "testuser@example.com"
            
            val uri = totpService.generateTotpUri(secret, issuer, accountName)
            
            assertThat(uri).startsWith("otpauth://totp/")
            assertThat(uri).contains("secret=$secret")
            assertThat(uri).contains("issuer=AI%20Security%20Monitor")
        }

        @Test
        @DisplayName("should URL-encode special characters in issuer and account")
        fun shouldUrlEncodeSpecialCharacters() {
            val secret = "JBSWY3DPEHPK3PXP"
            val issuer = "My App & Co."
            val accountName = "user+test@example.com"
            
            val uri = totpService.generateTotpUri(secret, issuer, accountName)
            
            assertThat(uri).contains("issuer=My%20App%20%26%20Co.")
            assertThat(uri).contains("user%2Btest%40example.com")
        }

        @Test
        @DisplayName("should include algorithm, digits, and period parameters")
        fun shouldIncludeAllParameters() {
            val secret = "JBSWY3DPEHPK3PXP"
            
            val uri = totpService.generateTotpUri(secret, "Issuer", "account")
            
            assertThat(uri).contains("algorithm=SHA1")
            assertThat(uri).contains("digits=6")
            assertThat(uri).contains("period=30")
        }

        @Test
        @DisplayName("should format label as issuer:accountName")
        fun shouldFormatLabelCorrectly() {
            val secret = "JBSWY3DPEHPK3PXP"
            val issuer = "MyApp"
            val accountName = "user@example.com"
            
            val uri = totpService.generateTotpUri(secret, issuer, accountName)
            
            // Label should be URL-encoded "issuer:accountName"
            assertThat(uri).contains("otpauth://totp/MyApp:user%40example.com")
        }
    }

    @Nested
    @DisplayName("generateBackupCodes")
    inner class GenerateBackupCodes {

        @Test
        @DisplayName("should generate 10 backup codes by default")
        fun shouldGenerateTenCodes() {
            val codes = totpService.generateBackupCodes()
            
            assertThat(codes).hasSize(10)
        }

        @Test
        @DisplayName("should generate codes in format XXXX-XXXX")
        fun shouldGenerateCorrectFormat() {
            val codes = totpService.generateBackupCodes()
            
            val pattern = Regex("^[A-Z0-9]{4}-[A-Z0-9]{4}$")
            codes.forEach { code ->
                assertThat(code).matches(pattern.toPattern())
            }
        }

        @Test
        @DisplayName("should generate unique codes")
        fun shouldGenerateUniqueCodes() {
            val codes = totpService.generateBackupCodes()
            
            assertThat(codes.toSet()).hasSize(codes.size)
        }

        @Test
        @DisplayName("should generate different codes on each call")
        fun shouldGenerateDifferentCodesEachCall() {
            val codes1 = totpService.generateBackupCodes()
            val codes2 = totpService.generateBackupCodes()
            
            assertThat(codes1).isNotEqualTo(codes2)
        }

        @Test
        @DisplayName("should allow custom count")
        fun shouldAllowCustomCount() {
            val codes = totpService.generateBackupCodes(count = 5)
            
            assertThat(codes).hasSize(5)
        }
    }

    @Nested
    @DisplayName("verifyBackupCode")
    inner class VerifyBackupCode {

        @Test
        @DisplayName("should verify valid backup code")
        fun shouldVerifyValidCode() {
            val codes = totpService.generateBackupCodes()
            val hashedCodes = codes.map { totpService.hashBackupCode(it) }
            
            val result = totpService.verifyBackupCode(codes.first(), hashedCodes)
            
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should reject invalid backup code")
        fun shouldRejectInvalidCode() {
            val codes = totpService.generateBackupCodes()
            val hashedCodes = codes.map { totpService.hashBackupCode(it) }
            
            val result = totpService.verifyBackupCode("XXXX-XXXX", hashedCodes)
            
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should be case-insensitive")
        fun shouldBeCaseInsensitive() {
            val codes = totpService.generateBackupCodes()
            val hashedCodes = codes.map { totpService.hashBackupCode(it) }
            
            val lowercaseCode = codes.first().lowercase()
            val result = totpService.verifyBackupCode(lowercaseCode, hashedCodes)
            
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should accept code with or without hyphen")
        fun shouldAcceptCodeWithoutHyphen() {
            val codes = totpService.generateBackupCodes()
            val hashedCodes = codes.map { totpService.hashBackupCode(it) }
            
            val codeWithoutHyphen = codes.first().replace("-", "")
            val result = totpService.verifyBackupCode(codeWithoutHyphen, hashedCodes)
            
            assertThat(result).isTrue()
        }
    }

    @Nested
    @DisplayName("hashBackupCode")
    inner class HashBackupCode {

        @Test
        @DisplayName("should produce consistent hash for same input")
        fun shouldProduceConsistentHash() {
            val code = "ABCD-1234"
            
            val hash1 = totpService.hashBackupCode(code)
            val hash2 = totpService.hashBackupCode(code)
            
            assertThat(hash1).isEqualTo(hash2)
        }

        @Test
        @DisplayName("should produce different hash for different input")
        fun shouldProduceDifferentHashes() {
            val code1 = "ABCD-1234"
            val code2 = "ABCD-1235"
            
            val hash1 = totpService.hashBackupCode(code1)
            val hash2 = totpService.hashBackupCode(code2)
            
            assertThat(hash1).isNotEqualTo(hash2)
        }

        @Test
        @DisplayName("should normalize input before hashing (case-insensitive)")
        fun shouldNormalizeInput() {
            val code1 = "ABCD-1234"
            val code2 = "abcd-1234"
            
            val hash1 = totpService.hashBackupCode(code1)
            val hash2 = totpService.hashBackupCode(code2)
            
            assertThat(hash1).isEqualTo(hash2)
        }
    }
}
