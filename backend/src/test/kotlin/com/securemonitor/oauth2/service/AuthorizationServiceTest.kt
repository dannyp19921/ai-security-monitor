package com.securemonitor.oauth2.service

import com.securemonitor.oauth2.dto.AuthorizationRequest
import com.securemonitor.oauth2.exception.OAuth2Exception
import com.securemonitor.oauth2.model.AuthorizationCode
import com.securemonitor.oauth2.model.OAuth2Client
import com.securemonitor.oauth2.repository.AuthorizationCodeRepository
import com.securemonitor.oauth2.repository.OAuth2ClientRepository
import com.securemonitor.service.AuditService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.assertj.core.api.Assertions.assertThat
import java.util.Optional

/**
 * Test suite for AuthorizationService.
 * 
 * Tests the authorization endpoint logic including:
 * - Request validation
 * - Client validation
 * - PKCE handling
 * - Authorization code generation
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("AuthorizationService")
class AuthorizationServiceTest {

    @Mock
    private lateinit var clientRepository: OAuth2ClientRepository
    
    @Mock
    private lateinit var codeRepository: AuthorizationCodeRepository
    
    @Mock
    private lateinit var pkceService: PkceService
    
    @Mock
    private lateinit var auditService: AuditService

    private lateinit var authorizationService: AuthorizationService

    private val testClient = OAuth2Client(
        id = 1L,
        clientId = "test-client",
        clientSecretHash = null,
        clientName = "Test Client",
        confidential = false,
        redirectUris = "http://localhost:3000/callback,https://example.com/callback",
        allowedScopes = "openid profile email",
        allowedGrantTypes = "authorization_code refresh_token",
        requirePkce = true
    )

    @BeforeEach
    fun setUp() {
        authorizationService = AuthorizationService(
            clientRepository = clientRepository,
            codeRepository = codeRepository,
            pkceService = pkceService,
            auditService = auditService
        )
    }

    @Nested
    @DisplayName("validateAuthorizationRequest")
    inner class ValidateAuthorizationRequest {

        @Test
        @DisplayName("should accept valid authorization request with PKCE")
        fun shouldAcceptValidRequest() {
            val request = AuthorizationRequest(
                responseType = "code",
                clientId = "test-client",
                redirectUri = "http://localhost:3000/callback",
                scope = "openid profile",
                state = "abc123",
                codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                codeChallengeMethod = "S256"
            )
            
            whenever(clientRepository.findByClientId("test-client"))
                .thenReturn(Optional.of(testClient))
            
            // Should not throw
            val validatedClient = authorizationService.validateAuthorizationRequest(request)
            
            assertThat(validatedClient.clientId).isEqualTo("test-client")
        }

        @Test
        @DisplayName("should reject request with unsupported response_type")
        fun shouldRejectUnsupportedResponseType() {
            val request = AuthorizationRequest(
                responseType = "token", // implicit flow - not supported
                clientId = "test-client",
                redirectUri = "http://localhost:3000/callback"
            )
            
            val exception = assertThrows<OAuth2Exception> {
                authorizationService.validateAuthorizationRequest(request)
            }
            
            assertThat(exception.error).isEqualTo("unsupported_response_type")
        }

        @Test
        @DisplayName("should reject request with unknown client_id")
        fun shouldRejectUnknownClient() {
            val request = AuthorizationRequest(
                responseType = "code",
                clientId = "unknown-client",
                redirectUri = "http://localhost:3000/callback"
            )
            
            whenever(clientRepository.findByClientId("unknown-client"))
                .thenReturn(Optional.empty())
            
            val exception = assertThrows<OAuth2Exception> {
                authorizationService.validateAuthorizationRequest(request)
            }
            
            assertThat(exception.error).isEqualTo("invalid_client")
        }

        @Test
        @DisplayName("should reject request with mismatched redirect_uri")
        fun shouldRejectMismatchedRedirectUri() {
            val request = AuthorizationRequest(
                responseType = "code",
                clientId = "test-client",
                redirectUri = "http://evil.com/callback" // not registered
            )
            
            whenever(clientRepository.findByClientId("test-client"))
                .thenReturn(Optional.of(testClient))
            
            val exception = assertThrows<OAuth2Exception> {
                authorizationService.validateAuthorizationRequest(request)
            }
            
            assertThat(exception.error).isEqualTo("invalid_request")
            assertThat(exception.errorDescription).contains("redirect_uri")
        }

        @Test
        @DisplayName("should reject request with invalid scope")
        fun shouldRejectInvalidScope() {
            val request = AuthorizationRequest(
                responseType = "code",
                clientId = "test-client",
                redirectUri = "http://localhost:3000/callback",
                scope = "openid admin" // 'admin' not allowed
            )
            
            whenever(clientRepository.findByClientId("test-client"))
                .thenReturn(Optional.of(testClient))
            
            val exception = assertThrows<OAuth2Exception> {
                authorizationService.validateAuthorizationRequest(request)
            }
            
            assertThat(exception.error).isEqualTo("invalid_scope")
        }

        @Test
        @DisplayName("should reject request missing PKCE when required")
        fun shouldRejectMissingPkce() {
            val request = AuthorizationRequest(
                responseType = "code",
                clientId = "test-client",
                redirectUri = "http://localhost:3000/callback",
                codeChallenge = null // missing PKCE
            )
            
            whenever(clientRepository.findByClientId("test-client"))
                .thenReturn(Optional.of(testClient))
            
            val exception = assertThrows<OAuth2Exception> {
                authorizationService.validateAuthorizationRequest(request)
            }
            
            assertThat(exception.error).isEqualTo("invalid_request")
            assertThat(exception.errorDescription).contains("PKCE")
        }

        @Test
        @DisplayName("should reject disabled client")
        fun shouldRejectDisabledClient() {
            val disabledClient = testClient.copy(enabled = false)
            val request = AuthorizationRequest(
                responseType = "code",
                clientId = "test-client",
                redirectUri = "http://localhost:3000/callback"
            )
            
            whenever(clientRepository.findByClientId("test-client"))
                .thenReturn(Optional.of(disabledClient))
            
            val exception = assertThrows<OAuth2Exception> {
                authorizationService.validateAuthorizationRequest(request)
            }
            
            assertThat(exception.error).isEqualTo("invalid_client")
        }
    }

    @Nested
    @DisplayName("createAuthorizationCode")
    inner class CreateAuthorizationCode {

        @Test
        @DisplayName("should create authorization code with all parameters")
        fun shouldCreateCodeWithAllParameters() {
            val request = AuthorizationRequest(
                responseType = "code",
                clientId = "test-client",
                redirectUri = "http://localhost:3000/callback",
                scope = "openid profile",
                state = "abc123",
                codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                codeChallengeMethod = "S256",
                nonce = "nonce123"
            )
            
            whenever(codeRepository.save(any<AuthorizationCode>()))
                .thenAnswer { it.arguments[0] as AuthorizationCode }
            
            val code = authorizationService.createAuthorizationCode(
                client = testClient,
                request = request,
                userId = 42L,
                username = "testuser"
            )
            
            assertThat(code.code).isNotBlank()
            assertThat(code.code.length).isGreaterThanOrEqualTo(32)
            assertThat(code.clientId).isEqualTo("test-client")
            assertThat(code.userId).isEqualTo(42L)
            assertThat(code.username).isEqualTo("testuser")
            assertThat(code.redirectUri).isEqualTo("http://localhost:3000/callback")
            assertThat(code.scope).isEqualTo("openid profile")
            assertThat(code.codeChallenge).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
            assertThat(code.codeChallengeMethod).isEqualTo("S256")
            assertThat(code.nonce).isEqualTo("nonce123")
            assertThat(code.used).isFalse()
            
            verify(codeRepository).save(any())
            verify(auditService).log(
                action = eq("OAUTH2_CODE_ISSUED"),
                username = eq("testuser"),
                resourceType = eq("OAUTH2_CLIENT"),
                resourceId = eq("test-client"),
                details = any(),
                success = eq(true)
            )
        }

        @Test
        @DisplayName("should generate unique codes")
        fun shouldGenerateUniqueCodes() {
            val request = AuthorizationRequest(
                responseType = "code",
                clientId = "test-client",
                redirectUri = "http://localhost:3000/callback"
            )
            
            whenever(codeRepository.save(any<AuthorizationCode>()))
                .thenAnswer { it.arguments[0] as AuthorizationCode }
            
            val codes = (1..100).map {
                authorizationService.createAuthorizationCode(
                    client = testClient,
                    request = request,
                    userId = 1L,
                    username = "user"
                ).code
            }.toSet()
            
            assertThat(codes).hasSize(100)
        }
    }

    @Nested
    @DisplayName("buildAuthorizationRedirect")
    inner class BuildAuthorizationRedirect {

        @Test
        @DisplayName("should build redirect URI with code and state")
        fun shouldBuildRedirectWithCodeAndState() {
            val redirectUri = authorizationService.buildAuthorizationRedirect(
                baseRedirectUri = "http://localhost:3000/callback",
                code = "auth-code-123",
                state = "state-abc"
            )
            
            assertThat(redirectUri).isEqualTo(
                "http://localhost:3000/callback?code=auth-code-123&state=state-abc"
            )
        }

        @Test
        @DisplayName("should build redirect URI without state when not provided")
        fun shouldBuildRedirectWithoutState() {
            val redirectUri = authorizationService.buildAuthorizationRedirect(
                baseRedirectUri = "http://localhost:3000/callback",
                code = "auth-code-123",
                state = null
            )
            
            assertThat(redirectUri).isEqualTo(
                "http://localhost:3000/callback?code=auth-code-123"
            )
        }

        @Test
        @DisplayName("should preserve existing query parameters")
        fun shouldPreserveExistingQueryParams() {
            val redirectUri = authorizationService.buildAuthorizationRedirect(
                baseRedirectUri = "http://localhost:3000/callback?existing=param",
                code = "auth-code-123",
                state = "state-abc"
            )
            
            assertThat(redirectUri).contains("existing=param")
            assertThat(redirectUri).contains("code=auth-code-123")
            assertThat(redirectUri).contains("state=state-abc")
        }
    }

    @Nested
    @DisplayName("buildErrorRedirect")
    inner class BuildErrorRedirect {

        @Test
        @DisplayName("should build error redirect with all parameters")
        fun shouldBuildErrorRedirect() {
            val redirectUri = authorizationService.buildErrorRedirect(
                baseRedirectUri = "http://localhost:3000/callback",
                error = "access_denied",
                errorDescription = "User denied consent",
                state = "state-abc"
            )
            
            assertThat(redirectUri).contains("error=access_denied")
            assertThat(redirectUri).contains("error_description=User")
            assertThat(redirectUri).contains("state=state-abc")
        }
    }
}
