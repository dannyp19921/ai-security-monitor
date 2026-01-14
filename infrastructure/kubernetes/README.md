# Kubernetes Deployment

This directory contains Kubernetes manifests for deploying AI Security Monitor.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Kubernetes Cluster                           │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    Namespace: ai-security-monitor            │   │
│  │                                                              │   │
│  │   ┌─────────────┐      ┌─────────────┐      ┌────────────┐  │   │
│  │   │   Ingress   │──────│   Service   │──────│ Deployment │  │   │
│  │   │   (HTTPS)   │      │  (ClusterIP)│      │  (2 pods)  │  │   │
│  │   └─────────────┘      └─────────────┘      └────────────┘  │   │
│  │          │                                        │         │   │
│  │          │              ┌─────────────┐          │         │   │
│  │          │              │  ConfigMap  │──────────┤         │   │
│  │          │              │  (config)   │          │         │   │
│  │          │              └─────────────┘          │         │   │
│  │          │              ┌─────────────┐          │         │   │
│  │          │              │   Secret    │──────────┤         │   │
│  │          │              │ (passwords) │          │         │   │
│  │          │              └─────────────┘          │         │   │
│  │          │                                        │         │   │
│  │          │              ┌─────────────┐          │         │   │
│  │          └──────────────│  PostgreSQL │──────────┘         │   │
│  │                         │   Service   │                    │   │
│  │                         └─────────────┘                    │   │
│  │                               │                            │   │
│  │                         ┌─────────────┐                    │   │
│  │                         │     PVC     │                    │   │
│  │                         │  (storage)  │                    │   │
│  │                         └─────────────┘                    │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Files

| File | Description |
|------|-------------|
| `namespace.yaml` | Namespace for resource isolation |
| `configmap.yaml` | Non-sensitive configuration |
| `secrets.yaml` | Sensitive configuration (passwords, keys) |
| `backend-deployment.yaml` | Backend application pods |
| `backend-service.yaml` | Internal networking for backend |
| `postgres.yaml` | PostgreSQL database |
| `ingress.yaml` | External HTTPS access |
| `kustomization.yaml` | Kustomize configuration |

## Prerequisites

- Kubernetes cluster (minikube, kind, GKE, EKS, AKS, etc.)
- kubectl configured
- Docker image built and pushed to registry

## Quick Start

### 1. Build and push Docker image

```bash
# Build the image
cd backend
docker build -t your-registry/ai-security-monitor-backend:latest .

# Push to registry
docker push your-registry/ai-security-monitor-backend:latest
```

### 2. Update image reference

Edit `backend-deployment.yaml` and update the image:
```yaml
image: your-registry/ai-security-monitor-backend:latest
```

### 3. Create secrets (do NOT use the placeholder values!)

```bash
kubectl create namespace ai-security-monitor

kubectl -n ai-security-monitor create secret generic ai-security-monitor-secrets \
  --from-literal=database-password='YOUR_SECURE_PASSWORD' \
  --from-literal=jwt-secret='YOUR_256_BIT_SECRET_MIN_32_CHARS' \
  --from-literal=groq-api-key='YOUR_GROQ_API_KEY'
```

### 4. Deploy with Kustomize

```bash
# Preview what will be applied
kubectl apply -k infrastructure/kubernetes/ --dry-run=client -o yaml

# Apply all resources
kubectl apply -k infrastructure/kubernetes/

# Watch deployment progress
kubectl -n ai-security-monitor get pods -w
```

### 5. Verify deployment

```bash
# Check all resources
kubectl -n ai-security-monitor get all

# Check pod logs
kubectl -n ai-security-monitor logs -l app=ai-security-monitor,component=backend

# Port forward for local testing
kubectl -n ai-security-monitor port-forward svc/ai-security-monitor-backend 8080:80

# Test health endpoint
curl http://localhost:8080/api/health
```

## Local Development with Minikube

```bash
# Start minikube
minikube start

# Enable ingress addon
minikube addons enable ingress

# Build image directly in minikube's Docker daemon
eval $(minikube docker-env)
cd backend && docker build -t ai-security-monitor-backend:latest .

# Update deployment to use local image
# Set imagePullPolicy: Never in backend-deployment.yaml

# Deploy
kubectl apply -k infrastructure/kubernetes/

# Get minikube IP and add to /etc/hosts
echo "$(minikube ip) api.ai-security-monitor.example.com" | sudo tee -a /etc/hosts

# Access the application
curl http://api.ai-security-monitor.example.com/api/health
```

## Production Considerations

### Security
- [ ] Use proper secrets management (Vault, Sealed Secrets, External Secrets)
- [ ] Enable Network Policies to restrict pod-to-pod communication
- [ ] Use Pod Security Policies / Pod Security Standards
- [ ] Enable RBAC for namespace access
- [ ] Use service mesh for mTLS (Istio, Linkerd)

### High Availability
- [ ] Use managed PostgreSQL (RDS, Cloud SQL, Azure Database)
- [ ] Increase replica count based on load
- [ ] Configure Pod Disruption Budgets
- [ ] Use multi-zone deployment

### Monitoring
- [ ] Deploy Prometheus for metrics
- [ ] Deploy Grafana for dashboards
- [ ] Configure alerts for critical metrics
- [ ] Enable distributed tracing (Jaeger, Zipkin)

### CI/CD
- [ ] Automated image building in CI pipeline
- [ ] GitOps with ArgoCD or Flux
- [ ] Automated rollbacks on failure

## Troubleshooting

### Pods not starting
```bash
# Check pod status
kubectl -n ai-security-monitor describe pod <pod-name>

# Check events
kubectl -n ai-security-monitor get events --sort-by='.lastTimestamp'
```

### Database connection issues
```bash
# Check PostgreSQL logs
kubectl -n ai-security-monitor logs -l component=database

# Test database connectivity from backend pod
kubectl -n ai-security-monitor exec -it <backend-pod> -- nc -zv postgres-service 5432
```

### Ingress not working
```bash
# Check ingress status
kubectl -n ai-security-monitor describe ingress ai-security-monitor-ingress

# Check ingress controller logs
kubectl -n ingress-nginx logs -l app.kubernetes.io/name=ingress-nginx
```

## Relevant for Cyberforsvaret Interview

This configuration demonstrates:

1. **Container orchestration** - Kubernetes deployment patterns
2. **Security best practices** - Non-root containers, security contexts, secrets management
3. **High availability** - Multiple replicas, rolling updates, health checks
4. **Infrastructure as Code** - Declarative configuration
5. **12-factor app principles** - Config separation, stateless processes
6. **Cloud-native patterns** - Service discovery, load balancing
