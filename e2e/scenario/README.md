# Scenario E2E

Real `git clone/push` flows + API permission enforcement scenarios. Parallel — each spec creates its own repo.

## Run

| Command                       | Runs                                       |
| ----------------------------- | ------------------------------------------ |
| `pnpm test:e2e:scenario`      | All scenario tests + teardown              |
| `pnpm test:e2e:scenario:only` | All scenario tests, no teardown (CI style) |

Needs backend + **git** on PATH.

## Specs

| File                                          | What it tests                                                                                   |
| --------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `scn-api-actions-permission-enforcement`      | ACTIONS_WRITE enforcement, secrets owner-only, ADMIN implies write, public repo anon read/write |
| `scn-api-collaborator-access`                 | Collaborator invite + repo access (private/public)                                              |
| `scn-api-collaborator-permission-enforcement` | PR_CREATE, PR_REVIEW, PR_MERGE, ISSUE_MANAGE, SETTINGS_WRITE, ADMIN spot-check                  |
| `scn-api-branch-default-and-archive`          | Default branch change + archive download                                                        |
| `scn-api-issue-private-repository`            | Issue CRUD on private repo                                                                      |
| `scn-api-pull-request-access-and-merge`       | PR create, review, merge flows                                                                  |
| `scn-api-pull-request-head-branch-lifecycle`  | Head branch deleted/restored after merge                                                        |
| `scn-api-pull-request-private-repo`           | PR on private repo (access control)                                                             |
| `scn-api-pull-request-public-repo`            | PR on public repo                                                                               |
| `scn-api-repo-access-control`                 | Private repo access (auth required)                                                             |
| `scn-api-repo-settings-pull-request`          | PR settings (merge strategies, etc.)                                                            |
| `scn-api-task-repository-pull-request`        | Task → PR link                                                                                  |
| `scn-api-webhook-repo-push`                   | Webhook delivery on push                                                                        |
| `scn-git-http-private-repo`                   | HTTP clone/push on private repo                                                                 |
| `scn-git-http-public-repo`                    | HTTP clone on public repo                                                                       |

---

## Optional: SAML SSO <a name="optional-saml"></a>

SAML tests are **excluded from the default workflow**. Run explicitly:

```bash
pnpm test:e2e:saml
```

**Prerequisites:**

1. Generate SP signing key pair:
   ```bash
   make saml-keygen          # writes to ~/.originhub/saml/
   ```
2. Start backend with SAML enabled:
   ```bash
   mvn spring-boot:run -pl originhub-backend \
     -Dspring-boot.run.profiles=local \
     -Doriginhub.sso.saml.enabled=true
   ```
3. Network access to `https://www.samltest.dev` required (IdP for tests).

Spec: `scn-api-saml-login.spec.ts` — provisions a fresh samltest.dev app + admin organization per run.

---

## Optional: LDAP SSO <a name="optional-ldap"></a>

LDAP tests are **excluded from the default workflow**. Run explicitly:

```bash
make ldap-up            # start OpenLDAP Docker container (localhost:389)
pnpm test:e2e:ldap
```

**Prerequisites:**

1. Start test LDAP server:
   ```bash
   make ldap-up          # ghcr.io/rroemhild/docker-test-openldap on :389
   ```
2. Start backend with LDAP enabled:
   ```bash
   mvn spring-boot:run -pl originhub-backend \
     -Dspring-boot.run.profiles=local \
     -Doriginhub.sso.ldap.enabled=true
   ```

Spec: `scn-api-ldap-login.spec.ts` — provisions a fresh admin organization per run; tears down in afterAll.
