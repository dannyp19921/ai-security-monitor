# Infrastructure

This directory contains infrastructure configurations for deploying AI Security Monitor.

## Structure

```
infrastructure/
├── kubernetes/          # Raw Kubernetes manifests
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── secrets.yaml
│   ├── backend-deployment.yaml
│   ├── backend-service.yaml
│   ├── postgres.yaml
│   ├── ingress.yaml
│   ├── kustomization.yaml
│   └── README.md
│
└── helm/               # Helm chart for templated deployment
    └── ai-security-monitor/
        ├── Chart.yaml
        ├── values.yaml
        └── templates/
            ├── _helpers.tpl
            ├── deployment.yaml
            ├── service.yaml
            ├── configmap.yaml
            ├── secret.yaml
            ├── ingress.yaml
            └── serviceaccount.yaml
```

## Deployment Options

### Option 1: Raw Kubernetes (simple, for learning)

```bash
# Apply all resources
kubectl apply -k kubernetes/
```

### Option 2: Helm (recommended for production)

```bash
# Install with default values
helm install ai-security-monitor helm/ai-security-monitor/

# Install with custom values
helm install ai-security-monitor helm/ai-security-monitor/ \
  --set config.oauth2Issuer=https://your-domain.com \
  --set secrets.databasePassword=your-secure-password

# Upgrade existing release
helm upgrade ai-security-monitor helm/ai-security-monitor/
```

## Why Two Options?

| Aspect | Raw Kubernetes | Helm |
|--------|---------------|------|
| Learning | Easier to understand | More complex |
| Reusability | Copy/paste | Parameterized |
| Environment configs | Manual editing | values.yaml per env |
| Upgrades | kubectl apply | helm upgrade |
| Rollback | Manual | helm rollback |
| Dependencies | Manual | Chart dependencies |

## Relevance for Job Applications

### DFØ (IAM Advisor)
- Infrastructure as Code principles
- Security configurations (secrets management, RBAC)
- Enterprise deployment patterns

### Cyberforsvaret (Open Source Developer)
- Kubernetes proficiency (directly mentioned as "ønskelig")
- Container orchestration
- Cloud-native application design
- DevOps practices
