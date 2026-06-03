# Scenario E2E tests

REST API + HTTP Git flows (no browser). Shared session: [`global-setup.ts`](../global-setup.ts) →
`e2e/.auth/session.json` (**owner** + **intruder**). Each test creates its own repo and runs **in parallel**.

## Prerequisites

- API up (see root [README](../README.md))
- `git` on `PATH` for Git scenarios

---

## Spec file → SCN IDs

| File                                                  | SCN IDs                                                                                                            |
| ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `scn-git-http-private-repo.spec.ts`                   | SCN-PRIV-GIT-01 … 04                                                                                               |
| `scn-git-http-public-repo.spec.ts`                    | SCN-PUB-GIT-01 … 04                                                                                                |
| `scn-api-pull-request-private-repo.spec.ts`           | SCN-PRIV-PR-01, 02                                                                                                 |
| `scn-api-pull-request-public-repo.spec.ts`            | SCN-PUB-PR-01                                                                                                      |
| `scn-api-pull-request-access-and-merge.spec.ts`       | SCN-PRIV-PR-03, 04; SCN-PUB-PR-02, 03                                                                              |
| `scn-api-repo-settings-pull-request.spec.ts`          | SCN-API-PRSET-01, 02                                                                                               |
| `scn-api-pull-request-head-branch-lifecycle.spec.ts`  | SCN-PR-DEL-01 … 04                                                                                                 |
| `scn-api-task-repository-pull-request.spec.ts`        | SCN-TASK-REPO-01, SCN-TASK-PR-01 … 05, SCN-TASK-ISSUE-01, SCN-TASK-PROJECT-01, SCN-TASK-GIT-01, SCN-TASK-COMMIT-01 |
| `scn-api-issue-private-repository.spec.ts`            | SCN-API-ISSUE-01                                                                                                   |
| `scn-api-branch-default-and-archive.spec.ts`          | SCN-API-BRANCH-01, SCN-API-ARCHIVE-01                                                                              |
| `scn-api-webhook-repo-push.spec.ts`                   | SCN-API-WH-01                                                                                                      |
| `scn-api-collaborator-access.spec.ts`                 | SCN-COLLABORATOR-01 … 08                                                                                           |
| `scn-api-collaborator-permission-enforcement.spec.ts` | SCN-COLLAB-PERM-PR-CREATE, PR-REVIEW, PR-MERGE, ISSUE-MANAGE, SETTINGS-WRITE, ADMIN                                |

---

## Known API / product limits

| Topic                      | Behavior                                                                                                    |
| -------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Invalid Bearer token       | PR create accepts **401 or 500**; must not be **201** (SCN-PRIV-PR-03)                                      |
| Guest private issue read   | **403** (`repoAccessDenied`) — SCN-API-ISSUE-01                                                             |
| Intruder PR on public repo | Current policy: intruder may get **201** (SCN-PUB-PR-02)                                                    |
| HTTP Git ACL               | SCN-PRIV-GIT-04, SCN-PUB-GIT-04 **skipped** — auth works; write ACL not as strict as SSH                    |
| REPO_PUSHED webhook        | SCN-API-WH-01 registers webhook; **outbound REPO_PUSHED** not asserted (event may not be published on push) |

---

## SCN-PRIV-GIT — Private repo HTTP Git

**File:** `scn-git-http-private-repo.spec.ts`

### SCN-PRIV-GIT-01 — Owner clone + push

|                  |                                                             |
| ---------------- | ----------------------------------------------------------- |
| **Precondition** | New private repo (`privateRepo` fixture), owner credentials |
| **Steps**        | `git clone` (user:pass), commit, `git push origin main`     |
| **Expected**     | **Pass** — clone and push succeed                           |

### SCN-PRIV-GIT-02 — Unauthenticated clone

|              |                                 |
| ------------ | ------------------------------- |
| **Steps**    | `git clone` without credentials |
| **Expected** | **Pass** — clone fails          |

### SCN-PRIV-GIT-03 — Wrong password

|              |                                   |
| ------------ | --------------------------------- |
| **Steps**    | `git clone` with invalid password |
| **Expected** | **Pass** — rejected               |

### SCN-PRIV-GIT-04 — Intruder push

|              |                                              |
| ------------ | -------------------------------------------- |
| **Expected** | Intruder cannot push to owner’s private repo |
| **Status**   | **Skip** — HTTP ACL gap in backend           |

---

## SCN-PUB-GIT — Public repo HTTP Git

**File:** `scn-git-http-public-repo.spec.ts`

### SCN-PUB-GIT-01 — Anonymous clone

|              |                                           |
| ------------ | ----------------------------------------- |
| **Expected** | **Pass** — unauthenticated clone succeeds |

### SCN-PUB-GIT-02 — Owner push

|              |                                        |
| ------------ | -------------------------------------- |
| **Expected** | **Pass** — push with valid credentials |

### SCN-PUB-GIT-03 — Unauthenticated push

|              |                                     |
| ------------ | ----------------------------------- |
| **Steps**    | Anonymous clone, commit, `git push` |
| **Expected** | **Pass** — push fails               |

### SCN-PUB-GIT-04 — Unauthorized user push

|              |                               |
| ------------ | ----------------------------- |
| **Expected** | Intruder cannot push          |
| **Status**   | **Skip** — HTTP write ACL gap |

---

## SCN-PRIV-PR / SCN-PUB-PR — Pull request API

**Files:** `scn-api-pull-request-private-repo.spec.ts`, `scn-api-pull-request-public-repo.spec.ts`,
`scn-api-pull-request-access-and-merge.spec.ts`

### SCN-PRIV-PR-01 — Owner PR on private repo

|              |                                                                |
| ------------ | -------------------------------------------------------------- |
| **Steps**    | Feature branch + blob; `POST .../pulls`; `GET .../pulls/{n}`   |
| **Expected** | **Pass** — GET **200**, `status: OPEN`, `sourceBranch` matches |

### SCN-PRIV-PR-02 — Intruder on private repo

|              |                           |
| ------------ | ------------------------- |
| **Steps**    | Intruder `POST .../pulls` |
| **Expected** | **Pass** — HTTP **≥ 400** |

### SCN-PRIV-PR-03 — No / invalid auth

|              |                                                                |
| ------------ | -------------------------------------------------------------- |
| **Steps**    | No token → POST; `Bearer invalid-token-scenario` → POST        |
| **Expected** | **Pass** — first **401**; second **401 or 500**, never **201** |

### SCN-PRIV-PR-04 — Merge updates main history

|              |                                                                               |
| ------------ | ----------------------------------------------------------------------------- |
| **Steps**    | Create PR, merge, list `main` commits                                         |
| **Expected** | **Pass** — PR `MERGED`; main commit count increases, message contains “merge” |

### SCN-PUB-PR-01 — Owner PR on public repo

|              |                                                   |
| ------------ | ------------------------------------------------- |
| **Expected** | **Pass** — GET **200**, title/branch/OPEN correct |

### SCN-PUB-PR-02 — Intruder PR on public repo

|              |                                              |
| ------------ | -------------------------------------------- |
| **Expected** | **Pass** — POST **201** (current API policy) |

### SCN-PUB-PR-03 — Anonymous PR list

|              |                                             |
| ------------ | ------------------------------------------- |
| **Steps**    | Create PR on public repo; list without auth |
| **Expected** | **Pass** — PR appears in list               |

---

## SCN-API-PRSET — Repo settings

**File:** `scn-api-repo-settings-pull-request.spec.ts`

### SCN-API-PRSET-01 — Delete head branch flags

|              |                                                                                 |
| ------------ | ------------------------------------------------------------------------------- |
| **Steps**    | `PATCH /api/repo/{owner}/{repo}` → `deleteHeadBranchOnPrMerge/Close: true`; GET |
| **Expected** | **Pass** — GET **200**, flags `true`                                            |

### SCN-API-PRSET-02 — Guest private repo

|              |                                              |
| ------------ | -------------------------------------------- |
| **Steps**    | `GET /api/repo/{owner}/{repo}` without token |
| **Expected** | **Pass** — **403**                           |

---

## SCN-PR-DEL — PR head branch lifecycle

**File:** `scn-api-pull-request-head-branch-lifecycle.spec.ts`

| SCN           | Repo setting                       | Event         | Expected                                |
| ------------- | ---------------------------------- | ------------- | --------------------------------------- |
| SCN-PR-DEL-01 | `deleteHeadBranchOnPrMerge: true`  | merge         | Branch deleted (`branchExists` → false) |
| SCN-PR-DEL-02 | `deleteHeadBranchOnPrClose: true`  | close (draft) | Branch deleted                          |
| SCN-PR-DEL-03 | both flags `false`                 | merge         | Branch remains                          |
| SCN-PR-DEL-04 | `deleteHeadBranchOnPrClose: false` | close         | Branch remains                          |

All **pass**; uses `waitUntil` for async cleanup.

---

## SCN-TASK-\* — Task, repo, PR integration

**File:** `scn-api-task-repository-pull-request.spec.ts`

### SCN-TASK-REPO-01 — Branch from task

|              |                                                                               |
| ------------ | ----------------------------------------------------------------------------- |
| **Steps**    | Link repo to project → task → `POST .../tasks/{code}/branch` → blob commit    |
| **Expected** | **Pass** — `branchName`, `branchRepoId`, `IN_PROGRESS`; commits on branch > 0 |

### SCN-TASK-PR-01 — PR → linkedPr

|              |                                                              |
| ------------ | ------------------------------------------------------------ |
| **Expected** | **Pass** — `linkedPr.number > 0`, `OPEN`, task `IN_PROGRESS` |

### SCN-TASK-PR-02 — Merge → COMPLETED

|              |                                          |
| ------------ | ---------------------------------------- |
| **Expected** | **Pass** — task `COMPLETED`, PR `MERGED` |

### SCN-TASK-PR-03 — Subtask linkedPr

|              |                                              |
| ------------ | -------------------------------------------- |
| **Expected** | **Pass** — subtask `linkedPr`, `IN_PROGRESS` |

### SCN-TASK-PR-04 — syncTaskStatusOnPrMerge off

|              |                                                  |
| ------------ | ------------------------------------------------ |
| **Steps**    | `syncTaskStatusOnPrMerge: false`, merge PR       |
| **Expected** | **Pass** — PR `MERGED`, task still `IN_PROGRESS` |

### SCN-TASK-PR-05 — PR close does not complete task

|              |                                            |
| ------------ | ------------------------------------------ |
| **Expected** | **Pass** — after close, task `IN_PROGRESS` |

### SCN-TASK-ISSUE-01 — Issue ↔ task

|              |                                              |
| ------------ | -------------------------------------------- |
| **Expected** | **Pass** — `linked-tasks` contains task code |

### SCN-TASK-PROJECT-01 — Linked repo + open PRs

|              |                                                 |
| ------------ | ----------------------------------------------- |
| **Expected** | **Pass** — `openPullRequests` includes PR title |

### SCN-TASK-GIT-01 — HTTP Git push on task branch

|              |                                   |
| ------------ | --------------------------------- |
| **Expected** | **Pass** — commit count increases |

### SCN-TASK-COMMIT-01 — Task PR merge → main

|              |                                                      |
| ------------ | ---------------------------------------------------- |
| **Expected** | **Pass** — merge message on `main`; task `COMPLETED` |

---

## SCN-API-ISSUE-01 — Private repo issues

**File:** `scn-api-issue-private-repository.spec.ts`

|              |                                                      |
| ------------ | ---------------------------------------------------- |
| **Steps**    | Owner: create, list, get, patch, delete; guest: get  |
| **Expected** | **Pass** — owner CRUD **200/204**; guest GET **403** |

---

## SCN-API-BRANCH-01 / SCN-API-ARCHIVE-01

**File:** `scn-api-branch-default-and-archive.spec.ts`

### SCN-API-BRANCH-01

|              |                                        |
| ------------ | -------------------------------------- |
| **Steps**    | Create `develop`, set as default       |
| **Expected** | **Pass** — `defaultBranch === develop` |

### SCN-API-ARCHIVE-01

|              |                                                        |
| ------------ | ------------------------------------------------------ |
| **Steps**    | `GET .../archive/main`                                 |
| **Expected** | **Pass** — **200**, zip content-type, body > 100 bytes |

---

## SCN-API-WH-01 — Webhook

**File:** `scn-api-webhook-repo-push.spec.ts`

|              |                                                                                             |
| ------------ | ------------------------------------------------------------------------------------------- |
| **Steps**    | Register repo `REPO_PUSHED` webhook; `putBlob`; project `TASK_CREATED` webhook; create task |
| **Expected** | **Pass** — webhook registered/listed; task **201**                                          |
| **Limit**    | Outbound **REPO_PUSHED** delivery not verified                                              |

---

## SCN-COLLABORATOR-\* — Collaborator lifecycle

**File:** `scn-api-collaborator-access.spec.ts`

| SCN                                 | Description                                        | Expected |
| ----------------------------------- | -------------------------------------------------- | -------- |
| full lifecycle                      | invite → accept → access → remove → deny           | **Pass** |
| SCN-COLLABORATOR-DECLINE            | Declined invitee cannot access; can be re-invited  | **Pass** |
| SCN-COLLABORATOR-SELF-REMOVE        | Collaborator removes themselves; access revoked    | **Pass** |
| SCN-COLLABORATOR-PROFILE-VISIBILITY | Accepted collab sees private repo in owner profile | **Pass** |
| SCN-COLLABORATOR-PERMISSIONS        | Only owner can update permissions; intruder → 403  | **Pass** |
| SCN-COLLABORATOR-CANNOT-INVITE-SELF | Owner cannot invite themselves → 400               | **Pass** |
| SCN-COLLABORATOR-DOUBLE-ACCEPT      | Cannot accept already-accepted invitation → 400    | **Pass** |
| SCN-COLLABORATOR-PUBLIC-REPO        | Public repo collaborator invite + list             | **Pass** |

---

## SCN-COLLAB-PERM-\* — Collaborator permission enforcement

**File:** `scn-api-collaborator-permission-enforcement.spec.ts`

Each test invites a fresh collaborator with `READ` only, verifies the operation is denied (403),
then grants the specific permission and verifies the operation succeeds.

| SCN                                  | Permission tested           | Endpoint tested                                | Without → With   |
| ------------------------------------ | --------------------------- | ---------------------------------------------- | ---------------- |
| SCN-COLLAB-PERM-PR-CREATE            | `PULL_REQUEST_CREATE`       | `POST .../pulls`                               | 403 → 201        |
| SCN-COLLAB-PERM-PR-REVIEW            | `PULL_REQUEST_REVIEW`       | `POST .../pulls/{n}/comments`                  | 403 → 201        |
| SCN-COLLAB-PERM-MERGE-IMPLIES-REVIEW | `PULL_REQUEST_MERGE` (only) | `POST .../pulls/{n}/comments`                  | — → 201          |
| SCN-COLLAB-PERM-PR-MERGE             | `PULL_REQUEST_MERGE`        | `POST .../pulls/{n}/merge`                     | 403 → 200        |
| SCN-COLLAB-PERM-ISSUE-MANAGE         | `ISSUE_MANAGE`              | `PATCH .../issues/{n}` (status toggle)         | 403 → 200        |
| SCN-COLLAB-PERM-ISSUE-CMT-DELETE     | `ISSUE_MANAGE`              | `DELETE .../issues/{n}/comments/{id}`          | 403              |
| SCN-COLLAB-PERM-SETTINGS-WRITE       | `SETTINGS_WRITE`            | `PATCH /api/repo/{owner}/{repo}` (description) | 403 → 200        |
| SCN-COLLAB-PERM-NO-RENAME            | `SETTINGS_WRITE`            | `PATCH /api/repo/{owner}/{repo}` (name)        | 403 (owner-only) |
| SCN-COLLAB-PERM-ADMIN                | `ADMIN`                     | PR create, comment, settings, issue mgmt       | all 20x          |

---
