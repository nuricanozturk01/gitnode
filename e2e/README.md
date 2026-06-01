# OriginHub E2E tests

Playwright-based tests for OriginHub. Two top-level areas:

| Path | Purpose |
|------|---------|
| `api/` | REST API tests against every Spring `@RestController` endpoint |
| `scenario/` | Scaffold for future browser/UI flows |

## Prerequisites

1. **PostgreSQL + API** at `http://localhost:8080` (default).

   ```bash
   make up
   ```

   Or local Spring Boot with `local` profile.

2. **Node.js 24** (`engines` in `package.json`).

## Install & run

```bash
cd e2e
pnpm install
pnpm test:e2e:api
```

| Variable | Default |
|----------|---------|
| `ORIGINHUB_API_BASE_URL` | `http://localhost:8080` |

Tests run **serially** (`fullyParallel: false`) because they share one registered user and fixture repo/project.

## Single-user auth

1. **`api/global-setup.ts`** — register once, create shared repo + project, seed `README.md` on `main`, write `e2e/.auth/session.json`.
2. **`api/fixtures/authenticated-api.ts`** — `session`, `authedRequest`, `api`.
3. Module specs use the shared session; `auth/auth.spec.ts` still registers a **new** user for the register endpoint test.

## Module layout & endpoint coverage

| Folder | Controllers covered | Endpoints |
|--------|---------------------|-----------|
| `auth/` | `AuthController` | register, login, refresh-token, send-password-recovery-mail, recover-password |
| `profile/` | `ProfileController` | me GET/PATCH, display-name, profile, password, public profile, search |
| `ssh/` | `SshKeyController` | list, add, delete |
| `repo/` | `RepoController` | create, get, list, patch, delete |
| `branch/` | `BranchController` | list, get, create, set default, delete |
| `commit/` | `CommitController` | list, get, diff |
| `tree/` | `TreeController` | tree, blob, raw, languages, archive, PUT blob |
| `snippet/` | `SnippetController`, `SnippetCommentController` | full CRUD, fork, revisions, repo link, by-owner, by-repo, raw file, comments |
| `issue/` | `IssueController` | CRUD, linked-tasks, comments CRUD |
| `task/` | `ProjectController`, `BoardController`, `TaskController` | projects, boards, columns, tasks, subtasks, branches |
| `pr/` | `PullRequestController`, `PullRequestCommitController` | CRUD, merge, commits, diff, comments |
| `tag/` | `TagController`, `ReleaseController` | tags + releases |
| `webhook/` | repo / user / project webhook controllers | CRUD each |
| `migration/` | `RepoMigrationController` | GET job, POST validation |
| `shared/` | actuator | `/actuator/health` |

**Not covered (no REST controller):** Git smart HTTP (`/git/**`), SSH git protocol (port 2222), `DELETE /api/users/me` (would destroy the shared session user).

Frontend E2E remains under `scenario/` for later.
