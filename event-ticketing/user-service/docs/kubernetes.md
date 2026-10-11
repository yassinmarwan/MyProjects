# Kubernetes Deployment

This document covers all the Kubernetes manifests for user-service and its dedicated PostgreSQL instance. All resources live in the `eventticketing` namespace.

---

## Files added

| Path | Kind | Purpose |
|------|------|---------|
| `k8s/secrets/jwt-secret.yaml` | Secret | Shared JWT signing key (used by all services) |
| `k8s/secrets/user-postgres-secret.yaml` | Secret | PostgreSQL credentials for `etdb-users` |
| `k8s/pvcs/user-postgres-pvc.yaml` | PersistentVolumeClaim | 1 Gi disk for user-postgres data |
| `k8s/statefulsets/user-postgres-statefulset.yaml` | StatefulSet | PostgreSQL 17 instance for user-service |
| `k8s/services/user-postgres-svc.yaml` | Service (headless) | Stable DNS for the StatefulSet (`clusterIP: None`) |
| `k8s/configmaps/user-service-configmap.yaml` | ConfigMap | Non-secret env vars for user-service |
| `k8s/deployments/user-service-deployment.yaml` | Deployment | Spring Boot container with health probes |
| `k8s/services/user-service-svc.yaml` | Service (ClusterIP) | Internal cluster access on port 8080 |

---

## Secrets

### `jwt-secret.yaml`

Holds the JWT signing key shared across all services. The value is the base64-encoded form of the secret string from `application.yml`.

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: jwt-secret
  namespace: eventticketing
type: Opaque
data:
  jwt-secret: <base64-encoded-secret>
```

Mounted into user-service as `JWT_SECRET` via `secretKeyRef`.

### `user-postgres-secret.yaml`

Holds the PostgreSQL credentials for the `etdb-users` database.

| Key | Value (decoded) |
|-----|----------------|
| `POSTGRES_USER` | `postgres` |
| `POSTGRES_PASSWORD` | `postgres` |
| `POSTGRES_DB` | `etdb-users` |

Change these before deploying to production.

---

## ConfigMap — `user-service-configmap.yaml`

Contains all non-secret configuration injected into the user-service container via `envFrom`.

| Key | Value | Maps to |
|-----|-------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://user-postgres:5432/etdb-users` | PostgreSQL URL |
| `SPRING_RABBITMQ_HOST` | `rabbitmq` | RabbitMQ hostname |
| `SPRING_DATA_REDIS_HOST` | `redis` | Redis hostname |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `SPRING_DATA_MONGODB_URI` | `mongodb://root:rootpass@mongo:27017/...` | MongoDB URI |
| `BOOKING_SERVICE_URL` | `http://booking-service:8080` | Feign target for booking-service |

The PostgreSQL username/password and `JWT_SECRET` come from secrets, not this ConfigMap.

---

## StatefulSet — `user-postgres-statefulset.yaml`

Runs a single PostgreSQL 17 instance dedicated to user-service.

- **Image:** `postgres:17`
- **Database:** `etdb-users`
- **Port:** `5432`
- Credentials loaded from `user-postgres-secret`
- Data persisted to the `user-postgres-pvc` PVC (1 Gi, `ReadWriteOnce`)

The headless service (`user-postgres-svc.yaml`, `clusterIP: None`) gives the StatefulSet pod a stable DNS name: `user-postgres.eventticketing.svc.cluster.local`.

---

## Deployment — `user-service-deployment.yaml`

Runs a single replica of the user-service Spring Boot application.

- **Image:** `user-service:latest` (`imagePullPolicy: Never` — built locally for Minikube)
- **Port:** `8080`
- Config injected via `user-service-configmap` (`envFrom`)
- DB credentials injected from `user-postgres-secret`
- JWT secret injected from `jwt-secret`

### Health probes

| Probe | Path | Initial delay | Period |
|-------|------|--------------|--------|
| Readiness | `GET /actuator/health` | 30 s | 10 s |
| Liveness | `GET /actuator/health` | 60 s | 30 s |

Kubernetes will not route traffic to the pod until the readiness probe passes, and will restart the pod if the liveness probe fails.

---

## Service — `user-service-svc.yaml`

Exposes user-service inside the cluster at `http://user-service:8080`.

```yaml
type: ClusterIP   # not reachable from outside the cluster
port: 8080
```

Other services (booking-service, api-gateway) reach user-service through this DNS name. External traffic reaches it only via the API Gateway NodePort service on port 30080.

---

## Deployment order

Follow the project-wide order in `Section 10.7`. For user-service specifically:

```bash
kubectl apply -f k8s/namespaces/
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/pvcs/user-postgres-pvc.yaml
kubectl apply -f k8s/statefulsets/user-postgres-statefulset.yaml
kubectl wait --for=condition=ready pod -l app=user-postgres -n eventticketing --timeout=120s
kubectl apply -f k8s/configmaps/user-service-configmap.yaml
kubectl apply -f k8s/deployments/user-service-deployment.yaml
kubectl apply -f k8s/services/user-service-svc.yaml
kubectl apply -f k8s/services/user-postgres-svc.yaml
```

Verify the pod is up:

```bash
kubectl get pods -n eventticketing -l app=user-service
kubectl logs -n eventticketing deploy/user-service
```
