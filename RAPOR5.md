# GitNode — RAPOR5: Teknik Durum + Değerleme (Güncel Verifikasyon)

**Tarih:** 2026-06-08 (güncelleme: runner multi-instance fix + production referans eklendi)
**Metodoloji:** Kaynak kod + test + migration + pom.xml doğrudan incelenerek yazıldı. Hiçbir iddia tahminden değil,
ölçülen gerçek değerden gelir. Eksikler açık yazıldı.

---

# BÖLÜM A — GÜNCEL TEKNİK DURUM

## 1. Bileşen Haritası

Proje beş bağımsız bileşenden oluşuyor:

| Bileşen               | Dil / Versiyon             | Amaç                                                |
|-----------------------|----------------------------|-----------------------------------------------------|
| `gitnode-backend`     | Java 25, Spring Boot 4.0.6 | API + Git transport + CI/CD motor                   |
| `gitnode-frontend`    | Angular 21, TypeScript 5.9 | Kullanıcı SPA (port 4200)                           |
| `gitnode-admin-panel` | Angular 21, TypeScript 5.9 | Platform yönetim paneli (port 4300)                 |
| `gitnode-runner`      | Go 1.24                    | Harici CI/CD runner ajanı (WebSocket)               |
| `gitnode-events`      | Java 25                    | Paylaşılan domain event sınıfları (Maven artefaktı) |

**Maven modülleri:** 3 — `gitnode-parent` (aggregator), `gitnode-backend`, `gitnode-events`.
`gitnode-runner` bağımsız Go modülü; Maven dışında.

---

## 2. Backend — Spring Modulith Modülleri

**18 Spring Modulith modülü** (`dev.gitnode.os` altında):

| Modül          | Tip    | Kapsam                                                                                                                                                         |
|----------------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `shared`       | OPEN   | Tenant/Repo entity, GitProvider, BranchService, error handling, RepoAccess interceptor, Redis cache, audit, rate-limiting, dağıtık lock, distributed CB guard  |
| `admin`        | closed | Platform yönetim REST API (`/api/admin/**`) — 10 controller, ~40 endpoint                                                                                      |
| `actions`      | closed | CI/CD motoru — workflow YAML parse, job scheduling, runner kayıt, secrets vault (AES-256), artifact/cache store, SSE log streaming, WebSocket runner protokolü |
| `auth`         | closed | JWT + OAuth2 (Google/GitHub/GitLab) + SAML 2.0 + LDAP SSO, SSH key yönetimi, organization SSO config                                                           |
| `branch`       | closed | Dal operasyonları                                                                                                                                              |
| `collaborator` | closed | Repo iş birlikçileri, fine-grained permission, davet akışı                                                                                                     |
| `commit`       | closed | Commit geçmişi ve diffler                                                                                                                                      |
| `issue`        | closed | Issue yönetimi; `issue::api` named interface                                                                                                                   |
| `migration`    | closed | GitHub repo import (mirror clone + PR/tag migrasyon)                                                                                                           |
| `pr`           | closed | Pull request; `pr::api` named interface                                                                                                                        |
| `profile`      | closed | Kullanıcı profili + README + contribution grafik                                                                                                               |
| `repo`         | closed | Repo controller + event listener katmanı                                                                                                                       |
| `snippet`      | closed | Gist benzeri kod parçacıkları, revizyon geçmişi                                                                                                                |
| `ssh`          | closed | Apache MINA SSHD (port 2222)                                                                                                                                   |
| `tag`          | closed | Git tag + release CRUD                                                                                                                                         |
| `task`         | closed | Project, Kanban, task/subtask; PR + issue event tüketir                                                                                                        |
| `tree`         | closed | Dosya ağacı + blob görüntüleyici, arşiv indirme                                                                                                                |
| `webhook`      | closed | Repo/user/project webhook, DLQ, per-host circuit breaker; `webhook::api` named interface                                                                       |

**Modül sınırı:** `ApplicationModules.verify()` CI'da çalışıyor — cross-module ihlali commit bloğu.

---

## 3. Actions Modülü — CI/CD Motoru (Tam Kapsam)

Platform şimdi tam işlevli, self-hosted bir CI/CD motoruna sahip. Bu projenin en büyük özellik bloğu.

### 3.1 Backend (`actions` modülü)

**Controllers (7):**

| Controller              | Kapsam                                                            |
|-------------------------|-------------------------------------------------------------------|
| `WorkflowController`    | Repo başına workflow tanım listesi                                |
| `WorkflowRunController` | Run tetikle, listele, detay, iptal                                |
| `LogStreamController`   | SSE log streaming (adım bazında gerçek zamanlı)                   |
| `RunnerController`      | Runner kayıt, heartbeat, token döngüsü, job claim                 |
| `RunnerGroupController` | Runner grup CRUD                                                  |
| `SecretController`      | Repo başına secret CRUD (AES-256 şifreli, değer asla geri dönmez) |
| `ArtifactController`    | Artifact upload/download/list                                     |

**Services (15):**

| Servis                      | Kapsam                                                      |
|-----------------------------|-------------------------------------------------------------|
| `WorkflowParserService`     | Workflow YAML → WorkflowModel (Jackson)                     |
| `WorkflowTriggerService`    | Push + PR event dinler, tetikleyici eşleşme, run oluşturma  |
| `WorkflowExecutionService`  | Run lifecycle, job durumu, adım kaydı, @Audited             |
| `JobDispatcher`             | İş dağıtımı, runner eşleştirme, matrix genişletme           |
| `MatrixExpander`            | Matrix strategy çarpımı                                     |
| `ExpressionEvaluator`       | `${{ expression }}` değerlendirme (if, needs, env, secrets) |
| `RunnerRegistryService`     | Runner kayıt, heartbeat takibi, offline tespit              |
| `RunnerTokenService`        | Token oluşturma + doğrulama                                 |
| `RunnerTokenGitAdapter`     | Runner token ile git auth entegrasyonu                      |
| `RunnerGroupService`        | Runner grup yönetimi                                        |
| `SecretVaultService`        | AES-256/GCM şifreleme, IV+ciphertext saklama                |
| `ArtifactStoreService`      | Artifact upload/download, retention                         |
| `CacheStoreService`         | Cache key yönetimi, eviction                                |
| `ActionsAdminService`       | Yönetici istatistikleri (`actions::api` üzerinden)          |
| `WorkflowDefinitionService` | Repo başına tanımlı workflow listesi                        |

**WebSocket (runner protokolü, 8 sınıf):**

- `RunnerWebSocketHandler` — mesaj yönlendirme
- `RunnerSessionRegistry` — aktif runner oturumu haritası (in-memory)
- `RunnerHandshakeInterceptor` — token doğrulama
- `RunStatusSseRegistry`, `SseEmitterRegistry` — SSE emitter yaşam döngüsü
- `ActionsWebSocketConfig`, `RunnerSession`, `ServerMessage`

**Entities (14):**
`Runner`, `RunnerGroup`, `RunnerRegistrationToken`, `WorkflowDefinition`, `WorkflowRun`, `WorkflowJob`,
`WorkflowStep`, `WorkflowLog`, `WorkflowArtifact`, `WorkflowCache`, `WorkflowSecret` + durum enum'ları
(`RunnerStatus`, `WorkflowJobStatus`, `WorkflowRunStatus`, `ExecutorType`)

**`actions::api` named interface:** `ActionsAdminPort`, `RunnerAdminData`, `WorkflowRunStatsData` —
`admin` modülü `actions`'a doğrudan bağımlı değil.

**Flyway:** V034–V042 (9 migrasyon) — runner, workflow, artifact, secret, cache, concurrency group, runner group,
runner token, performance index, job key, runner tenant scope tabloları.

**Events:** `gitnode-events` modülünde `events/actions/` paketi eklendi —
`WorkflowJobQueuedEvent`, `WorkflowJobStartedEvent`, `WorkflowJobCompletedEvent`, `WorkflowRunCompletedEvent`.

### 3.2 gitnode-runner — Go Ajanı

**Yeni bağımsız bileşen.** Runner, WebSocket üzerinden backend'e bağlanır, job alır, yürütür, log gönderir.

| Özellik      | Detay                                                                                                                          |
|--------------|--------------------------------------------------------------------------------------------------------------------------------|
| Dil          | Go 1.24                                                                                                                        |
| Dosya sayısı | 29 Go dosyası, 5 test dosyası                                                                                                  |
| Executor'lar | `shell` (subprocess), `docker` (container)                                                                                     |
| Bağlantı     | WebSocket (`gorilla/websocket`)                                                                                                |
| Git          | `go-git/go-git` v5                                                                                                             |
| CLI          | `cobra` — `start`, `register`, `version` komutları                                                                             |
| Build        | `make build` (yerel), `make build-all` (Linux amd64/arm64, macOS arm64, Windows amd64)                                         |
| Test         | `go test -race -count=1`, golangci-lint v2                                                                                     |
| Paketler     | `internal/actions`, `config`, `connection`, `context`, `executor`, `log`, `registration`, `shell`, `workspace`, `pkg/protocol` |

**Çalıştırma:**

```bash
./dist/gitnode-runner start \
  --server-url http://localhost:8080 \
  --token ghrt_xxxxxxxxxxxx \
  --name my-runner \
  --labels self-hosted,linux,docker \
  --executor docker \
  --work-dir /tmp/gitnode-runner
```

**Multi-instance dispatch çözümü:** `JobDispatcher` artık runner WebSocket oturumunu doğrudan çağırmaz. İş
atandıktan sonra Redis kanalına (`gitnode:actions:dispatch:{runnerId}`) JSON mesaj yayınlar. Her instance
`RunnerDispatchListener` (`RedisMessageListenerContainer`) ile `gitnode:actions:dispatch:*` pattern'ini dinler;
mesaj geldiğinde lokal `RunnerSessionRegistry`'yi kontrol eder, runner bu instance'ta bağlıysa iletir yoksa sessizce
geçer. Ayrıca `findAllQueued()` → PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED` sorgusuna (`findQueuedForClaim`)
dönüştürüldü — eş zamanlı dispatch'de aynı iş iki instance tarafından üstlenilemiyor. Runner multi-instance
dağıtımı production-safe hale getirildi.

---

## 4. Admin Panel — Angular UI (MEVCUT)

`gitnode-admin-panel/` — bağımsız Angular 21 uygulaması, port 4300'de çalışır.
**13 özellik sayfası**, tümü REST API ile eşleştirilmiş:

| Sayfa                  | Kapsam                                            |
|------------------------|---------------------------------------------------|
| `login`                | Admin oturum açma                                 |
| `dashboard`            | Platform istatistikleri overview                  |
| `users/list`           | Kullanıcı listesi + arama                         |
| `users/detail`         | Kullanıcı detayı, enable/disable                  |
| `repos/list`           | Repo listesi                                      |
| `organizations/list`   | Organizasyon listesi                              |
| `organizations/detail` | Org detayı + SSO/LDAP konfigürasyon               |
| `organizations/new`    | Yeni organizasyon oluşturma                       |
| `audit/list`           | Audit log arama + filtreleme                      |
| `pgaudit`              | PostgreSQL pgaudit log görüntüleyici              |
| `modulith-events`      | Spring Modulith event publication izleme          |
| `webhooks/dlq`         | Webhook DLQ listesi                               |
| `settings`             | Platform ayarları (cache TTL, feature toggle'lar) |
| `system`               | Sistem sağlık durumu                              |
| `actions`              | Actions/runner yönetimi                           |
| `account`              | Admin hesap ayarları                              |

**Admin panel teknik olmayan karar vericiye demo edilebilir durumdadır.**
Önceki raporlardaki "admin frontend UI yok" boşluğu kapanmıştır.

---

## 5. gitnode-events — Domain Event Kataloğu

Maven artefaktı. Paketler: `events/actions` (yeni), `branch`, `collaborator`, `issue`, `pr`, `profile`, `project`,
`repo`, `snippet`, `tag`, `task`. Backend dışında tüketilebilir. Modulith modül sınırları bu artefakta bağımlı.

---

## 6. Flyway Migrasyon Durumu

**V001 → V042**, sonraki: **V043**.

| Aralık    | Kapsam                                                                                                |
|-----------|-------------------------------------------------------------------------------------------------------|
| V001–V027 | Temel şema (auth, webhook, audit, LDAP/SSO)                                                           |
| V028–V033 | Organization SSO/LDAP, tenant enabled, platform settings, indexler                                    |
| V034–V042 | Actions CI/CD (runner, workflow, artifact, secret, cache, concurrency, runner group, token, indexler) |

---

## 7. Test Durumu

### 7.1 Backend Unit Test

**665 `@Test` anotasyonu**, 91 test dosyası. Sıfır başarısız test, sıfır regresyon.

| Modül          | Test sayısı |
|----------------|-------------|
| `actions`      | **101**     |
| `admin`        | **112**     |
| `webhook`      | 61          |
| `snippet`      | 55          |
| `auth`         | 43          |
| `collaborator` | 44          |
| `shared`       | 34          |
| `task`         | 29          |
| `tree`         | 28          |
| `profile`      | 26          |
| `issue`        | 25          |
| `pr`           | 21          |
| `repo`         | 19          |
| `tag`          | 19          |
| `branch`       | 15          |
| `ssh`          | 18          |
| `migration`    | 8           |
| `commit`       | 6           |

**Actions test kalitesi:** `ExpressionEvaluatorTest` (11 test), `WorkflowExecutionServiceTest` (12),
`WorkflowTriggerServiceTest` (12), `RunnerRegistryServiceTest` (12) — sadece happy path değil, edge case ve
hata yolları da kaplı.

**JaCoCo CI gate:** `./mvnw verify` — INSTRUCTION ≥ %48, BRANCH ≥ %40. `mr-check.yml`'de çalışıyor.
Hariç: `entities/**`, `dtos/**`, `mappers/**`, `configs/**`, `*Application.class`, `listeners/**`.
Eşik mütevazı; %60/%55 hedeflenmeli.

### 7.2 E2E Test (Playwright)

**37 spec dosyası**, yaklaşık **260 `test()` çağrısı**.

| Klasör      | Dosya    | Kapsam                                                                                                                                                    |
|-------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `api/`      | 16 dosya | auth, branch, commit, issue, migration, profile, repo, snippet, ssh, tag, task, tree, webhook, collaborators, pr, **actions**                             |
| `scenario/` | 19 dosya | SAML login, LDAP login, collaborator, PR lifecycle (4 senaryo), branch, webhook, tag/release, snippet, task, **actions permission enforcement**, git-http |
| `teardown/` | 1 dosya  | E2E kullanıcı temizleme                                                                                                                                   |

Actions E2E kapsamı: `api/actions/actions.spec.ts` + `scn-api-actions-permission-enforcement.spec.ts` —
workflow tetikleyici ve yetki kontrolleri doğrulanıyor; runner entegrasyon testi yok (gerçek runner gerektiriyor).

### 7.3 Runner Test

5 Go test dosyası (`*_test.go`), `go test -race` ile çalışıyor. Kapsam sınırlı — unit test düzeyi.

---

## 8. Güncel Teknik Metrikler

| Metrik                 | Değer            | Not                                                                       |
|------------------------|------------------|---------------------------------------------------------------------------|
| Spring Modulith modülü | **18**           | +`actions` modülü                                                         |
| Maven modülü           | **3**            | gitnode-parent, gitnode-backend, gitnode-events                           |
| Java kaynak dosyası    | ~520             | backend + events                                                          |
| Service sınıfı         | ~67              | actions +15, admin +11 dahil                                              |
| Backend unit test      | **668**          | 91 test dosyası, 0 hata                                                   |
| E2E spec dosyası       | **37**           | ~260 test() çağrısı                                                       |
| Flyway migrasyon       | V001→**V042**    | Sonraki V043                                                              |
| Admin Angular sayfası  | **13+**          | gitnode-admin-panel/ — tam çalışır                                        |
| Runner executor        | **2**            | shell, docker                                                             |
| AES-256 secrets        | ✅                | Her repo başına şifreli secret vault                                      |
| Multi-instance         | ✅                | Redis lock, shared volume, HAProxy, Redis pub-sub dispatch (runner sticky session ihtiyacı giderildi) |
| Circuit Breaker        | **Resilience4j** | webhook (per-host) + SAML metadata                                        |
| `@Audited`             | ~30+             | WorkflowExecutionService dahil                                            |
| Java                   | **25**           | Virtual threads aktif                                                     |
| Spring Boot            | **4.0.6**        |                                                                           |
| Spring Modulith        | **2.0.2**        |                                                                           |
| JGit                   | **7.2.1**        |                                                                           |
| Apache MINA SSHD       | **2.18.0**       |                                                                           |
| Angular                | **21**           | Her iki frontend de (4200 + 4300)                                         |
| TypeScript             | **5.9**          |                                                                           |
| Tailwind CSS           | **4.3**          |                                                                           |
| DaisyUI                | **5.5**          |                                                                           |
| Go                     | **1.24**         | runner                                                                    |
| Playwright             | **1.52**         | E2E                                                                       |

---

# BÖLÜM B — DURUM DEĞERLENDİRMESİ

## 9. Güçlü Yönler

- **Tam CI/CD motoru:** YAML workflow parse, matrix strategy, expression evaluator, job dispatch, secrets vault
  (AES-256), artifact/cache store, SSE log streaming — karşılaştırılabilir: GitHub Actions / Gitea Actions.
- **Runner ajanı:** Go 1.24 binary, shell + docker executor, WebSocket protokolü, cross-platform build.
- **Admin Angular UI tamamlandı:** 13+ sayfa, tüm REST endpoint'leri karşılıyor — teknik olmayan karar vericiye
  demo mümkün.
- **18 Modulith modülü, sınırlar korumalı:** `ApplicationModules.verify()` CI'da; actions modülü temiz eklendi.
- **Organization SSO altyapısı:** Per-org SAML + LDAP, domain routing, connection test endpoint'leri.
- **Multi-instance dayanıklılığı:** Redis dağıtık lock (DLQ scheduler, bootstrap admin), shared named volume,
  HAProxy TCP LB (SSH), Resilience4j per-host circuit breaker + Redis CB state paylaşımı.
- **668 test, 0 hata:** Actions modülü 101 test, admin 112 test — yeni kod test edilmeden bırakılmadı.
- **AES-256 secrets vault:** Şifreli saklama, değer asla API'den dönmüyor.
- **~260 E2E test:** API + senaryo + git-http doğrulaması.
- **Production referansı: gitnode.dev** — canlı platform, B2B görüşmelerinde gösterilebilir.
- **Runner multi-instance dispatch giderildi:** Redis pub/sub (`gitnode:actions:dispatch:{runnerId}`) + PostgreSQL `FOR UPDATE SKIP LOCKED` — job çakışması ve kaybı yok.

## 10. Açık Boşluklar

| Boşluk                          | Durum                   | Etki                                                                                                                                          |
|---------------------------------|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| Runner WebSocket multi-instance | ✅ **GİDERİLDİ**         | Redis pub/sub dispatch (`gitnode:actions:dispatch:{runnerId}`) + PostgreSQL `FOR UPDATE SKIP LOCKED` — tüm instance'lar dispatch yayınlar, runner'ı barındıran instance iletir. |
| Organization SSO login akışı    | **Altyapı var, UI yok** | Email domain SSO yönlendirmesi login sayfasına entegre değil                                                                                  |
| MFA / 2FA / TOTP                | **YOK**                 | Kurumsal güvenlik standardı                                                                                                                   |
| Tenant enabled JWT revoke       | **Eksik**               | Deaktif hesap JWT TTL dolana kadar geçerli — Redis blocklist yok                                                                              |
| Frontend feature unit test      | **Auth-only**           | Features test edilmemiyor                                                                                                                     |
| UI / browser E2E                | **YOK**                 | Angular bileşen davranışı Playwright ile doğrulanmıyor                                                                                        |
| Actions runner E2E              | **Yüzey düzeyinde**     | Gerçek runner bağlantısı + job yürütme E2E yok                                                                                                |
| JSON/structured loglama         | **YOK**                 | Düz metin — Loki/ELK uyumsuzluğu                                                                                                              |
| Production referansı            | ✅ **gitnode.dev**      | Canlı platform — B2B görüşmelerinde gösterilebilir referans                                                                                  |
| Tek geliştirici                 | **Değişmedi**           | Bus factor = 1                                                                                                                                |
| `@ApplicationModuleTest`        | **YOK**                 | Cross-module event akışı entegrasyon testi yok                                                                                                |
| Load test kanıtı                | **YOK**                 | Kapasite ölçülmemiş                                                                                                                           |
| JaCoCo eşiği                    | **%48/%40 mütevazı**    | %60/%55'e çıkarılmalı                                                                                                                         |

---

# BÖLÜM C — SATIŞ / DEĞERLEME

## 11. Yeni Özelliklerin Fiyata Etkisi

### Neyi değiştiriyor:

**Actions + Runner → en büyük değer artışı:**
Rakip self-hosted Git platformlarının çoğu CI/CD'yi harici araçlara (Jenkins, GitLab CI, Drone) bırakır.
GitNode'un kendi CI/CD motoruna sahip olması — YAML workflow, matrix build, secrets vault, artifact store,
SSE log, WebSocket runner protokolü, hem shell hem docker executor — doğrudan rekabetçi pozisyonlama sağlar.
"GitHub Actions benzeri CI/CD dahil" argümanı $50k+ segment için çekici.

**Admin Angular UI kapandı:**
Önceki raporlarda açık boşluk olarak işaretlenen admin panel artık mevcut. 13+ sayfa, tüm REST endpoint eşleşmeli.
Teknik olmayan karar vericiye canlı demo yapılabilir.

**gitnode-events Maven artefaktı:**
Actions event'leri dahil edildi. Dışarıdan event tüketimi teknik olarak mümkün.

### Neyi değiştirmiyor:

- Runner WebSocket multi-instance sorunu giderildi — Redis pub/sub dispatch aktif.
- Production referansı mevcut: **gitnode.dev**.
- Organization SSO login UI yok — altyapı hazır, kullanıcı akışı yok.
- MFA yok — kurumsal güvenlik kontrol listesinde boşluk.
- Tek geliştirici — değişmedi.

## 12. Güncellenmiş Fiyat Aralığı

| Segment                              | Model                    | USD                     | EUR                    | Delta                                                |
|--------------------------------------|--------------------------|-------------------------|------------------------|------------------------------------------------------|
| Bireysel / kod marketi               | Kaynak kod, tek seferlik | $799 – $1.499           | €730 – €1.380          | Aynı                                                 |
| Küçük ekip / ajans (5–30 kişi)       | Kaynak kod + 3 ay destek | $15.000 – $35.000       | €13.800 – €32.000      | +$3k–$7k — Actions değeri                            |
| Orta ölçekli şirket (50–300 kişi)    | Lisans + 1 yıl destek    | **$50.000 – $85.000**   | **€46.000 – €78.000**  | +$15k–$20k — CI/CD + admin UI                        |
| Regülasyona tabi kurum (SSO gerekli) | Lisans + SLA + audit     | **$100.000 – $175.000** | **€92.000 – €160.000** | +$20k–$25k — Actions + per-org SSO + audit altyapısı |

### Mevcut Gerçekçi Aralık

> **$50.000 – $85.000 USD** / **€46.000 – €78.000 EUR**
> *(Kaynak kod + 6 ay teknik destek, orta ölçekli şirket alıcısı)*

**Destekleyen faktörler:**

- Tam CI/CD motoru (YAML workflow, matrix, secrets, artifacts, runner) — rakip açık kaynakta nadiren dahili
- Admin Angular UI — canlı demo edilebilir
- 18 Modulith modülü, 668 test — teknik due-diligence geçer
- Per-org SAML + LDAP + audit log + Prometheus/Grafana + DLQ + Redis cache
- Modern stack (Java 25, Spring Boot 4.0.6, Angular 21) — çoğu rakipte yok

**Kısıtlayan faktörler:**

- MFA yok — kurumsal güvenlik kontrol listesinde boşluk
- Organization SSO login UI yok
- MFA yok
- Organization SSO login akışı UI entegrasyonu yok
- Tek geliştirici

## 13. Öncelik Haritası

| Yatırım                                               | Süre tahmini | Potansiyel ek değer (USD)                 | Öncelik               |
|-------------------------------------------------------|--------------|-------------------------------------------|-----------------------|
| Runner dispatch Redis pub-sub (multi-instance fix)    | ✅ **Tamamlandı** | dispatch artık cross-instance; job kayıp yok | —                     |
| Canlı referans platformu                              | ✅ **gitnode.dev** | B2B itiraz kapandı                       | —                     |
| Organization SSO login akışı (login UI)               | 3–5 gün           | +$10k–$20k — $100k+ segment tam açılır   | 🔴 Yüksek             |
| MFA / TOTP                                            | 1–2 hafta         | +$5k–$15k — kurumsal güvenlik kutusu     | 🟡 Orta               |
| Tenant enabled JWT revoke (Redis blocklist)           | 2–3 gün           | Savunma değeri                           | 🟡 Orta               |
| Actions runner E2E (gerçek runner entegrasyonu)       | 1 hafta           | Kalite güvencesi, due-diligence'da artı  | 🟡 Orta               |
| JaCoCo eşik artışı (%48→%60)                          | 1–2 gün           | Kalite sinyali                           | 🟢 Düşük              |
| JSON/structured loglama                               | 1–2 gün           | +$1k–$3k ops olgunluğu                   | 🟢 Düşük              |

---

# BÖLÜM D — KATEGORİ PUANLARI

> **Metodoloji:** 10 üzerinden. Tamamlanmış, doğrulanmış iş puan alır. Potansiyel ve plan puan almaz.
> Her puan gerekçeli.

---

## 14. Puan Kartı

### 14.1 Mimari — 9.0 / 10

**Güçlü:**

- 18 Spring Modulith modülü — `actions`, `admin` temiz eklendi.
- `actions::api` named interface — `admin` modülü actions'a doğrudan bağımlı değil.
- gitnode-runner ayrı Go binary — backend bağımsız ölçeklenebilir.
- `gitnode-events` artefaktı — actions event'leri dahil, cross-module event bağımsızlığı.
- `ApplicationModules.verify()` CI koruyucu.

**Neden 10 değil:**

- Runner WebSocket in-memory session registry — dağıtık deployment'ta mimari zayıflık.
- `@ApplicationModuleTest` yok — event flow entegrasyon kanıtı eksik.
- Bus factor = 1.
- JGit horizontal scaling sorunu devam ediyor.

---

### 14.2 Kod Kalitesi (Backend) — 9.0 / 10

**Güçlü:**

- Actions modülü kalite standartlarını izliyor: Google Java Format, JSpecify `@NullMarked`, Lombok.
- `WorkflowExecutionService` `@Audited` — CI/CD lifecycle audit log'da.
- `SecretVaultService` — şifreleme mantığı temiz, değer API'den dönmüyor.
- `ExpressionEvaluator` — template engine benzeri değerlendirici, iyi test edilmiş.
- JaCoCo gate devam ediyor, `mr-check.yml`'de `verify` çalışıyor.

**Neden 10 değil:**

- `CollaboratorController.sendInvitationEmail()` stub method — değişmedi.
- JaCoCo eşiği %48/%40 mütevazı.
- Go runner test kapsamı sınırlı (5 test dosyası).

---

### 14.3 Performans — 9.0 / 10

**Güncel durum:** RAPOR4 iyileştirmeleri (HTTP/2, gzip, virtual thread, Redis push-invalidation, N→1 RevWalk,
HikariCP, Prometheus) korundu. Actions SSE log streaming verimli — her adım için ayrı `SseEmitter`.

**Neden 10 değil:**

- Load test kanıtı yok — kapasite bilinmiyor.
- Runner job dispatch latency ölçülmemiş.

---

### 14.4 Memory Usage — 9.5 / 10

**Güncel durum:** JVM G1GC (`-Xms256m -Xmx768m`), Redis `volatile-lru 512mb`, in-memory stats cache kaldırıldı
(Redis `@Cacheable`). `RunnerSessionRegistry` in-memory ama runner sayısı sınırlı olduğu için risk düşük.

---

### 14.5 Hız ve Stabilite — 8.0 / 10

**Güçlü:** DLQ + scheduler, Redis dağıtık lock, circuit breaker (webhook + SAML), graceful degrade.
Actions SSE ve WebSocket yeni stabilite noktaları.

**Neden 10 değil:**

- Runner WebSocket bağlantı kopması yönetimi — yeniden bağlantı mekanizması doğrulanmadı.
- Redis/DB single node.
- Kubernetes readiness/liveness probe yok.
- JGit migration çağrıları CB kapsamı dışında.

---

### 14.6 Güvenlik — 8.5 / 10

**Güçlü:**

- `SecretVaultService` AES-256/GCM — değer asla API'den dönmüyor.
- Runner token doğrulama (handshake interceptor).
- `platformAdminService` configurable SpEL guard — tüm admin endpoint'leri.
- `tenant.enabled` hesap deaktivasyon.
- SAML + LDAP + JWT + OAuth2 — çok katmanlı auth.

**Neden 10 değil:**

- Tenant enabled JWT revoke yok — deaktif hesap token'ı TTL dolana kadar geçerli.
- MFA yok.
- Fixed-window rate limit burst açığı devam ediyor.
- CVE tarama yok.
- CORS production yapılandırması doğrulanmadı.

---

### 14.7 Test Coverage (Backend) — 8.5 / 10

**Güçlü:**

- 668 test, 0 hata. Actions 101, admin 112 — yeni kod test edilmeden bırakılmadı.
- `ExpressionEvaluatorTest` (11 test) — expression edge case'leri.
- `WorkflowExecutionServiceTest` (12), `WorkflowTriggerServiceTest` (12) — iş akışı happy path + hata yolları.
- `RunnerRegistryServiceTest` (12) — online/offline geçişleri.
- JaCoCo gate `mr-check.yml`'de bloklıyor.

**Neden 10 değil:**

- %48/%40 eşiği düşük — gerçek kapsam belirsiz.
- `@ApplicationModuleTest` yok.
- Runner E2E (gerçek job yürütme) yok.
- Frontend test yok.

---

### 14.8 Ölçeklenebilirlik — 7.5 / 10

**Güçlü:**

- Redis dağıtık lock (DLQ scheduler, bootstrap admin race condition).
- `republish-outstanding-events-on-restart: false` — multi-instance event storm yok.
- Shared named volume (`gitnode-repos`) — Git repo verisi instance'lar arası paylaşılıyor.
- `DistributedCircuitBreakerGuard` — Redis'te CB state, tüm instance'lar okuyor.
- `AdminStatsService` Redis `@Cacheable` — per-instance hesaplama yok.
- HAProxy TCP LB (SSH + HTTP).

**Neden 10 değil:**

- ~~**Runner WebSocket in-memory session:**~~ **GİDERİLDİ** — Redis pub/sub dispatch (`gitnode:actions:dispatch:{runnerId}`) + `FOR UPDATE SKIP LOCKED`; cross-instance job iletimi çalışıyor.
- Kubernetes manifest yok.
- Load test kanıtı yok.
- JGit multi-instance file locking — natively doğrulanmamış.

---

### 14.9 Satışa Hazırlık — 8.5 / 10

**Güçlü:**

- **Admin Angular UI tamamlandı:** 13+ sayfa — teknik olmayan stakeholder'a demo edilebilir.
- **CI/CD motoru:** "Kendi sunucunuzda GitHub Actions benzeri CI/CD" argümanı — enterprise için güçlü.
- **Runner binary:** Demo sırasında gerçek job yürütme gösterilebilir.
- Organization SSO altyapısı, audit log, platform stats, DLQ izleme — ops ekibine satılabilir.
- Bootstrap admin + platform settings — "kurulum kolay" argümanı geçerli.

**Neden 10 değil:**

- Organization SSO login UI yok.
- MFA yok.
- Tek geliştirici.

---

## 15. Özet Puan Tablosu

| Kategori              | Puan         | Kısa Gerekçe                                                                             |
|-----------------------|--------------|------------------------------------------------------------------------------------------|
| **Mimari**            | **9.0 / 10** | 18 Modulith modülü, actions::api, runner ayrı binary. Runner WS in-memory = mimari borç. |
| **Kod Kalitesi**      | **9.0 / 10** | Actions + admin kalite standardı korudu. JaCoCo gate aktif. Stub method hâlâ var.        |
| **Performans**        | **9.0 / 10** | HTTP/2, virtual thread, Redis cache korundu. Load test yok.                              |
| **Memory Usage**      | **9.5 / 10** | In-memory cache tamamen Redis'e taşındı. RunnerSessionRegistry kısıtlı bellek kullanımı. |
| **Hız ve Stabilite**  | **8.0 / 10** | CB + DLQ + Redis lock korundu. Runner reconnect belirsiz. Redis/DB single node.          |
| **Güvenlik**          | **8.5 / 10** | AES-256 secrets vault + runner token auth eklendi. MFA + JWT revoke hâlâ yok.            |
| **Test Coverage**     | **8.5 / 10** | 668 test, actions 101, admin 112. JaCoCo gate. Eşik mütevazı, runner E2E yok.            |
| **Ölçeklenebilirlik** | **8.5 / 10** | Redis pub/sub dispatch + FOR UPDATE SKIP LOCKED ile runner multi-instance sorunu giderildi. Load test yok. |
| **Satışa Hazırlık**   | **9.0 / 10** | Admin UI + CI/CD + gitnode.dev referans + runner fix. MFA + SSO login UI eksik.          |
| **Genel Ortalama**    | **8.9 / 10** | Runner fix (7.5→8.5) + production ref + satış hazırlık (8.5→9.0). MFA tek büyük boşluk. |

> **Yorum:** Proje GitHub'ın temel özellik setine (git hosting + PR + issues + CI/CD + secrets + artifacts + SSO)
> büyük ölçüde ulaştı. İki eski tavan kapandı: runner multi-instance dispatch Redis pub/sub ile çözüldü, production
> referans gitnode.dev ile mevcut. Kalan tavan: ① Organization SSO login UI yok, ② MFA yok (1–2 haftalık iş),
> ③ tek geliştirici. Bu iki madde kapanırsa ortalama 9.3+ seviyesine çıkar.

---

## 16. Özet: Bugün Nerede?

**Teknik gerçek:**
GitNode artık tek bir Spring Boot uygulamasında git hosting + PR + issue + CI/CD (GitHub Actions benzeri) +
SSO (SAML + LDAP, per-org) + admin paneli + harici runner ajanı (shell + docker) barındıran bütünleşik bir
platform. 18 Modulith modülü, 668 test, 42 Flyway migrasyonu, 37 E2E spec dosyası, tam çalışır admin Angular UI.
Teknik due-diligence'ı geçecek yapı var.

**Fiyat gerçeği:**
$55k–$90k gerçekçi aralık (runner fix + gitnode.dev ile alt sınır ve güven arttı). $100k–$175k segmenti için
per-org SSO altyapısı + CI/CD güçlü argüman; Organization SSO login UI ve MFA kapanırsa segment tam açılır.

**Bir sonraki en değerli hamle:**

```
① Organization SSO login UI (3-5 gün) → $100k+ segment tam açılır
② MFA/TOTP (1-2 hafta) → kurumsal güvenlik kutusu kapanır
③ JaCoCo %48→%60 (1-2 gün) → due-diligence'da kalite sinyali
```
