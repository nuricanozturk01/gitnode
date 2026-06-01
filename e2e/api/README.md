# API E2E tests

## Auth and session

1. **`global-setup.ts`** — resolves **owner** and **intruder** (login from `.env` or auto-register), creates shared
   repo + project, writes `e2e/.auth/session.json`.
2. **`fixtures/authenticated-api.ts`** — `session`, `authedRequest`, `api`.
3. **`auth/auth.spec.ts`** — registers a disposable user (`reg_*`) for the register test, then **`DELETE /api/users/me`
   ** in the same test (not left for teardown).

`DELETE /api/users/me` is **not** tested here (see [teardown](../teardown/README.md)).

## Module layout

| Folder       | Controllers                      | Coverage                                                    |
|--------------|----------------------------------|-------------------------------------------------------------|
| `auth/`      | `AuthController`                 | register, login, refresh-token, password recovery           |
| `profile/`   | `ProfileController`              | me, display-name, profile, password, public profile, search |
| `ssh/`       | `SshKeyController`               | list, add, delete                                           |
| `repo/`      | `RepoController`                 | create, get, list, patch, delete                            |
| `branch/`    | `BranchController`               | list, get, create, default, delete                          |
| `commit/`    | `CommitController`               | list, get, diff                                             |
| `tree/`      | `TreeController`                 | tree, blob, raw, languages, archive, PUT blob               |
| `snippet/`   | `SnippetController`, comments    | CRUD, fork, revisions, repo link, raw, comments             |
| `issue/`     | `IssueController`                | CRUD, linked-tasks, comments                                |
| `task/`      | Project, board, task             | projects, boards, columns, tasks, subtasks, branches        |
| `pr/`        | `PullRequestController`, commits | CRUD, merge, commits, diff, comments (`z-pr.spec.ts`)       |
| `tag/`       | tags + releases                  | full CRUD                                                   |
| `webhook/`   | repo / user / project            | CRUD each                                                   |
| `migration/` | `RepoMigrationController`        | GET job, POST validation                                    |
| `shared/`    | actuator                         | `/actuator/health`                                          |

## Run

From `e2e/`:

```bash
pnpm test:e2e:api
```
