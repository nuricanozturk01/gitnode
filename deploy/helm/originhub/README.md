## OriginHub Helm Chart

### Template

```sh
helm template originhub . -f values.yml -n originhub
```

### Install (manual, without Argo CD)

```sh
helm upgrade --install originhub . -f values.yml -n originhub --create-namespace
```

Domain URLs: edit **`domain.apiHost`**, **`domain.frontendUrl`**, and component `host` fields in `values.yml`.

See [deploy/README.md](../../README.md).
