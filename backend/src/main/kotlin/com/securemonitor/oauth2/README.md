# OAuth 2.0 / OpenID Connect Implementation

This module implements an OAuth 2.0 Authorization Server with OpenID Connect (OIDC) support.

## Overview

This implementation follows these specifications:
- [RFC 6749 - OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc6749)
- [RFC 7636 - PKCE](https://datatracker.ietf.org/doc/html/rfc7636)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html)

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/oauth2/authorize` | GET | Authorization endpoint - initiates the flow |
| `/oauth2/token` | POST | Token endpoint - exchanges code for tokens |
| `/oauth2/userinfo` | GET | UserInfo endpoint - returns user claims |
| `/.well-known/openid-configuration` | GET | OIDC Discovery document |
| `/.well-known/jwks.json` | GET | JSON Web Key Set |

## Supported Flows

### Authorization Code Flow with PKCE (Recommended)

```
┌──────────┐                                  ┌──────────────┐
│          │                                  │              │
│  Client  │                                  │    Auth      │
│  (SPA)   │                                  │   Server     │
│          │                                  │              │
└────┬─────┘                                  └──────┬───────┘
     │                                               │
     │  1. Generate code_verifier (random string)    │
     │  2. Compute code_challenge = SHA256(verifier) │
     │                                               │
     │  3. GET /oauth2/authorize                     │
     │     ?response_type=code                       │
     │     &client_id=...                            │
     │     &redirect_uri=...                         │
     │     &code_challenge=...                       │
     │     &code_challenge_method=S256              │
     │     &scope=openid profile                     │
     │     &state=...                                │
     │─────────────────────────────────────────────▶│
     │                                               │
     │  4. User authenticates & consents             │
     │                                               │
     │  5. 302 Redirect to redirect_uri              │
     │     ?code=AUTHORIZATION_CODE                  │
     │     &state=...                                │
     │◀─────────────────────────────────────────────│
     │                                               │
     │  6. POST /oauth2/token                        │
     │     grant_type=authorization_code             │
     │     &code=AUTHORIZATION_CODE                  │
     │     &redirect_uri=...                         │
     │     &client_id=...                            │
     │     &code_verifier=... (original verifier)    │
     │─────────────────────────────────────────────▶│
     │                                               │
     │  7. Validate code_verifier matches challenge  │
     │                                               │
     │  8. 200 OK                                    │
     │     {                                         │
     │       "access_token": "...",                  │
     │       "token_type": "Bearer",                 │
     │       "expires_in": 3600,                     │
     │       "refresh_token": "...",                 │
     │       "id_token": "..."                       │
     │     }                                         │
     │◀─────────────────────────────────────────────│
     │                                               │
```

## PKCE (Proof Key for Code Exchange)

PKCE prevents authorization code interception attacks. It's **required** for public clients (SPAs, mobile apps).

### How it works:

1. **Client generates `code_verifier`**: A cryptographically random string (43-128 chars)
2. **Client computes `code_challenge`**: `BASE64URL(SHA256(code_verifier))`
3. **Authorization request** includes `code_challenge` and `code_challenge_method=S256`
4. **Token request** includes the original `code_verifier`
5. **Server verifies** that `SHA256(code_verifier) == stored_challenge`

### Example:

```javascript
// 1. Generate code verifier
const codeVerifier = generateRandomString(64);
// e.g., "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

// 2. Compute code challenge
const codeChallenge = base64UrlEncode(sha256(codeVerifier));
// e.g., "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

// 3. Include in authorization request
const authUrl = `/oauth2/authorize?
  response_type=code&
  client_id=my-app&
  redirect_uri=${encodeURIComponent(redirectUri)}&
  code_challenge=${codeChallenge}&
  code_challenge_method=S256&
  scope=openid profile`;
```

## Token Types

### Access Token
- JWT format
- Used to access protected resources
- Short-lived (1 hour default)
- Contains: `sub`, `iss`, `aud`, `exp`, `iat`, `scope`, `username`

### ID Token (OIDC)
- JWT format
- Contains user identity claims
- Only issued when `openid` scope is requested
- Contains: `sub`, `iss`, `aud`, `exp`, `iat`, `auth_time`, `nonce`, `preferred_username`

### Refresh Token
- Opaque token
- Used to obtain new access tokens
- Long-lived (30 days default)
- Single-use (rotated on each use)

## Scopes

| Scope | Description | Claims |
|-------|-------------|--------|
| `openid` | Required for OIDC | `sub` |
| `profile` | Basic profile info | `preferred_username`, `name` |
| `email` | Email address | `email`, `email_verified` |

## Client Types

### Public Clients
- Cannot securely store credentials
- Examples: SPAs, mobile apps
- **Must use PKCE**
- No `client_secret`

### Confidential Clients
- Can securely store credentials
- Examples: Server-side apps
- Can use `client_secret` instead of PKCE
- Authenticate via Basic auth or POST body

## Security Considerations

1. **Always use HTTPS** in production
2. **PKCE is mandatory** for public clients
3. **State parameter** prevents CSRF attacks
4. **Authorization codes** are single-use and short-lived
5. **Redirect URIs** must exactly match registered URIs
6. **Token storage**: Store tokens securely (HttpOnly cookies or secure storage)

## Error Responses

All errors follow OAuth 2.0 error format:

```json
{
  "error": "invalid_request",
  "error_description": "Human-readable description"
}
```

### Error Codes

| Code | Description |
|------|-------------|
| `invalid_request` | Missing or invalid parameter |
| `invalid_client` | Client authentication failed |
| `invalid_grant` | Authorization code invalid or expired |
| `unauthorized_client` | Client not authorized for grant type |
| `unsupported_grant_type` | Grant type not supported |
| `invalid_scope` | Requested scope not allowed |
| `access_denied` | User denied consent |

## Testing

### Using cURL

```bash
# 1. Start authorization (in browser)
open "http://localhost:8080/oauth2/authorize?response_type=code&client_id=test-client&redirect_uri=http://localhost:3000/callback&scope=openid%20profile&state=abc123&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256"

# 2. Exchange code for tokens
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=RECEIVED_CODE" \
  -d "redirect_uri=http://localhost:3000/callback" \
  -d "client_id=test-client" \
  -d "code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

# 3. Get user info
curl http://localhost:8080/oauth2/userinfo \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

## Configuration

```yaml
oauth2:
  issuer: http://localhost:8080
  access-token-lifetime: 3600      # seconds
  refresh-token-lifetime: 2592000  # seconds
  authorization-code-lifetime: 600 # seconds
  require-pkce: true
```

## Relevant for DFØ Interview

This implementation demonstrates:

1. **Deep understanding of OAuth 2.0/OIDC protocols** - Built from scratch, not using a library
2. **PKCE implementation** - Critical for modern security
3. **Token handling** - JWT generation, validation, claims
4. **Security best practices** - Input validation, timing-safe comparisons, audit logging
5. **Federation concepts** - Can act as both IdP and SP
6. **Standards compliance** - Follows RFCs and OIDC specs

### Key talking points:

- **Why PKCE?** Prevents authorization code interception in public clients
- **Why S256 over plain?** Cryptographic binding, replay protection
- **Why short-lived codes?** Minimizes window of attack
- **Why single-use codes?** Prevents replay attacks
- **Why state parameter?** CSRF protection
- **Why nonce in ID token?** Replay protection for ID tokens
