```
kubectl apply -f deploy\k8s\postgres-secrets.yaml
kubectl patch -n tournmgmt deployment tournmgmt -p "{\"spec\": { \"replicas\" : 0}}"
```
