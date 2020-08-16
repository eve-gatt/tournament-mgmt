```
kubectl apply -f deploy\k8s\postgres-secrets.yaml
kubectl patch deployment tournmgmt -p "{\"spec\": { \"replicas\" : 0}}"
```
