// backend/src/main/kotlin/com/securemonitor/scim/controller/ScimConfigController.kt
package com.securemonitor.scim.controller

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * SCIM 2.0 Service Provider Configuration endpoints.
 * 
 * These endpoints provide metadata about the SCIM implementation,
 * allowing clients to discover supported features.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7644#section-4">RFC 7644 Section 4</a>
 */
@RestController
@RequestMapping("/scim/v2")
class ScimConfigController {
    
    companion object {
        private const val SCIM_MEDIA_TYPE = "application/scim+json"
    }
    
    /**
     * Service Provider Configuration endpoint.
     * 
     * Returns metadata about the SCIM service provider's capabilities.
     * 
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7643#section-5">RFC 7643 Section 5</a>
     */
    @GetMapping("/ServiceProviderConfig", produces = [SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE])
    fun getServiceProviderConfig(): ResponseEntity<Map<String, Any>> {
        val config = mapOf(
            "schemas" to listOf("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"),
            "documentationUri" to "https://github.com/dannyp19921/ai-security-monitor",
            "patch" to mapOf("supported" to true),
            "bulk" to mapOf(
                "supported" to false,
                "maxOperations" to 0,
                "maxPayloadSize" to 0
            ),
            "filter" to mapOf(
                "supported" to true,
                "maxResults" to 100
            ),
            "changePassword" to mapOf("supported" to false),
            "sort" to mapOf("supported" to false),
            "etag" to mapOf("supported" to false),
            "authenticationSchemes" to listOf(
                mapOf(
                    "type" to "oauthbearertoken",
                    "name" to "OAuth Bearer Token",
                    "description" to "Authentication using JWT Bearer tokens",
                    "specUri" to "https://datatracker.ietf.org/doc/html/rfc6750",
                    "primary" to true
                )
            ),
            "meta" to mapOf(
                "resourceType" to "ServiceProviderConfig",
                "location" to "/scim/v2/ServiceProviderConfig"
            )
        )
        
        return ResponseEntity.ok(config)
    }
    
    /**
     * Resource Types endpoint.
     * 
     * Lists the types of SCIM resources supported by this server.
     * 
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7643#section-6">RFC 7643 Section 6</a>
     */
    @GetMapping("/ResourceTypes", produces = [SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE])
    fun getResourceTypes(): ResponseEntity<Map<String, Any>> {
        val resourceTypes = mapOf(
            "schemas" to listOf("urn:ietf:params:scim:api:messages:2.0:ListResponse"),
            "totalResults" to 2,
            "Resources" to listOf(
                mapOf(
                    "schemas" to listOf("urn:ietf:params:scim:schemas:core:2.0:ResourceType"),
                    "id" to "User",
                    "name" to "User",
                    "endpoint" to "/scim/v2/Users",
                    "description" to "User Account",
                    "schema" to "urn:ietf:params:scim:schemas:core:2.0:User",
                    "meta" to mapOf(
                        "resourceType" to "ResourceType",
                        "location" to "/scim/v2/ResourceTypes/User"
                    )
                ),
                mapOf(
                    "schemas" to listOf("urn:ietf:params:scim:schemas:core:2.0:ResourceType"),
                    "id" to "Group",
                    "name" to "Group",
                    "endpoint" to "/scim/v2/Groups",
                    "description" to "Group (Role)",
                    "schema" to "urn:ietf:params:scim:schemas:core:2.0:Group",
                    "meta" to mapOf(
                        "resourceType" to "ResourceType",
                        "location" to "/scim/v2/ResourceTypes/Group"
                    )
                )
            )
        )
        
        return ResponseEntity.ok(resourceTypes)
    }
    
    /**
     * Schemas endpoint.
     * 
     * Returns the schemas supported by this SCIM server.
     * 
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7643#section-7">RFC 7643 Section 7</a>
     */
    @GetMapping("/Schemas", produces = [SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE])
    fun getSchemas(): ResponseEntity<Map<String, Any>> {
        val schemas = mapOf(
            "schemas" to listOf("urn:ietf:params:scim:api:messages:2.0:ListResponse"),
            "totalResults" to 2,
            "Resources" to listOf(
                mapOf(
                    "id" to "urn:ietf:params:scim:schemas:core:2.0:User",
                    "name" to "User",
                    "description" to "User Account",
                    "attributes" to listOf(
                        mapOf(
                            "name" to "userName",
                            "type" to "string",
                            "multiValued" to false,
                            "required" to true,
                            "uniqueness" to "server"
                        ),
                        mapOf(
                            "name" to "emails",
                            "type" to "complex",
                            "multiValued" to true,
                            "required" to false
                        ),
                        mapOf(
                            "name" to "active",
                            "type" to "boolean",
                            "multiValued" to false,
                            "required" to false
                        ),
                        mapOf(
                            "name" to "groups",
                            "type" to "complex",
                            "multiValued" to true,
                            "mutability" to "readOnly"
                        )
                    ),
                    "meta" to mapOf(
                        "resourceType" to "Schema",
                        "location" to "/scim/v2/Schemas/urn:ietf:params:scim:schemas:core:2.0:User"
                    )
                ),
                mapOf(
                    "id" to "urn:ietf:params:scim:schemas:core:2.0:Group",
                    "name" to "Group",
                    "description" to "Group (Role)",
                    "attributes" to listOf(
                        mapOf(
                            "name" to "displayName",
                            "type" to "string",
                            "multiValued" to false,
                            "required" to true
                        ),
                        mapOf(
                            "name" to "members",
                            "type" to "complex",
                            "multiValued" to true,
                            "required" to false
                        )
                    ),
                    "meta" to mapOf(
                        "resourceType" to "Schema",
                        "location" to "/scim/v2/Schemas/urn:ietf:params:scim:schemas:core:2.0:Group"
                    )
                )
            )
        )
        
        return ResponseEntity.ok(schemas)
    }
}
