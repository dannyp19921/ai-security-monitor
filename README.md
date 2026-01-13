# AI Security Monitor

A full-stack IAM (Identity and Access Management) demonstration project featuring JWT authentication, role-based access control, audit logging, and AI-powered security assistance.

## 🌐 Live Demo

**Frontend:** https://ai-security-monitor.vercel.app

## ✨ Features

- **Authentication:** JWT-based login and registration
- **RBAC:** Role-based access control (USER/ADMIN roles)
- **Audit Logging:** All security events logged with timestamps and IP addresses
- **AI Assistant:** Security-focused chat powered by Groq LLM
- **Responsive Design:** Mobile-first UI

## 🏗️ Architecture
```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│                 │     │                 │     │                 │
│  React Frontend │────▶│  Kotlin Backend │────▶│   PostgreSQL    │
│  (Vercel)       │     │  (Railway)      │     │   (Railway)     │
│                 │     │                 │     │                 │
└─────────────────┘     └────────┬────────┘     └─────────────────┘
                                 │
                                 ▼
                        ┌─────────────────┐
                        │    Groq LLM     │
                        │   (Cloud API)   │
                        └─────────────────┘
```

## 🛠️ Tech Stack

| Layer    | Technology                     |
|----------|--------------------------------|
| Frontend | React, TypeScript, Tailwind CSS |
| Backend  | Kotlin, Spring Boot 3.5        |
| Database | PostgreSQL 16                  |
| Auth     | JWT (jjwt), BCrypt             |
| AI       | Groq API (Llama 3.1)           |
| Deploy   | Vercel (frontend), Railway (backend) |

## 🚀 Local Development

### Prerequisites

- Docker and Docker Compose
- Node.js 20+
- Java 21+

### Quick Start (Docker)
```bash
# Clone the repository
git clone https://github.com/dannyp19921/ai-security-monitor.git
cd ai-security-monitor

# Set your Groq API key
export GROQ_API_KEY=your_key_here

# Start backend and database
docker-compose up -d

# Start frontend
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 in your browser.

### Manual Setup

See [Backend README](./backend/README.md) and [Frontend README](./frontend/README.md) for detailed setup instructions.

## 📁 Project Structure
```
ai-security-monitor/
├── backend/                 # Kotlin Spring Boot API
│   ├── src/main/kotlin/
│   │   └── com/securemonitor/
│   │       ├── config/      # Security, CORS configuration
│   │       ├── controller/  # REST endpoints
│   │       ├── dto/         # Data transfer objects
│   │       ├── model/       # JPA entities
│   │       ├── repository/  # Database repositories
│   │       ├── security/    # JWT filter and service
│   │       └── service/     # Business logic
│   └── Dockerfile
├── frontend/                # React TypeScript app
│   ├── src/
│   │   ├── components/      # Reusable UI components
│   │   ├── hooks/           # Custom React hooks
│   │   ├── pages/           # Page components
│   │   ├── services/        # API clients
│   │   └── types/           # TypeScript interfaces
└── docker-compose.yml       # Local development setup
```

## 🔒 API Endpoints

| Method | Endpoint                  | Auth     | Description           |
|--------|---------------------------|----------|-----------------------|
| GET    | /api/health               | Public   | Health check          |
| POST   | /api/auth/register        | Public   | Register new user     |
| POST   | /api/auth/login           | Public   | Login, returns JWT    |
| GET    | /api/audit/logs           | Required | Get audit logs        |
| POST   | /api/ai/chat              | Required | Chat with AI assistant|

## 👤 Author

Daniel-Aston Brandsgård Parker

## 📄 License

MIT