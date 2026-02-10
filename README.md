# AI Security Monitor

A comprehensive IAM (Identity and Access Management) portfolio project demonstrating enterprise-grade security features including JWT authentication, OAuth 2.0/OIDC Provider, Multi-Factor Authentication (MFA/TOTP), SCIM 2.0 provisioning, role-based access control, and AI-powered security assistance.

## 🌐 Live Demo

- **Frontend:** https://ai-security-monitor.vercel.app
- **Backend API:** https://ai-security-monitor-production.up.railway.app

## ✨ Features

### Authentication & Authorization
- **JWT Authentication** - Secure token-based auth with BCrypt password hashing
- **Multi-Factor Authentication (MFA)** - TOTP-based 2FA with backup codes (RFC 6238)
- **OAuth 2.0/OIDC Provider** - Built from scratch with PKCE support (RFC 7636)
- **Role-Based Access Control (RBAC)** - USER and ADMIN roles
- **Google OAuth2 Login** - Federated identity support

### Identity Provisioning
- **SCIM 2.0 API** - Standardized user provisioning (RFC 7643/7644)
- **Automated User Lifecycle** - Create, update, deactivate users programmatically
- **Group/Role Management** - SCIM Groups mapped to internal roles

### Security Features
- **Comprehensive Audit Logging** - All security events tracked with timestamps and IP addresses
- **PKCE (S256)** - Proof Key for Code Exchange for public clients
- **Timing-Safe Comparisons** - Protection against timing attacks
- **Backup Codes** - SHA-256 hashed recovery codes for MFA

### Additional Features
- **AI Security Assistant** - Security-focused chat powered by Groq LLM
- **Admin Panel** - User management and role assignment
- **Responsive Design** - Mobile-first UI with Tailwind CSS

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              FRONTEND (Vercel)                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │  LoginForm  │  │  MfaSetup   │  │  MfaVerify  │  │  Dashboard  │        │
│  │  + MFA Flow │  │  (QR Code)  │  │  (TOTP/Backup)│ │  + Admin   │        │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘        │
│                              React + TypeScript + Tailwind                   │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼ HTTPS/JWT
┌─────────────────────────────────────────────────────────────────────────────┐
│                            BACKEND (Railway)                                 │
│                         Kotlin + Spring Boot 3.5                            │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                        Security Layer                                 │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                  │  │
│  │  │ JWT Filter  │  │ CORS Config │  │ BCrypt      │                  │  │
│  │  │ + MFA Check │  │             │  │ Encoder     │                  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                  │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
│  │Auth Module  │  │ MFA Module  │  │OAuth2 Prov. │  │ SCIM 2.0    │       │
│  │- login      │  │- RFC 6238   │  │- /authorize │  │- /Users     │       │
│  │- register   │  │- TOTP       │  │- /token     │  │- /Groups    │       │
│  │- MFA check  │  │- backup     │  │- /userinfo  │  │- CRUD ops   │       │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘       │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                         Data Layer                                    │  │
│  │  ┌───────────────────┐  ┌───────────────────┐  ┌─────────────────┐  │  │
│  │  │   User Entity     │  │    Role Entity    │  │   AuditLog      │  │  │
│  │  │   + MFA fields    │  │    (USER/ADMIN)   │  │   (all events)  │  │  │
│  │  └───────────────────┘  └───────────────────┘  └─────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────┐
                         │   PostgreSQL (Railway)   │
                         └──────────────────────────┘
```

## 🔐 MFA Flow

```
     ┌────────┐          ┌─────────┐             ┌─────────┐            ┌─────────┐
     │  User  │          │ Frontend│             │ Backend │            │  Auth   │
     │        │          │         │             │         │            │   App   │
     └───┬────┘          └────┬────┘             └────┬────┘            └────┬────┘
         │                    │                       │                      │
         │  1. Enable MFA     │                       │                      │
         │───────────────────>│  POST /mfa/setup     │                      │
         │                    │──────────────────────>│                      │
         │                    │                       │                      │
         │                    │  {secret, qrCodeUri} │                      │
         │                    │<──────────────────────│                      │
         │                    │                       │                      │
         │  2. Show QR Code   │                       │                      │
         │<───────────────────│                       │                      │
         │                    │                       │                      │
         │  3. Scan QR        │                       │                      │
         │────────────────────────────────────────────────────────────────────>
         │                    │                       │                      │
         │  4. Enter Code     │                       │                      │
         │───────────────────>│  POST /mfa/verify    │                      │
         │                    │──────────────────────>│                      │
         │                    │                       │                      │
         │                    │  {backupCodes[10]}   │                      │
         │                    │<──────────────────────│                      │
         │                    │                       │                      │
         │  5. Save Backup    │                       │                      │
         │<───────────────────│                       │                      │
```

## 🔑 OAuth 2.0/OIDC Provider

This project includes a **custom-built OAuth 2.0/OIDC Provider** (not using Keycloak or other libraries) to demonstrate deep protocol understanding.

### Supported Features
- Authorization Code Flow with PKCE (RFC 7636)
- OpenID Connect Discovery (`/.well-known/openid-configuration`)
- JWKS Endpoint (`/.well-known/jwks.json`)
- UserInfo Endpoint (`/oauth2/userinfo`)
- ID Tokens with standard claims

### Endpoints
| Endpoint | Description |
|----------|-------------|
| `GET /oauth2/authorize` | Authorization endpoint |
| `POST /oauth2/token` | Token exchange |
| `GET /oauth2/userinfo` | User information |
| `GET /.well-known/openid-configuration` | OIDC Discovery |
| `GET /.well-known/jwks.json` | JSON Web Key Set |

### Example Flow
```bash
# 1. Generate PKCE verifier and challenge
CODE_VERIFIER=$(openssl rand -base64 32 | tr -d '=+/' | cut -c1-43)
CODE_CHALLENGE=$(echo -n $CODE_VERIFIER | openssl sha256 -binary | base64 | tr -d '=' | tr '+/' '-_')

# 2. Authorize (browser redirect)
https://api.example.com/oauth2/authorize?
  response_type=code&
  client_id=my-client&
  redirect_uri=http://localhost:3000/callback&
  scope=openid%20profile%20email&
  code_challenge=$CODE_CHALLENGE&
  code_challenge_method=S256&
  state=random-state

# 3. Exchange code for tokens
curl -X POST https://api.example.com/oauth2/token \
  -d "grant_type=authorization_code" \
  -d "code=AUTH_CODE" \
  -d "redirect_uri=http://localhost:3000/callback" \
  -d "client_id=my-client" \
  -d "code_verifier=$CODE_VERIFIER"
```

## 📋 SCIM 2.0 API

SCIM (System for Cross-domain Identity Management) enables automated user provisioning from HR systems, IdPs, and other identity sources. This implementation follows RFC 7643 (Core Schema) and RFC 7644 (Protocol).

### Why SCIM?

In enterprise environments, manually managing user accounts across multiple systems is error-prone and doesn't scale. SCIM provides:
- **Automated Provisioning** - Create users automatically when they join
- **Lifecycle Management** - Deactivate accounts when employees leave
- **Consistency** - Standardized API works with any SCIM-compliant IdP
- **Audit Trail** - All provisioning events are logged

### Discovery Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /scim/v2/ServiceProviderConfig` | Server capabilities and supported features |
| `GET /scim/v2/ResourceTypes` | Available resource types (Users, Groups) |
| `GET /scim/v2/Schemas` | SCIM schemas with attribute definitions |

### User Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/scim/v2/Users` | List users with filtering/pagination |
| `GET` | `/scim/v2/Users/{id}` | Get single user |
| `POST` | `/scim/v2/Users` | Create new user |
| `PUT` | `/scim/v2/Users/{id}` | Replace user (full update) |
| `PATCH` | `/scim/v2/Users/{id}` | Partial update user |
| `DELETE` | `/scim/v2/Users/{id}` | Delete user |

### Group Endpoints (Read-Only)

Groups are mapped to internal roles (USER, ADMIN).

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/scim/v2/Groups` | List all groups/roles |
| `GET` | `/scim/v2/Groups/{id}` | Get single group with members |

### Example Usage

**Create a User:**
```bash
curl -X POST https://api.example.com/scim/v2/Users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/scim+json" \
  -d '{
    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
    "userName": "jdoe",
    "emails": [{"value": "jdoe@example.com", "primary": true}],
    "active": true,
    "password": "SecurePass123!"
  }'
```

**Response:**
```json
{
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "id": "42",
  "userName": "jdoe",
  "emails": [{"value": "jdoe@example.com", "primary": true}],
  "active": true,
  "groups": [{"value": "1", "display": "USER", "$ref": "/scim/v2/Groups/1"}],
  "meta": {
    "resourceType": "User",
    "created": "2025-01-14T10:30:00Z",
    "location": "/scim/v2/Users/42"
  }
}
```

**List Users with Filter:**
```bash
# Find user by email
curl "https://api.example.com/scim/v2/Users?filter=emails.value%20eq%20%22jdoe@example.com%22" \
  -H "Authorization: Bearer $TOKEN"

# Find active users
curl "https://api.example.com/scim/v2/Users?filter=active%20eq%20true&startIndex=1&count=10" \
  -H "Authorization: Bearer $TOKEN"
```

**Deactivate User (PATCH):**
```bash
curl -X PATCH https://api.example.com/scim/v2/Users/42 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/scim+json" \
  -d '{
    "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
    "Operations": [
      {"op": "replace", "path": "active", "value": false}
    ]
  }'
```

**Delete User:**
```bash
curl -X DELETE https://api.example.com/scim/v2/Users/42 \
  -H "Authorization: Bearer $TOKEN"
# Returns 204 No Content
```

### Supported Features

| Feature | Status | Notes |
|---------|--------|-------|
| Filter | ✅ | `userName eq`, `emails.value eq`, `active eq` |
| Pagination | ✅ | `startIndex`, `count` parameters |
| PATCH Operations | ✅ | `add`, `replace`, `remove` |
| Bulk Operations | ❌ | Not implemented |
| Sorting | ❌ | Not implemented |
| ETags | ❌ | Not implemented |

### Error Responses

SCIM uses standardized error format (RFC 7644 Section 3.12):

```json
{
  "schemas": ["urn:ietf:params:scim:api:messages:2.0:Error"],
  "status": "409",
  "scimType": "uniqueness",
  "detail": "userName 'jdoe' is already taken"
}
```

| Status | scimType | Description |
|--------|----------|-------------|
| 400 | `invalidValue` | Invalid attribute value |
| 404 | `noTarget` | Resource not found |
| 409 | `uniqueness` | Unique constraint violation |

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | React 18, TypeScript, Tailwind CSS, Vite |
| Backend | Kotlin, Spring Boot 3.5, Spring Security |
| Database | PostgreSQL 16 |
| Auth | JWT (jjwt), BCrypt, TOTP (RFC 6238) |
| OAuth | Custom OIDC Provider with PKCE |
| Provisioning | SCIM 2.0 (RFC 7643/7644) |
| AI | Groq API (Llama 3.1) |
| Deploy | Vercel (frontend), Railway (backend + DB) |
| CI/CD | GitHub Actions |
| Infra | Kubernetes manifests, Helm charts |

## 🚀 Local Development

### Prerequisites
- Docker and Docker Compose
- Node.js 20+
- Java 21+

### Quick Start

```bash
# Clone repository
git clone https://github.com/dannyp19921/ai-security-monitor.git
cd ai-security-monitor

# Start database
docker-compose up -d

# Backend (terminal 1)
cd backend
./gradlew bootRun

# Frontend (terminal 2)
cd frontend
npm install
npm run dev
```

### Environment Variables

**Backend (`backend/src/main/resources/application.yml`):**
```yaml
PGHOST: localhost
PGPORT: 5432
PGDATABASE: securemonitor
PGUSER: securemonitor
PGPASSWORD: localdev123
JWT_SECRET: your-256-bit-secret
GROQ_API_KEY: your-groq-api-key
GOOGLE_CLIENT_ID: your-google-client-id
GOOGLE_CLIENT_SECRET: your-google-client-secret
```

**Frontend (`.env`):**
```
VITE_API_URL=http://localhost:8080
```

## 🧪 Testing

```bash
# Run all backend tests
cd backend
./gradlew test

# Run specific test class
./gradlew test --tests "TotpServiceTest"

# Test coverage report
./gradlew jacocoTestReport
```

### Test Coverage Highlights
- **TotpService:** 33 tests covering RFC 6238 compliance
- **PkceService:** PKCE S256 and plain method tests
- **OAuth2 flows:** Authorization code exchange tests

## 📁 Project Structure

```
ai-security-monitor/
├── backend/
│   └── src/main/kotlin/com/securemonitor/
│       ├── config/          # Security, CORS configuration
│       ├── controller/      # REST endpoints
│       ├── dto/             # Data transfer objects
│       ├── mfa/             # MFA module (TOTP, backup codes)
│       │   ├── controller/
│       │   ├── dto/
│       │   └── service/
│       ├── model/           # JPA entities
│       ├── oauth2/          # OAuth 2.0/OIDC Provider
│       │   ├── controller/
│       │   ├── dto/
│       │   ├── model/
│       │   ├── repository/
│       │   └── service/
│       ├── scim/            # SCIM 2.0 API
│       │   ├── controller/  # User, Group, Config endpoints
│       │   └── dto/         # SCIM resource types
│       ├── repository/      # Data access
│       ├── security/        # JWT, filters
│       └── service/         # Business logic
├── frontend/
│   └── src/
│       ├── components/
│       │   ├── auth/        # Login, Register, MFA components
│       │   ├── admin/       # Admin panel
│       │   ├── chat/        # AI chat
│       │   └── ui/          # Reusable UI components
│       ├── pages/           # Page components
│       ├── services/        # API services
│       └── types/           # TypeScript types
├── infrastructure/
│   ├── kubernetes/          # K8s manifests
│   └── helm/                # Helm charts
└── docker-compose.yml
```

## Key Technical Highlights

### OAuth 2.0/OIDC Implementation
- Built OAuth 2.0 Provider from scratch (not using Keycloak) to demonstrate deep protocol understanding
- Implemented PKCE with S256 method for public client security
- Full OIDC compliance with Discovery and JWKS endpoints

### SCIM 2.0 Implementation
- Standardized user provisioning following RFC 7643/7644
- Full CRUD operations with PATCH support for partial updates
- Filter and pagination for efficient querying
- Enables integration with enterprise IdPs (Azure AD, Okta, etc.)

### Security Best Practices
- Timing-safe comparisons to prevent timing attacks
- SHA-256 hashed backup codes
- Comprehensive audit logging of all security events
- Short-lived authorization codes (10 minutes)
- Single-use codes to prevent replay attacks

### MFA Implementation
- RFC 6238 compliant TOTP with 33 unit tests
- Clock drift tolerance (±30 seconds)
- Backup codes for account recovery

### Kotlin & Spring Boot
- Clean, idiomatic Kotlin with Spring Boot
- Data classes, extension functions, null safety
- RESTful API design with PostgreSQL/JPA

### DevOps & Infrastructure
- Docker containerization
- Kubernetes manifests + Helm charts
- GitHub Actions CI/CD
- Railway + Vercel deployment

## 📜 License

MIT License - see [LICENSE](LICENSE) for details.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
