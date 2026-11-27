# Deploying MiniShop on Kubernetes

These manifests run the **API only**. The database, Redis and Kafka are external managed services (the original deployment used Neon PostgreSQL, Upstash Redis and a SASL-secured Kafka such as Redpanda), configured through environment variables.

> The manifests are validated against the Kubernetes 1.30 schema but have not been applied to a live cluster by the author yet.

| File | Purpose |
|---|---|
| `namespace.yaml` | `minishop` namespace |
| `configmap.yaml` | Non-secret settings (`SPRING_PROFILES_ACTIVE=prod`, `PORT`) |
| `secret.example.yaml` | Template of the secret keys; never commit real values |
| `deployment.yaml` | 2 replicas, rolling updates with zero unavailable pods, startup/readiness/liveness probes on `/actuator/health`, CPU and memory limits |
| `service.yaml` | ClusterIP service on port 80 |
| `ingress.yaml` | NGINX ingress, replace the host name |
| `hpa.yaml` | Autoscaling 2 to 5 pods at 70% CPU (needs metrics-server) |
| `kustomization.yaml` | Applies everything except the secret in one command |

## Try it locally with kind or minikube

1. Build the image and load it into the cluster:
   ```bash
   docker build -t minishop:local .
   kind load docker-image minishop:local        # minikube: minikube image load minishop:local
   ```
2. In `deployment.yaml` set `image: minishop:local` and `imagePullPolicy: IfNotPresent`.
3. Create the namespace and the secret (replace the values):
   ```bash
   kubectl apply -f k8s/namespace.yaml
   kubectl -n minishop create secret generic minishop-secrets \
     --from-literal=DATABASE_URL='jdbc:postgresql://HOST:5432/minishop?sslmode=require' \
     --from-literal=DATABASE_USER='...' --from-literal=DATABASE_PASSWORD='...' \
     --from-literal=REDIS_URL='rediss://default:...@HOST:6379' \
     --from-literal=KAFKA_BOOTSTRAP_SERVERS='HOST:9092' \
     --from-literal=KAFKA_SASL_USER='...' --from-literal=KAFKA_SASL_PASSWORD='...' \
     --from-literal=JWT_SIGNING_KEY="$(openssl rand -base64 48)"
   ```
4. Apply and watch:
   ```bash
   kubectl apply -k k8s/
   kubectl -n minishop rollout status deployment/minishop
   kubectl -n minishop port-forward svc/minishop 8080:80
   ./scripts/smoke.sh http://localhost:8080
   ```

## Publishing the image to GitHub Container Registry

```bash
echo $GITHUB_TOKEN | docker login ghcr.io -u dialarblack237 --password-stdin
docker build -t ghcr.io/dialarblack237/minishop:0.1.0 .
docker push ghcr.io/dialarblack237/minishop:0.1.0
```

## Notes

- Cold start takes 30 to 60 seconds, so the startup probe allows up to 150 seconds before restarting.
- Kafka and Redis TLS are required in production; the prod profile expects `ca.pem` on the classpath for Kafka.
- Roll back with `kubectl -n minishop rollout undo deployment/minishop`.
