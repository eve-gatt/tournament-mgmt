```
kubectl apply -f deploy~~/~~k8s/postgres-secrets.yaml
kubectl apply -f deploy/k8s/esi-secrets.yaml
kubectl apply -f deploy/k8s/app-secrets.yaml
kubectl patch -n tournmgmt deployment tournmgmt -p "{/"spec/": { /"replicas/" : 0}}"
```
