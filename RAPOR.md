# OriginHub — Issues & Task Entegrasyonu Raporu

## 1. Issues Nedir, Nasıl Çalışır?

Issues, **repo bazlı** bir iş takip sistemidir. Her issue bir repoya aittir ve bağımsız olarak yaşar.

### Veri Modeli

```
issues tablosu
├── id          (UUID, PK)
├── repo_id     → repositories (ON DELETE CASCADE)
├── number      (repo içinde sıralı: #1, #2, #3...)
├── title       (max 255 karakter)
├── description (TEXT, markdown)
├── status      ('OPEN' | 'CLOSED')
├── author_id   → tenant (kim yarattı)
├── assignee_id → tenant (kime atandı, nullable)
├── created_at
├── updated_at
└── closed_at

issue_comments tablosu
├── id
├── issue_id → issues (ON DELETE CASCADE)
├── author_id → tenant
├── body (TEXT)
├── created_at
└── updated_at
```

### API Endpoints

```
GET  /api/repos/{owner}/{repo}/issues?status=OPEN&page=0&size=20   → sayfalı liste
POST /api/repos/{owner}/{repo}/issues                               → yeni issue (auth gerekli)
GET  /api/repos/{owner}/{repo}/issues/{number}                      → detay + yorumlar
PUT  /api/repos/{owner}/{repo}/issues/{number}                      → güncelle (title, desc, assignee, status)
DEL  /api/repos/{owner}/{repo}/issues/{number}                      → sil (yazar veya admin)

POST /api/repos/{owner}/{repo}/issues/{number}/comments             → yorum ekle
PUT  /api/repos/{owner}/{repo}/issues/{number}/comments/{id}        → yorum güncelle
DEL  /api/repos/{owner}/{repo}/issues/{number}/comments/{id}        → yorum sil
```

### Erişim Kuralları

| Kullanıcı | Görebilir mi? | Issue açabilir mi? | Yorum yapabilir mi? |
|---|---|---|---|
| Anonim (gizli sekme) | Public repo ✅ / Private repo ❌ | ❌ | ❌ |
| Giriş yapmış | Public + Private (kendi) ✅ | ✅ | ✅ |
| Admin | Hepsi ✅ | ✅ | ✅ |

Frontend'de "New issue" butonu `@if (repoContext.isLoggedIn())` ile koruludur. `issues/new` route'u `canActivate: [authGuard]` ile koruludur. Unauthenticated biri URL'yi direkt yazsa login'e yönlenir.

---

## 2. Tasks Nedir, Nasıl Çalışır?

Tasks, **proje bazlı** bir Kanban yönetim sistemidir. Issues'dan tamamen bağımsızdır. Bir task mutlaka bir projeye, bir proje de bir board'a bağlıdır.

### Hiyerarşi

```
Project (ör: "originhub", prefix: "OH")
└── Board (birden fazla olabilir, her biri kendi kolonlarına sahip)
    └── BoardColumn (ör: "Backlog", "In Progress", "Done")
        └── Task (ör: OH-1, OH-2...)
            ├── Subtask (SUB-1, SUB-2...)
            ├── linkedBranch (opsiyonel)
            ├── linkedPr (opsiyonel, otomatik bağlanır)
            └── linkedIssue (opsiyonel, manuel bağlanır)
```

### Task Yaşam Döngüsü

```
Yaratılır (NOT_STARTED)
    ↓ Branch yaratılır (UI'dan veya manuel)
IN_PROGRESS
    ↓ PR açılır (branch'ten otomatik linklenir)
IN_PROGRESS + linkedPr = OPEN
    ↓ PR merge edilir
COMPLETED  (proje ayarında syncTaskStatusOnPrMerge = true ise otomatik)
```

---

## 3. Issue ↔ Task Entegrasyonu

Bu iki sistem **tek yönlü gevşek bağlantı (loose coupling)** ile entegre edilmiştir. Issue, Task'ın nedenini açıklar; Task, Issue'nun çözüm sürecini izler.

### Veritabanı Bağlantısı

`tasks` tablosuna Flyway migration V005 ile şu kolon eklendi:

```sql
ALTER TABLE tasks
  ADD COLUMN IF NOT EXISTS linked_issue_id UUID REFERENCES issues (id) ON DELETE SET NULL;
```

`ON DELETE SET NULL` kritik: Issue silinirse task bozulmaz, sadece link kopar.

### Nasıl Bağlanır?

Bir task'a issue linkleme **task düzenleme (update) ile yapılır**, task yaratırken değil:

```
PATCH /api/projects/{owner}/{projectCode}/tasks/{taskCode}
Body: { "linkedIssueId": "<issue-uuid>" }

// Linki kesmek için:
Body: { "unlinkIssue": true }
```

Backend `TaskUpdateForm`:
```java
private UUID linkedIssueId;   // linke için
private boolean unlinkIssue;  // kesmek için
```

`TaskService.applyRelationUpdates()` bu iki alanı işler:
```java
if (form.isUnlinkIssue()) {
    task.setLinkedIssue(null);
} else if (form.getLinkedIssueId() != null) {
    var issue = issueRepository.findById(form.getLinkedIssueId()).orElseThrow(...);
    task.setLinkedIssue(issue);
}
```

### Frontend — Task Detay Sayfasında Ne Görünür?

`task-detail.page.html` — linked issue varsa gösterir:
- Issue numarası (#42) ve başlığı
- Status badge: OPEN (yeşil) / CLOSED (gri)
- Issue detay sayfasına link (repo varsa)
- "Unlink" butonu

Board kartında (`TaskInfo`) ise sadece `hasLinkedIssue: boolean` gelir — kart üzerinde bir ikon olarak gösterilir, detay değil.

### PR Otomatik Linkleme (Karşılaştırma için)

PR linkleme **otomatiktir** — PR açılırken source branch'e göre task aranır, bulunursa `linkedPr` set edilir. Issue linkleme ise **tamamen manueldir**.

---

## 4. GitHub-Like Çift Yönlü Entegrasyon (Implement Edildi)

### Ne Yapıldı?

#### Backend
| Değişiklik | Dosya |
|---|---|
| `IssueLinkedTaskInfo` DTO (yeni) | `issue/dtos/IssueLinkedTaskInfo.java` |
| `TaskRepository.findByLinkedIssueId()` JPQL sorgusu | `task/repositories/TaskRepository.java` |
| `IssueService.getLinkedTasks()` metodu | `issue/services/IssueService.java` |
| `GET /{number}/linked-tasks` endpoint | `issue/controllers/IssueController.java` |
| `TaskForm.linkedIssueId` — task yaratırken issue link | `task/dtos/TaskForm.java` |
| `TaskService.create()` — linkedIssueId handling | `task/services/TaskService.java` |

#### Frontend
| Değişiklik | Dosya |
|---|---|
| `IssueLinkedTaskInfo` interface | `domain/repository/models/issue.model.ts` |
| `IssueService.getLinkedTasks()` | `core/issue/services/issue.service.ts` |
| Issue detail — "Development" bölümü (linked tasks listesi) | `features/repo/issues/issue-detail.page.ts/.html` |
| Task detail — "Link an issue" butonu + modal | `features/project/task-detail/task-detail.page.ts/.html` |
| `TaskForm.linkedIssueId` | `domain/project/models/task.model.ts` |
| `LayoutDashboard` icon kaydı | `lucide-icons.ts` |

#### Unit Testler
| Test Dosyası | Kapsam |
|---|---|
| `IssueServiceLinkedTasksTest.java` | `getLinkedTasks`: happy path, empty list, multi-project, repo not found, issue not found, COMPLETED status |
| `TaskServiceIssueLinkTest.java` | `create` + `update`: link, null link, link not found, unlink, unlink wins over link, no-op preserves existing |

### Nasıl Çalışıyor?

```
Issue Detay Sayfası                    Task Detay Sayfası
─────────────────────                  ──────────────────────────
"Development" bölümü                   "Linked Issue" kartı
  • Linked task'ları listeler            • Issue varsa: başlık, status,
  • Task code, title, status               issue sayfasına link, "Unlink" btn
  • Project adı                          • Issue yoksa: "Link an issue" btn
  • /projects/{code}/tasks/{code}           → Modal: owner + repo + issue#
    linkiyle task detaya gider              → Validate (GET issue) → Link
```

**Akış:**
1. Issue yaratılır (repo'da)
2. Task yaratılır (projede) — opsiyonel `linkedIssueId` ile direkt linklenebilir
3. Task detay sayfasında "Link an issue" → repo owner, repo adı, issue no gir → validate → link
4. Issue detay sayfasında "Development" → hangi task'ların bu issue'yu çözdüğü görünür

**Tek yönlü write, çift yönlü read:**  
Linkleme her zaman task tarafından yapılır (`PATCH /tasks/{code}`).  
Issue sayfası sadece okur (`GET /issues/{number}/linked-tasks`).

---

## 5. Eski Eksiklik Durumu (Artık Çözüldü)

**Evet, tespit doğru.** Şu an ne task yaratırken issue seçilebilir, ne de issue yaratırken task seçilebilir.

### Issue Yaratma Formu (`IssueForm`)
```java
String title;
String description;
UUID assigneeId;
// linkedTaskId YOK
```

### Task Yaratma Formu (`TaskForm`)
```java
String title;
String description;
String type;
UUID boardColumnId;
UUID assigneeId;
int position;
// linkedIssueId YOK
```

### Etki

- Issue açıp arkasından task yaratıyorsan, task'ı açıp "link issue" yapman gerekiyor.
- Issue'dan direkt "Bu issue için task yarat" akışı yok.
- Task'tan "Bu task hangi issue'yu çözüyor?" bağlantısı yaratırken kurulamıyor, sonradan kuruluyor.

---

## 5. Eksikliği Gidermek İçin Ne Yapılmalı?

### Seçenek A — Task yaratırken issue seç (Minimum iş)

Backend `TaskForm`'a `linkedIssueId` ekle:
```java
private UUID linkedIssueId; // opsiyonel
```

`TaskService.create()`'e ekle:
```java
if (form.getLinkedIssueId() != null) {
    var issue = issueRepository.findById(form.getLinkedIssueId()).orElseThrow(...);
    task.setLinkedIssue(issue);
}
```

Frontend'de board'daki "yeni task" modalına dropdown ekle (repo seç → issue listesi).

### Seçenek B — Issue detay sayfasından "Create Task" butonu

Issue detay sayfasına "Create linked task" butonu ekle. Tıklanınca `/projects/{code}` seç → task yaratılır, `linkedIssueId` otomatik dolu gelir.

### Seçenek C — İkisi bir arada (GitHub tarzı)

Issue sayfasında "Development" sidepanel: linked task ve PR listesi.
Task sayfasında zaten var olan linked issue bölümü issue yoksa "Link an issue" butonu gösterir, tıklanınca repo seç + issue ara/seç şeklinde çalışır.

---

## 6. Mevcut Durum Özeti

| Özellik | Durum |
|---|---|
| Issue yaratma | ✅ Çalışıyor |
| Issue listeleme (open/closed, sayfalı) | ✅ Çalışıyor |
| Issue detay + yorumlar | ✅ Çalışıyor |
| Issue close/reopen | ✅ Çalışıyor |
| Task'a issue linkleme (sonradan, modal ile) | ✅ Çalışıyor |
| Task yaratırken issue linkleme (form'da) | ✅ Backend hazır, frontend create modal'ında minimal (link sonradan yapılır) |
| Task detayda linked issue gösterimi + link/unlink | ✅ Çalışıyor |
| Task detayda "Link an issue" modal | ✅ Çalışıyor |
| Issue detayda "Development" bölümü (linked tasks) | ✅ Çalışıyor |
| Board kartında linked issue indikatörü | ✅ Çalışıyor |
| Issue silinince task bozulmaması | ✅ ON DELETE SET NULL |
| Linked issue'ya tıklayınca issue sayfasına gitme | ✅ Çalışıyor (branchRepo varsa) |
| Linked task'a tıklayınca task sayfasına gitme | ✅ Çalışıyor |
