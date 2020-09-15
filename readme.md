## Manual Test Scenarios

For each scenario, consider:
* how intuitive it is to work how to do the scenario
* did you encounter any problems

### Scenarios

* Create a tournament
* Register a team
* Add pilots to the team
* Login as the team captain
* As captain, kick a pilot
* As captain, add pilot
* As captain, lock team
* Login as team member
* As team member, view team roster
* As organiser add an assistant organiser
* Login as assistant organiser
* As assistant, edit name of tournament
* As assistant, add a referee
* Login as referee
* As referee view a team roster

```
kubectl apply -f deploy~~/~~k8s/postgres-secrets.yaml
kubectl apply -f deploy/k8s/esi-secrets.yaml
kubectl apply -f deploy/k8s/app-secrets.yaml
kubectl patch -n tournmgmt deployment tournmgmt -p "{/"spec/": { /"replicas/" : 0}}"
```
