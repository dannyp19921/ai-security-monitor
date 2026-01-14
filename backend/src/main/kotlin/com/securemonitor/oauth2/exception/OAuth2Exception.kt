package com.securemonitor.oauth2.exception

import org.springframework.http.HttpStatus

/**
 * Exception representing OAuth 2.0 error responses.
 * 
 * Follows RFC 6749 Section 4.1.2.1 and 5.2 for error response format.
 * All OAuth 2.0 errors should use this exception to ensure consistent
 * error responses across all endpoints.
 * 
 * @property error The OAuth 2.0 error code (e.g., "invalid_request", "invalid_grant")
 * @property errorDescription Human-readable description of the error
 * @property errorUri Optional URI pointing to documentation about the error
 * @property httpStatus The HTTP status code to return
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1">RFC 6749 Section 4.1.2.1</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-5.2">RFC 6749 Section 5.2</a>
 */
class OAuth2Exception(
    val error: String,
    val errorDescription: String? = null,
    val errorUri: String? = null,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST
) : RuntimeException(errorDescription ?: error) {

    companion object {
        /**
         * Standard OAuth 2.0 error codes as defined in RFC 6749.
         */
        object ErrorCodes {
            /** The request is malformed or missing required parameters */
            const val INVALID_REQUEST = "invalid_request"
            
            /** Client authentication failed */
            const val INVALID_CLIENT = "invalid_client"
            
            /** The provided authorization grant is invalid or expired */
            const val INVALID_GRANT = "invalid_grant"
            
            /** The client is not authorized for this grant type */
            const val UNAUTHORIZED_CLIENT = "unauthorized_client"
            
            /** The grant type is not supported */
            const val UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type"
            
            /** The requested scope is invalid or exceeds granted scope */
            const val INVALID_SCOPE = "invalid_scope"
            
            /** The resource owner denied the request */
            const val ACCESS_DENIED = "access_denied"
            
            /** The response type is not supported */
            const val UNSUPPORTED_RESPONSE_TYPE = "unsupported_response_type"
            
            /** The server encountered an unexpected error */
            const val SERVER_ERROR = "server_error"
            
            /** The server is temporarily unavailable */
            const val TEMPORARILY_UNAVAILABLE = "temporarily_unavailable"
        }

        /**
         * Factory methods for common OAuth 2.0 errors.
         */
        fun invalidRequest(description: String) = OAuth2Exception(
            error = ErrorCodes.INVALID_REQUEST,
            errorDescription = description,
            httpStatus = HttpStatus.BAD_REQUEST
        )

        fun invalidClient(description: String) = OAuth2Exception(
            error = ErrorCodes.INVALID_CLIENT,
            errorDescription = description,
            httpStatus = HttpStatus.UNAUTHORIZED
        )

        fun invalidGrant(description: String) = OAuth2Exception(
            error = ErrorCodes.INVALID_GRANT,
            errorDescription = description,
            httpStatus = HttpStatus.BAD_REQUEST
        )

        fun unauthorizedClient(description: String) = OAuth2Exception(
            error = ErrorCodes.UNAUTHORIZED_CLIENT,
            errorDescription = description,
            httpStatus = HttpStatus.FORBIDDEN
        )

        fun unsupportedGrantType(grantType: String) = OAuth2Exception(
            error = ErrorCodes.UNSUPPORTED_GRANT_TYPE,
            errorDescription = "Grant type '$grantType' is not supported",
            httpStatus = HttpStatus.BAD_REQUEST
        )

        fun invalidScope(description: String) = OAuth2Exception(
            error = ErrorCodes.INVALID_SCOPE,
            errorDescription = description,
            httpStatus = HttpStatus.BAD_REQUEST
        )

        fun accessDenied(description: String) = OAuth2Exception(
            error = ErrorCodes.ACCESS_DENIED,
            errorDescription = description,
            httpStatus = HttpStatus.FORBIDDEN
        )

        fun serverError(description: String) = OAuth2Exception(
            error = ErrorCodes.SERVER_ERROR,
            errorDescription = description,
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR
        )
    }

    /**
     * Converts this exception to a map suitable for JSON serialization.
     * Follows OAuth 2.0 error response format.
     */
    fun toErrorResponse(): Map<String, String?> = buildMap {
        put("error", error)
        errorDescription?.let { put("error_description", it) }
        errorUri?.let { put("error_uri", it) }
    }

    override fun toString(): String = buildString {
        append("OAuth2Exception(error=$error")
        errorDescription?.let { append(", description=$it") }
        append(", status=$httpStatus)")
    }
}
