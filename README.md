<div align="center">

<br/>

<td><img src="images/logo.png" alt="Logo" width="45%"/></td>

### A simple, self-hosted Git registry — your code, your server, your rules.

<br/>

[![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-21-DD0031?style=for-the-badge&logo=angular)](https://angular.dev)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-4.x-38BDF8?style=for-the-badge&logo=tailwindcss&logoColor=white)](https://tailwindcss.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)

<br/>

[Features](#-features) · [Demo](#-demo) · [Tech Stack](#-tech-stack) · [Getting Started](#-getting-started) · [Roadmap](#-roadmap) · [License](#-license)

<br/>

</div>

---

## What is OriginHub?

OriginHub is a simple, open-source, self-hosted Git registry inspired by GitHub. It gives you full control over your
repositories, pull requests, and CI/CD pipelines — running entirely on your own infrastructure, with zero dependency on
third-party platforms.

No subscriptions. No data leaving your servers. No vendor lock-in. Just Git, hosted your way.

OriginHub is built for developers and teams who care about ownership — whether you're an indie developer running it on a
VPS, or an enterprise team deploying it on private infrastructure. If you've ever thought *"I wish GitHub ran on my own
server"*, OriginHub is for you.

---

## 🎬 Demo

<div align="center">

<a href="https://youtu.be/mis1Za2800E"><img src="images/cover.png" alt="OriginHub walkthrough — play on YouTube" width="480" /></a>

**[Watch on YouTube →](https://youtu.be/mis1Za2800E)**

</div>

---

## ✨ Features

OriginHub covers the core Git hosting loop — repos, review, browsing, issues, project boards, webhooks, and code snippets — all tied to your repositories on your own infrastructure.

### 📁 Repository management

- Create, clone, push, and pull repositories
- **Public and private** repositories, descriptions, and **topics**
- **Git over HTTP and HTTPS (TLS)**: smart HTTP backend at `/git/…` — use `http://` or `https://` remote URLs with your
  OriginHub host (same protocol support for the web UI and API)
- **SSH** Git on a configurable port (default **2222** in Docker)
- Per-repo **Settings**: general metadata, optional **auto-delete head branch** after PR merge or close

### 👤 Public profile

- Every account has a public profile at `/:username` showing public repositories
- Optional **profile README** rendered from the account’s special repository
- Paginated public repository list

### 📥 GitHub repository migration

- **Migrate from GitHub** with a repository URL and **personal access token** (classic or fine-grained with repo read)
- **Mirror clone** the Git history into your OriginHub account
- Optionally migrate **pull requests** from GitHub in the same job

### 🗂 Code browsing

- File tree with breadcrumbs; blob viewer and **raw** file URLs
- **Markdown README** on the repo home (images and relative links resolved like on GitHub)
- Commit history and diffs

### 🔀 Pull requests

- Open, review, merge, or close PRs
- Merge strategies: **merge commit**, **squash**, **rebase**
- Draft PRs, inline discussion, file-level comments

### 🐛 Issues

- Track bugs and feature requests per repository
- Labels, comments, open/close status
- **Link issues to Kanban tasks** — resolving a PR can auto-complete linked tasks

### 📋 Project management (Kanban)

- **Projects** with **boards** and configurable **columns** (per-project)
- **Tasks** and **subtasks** with types, status, assignee, and ordering
- Create **Git branches** from a task or subtask (conventional branch names, e.g. `TASK-1` or `TASK-1.SUB-1-…`)
- **Link** a branch’s pull request to the task or subtask; see PR status on the card
- **Optional automation** (per project): when a linked PR is **merged**, mark the task or subtask **completed**
- **Project settings** page for the above PR → status behaviour
- Projects linked to a repository are paginated in the repo’s **Projects** tab

### 📝 Code snippets (Gist-like)

- Create **public** or **private** snippets with syntax-highlighted code blocks
- **Multi-file** support per snippet
- Full **revision history** — track edits and diff between revisions
- **Fork** any public snippet
- Paginated snippets per repository in the repo’s **Snippets** tab
- Manage all your snippets from the **Snippets** section in the app bar

### 🔔 Webhooks

- **Signed HTTP delivery** to your services for pushes, PR events, and more
- **Retries** on delivery failure with full delivery log visibility
- Configured per-repository in **Settings → Webhooks**

### ⚡ Actions — CI/CD *(coming soon)*

- YAML workflows, job/step execution, SSE logs, run history, triggers (push / PR / manual)

### 🔐 Authentication

- Username + password with JWT
- OAuth2: **Google**, **GitHub**, **GitLab**
- SSH public keys for Git over SSH

---

## 🛠 Tech Stack

| Layer      | Technology                                      |
|------------|-------------------------------------------------|
| Language   | Java 25                                         |
| Framework  | Spring Boot 4, Spring Security, Spring Data JPA |
| Git Engine | Eclipse JGit                                    |
| SSH Server | Apache MINA SSHD                                |
| Auth       | JWT, OAuth2 (Google · GitHub · GitLab)          |
| Database   | PostgreSQL, Flyway                              |
| Frontend   | Angular 21, TypeScript 5                        |
| Styling    | Tailwind CSS 4, DaisyUI 5                       |
| Container  | Docker (multi-stage build, single image)        |

---

## 🚀 Getting Started

> 📖 Full documentation: **[originhub.nuricanozturk.com/docs](https://originhub.nuricanozturk.com/docs)** [Only Documentation. Not deployed to cloud]

### Option 1 — Docker Run (Manual)

```bash
SECRET=$(openssl rand -base64 64 | tr -d '\n')
docker network create originhub
docker run -d \
  --name originhub-postgres \
  --network originhub \
  -e POSTGRES_DB=originhub \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=admin123 \
  postgres:17
docker run -d \
  --name originhub \
  --network originhub \
  -p 8080:8080 \
  -p 2222:2222 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://originhub-postgres:5432/originhub \
  -e SPRING_DATASOURCE_USERNAME=admin \
  -e SPRING_DATASOURCE_PASSWORD=admin123 \
  -e "ORIGINHUB_JWT_SECRET=$SECRET" \
  -e ORIGINHUB_GIT_REPO__ROOT=/data/repos \
  -e SPRING_PROFILES_ACTIVE=os \
  -v originhub-repos:/data/repos \
  repo.repsy.io/nuricanozturk/originhub/originhub-os:latest
```

---

### Option 2 — Makefile

```bash
git clone https://github.com/nuricanozturk01/originhub.git
cd originhub
make up
```

Edit the variables at the top of the `Makefile` before running — at minimum set `JWT_SECRET`. OAuth2 keys are optional.

| Target | Description |
|---|---|
| `make up` | Create network, start DB and app |
| `make down` | Stop and remove containers |
| `make start` / `make stop` | Start or stop existing containers |
| `make restart` | Stop then start |
| `make logs` | Follow app logs |
| `make logs-db` | Follow database logs |
| `make ps` | List running containers |
| `make clean` | Remove containers and network (volumes kept) |
| `make purge` | Remove everything including repo data ⚠️ |

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `ORIGINHUB_JWT_SECRET` | ✅ | — | Min 32-char secret for JWT signing |
| `DB_USER` | | `admin` | PostgreSQL username |
| `DB_PASSWORD` | | `admin123` | PostgreSQL password |
| `ORIGINHUB_GIT_REPO__ROOT` | | `/data/repos` | Git repository storage path |
| `ORIGINHUB_FRONTEND_BASE_URL` | | `http://localhost:8080` | Public base URL |
| `OAUTH2_GOOGLE_CLIENT_ID` | | — | Google OAuth2 client ID |
| `OAUTH2_GOOGLE_CLIENT_SECRET` | | — | Google OAuth2 client secret |
| `OAUTH2_GITHUB_CLIENT_ID` | | — | GitHub OAuth2 client ID |
| `OAUTH2_GITHUB_CLIENT_SECRET` | | — | GitHub OAuth2 client secret |
| `OAUTH2_GITLAB_CLIENT_ID` | | — | GitLab OAuth2 client ID |
| `OAUTH2_GITLAB_CLIENT_SECRET` | | — | GitLab OAuth2 client secret |

---

## 🗺 Roadmap

OriginHub is under active development. Here's what's planned:

- [X] HTTPS Git support
- [X] Github Repo Migration
- [X] Project board (Kanban) integrated with repositories
- [X] Code snippets (Gist-like)
- [X] Repo Issues
- [X] Public repositories
- [X] Public Profile and ReadMe
- [X] Webhooks
- [ ] Actions — CI/CD
- [ ] [Repsy](https://github.com/repsyio/repsy) package management integration
- [ ] Tags and releases
- [ ] Two-factor authentication (TOTP)

---

## ☕ Support

If OriginHub saves you time or you just want to say thanks, consider buying me a coffee. It keeps the project alive and the commits coming.

<a href="https://www.buymeacoffee.com/nuricanozturk" target="_blank">
  <img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" height="50" />
</a>

---
