// backend/src/main/kotlin/com/securemonitor/scim/dto/ScimDtos.kt
package com.securemonitor.scim.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * SCIM 2.0 DTOs following RFC 7643 (Core Schema) and RFC 7644 (Protocol).
 * 
 * SCIM (System for Cross-domain Identity Management) provides a standardized
 * API for managing user identities across systems. This enables automated
 * user provisioning from HR systems, identity providers, and other sources.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7643">RFC 7643 - SCIM Core Schema</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7644">RFC 7644 - SCIM Protocol</a>
 */

// =============================================================================
// Common SCIM Types
// =============================================================================

/**
 * SCIM Meta attribute containing resource metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimMeta(
    val resourceType: String,
    val created: String? = null,
    val lastModified: String? = null,
    val location: String? = null,
    val version: String? = null
)

/**
 * SCIM Email attribute (multi-valued).
 */
data class ScimEmail(
    val value: String,
    val type: String? = "work",
    val primary: Boolean = true
)

/**
 * SCIM Group reference (for user's group membership).
 */
data class ScimGroupRef(
    val value: String,
    val display: String? = null,
    @JsonProperty("\$ref")
    val ref: String? = null
)

/**
 * SCIM Member reference (for group's members).
 */
data class ScimMemberRef(
    val value: String,
    val display: String? = null,
    @JsonProperty("\$ref")
    val ref: String? = null,
    val type: String? = "User"
)

// =============================================================================
// User Resource
// =============================================================================

/**
 * SCIM User Resource (RFC 7643 Section 4.1).
 * 
 * Represents a user identity with standard attributes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimUser(
    val schemas: List<String> = listOf(SCHEMA_USER),
    val id: String? = null,
    val externalId: String? = null,
    val userName: String,
    val name: ScimName? = null,
    val displayName: String? = null,
    val emails: List<ScimEmail>? = null,
    val active: Boolean = true,
    val groups: List<ScimGroupRef>? = null,
    val meta: ScimMeta? = null,
    
    // Extension: MFA status
    @JsonProperty("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
    val enterpriseUser: ScimEnterpriseUser? = null
) {
    companion object {
        const val SCHEMA_USER = "urn:ietf:params:scim:schemas:core:2.0:User"
        const val SCHEMA_ENTERPRISE = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
    }
}

/**
 * SCIM Name attribute (complex).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimName(
    val formatted: String? = null,
    val familyName: String? = null,
    val givenName: String? = null
)

/**
 * SCIM Enterprise User extension.
 * Used for additional enterprise attributes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimEnterpriseUser(
    val employeeNumber: String? = null,
    val department: String? = null,
    val manager: ScimManagerRef? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimManagerRef(
    val value: String? = null,
    val displayName: String? = null
)

// =============================================================================
// Group Resource
// =============================================================================

/**
 * SCIM Group Resource (RFC 7643 Section 4.2).
 * 
 * Represents a group/role for access control.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimGroup(
    val schemas: List<String> = listOf(SCHEMA_GROUP),
    val id: String? = null,
    val displayName: String,
    val members: List<ScimMemberRef>? = null,
    val meta: ScimMeta? = null
) {
    companion object {
        const val SCHEMA_GROUP = "urn:ietf:params:scim:schemas:core:2.0:Group"
    }
}

// =============================================================================
// List Response
// =============================================================================

/**
 * SCIM List Response (RFC 7644 Section 3.4.2).
 * 
 * Used for returning multiple resources with pagination.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimListResponse<T>(
    val schemas: List<String> = listOf(SCHEMA_LIST),
    val totalResults: Int,
    val startIndex: Int = 1,
    val itemsPerPage: Int,
    @JsonProperty("Resources")
    val resources: List<T>
) {
    companion object {
        const val SCHEMA_LIST = "urn:ietf:params:scim:api:messages:2.0:ListResponse"
    }
}

// =============================================================================
// Error Response
// =============================================================================

/**
 * SCIM Error Response (RFC 7644 Section 3.12).
 */
data class ScimError(
    val schemas: List<String> = listOf(SCHEMA_ERROR),
    val status: String,
    val scimType: String? = null,
    val detail: String? = null
) {
    companion object {
        const val SCHEMA_ERROR = "urn:ietf:params:scim:api:messages:2.0:Error"
        
        // Error types
        const val TYPE_INVALID_FILTER = "invalidFilter"
        const val TYPE_TOO_MANY = "tooMany"
        const val TYPE_UNIQUENESS = "uniqueness"
        const val TYPE_MUTABILITY = "mutability"
        const val TYPE_INVALID_VALUE = "invalidValue"
        const val TYPE_NO_TARGET = "noTarget"
    }
}

// =============================================================================
// Request DTOs
// =============================================================================

/**
 * Request body for creating a SCIM User.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimUserCreateRequest(
    val schemas: List<String>? = null,
    val userName: String,
    val name: ScimName? = null,
    val displayName: String? = null,
    val emails: List<ScimEmail>? = null,
    val active: Boolean = true,
    val password: String? = null,
    val externalId: String? = null
)

/**
 * SCIM Patch Operation (RFC 7644 Section 3.5.2).
 */
data class ScimPatchOp(
    val schemas: List<String> = listOf("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
    @JsonProperty("Operations")
    val operations: List<ScimPatchOperation>
)

data class ScimPatchOperation(
    val op: String, // "add", "remove", "replace"
    val path: String? = null,
    val value: Any? = null
)
