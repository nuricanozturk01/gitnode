## OriginHub Helm Chart

### Install

```sh
helm upgrade --install originhub . -f values.yml -f local.yml -n originhub --create-namespace
```

### Template

```sh
helm template originhub . -f values.yml -f local.yml -n originhub
```

### Production

```sh
cp prod.yml.example prod.yml
# fill domain.* and base64 secrets
helm upgrade --install originhub . -f values.yml -f prod.yml -n originhub --create-namespace
```

Domain URLs: edit **`domain.apiHost`** and **`domain.frontendUrl`** only.

See [deploy/README.md](../../README.md).
