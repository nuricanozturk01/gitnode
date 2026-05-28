# OriginHub Erişim & Güvenlik Raporu

## 1. Public/Private Repo Erişim Kontrolü (REST API)

### Nasıl Çalışıyor

**`RepoService`** merkezi erişim mantığını taşıyor:

| Metod | Davranış |
|---|---|
| `findByOwnerAndName(owner, repo, requesterId)` | Private → sadece owner/admin |
| `findAllByOwner(owner, pageable, requesterId)` | Owner/admin → tümü; diğer → yalnızca public |
| `assertUserCanAccessRepo(tenantId, owner, repo)` | Private repoya null tenantId → `AccessNotAllowedException` |
| `create()` | `isPrivate` null gelirse **default: true** (güvenli default) |

**SecurityConfig izin matrisi:**

```
GET /api/users/*          → permitAll (profil - OK)
GET /api/repo/*           → permitAll (RepoService içinde privacy check var - OK)
GET /api/repos/**         → permitAll (issue/branch/pr endpoint'leri dahil - SORUNLU)
GET /api/snippets/**      → permitAll
    /git/**               → permitAll (HttpGitFilter devralıyor)
/api/**                   → authenticated
```

### Eksikler

**BUG-1 (KRİTİK):** `GET /api/repos/**` tümüyle `permitAll`. Bu pattern `IssueController` endpoint'lerini de kapsıyor (`/api/repos/{owner}/{repo}/issues/**`). Private repolar için issue okuma herkese açık.

---

## 2. Issue Erişim Kontrolü

### Nasıl Çalışıyor

`IssueController` mapping: `/api/repos/{owner}/{repo}/issues`

| Endpoint | Auth Header | Erişim Kontrolü |
|---|---|---|
| `GET /` (liste) | isteğe bağlı yok | **YOK** |
| `GET /{number}` | yok | **YOK** |
| `GET /{number}/linked-tasks` | yok | **YOK** |
| `GET /{number}/comments` | yok | **YOK** |
| `POST /` | zorunlu (yazar ID için) | **YOK** – private repo check yok |
| `PATCH /{number}` | **yok** | **YOK** – kim olsa güncelleyebilir |
| `DELETE /{number}` | **yok** | **YOK** – kim olsa silebilir |
| `PATCH /{number}/comments/{id}` | **yok** | **YOK** |
| `DELETE /{number}/comments/{id}` | **yok** | **YOK** |

`IssueService` içinde de `repoService.assertUserCanAccessRepo()` çağrısı **yok**.

### Eksikler

**BUG-2 (KRİTİK):** Private repodaki issue'lar anonim kullanıcıya tamamen açık.

**BUG-3 (KRİTİK):** `PATCH`/`DELETE` issue & comment endpoint'lerinde auth header yok → herkes başkasının issue/comment'ini güncelleyip silebilir.

---

## 3. Issue ↔ Task Etkileşimi

### Nasıl Çalışıyor (son mimari)

```
task.Task.linkedIssueId (UUID)
     │
     ▼
TaskService.create()         → IssueQueryService.findById() ile varlık doğrular
TaskService.update()         → IssueQueryService.findById() ile varlık doğrular
TaskService.buildLinkedIssueInfo(UUID) → IssueQueryService.findById()
     │
     ▼
issue.IssueService.getLinkedTasks()  → TaskQueryPort → TaskQueryAdapter
                                        → TaskRepository.findByLinkedIssueId(UUID)
```

**Bağımlılık yönü:** `task` → `issue` (tek yön, Spring Modulith uyumlu ✓)

**Veri akışı:**
- Task oluştururken `linkedIssueId` set edilebilir → `IssueQueryService` ile validate
- Issue silindiğinde DB'deki `linked_issue_id` kolon `SET_NULL` (Hibernate annotation kaldırıldı ama DB constraint hâlâ çalışıyor olmalı)
- `IssueService.getLinkedTasks()` → `TaskQueryPort.findByLinkedIssueId()` → `LinkedTaskData` listesi döner → `IssueLinkedTaskInfo`'ya map'lenir

### Eksikler

**BUG-4 (ORTA):** `@OnDelete(SET_NULL)` annotation `Task.linkedIssueId` alanından kaldırıldı. Hibernate DDL otomatik schema oluşturuyorsa bu FK SET_NULL davranışını kaybeder. Flyway migration varsa sorun yok.

**BUG-5 (DÜŞÜK):** Issue silindiğinde task'taki `linkedIssueId` temizlenmez. `IssueQueryService.findById()` `Optional.empty()` döner, `buildLinkedIssueInfo` null döner — gösterimde sorun yok ama stale ID kalır.

---

## 4. Git HTTP Erişimi

### Nasıl Çalışıyor

`HttpGitAuthenticationFilter` → `/git/**` pattern'i yakalar:

```
istek geldi
├── Authorization: Basic header var?
│   └── YES → authenticate() → SHA-256(password+salt) hash kontrolü → başarılıysa geç
└── NO → isPublicReadRequest()?
    ├── write request (git-receive-pack)? → 401
    ├── repo bulunamadı? → false → 401
    └── repo.isPrivate() == false? → true → geç (anonim okuma)
        └── repo.isPrivate() == true? → false → 401
```

**Public repo clone:** kimlik doğrulama olmadan `git clone` mümkün ✓  
**Private repo clone:** Basic auth zorunlu ✓  
**Push:** her zaman Basic auth zorunlu ✓

### Eksikler

**BUG-6 (KRİTİK):** `OriginHubSshServer.assertAccess()` metodu **boş**:
```java
private void assertAccess(Tenant tenant, String owner, String repoName, boolean isWrite) {
    log.debug("Access check: ...");
    // HİÇBİR KONTROL YOK
}
```
SSH ile giriş yapan **herhangi** bir kullanıcı başkasının **private** reposuna push/pull yapabilir.

---

## 5. Profil Erişimi

`GET /api/users/{username}` → `permitAll` → `TenantPublicProfileDto(username, displayName, avatarUrl)` döner — email, hash, salt expose edilmiyor ✓

---

## 6. Diğer Modüller – Private Repo Kontrolü

`assertUserCanAccessRepo()` sadece **`TreeController`** tarafından çağrılıyor. Diğer modüller:

| Modül | Private Repo Kontrolü |
|---|---|
| `tree` (dosya ağacı) | ✓ Var |
| `issue` | ✗ **Yok** |
| `branch` | ✗ Yok (muhtemelen) |
| `commit` | ✗ Yok (muhtemelen) |
| `pr` | ✗ Yok (muhtemelen) |
| `snippet` | GET permitAll |

---

## 7. Özet: Öncelik Sırasına Göre Yapılacaklar

| # | Öncelik | Sorun | Düzeltme |
|---|---|---|---|
| 1 | KRİTİK | `OriginHubSshServer.assertAccess()` boş | owner kontrolü + private repo için tenant == owner/admin zorunlu yap |
| 2 | KRİTİK | Issue GET endpoint'leri private repoda da açık | `IssueService` başına `repoService.assertUserCanAccessRepo()` ekle |
| 3 | KRİTİK | Issue PATCH/DELETE'te auth header yok | Auth header zorunlu yap, sadece yazar/admin güncelleme yapabilsin |
| 4 | KRİTİK | Comment PATCH/DELETE'te auth header yok | Aynı yazar kontrolü |
| 5 | ORTA | branch/commit/pr modülleri private repo kontrolü eksik | Her modülde `assertUserCanAccessRepo()` çağrısı ekle |
| 6 | ORTA | `Task.linkedIssueId` üzerinde `@OnDelete(SET_NULL)` yok | DB migration veya `@ForeignKey` annotation ekle |
| 7 | DÜŞÜK | Issue silindiğinde `Task.linkedIssueId` stale kalır | `IssueDeletedEvent` yayınla, task modülü dinlesin ve null yapsın |
| 8 | DÜŞÜK | CORS `allowedOriginPatterns("*")` + `allowedMethods("*")` | Production'da domain kısıtla |
