# Scenario E2E

API + real `git clone/push` flows. Uses shared **owner** / **intruder** session. Tests run **in parallel** (each creates its own repo).

## Run

```bash
make infra
./mvnw spring-boot:run -pl originhub-backend -Dspring-boot.run.profiles=local

cd e2e
pnpm test:e2e:scenario          # scenario + teardown
pnpm test:e2e:scenario:only     # scenario only (CI style)
```

Needs **git** on `PATH`.

---

## SAML & LDAP (optional)

**Not run in CI.** Skipped unless you use the dedicated commands below.

| | LDAP | SAML |
| --- | --- | --- |
| **Command** | `pnpm test:e2e:ldap` | `pnpm test:e2e:saml` |
| **Extra setup** | `make ldap-up` | `make saml-keygen` (once) |
| **Backend** | local profile, LDAP enabled | local profile, SAML enabled + signing keys |
| **Admin** | `admin` / `Admin123` | same |

**LDAP — copy/paste**

```bash
make infra && make ldap-up
./mvnw spring-boot:run -pl originhub-backend -Dspring-boot.run.profiles=local
cd e2e && pnpm test:e2e:ldap
make ldap-down
```

Port mapping must be **`389:10389`** (`make ldap-up` does this). Test user: `fry` / `fry` @ `planetexpress.com`.

**SAML — copy/paste**

```bash
make saml-keygen
make infra
./mvnw spring-boot:run -pl originhub-backend -Dspring-boot.run.profiles=local
cd e2e && pnpm test:e2e:saml
```

Needs outbound HTTPS to [samltest.dev](https://www.samltest.dev). Does not complete IdP login in the browser.

**What each run does:** create temp org → run tests → delete users + delete org (`afterAll`).

---

## Spec files

| File | SCN IDs |
| --- | --- |
| `scn-git-http-private-repo.spec.ts` | SCN-PRIV-GIT-01 … 04 |
| `scn-git-http-public-repo.spec.ts` | SCN-PUB-GIT-01 … 04 |
| `scn-api-pull-request-*.spec.ts` | SCN-PRIV-PR-*, SCN-PUB-PR-* |
| `scn-api-repo-settings-pull-request.spec.ts` | SCN-API-PRSET-01, 02 |
| `scn-api-pull-request-head-branch-lifecycle.spec.ts` | SCN-PR-DEL-01 … 04 |
| `scn-api-task-repository-pull-request.spec.ts` | SCN-TASK-* |
| `scn-api-issue-private-repository.spec.ts` | SCN-API-ISSUE-01 |
| `scn-api-branch-default-and-archive.spec.ts` | SCN-API-BRANCH-01, SCN-API-ARCHIVE-01 |
| `scn-api-webhook-repo-push.spec.ts` | SCN-API-WH-01 |
| `scn-api-collaborator-access.spec.ts` | SCN-COLLABORATOR-01 … 08 |
| `scn-api-collaborator-permission-enforcement.spec.ts` | SCN-COLLAB-PERM-* |
| `scn-api-saml-login.spec.ts` | SCN-SAML-01 … 04 |
| `scn-api-ldap-login.spec.ts` | SCN-LDAP-01 … 06 |

Test steps and expectations live in the spec files and test titles.

---

## Known quirks

| Topic | Note |
| --- | --- |
| HTTP Git write ACL | SCN-PRIV/PUB-GIT-04 skipped — SSH is stricter |
| SAML / LDAP UI | API-only; check login page manually |
| SAML / LDAP in CI | Always skipped |
