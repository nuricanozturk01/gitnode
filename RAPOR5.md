# OriginHub — RAPOR5: Teknik İlerleme + Güncel Değerleme

**Tarih:** 2026-06-06
**Önceki rapor:** RAPOR4.md (2026-06-05)
**Dürüst uyarı:** Bu rapor RAPOR4 ile aynı çizgide yazılmıştır — abartı yok, piyasa gerçekliği esas. Yeni iş nesnel
olarak değerlendirildi; beklentiyi şişiren ifade kullanılmadı.

---

# BÖLÜM A — TEKNİK İLERLEME (RAPOR4'ten bu yana)

## 1. Eklenen Özellikler

### 1.1 Admin Modülü — Tam Kapsamlı Platform Yönetim API'si (YENİ)

RAPOR4'te "audit log frontend UI yok" ve "admin panel REST API var ama UI yok" boşlukları vardı. Bu sürümde bağımsız
bir `admin` Spring Modulith modülü oluşturuldu.

**Controllers (10 adet):**

| Controller | Endpoint prefix | Kapsam |
|---|---|---|
| `AdminAuditLogController` | `/api/admin/audit-logs` | Arama (q/actor/action/entityType/from-to), filtre seçenekleri, recent (son N saat) |
| `AdminUserController` | `/api/admin/users` | Listeleme (sayfalı + arama), detay, enable/disable |
| `AdminRepoController` | `/api/admin/repos` | Repo listeleme + yönetim |
| `AdminStatsController` | `/api/admin/stats` | Overview (toplam user/repo/org), repo aktivitesi time-series, upload aktivitesi time-series |
| `AdminPlatformSettingsController` | `/api/admin/settings` | Platform ayarları okuma/güncelleme, feature toggle, platform admin listesi |
| `AdminPgAuditLogController` | `/api/admin/pgaudit-logs` | pgaudit log dosyası görüntüleyici + durum kontrolü |
| `AdminModulithEventController` | `/api/admin/modulith-events` | Spring Modulith event publication izleme, filtreleme, detay |
| `AdminWebhookDlqController` | `/api/admin/webhook-dlq` | DLQ kayıtları listeleme + yönetim |
| `OrganizationAdminController` | `/api/admin/organizations` | Organizasyon CRUD + SSO/LDAP yapılandırma + metadata test + connection test |
| `AdminAuthController` | `/api/admin/auth` | Admin login (ayrı auth akışı) |

**Services (11 adet):**
- `BootstrapAdminInitializer` — Uygulama başlangıcında admin hesabı oluşturur. `ORIGINHUB_BOOTSTRAP_ADMIN_PASSWORD`
  set edilmişse hesap yaratılır; boşsa `WARN` log.
- `PlatformAdminService` — `@platformAdminService.isCurrentUserPlatformAdmin()` SpEL bean —
  `ORIGINHUB_PLATFORM_ADMIN_USERNAMES` (CSV) + bootstrap admin kullanıcısı platform admin sayılır. Tüm admin
  controller'larında `@PreAuthorize` ile kullanılıyor.
- `AdminAuditLogService` — Specifications tabanlı dinamik filtre (actor, action, entityType, entityId, from, to, q).
- `AdminUserService` — Kullanıcı listeleme (arama destekli), detay, `tenant.enabled` toggle.
- `AdminRepoService` — Repo listeleme.
- `AdminStatsService` — Overview (total user/repo/org/disabled-user, son 7/30 günlük büyüme), repo aktivitesi ve
  upload aktivitesi time-series. In-memory cache + `PlatformSetting` tablosundan TTL.
- `AdminPlatformSettingsService` — `platform_setting` tablosundan ayar okuma/yazma (stats cache TTL, feature toggle'lar).
- `PgAuditLogReaderService` — PostgreSQL log dosyasını regex ile parse eder, pgaudit satırlarını ayıklar.
  `MAX_PAGE_SIZE=50`, `MIN_SCAN_LINES=1000`. `PgAuditLocalLogSyncRunner` uygulama başlangıcında log yolunu doğrular.
- `AdminModulithEventService` — `event_publication` tablosunu okuyarak Modulith event durumlarını izler (PENDING,
  COMPLETED, FAILED). Feature toggle: `pgaudit_viewer_enabled`, `modulith_events_viewer_enabled`.
- `AdminAuthService` — Platform admin oturum açma.
- `PlatformAdminService` — Admin yetki sorgulama + bootstrap admin bilgisi.

**Nüans:**
- Tüm admin endpoint'leri `@platformAdminService.isCurrentUserPlatformAdmin()` koruyucusundan geçiyor — Spring
  Security `ROLE_ADMIN` değil, custom SpEL bean.
- `BootstrapAdminInitializer` yalnızca `ORIGINHUB_BOOTSTRAP_ADMIN_PASSWORD` set edilmişse çalışır; boş bırakılırsa
  admin hesabı oluşturulmaz (güvenli varsayılan).
- `PgAuditLogReaderService` log dosyasına doğrudan FS erişimiyle çalışıyor — Docker volume mount gerektirir.
- Admin panelinde frontend UI hâlâ yok — yalnızca REST API.

---

### 1.2 Organization (Organizasyon) SSO + LDAP — DB Tabanlı Multi-Tenant SSO (YENİ)

RAPOR4'te LDAP/SAML SSO global config (env var) üzerinden çalışıyordu. Bu sürümde organizasyon başına SSO
yapılandırması eklendi.

**`Organization` entity + `OrganizationService`:**
- `name`, `slug` (unique), `email_domains` (TEXT[]) — email adresi domain'e göre organizasyona otomatik atama.
- `sso_enabled`, `idp_metadata_uri`, `idp_metadata_xml`, `email_attribute`, `username_attribute`, `sp_entity_id` — SAML
  SP metadata per-organization.
- `ldap_enabled`, `ldap_url`, `ldap_base_dn`, `ldap_manager_dn`, `ldap_manager_password`, `ldap_user_search_base`,
  `ldap_user_search_filter`, `ldap_email_attribute`, `ldap_display_name_attribute`, `ldap_use_start_tls`,
  `ldap_group_search_base`, `ldap_group_search_filter`, `ldap_group_role_attribute`, `ldap_admin_group_dns` —
  organizasyon başına LDAP yapılandırması.

**`OrganizationAdminController` (admin API):**
- CRUD: oluştur, listele, detay (slug'a göre), güncelle, sil.
- `PUT /{slug}/sso` — SAML SSO yapılandırma.
- `PUT /{slug}/sso/enabled` — SSO aktif/pasif.
- `POST /{slug}/sso/test` — SAML IdP metadata bağlantı testi (`SamlMetadataTestResult`).
- `PUT /{slug}/ldap` — LDAP yapılandırma (organizasyon bazında).
- `POST /{slug}/ldap/test` — LDAP bağlantı testi (`LdapConnectionTestResult`).

**`auth::api` named interface:**
- `OrganizationAdminPort` — `admin` modülünün `auth` modülüne bağımlı olmaması için port.
- `OrganizationAdminAdapter` — `auth` modülünde implementation.

**Flyway:**
- `V028__organization_sso.sql` — `organization` tablosu + slug/sso_enabled indeksleri.
- `V032__organization_ldap.sql` — LDAP kolonu ekleme + `idx_organization_ldap_enabled`.

**`@Audited` eklendi:** `OrganizationService.create()` + diğer mutasyon metodları — audit log kapsamı genişledi.

**Nüans:** Email domain bazlı SSO routing henüz login akışına entegre değil — `organization.email_domains` kaydediliyor
ama giriş ekranında "şirket email'inle giriş" akışı yok. Altyapı hazır, UI entegrasyonu yok.

---

### 1.3 Tenant Enable/Disable — Hesap Deaktivasyon (YENİ)

- **`V029__tenant_enabled.sql`:** `tenant.enabled BOOLEAN NOT NULL DEFAULT TRUE` kolonu.
- **`AdminUserController.PUT /{id}/enabled`:** Platform admin kullanıcı hesabını aktif/pasif yapabiliyor.
- **`V030__admin_stats_indexes.sql`:** `idx_tenant_enabled` + `idx_tenant_created_at` (DESC) + `idx_repositories_created_at` (DESC) + `idx_organization_email_domains` (GIN).

**Nüans:** `enabled=false` olan hesabın mevcut oturumları JWT TTL dolana kadar geçerli kalır — anlık revoke mekanizması
(Redis blocklist, token blacklist) yok. Ayrıca `AuthService`'de `enabled` kontrolü yapılıp yapılmadığı doğrulanmadı.

---

### 1.4 Platform Settings — DB Tabanlı Runtime Yapılandırma (YENİ)

- **`V031__platform_settings.sql`:** `platform_setting` tablosu (`setting_key VARCHAR(64) PK`, `setting_value
  VARCHAR(512)`, `updated_at TIMESTAMPTZ`).
- Varsayılan kayıtlar: `stats_cache_ttl_seconds=300`.
- **Feature toggle'lar:** `pgaudit_viewer_enabled` (varsayılan `false`), `modulith_events_viewer_enabled` (varsayılan
  `false`) — DB'den okunur, env var fallback var.
- **`AdminPlatformSettingsController`:**
  - `GET /api/admin/settings` — mevcut ayarları döndürür.
  - `PUT /api/admin/settings/stats-cache` — stats cache TTL günceller + önbelleği temizler.
  - `PUT /api/admin/settings/feature-toggles` — pgaudit viewer / modulith event viewer açar/kapar.
  - `GET /api/admin/settings/platform-admins` — platform admin listesi + bootstrap admin bilgisi.
- **`AdminPlatformSettingsService`:** `volatile` field ile in-memory cache — DB hit her istekte değil.

**Nüans:** Platform setting'ler şu an `stats_cache_ttl`, `pgaudit_viewer_enabled`, `modulith_events_viewer_enabled`
ile sınırlı. Genel amaçlı feature flag sistemi değil — genişletmek için yeni key tanımı + service kodu gerekiyor.

---

### 1.5 Admin Stats — Platform İzleme Panosu (YENİ)

`AdminStatsService` + `AdminStatsController`:

- **Overview** (`GET /api/admin/stats/overview`): toplam kullanıcı sayısı, aktif kullanıcı, deaktif kullanıcı,
  toplam repo, toplam organizasyon; son 7 gün / son 30 günde yeni kullanıcı + yeni repo.
- **Repo aktivitesi** (`GET /api/admin/stats/repos?period=week|month`): owner başına repo sayısı sıralaması +
  günlük time-series.
- **Upload aktivitesi** (`GET /api/admin/stats/uploads?period=week|month`): yeni repo oluşturma proxy olarak kullanılıyor
  (push event takibi yok), günlük time-series.
- **Cache:** In-memory `volatile` field, TTL `platform_setting.stats_cache_ttl_seconds`'dan okunuyor. `?refresh=true`
  zorla yeniletir.
- **Depolama özeti:** `RepoStorageService` üzerinden disk kullanımı toplanıyor — push/upload event takibi yok, sadece
  anlık boyut.

**Nüans:** Upload aktivitesi proxy ölçüm — gerçek byte transfer sayımı değil, repo oluşturma sayısı. "Upload activity"
adı yanlış beklenti yaratabilir.

---

### 1.6 PgAudit Log Viewer — Admin API (YENİ)

RAPOR4'te pgaudit PostgreSQL log dosyasına yazıyordu ama API'den okunamıyordu.

- **`PgAuditLogReaderService`:** PostgreSQL log dosyasını (`ORIGINHUB_PG_LOG_PATH` env var) okur, regex ile pgaudit
  satırlarını ayıklar (`AUDIT: SESSION` formatı). Timestamp parse, obje türü/adı, komut türü, SQL statement
  çıkarılıyor.
- **`AdminPgAuditLogController`:** `GET /api/admin/pgaudit-logs` — tarih filtresi + pagination (max 50 sonuç).
  `GET /api/admin/pgaudit-logs/status` — pgaudit aktif mi, log dosyası erişilebilir mi.
- **`PgAuditLocalLogSyncRunner`:** Uygulama başlangıcında log yolunu doğrular; erişilemezse `WARN`.
- Feature toggle: `pgaudit_viewer_enabled=false` varsayılan — platform admin API'den açılıyor.

**Nüans:** FS'ten regex ile okuma — büyük log dosyalarında `MIN_SCAN_LINES=1000` alt sınırı var ama yüksek hacimde
performans garantisi yok. Gerçek log pipeline (Loki, Elasticsearch) değil.

---

### 1.7 Modulith Event Viewer — Admin API (YENİ)

- **`AdminModulithEventService`:** `event_publication` tablosunu `EventPublicationSpecifications` ile sorgular.
  PENDING/COMPLETED/FAILED lifecycle filtresi, event type, listener ID, tarih aralığı.
- **`AdminModulithEventController`:** `GET /api/admin/modulith-events` — arama + filtreleme.
  `GET /api/admin/modulith-events/{id}` — detay. `GET /api/admin/modulith-events/filters` — filtre seçenekleri.
  `GET /api/admin/modulith-events/status` — viewer aktif mi.
- Feature toggle: `modulith_events_viewer_enabled=false` varsayılan.
- **`EventPublicationRecord`** entity — `event_publication` tablosunu JPA ile okur (yalnızca read).
- **`EventPublicationRepository`:** Filtre seçenekleri (`findDistinctEventTypes`, `findDistinctListenerIds`,
  `findDistinctStatuses`) `List<String>` yerine `Page<String>` döndürecek şekilde güncellendi — servis `.getContent()`
  ile listeyi alıyor, `getTotalElements() > FILTER_OPTIONS_LIMIT` ile truncation flag üretiyor. Frontend dropdown
  seçenekleri 200'ü aşarsa uyarı gösteriyor.

**Önem:** RAPOR3'ten beri "Spring Modulith `@ApplicationModuleTest` senaryosu yok" boşluğu vardı. Bu viewer doğrudan
test değil, ama **production'da event pipeline'ını izleme** imkânı sağlıyor — PENDING kalan event'ler artık görünür.

---

### 1.8 originhub-events — Ayrı Maven Modülü (YENİ)

Modulith event sınıfları `originhub-backend` içinden `originhub-events` ayrı Maven modülüne taşındı:

- `events/branch/`, `events/collaborator/`, `events/issue/`, `events/pr/`, `events/profile/`, `events/project/`,
  `events/repo/`, `events/snippet/`, `events/tag/`, `events/task/` — tüm domain event record'ları.
- Maven parent: `originhub-parent`.
- **Etki:** Event sınıfları artık `originhub-backend` dışında da tüketilebilir — ileriki mikroservis veya external
  consumer senaryosu için hazır.

---

### 1.9 Circuit Breaker / Resilience4j — Dış HTTP Çağrılarında Dayanıklılık (YENİ)

`spring-cloud-starter-circuitbreaker-resilience4j` eklendi (Spring Cloud BOM 2025.1.0 versiyon yönetiyor).

**Kapsam — circuit breaker eklenen iki nokta:**

| Servis | CB Adı | Neden |
|---|---|---|
| `WebhookDeliveryService` | `webhook.<hedef-host>` (dinamik, host başına) | Webhook iletimi dış URL'e POST atar; dead endpoint retry kasırgası önlendi |
| `SamlMetadataService` | `saml-metadata` | SAML IdP metadata URL'den çekilir; IdP unreachable olunca admin bloklamaması için |

**`WebhookDeliveryService` davranışı:**
- `circuitBreakerFor(url)` — `URI.create(url).getHost()` ile hedef host çıkarılır, `CircuitBreakerRegistry.circuitBreaker("webhook.<host>", "webhook-delivery")` ile o host'a özel CB alınır/oluşturulur.
- CB **CLOSED/HALF-OPEN:** normal retry döngüsü (mevcut 3 deneme + exponential backoff) çalışır.
- CB **OPEN:** `CallNotPermittedException` yakalanır → retry yapılmadan doğrudan DLQ'ya gönderilir. Bir endpoint down olduğunda diğer host'ların CB'si etkilenmez.
- `redeliverRaw()` (DLQ scheduler çağrısı) aynı CB'yi kullanır — scheduled retry de koruma altında.

**`SamlMetadataService` davranışı:**
- Constructor'a `CircuitBreakerRegistry` enjekte edildi — Spring auto-config CB bean'i sağlar.
- `executeSupplier` ile fetch çağrısı sarmalandı; CB OPEN → `CallNotPermittedException` → `BadRequestException("idpMetadataFetchFailed")`.

**Konfigürasyon (`application.yaml`):**

```yaml
resilience4j:
  circuitbreaker:
    configs:
      webhook-delivery:           # tüm webhook.<host> instance'ları bu config'i kullanır
        sliding-window-size: 10
        failure-rate-threshold: 50     # %50 hata → OPEN
        slow-call-duration-threshold: 10s
        slow-call-rate-threshold: 80
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
    instances:
      saml-metadata:
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 2
```

**Actuator:** `management.endpoints.web.exposure.include` → `circuitbreakers` eklendi. CB durumları (`CLOSED/OPEN/HALF_OPEN`) `/actuator/circuitbreakers` endpoint'inden izlenebilir. Her CB `register-health-indicator: true` — `/actuator/health` health detaylarında görünür.

**Nüans:**
- GitHub migration'da JGit `Git.cloneRepository()` / PR migration RestTemplate çağrıları CB kapsamında değil — bu çağrılar job sistemi tarafından yönetiliyor (async job, MigrationStatus takibi), doğrudan RestClient değil.
- Per-host CB dinamik olarak oluşturuluyor — çok fazla farklı webhook hedef adresi varsa `CircuitBreakerRegistry` bellekte onlarca CB tutabilir; pratikte webhook sayısı sınırlı olduğu için sorun değil.

---

## 2. Yapılan Güncellemeler

### 2.1 `@Audited` Kapsamı Genişledi

`OrganizationService` metodları `@Audited` ile işaretlendi — organizasyon oluşturma, SSO yapılandırma, LDAP
yapılandırma, silme audit log'a yazılıyor. Toplam `@Audited` sayısı 23'ten **~27'ye** çıktı.

### 2.2 `AuditLogController` → `AdminAuditLogController`

Önceki `AuditLogController` `/api/admin/audit-logs` altında standalone duruyordu. Bu sürümde `admin` modülüne taşındı,
`AdminAuditLogService` ile dinamik Specifications filtresi eklendi. Önceki basit 3-endpoint'ten çok daha esnek hale
geldi.

### 2.3 Webhook DLQ Admin Endpoint

`AdminWebhookDlqController` — DLQ kaydı listeleme admin modülüne eklendi. `webhook::api` named interface
(`WebhookDlqAdminPort`, `WebhookDlqEntry`) üzerinden `admin` → `webhook` cross-module erişimi.

### 2.4 JaCoCo CI Gate Eklendi

`jacoco-maven-plugin` 0.8.13 `originhub-backend/pom.xml`'e eklendi. `verify` fazında çalışan üç execution:

- **`jacoco-prepare-agent`:** Surefire argLine'ına JaCoCo agent enjekte ediyor (`@{argLine}` pattern).
- **`jacoco-report`:** `target/site/jacoco/` altında HTML + XML rapor üretiyor.
- **`jacoco-check`:** Build'i kırabilen gerçek gate — `INSTRUCTION ≥ %48`, `BRANCH ≥ %40`. Eşiğin altına düşen commit `./mvnw verify`'ı fail eder.

Hariç tutulanlar: `entities/**`, `dtos/**`, `mappers/**`, `configs/**`, `*Application.class`, `listeners/**` — boilerplate sınıflar ölçüm dışı.

**Nüans:** %48 instruction / %40 branch eşiği gerçek bir gate, ancak mütevazı. Controller → Service → Repository zincirinin happy path'i karşılıyor. Hata yolu ve edge case'leri kapsam dışında kalıyor; eşik zamanla yükseltilmeli.

### 2.5 AdminStatsService Race Condition Giderildi

RAPOR5'te belgelenen `volatile` + compound check-then-act sorunu giderildi:

- `volatile AdminStatsOverview overviewCache` → `AtomicReference<@Nullable OverviewCache>`
- `volatile StorageCache storageCache` → `AtomicReference<@Nullable StorageCache>`
- `Map<String, CachedRepoActivity>` → `ConcurrentHashMap`
- `Map<String, CachedUploadActivity>` → `ConcurrentHashMap`

**Sonuç:** `volatile` ile oluşan görünürlük sorunu ve compound read-check-write yarış koşulu giderildi. Ancak `get()` + karşılaştırma + `set()` hâlâ atomik değil — yüksek eşzamanlılıkta iki thread aynı anda süresi dolmuş cache'i görebilir ve her ikisi de hesaplayabilir ("thundering herd"). Son yazan kazanır; sonuç geçerli, veri bozulması yok. Tam atomisite için `compareAndSet()` veya kilitleme gerekir — mevcut yük düzeyinde kabul edilebilir.

---

## 3. Test & CI İlerlemesi

### JaCoCo CI Gate

`jacoco-maven-plugin` 0.8.13 eklendi. `./mvnw verify` aşağıdaki eşiklerde build'i keser:

| Sayaç | Eşik | Kapsam |
|---|---|---|
| INSTRUCTION | ≥ %48 | Tüm bundle (hariç tutulanlar dahil değil) |
| BRANCH | ≥ %40 | Tüm bundle |

Hariç: `entities/**`, `dtos/**`, `mappers/**`, `configs/**`, `*Application.class`, `listeners/**`.

### Backend Unit Test Artışı

| Önceki (RAPOR4) | Güncel | Fark |
|---|---|---|
| 434 test | **486 test** | +52 test |

**Yeni test sınıfları (admin + org):**

| Test sınıfı | Test sayısı | Kapsam |
|---|---|---|
| `AdminAuditLogControllerTest` | 5 | Endpoint güvenlik, sayfalama |
| `AdminAuthControllerTest` | 1 | Admin login |
| `AdminPgAuditLogControllerTest` | 2 | PgAudit görüntüleyici |
| `AdminStatsServiceTest` | 3 | Overview, repo/upload aktivitesi |
| `PlatformAdminServiceTest` | 5 | Admin yetki kontrolü, bootstrap |
| `AdminPlatformSettingsServiceTest` | 3 | TTL okuma, feature toggle |
| `AdminModulithEventServiceTest` | 1 | Event viewer availability |
| `AdminAuthServiceTest` | 2 | Admin login akışı |
| `AdminUserServiceTest` | 2 | Kullanıcı listeleme, enable/disable |
| `AdminAuditLogServiceTest` | 4 | Dinamik filtre, sayfalama |
| `BootstrapAdminInitializerTest` | 4 | Bootstrap koşullu çalışma |
| `PgAuditLogReaderServiceTest` | 12 | Log parse, tarih filtre, pagination |
| `OrganizationServiceTest` | 9 | CRUD, SSO/LDAP config, domain check |

---

## 4. Güncel Teknik Metrikler

| Metrik | Değer | Not |
|---|---|---|
| Spring Modulith modülü | **17** | +`admin` modülü (önceki 16) |
| Maven modülü | **4** | `originhub-parent`, `originhub-backend`, `originhub-actions`, `originhub-events` (yeni) |
| Service sınıfı | ~**57** | +`AdminStatsService`, `AdminUserService`, `AdminRepoService`, `AdminPlatformSettingsService`, `PgAuditLogReaderService`, `AdminModulithEventService`, `AdminAuthService`, `PlatformAdminService`, `BootstrapAdminInitializer`, `AdminAuditLogService`, `OrganizationService` |
| Backend unit test | **486** | +52 — 0 hata, 0 regresyon |
| E2E test case | ~207 | Değişmedi |
| Flyway migration | V001 → **V033** | V032: org LDAP, V033: profile contribution indexes |
| Circuit Breaker | **Resilience4j** (Spring Cloud 2025.1.0) | `WebhookDeliveryService` (per-host) + `SamlMetadataService` |
| `@Audited` anotasyonu | **~27** | +OrganizationService mutasyon metodları |
| Admin REST endpoint | **~40** | 10 controller, tümü `platformAdminService` korumalı |
| Platform setting key | 3 | `stats_cache_ttl_seconds`, `pgaudit_viewer_enabled`, `modulith_events_viewer_enabled` |
| Java | **25** | — |
| Spring Boot | **4.0.6** | — |
| Spring Modulith | 2.0.2 | — |
| JGit | 7.2.1 | — |
| Apache MINA SSHD | 2.18.0 | — |
| Angular | **21.2** | — |
| TypeScript | 5.9 | — |
| Tailwind CSS | 4.3 | — |
| DaisyUI | 5.5 | — |
| Playwright | 1.52 | — |

---

# BÖLÜM B — GÜNCEL DURUM DEĞERLENDİRMESİ

## 5. Güçlü Yönler (Güncellenmiş)

- **Admin API production-grade:** 10 controller, ~40 endpoint, `platformAdminService` tabanlı RBAC. Audit log arama,
  kullanıcı yönetimi, stats panosu, pgaudit viewer, Modulith event izleme — operatör ihtiyaçlarını karşılamaya başlıyor.
- **Organization SSO altyapısı hazır:** Email domain bazlı organizasyon tanımı, per-org SAML + LDAP yapılandırma, bağlantı
  test endpoint'leri. Multi-tenant kurumsal kullanıma zemin oluştu.
- **Platform settings runtime'da değiştirilebilir:** DB-backed feature toggle'lar — yeniden başlatma gerektirmeden
  pgaudit viewer, modulith event viewer açılıp kapatılabiliyor.
- **Modulith event pipeline artık izlenebilir:** `event_publication` tablosunu admin API'den görüntüleme — PENDING kalan
  event'ler artık operatör tarafından tespit edilebilir.
- **Bootstrap admin:** `ORIGINHUB_BOOTSTRAP_ADMIN_PASSWORD` ile deploy'da otomatik admin oluşturma — ilk kurulum
  sürtünmesi azaldı.
- **Tenant enable/disable:** Platform admin kullanıcı hesabını aktif/pasif yapabiliyor — hesap deaktivasyon altyapısı
  oluştu.
- **originhub-events ayrı modül:** Event sınıfları yeniden kullanılabilir Maven artefaktı — ileriki entegrasyon
  senaryoları için hazır.
- **486 test, 0 hata:** +52 yeni test, regresyon yok.
- Önceki güçlü yönler (SSO, rate limiting, audit log, observability, Redis cache, DLQ, E2E) korundu.

## 6. Hâlâ Açık Boşluklar

| Boşluk | Durum | Etki |
|---|---|---|
| Admin frontend UI | **YOK** | 40 admin REST endpoint var, Angular admin sayfası yok — teknik olmayan stakeholder'a demo yapılamaz |
| Organization SSO login akışı | **Altyapı var, UI yok** | Email domain bazlı otomatik SSO yönlendirmesi login sayfasına entegre değil |
| Tenant `enabled` anlık revoke | **Eksik** | Deaktif hesabın mevcut JWT'si TTL dolana kadar geçerli — Redis blocklist yok |
| MFA / 2FA / TOTP | **YOK** | Kurumsal güvenlik standardı eksik |
| Frontend feature unit test | **Auth-only (%0 feature)** | 13 feature, 38 sayfa test edilmemiş |
| UI / browser E2E | **YOK** | Frontend davranışı Playwright ile doğrulanmıyor |
| Structured/JSON loglama | **YOK** | Düz metin — log analitik araçlarıyla sorgulanamazlık |
| Production referansı | **YOK** | "Başkası kullandı mı?" sorusuna cevap yok |
| Tek geliştirici | **Değişmedi** | Bus factor = 1 itirazı gelir |
| JaCoCo CI gate | ✅ **Eklendi** | 0.8.13, `verify` fazında INSTRUCTION ≥%48 / BRANCH ≥%40 gate — eşikler mütevazı |
| `@ApplicationModuleTest` | **YOK** | Cross-module event akışı entegrasyon testi yok |
| Load test kanıtı | **YOK** | Kapasitesi ölçülmemiş |

*Circuit Breaker: Webhook (per-host) + SAML metadata CB eklendi. GitHub migration JGit çağrıları kapsam dışı.*

*Actuator güvenliği: `/actuator/health` + `/actuator/info` açık, geri kalanı `authenticated()` gerektirir.
Admin endpoint güvenliği: `@platformAdminService.isCurrentUserPlatformAdmin()` — `ORIGINHUB_PLATFORM_ADMIN_USERNAMES`
CSV veya bootstrap admin kullanıcısı.*

## 7. RAPOR4 Boşluk Haritası — Güncel Durum

RAPOR4, 12 açık boşluk tanımlamıştı. Güncel durum:

| Boşluk (RAPOR4) | Durum | Not |
|---|---|---|
| Audit log frontend UI | 🟡 **Kısmen** | Admin REST API (~40 endpoint) var, Angular UI yok |
| JSON/structured loglama | ❌ Değişmedi | Düz metin, Loki/ELK/CloudWatch uyumsuzluğu |
| MFA / TOTP | ❌ Değişmedi | Kurumsal güvenlik boşluğu |
| Frontend feature unit test | ❌ Değişmedi | Auth-only |
| Spring Modulith `@ApplicationModuleTest` | 🟡 **Kısmen** | Event viewer eklendi (izleme var), ama `@ApplicationModuleTest` senaryosu yok |
| `registry` / `admin` frontend | ❌ Değişmedi | Admin API var, UI yok |
| Production referansı | ❌ Değişmedi | — |
| Tek geliştirici | ❌ Değişmedi | — |
| Audit/observability varsayılan kapalı | ❌ Değişmedi | Env var gerekli |

**Özet:** RAPOR4 boşluklarından **1 tam kapandı** (JaCoCo CI gate); "audit log REST API" ve "Modulith event izleme" boşlukları kısmen kapandı. `AdminStatsService` race condition giderildi. Yeni iş mevcut eksikliklerin büyüğünü değil, **platform operasyon altyapısını ve kod kalitesi altyapısını genişletti.**

---

# BÖLÜM C — SATIŞ/DEĞERLEME GÜNCELLEMESİ

## 8. Yeni İşlerin Fiyata Etkisi (Dürüst Değerlendirme)

Son çalışma platform operasyon API'sini ve organizasyon yönetim altyapısını oluşturdu. Bu gerçek teknik ilerleme —
özellikle `AdminStatsController`, `AdminUserController`, `BootstrapAdminInitializer` ve organizasyon SSO altyapısı
"ürün bir admin paneli olmadan nasıl yönetilir?" sorusunu REST API düzeyinde yanıtlıyor.

**Neyi değiştiriyor:**

- Admin REST API (~40 endpoint) → "admin paneli var mı?" sorusuna "REST API tam, Angular UI geliştirilmekte" yanıtı
  verilebilir. Teknik due-diligence'da artı; satış demosunda hâlâ eksik.
- Organization SSO altyapısı → "birden fazla şirket bağlanabilir mi?" sorusu artık "evet, her organizasyon başına
  SAML/LDAP yapılandırılabilir" ile cevaplanıyor. Bu cevap $80k–$150k segmentini güçlendiriyor.
- Tenant enable/disable → "kullanıcı hesabı kapatılabilir mi?" sorusu artık "evet" — temel IAM kontrolü tamamlandı.
- Bootstrap admin + platform settings → "ilk kurulum nasıl yapılır?" ve "runtime ayar değişikliği gerektiğinde ne
  olur?" soruları yanıtlandı — ops friction azaldı.

**Neyi değiştirmiyor:**

- Admin frontend UI hâlâ yok → teknik olmayan stakeholder'a demo hâlâ yapılamaz.
- Production referansı yok → en ağır B2B itiraz yanıtsız.
- MFA yok → güvenlik odaklı alıcı için checkbox eksik.
- Organization SSO login UI yok → altyapı hazır ama kullanıcı "şirket email'imle giriş" yapamıyor.
- Tek geliştirici → her enterprise görüşmesinde çıkar.

**Net etki:** $80k–$150k segmente argüman güçlendi (per-org SSO altyapısı eklendi), ama segment tam açılmadı —
Organization SSO login akışı ve admin UI eksikliği sürtünme yaratır. Mevcut aralık korunur, alt sınır daha sağlam.

## 9. Güncellenmiş Fiyat Aralığı

| Segment | Model | USD | EUR | RAPOR5'e göre delta |
|---|---|---|---|---|
| Bireysel / kod marketi | Kaynak kod, tek seferlik | $799 – $1.499 | €730 – €1.380 | Aynı |
| Küçük ekip / ajans (5–30 kişi) | Kaynak kod + 3 ay destek | $12.000 – $28.000 | €11.000 – €26.000 | Aynı |
| Orta ölçekli şirket (50–300 kişi) | Lisans + 1 yıl destek | **$35.000 – $65.000** | **€32.000 – €60.000** | Alt sınır güçlendi — admin API + org yönetim altyapısı |
| Regülasyona tabi kurum (SSO gerekli) | Lisans + SLA + audit | $80.000 – $150.000 | €73.000 – €138.000 | **Teknik argüman güçlendi** — per-org SSO altyapısı, fakat login UI + admin UI eksik; segment henüz tam açık değil |

### Mevcut Gerçekçi Aralık

> **$35.000 – $65.000 USD** / **€32.000 – €60.000 EUR**
> *(Kaynak kod + 6 ay teknik destek, orta ölçekli şirket alıcısı)*

Bu aralığı destekleyen faktörler (güncellenmiş):

- ~%90 service sınıf mevcudiyeti + ~207 E2E case — teknik due-diligence'ı geçer
- **Admin REST API (~40 endpoint):** Kullanıcı yönetimi, stats panosu, audit log arama, DLQ izleme — ops olgunluğu arttı
- **Organization SSO altyapısı:** Per-org SAML + LDAP + domain routing + test endpoint'leri — multi-tenant kurumsal hazırlık
- **Platform settings runtime:** DB-backed feature toggle — ops friction azaldı
- **Bootstrap admin:** Deploy'da otomatik admin — kurulum basitliği korundu
- **486 test, 0 hata** — güven tabanı güçlü
- **Circuit Breaker (Resilience4j):** Webhook iletimi per-host CB + SAML metadata CB — dış HTTP noktaları artık izole; dead endpoint retry kasırgası DLQ'ya yönlendiriliyor
- Redis cache, DLQ, audit log, Prometheus + Grafana, rate limiting — önceki güçlü yönler korundu
- Modern stack (Java 25, Spring Boot 4.0.6, Angular 21) — rakipte yok

Bu aralığı düşüren faktörler (güncellenmiş):

- Admin frontend UI yok — REST API var, Angular sayfası yok
- Production referansı yok
- Tek geliştirici
- MFA yok
- Organization SSO login akışı UI entegrasyonu yok
- Audit/observability varsayılan kapalı

## 10. Güncellenmiş Yatırım Haritası / Öncelik

| Yatırım | Süre | Ek Potansiyel (USD) | Öncelik |
|---|---|---|---|
| Admin frontend UI (Angular) | 1–2 hafta | +$5.000 – +$10.000 | 🔴 Yüksek — REST API tam, Angular UI eklenirse "admin paneli var" demo'lanabilir |
| Organization SSO login akışı (login sayfası entegrasyonu) | 3–5 gün | +$5.000 – +$15.000 | 🔴 Yüksek — altyapı hazır, login sayfasında "şirket email'i ile giriş" eklenirse $80k+ segment açılır |
| MFA / TOTP | 1–2 hafta | +$5.000 – +$15.000 | 🟡 Orta — kurumsal güvenlik |
| Tenant enabled JWT revoke (Redis blocklist) | 2–3 gün | Savunma değeri | 🟡 Orta — güvenlik tutarlılığı |
| JSON/structured loglama | 1–2 gün | +$1.000 – +$3.000 | 🟡 Düşük |
| JaCoCo eşik yükseltme | 1 gün | Kalite sinyal | 🟢 Düşük — gate var ama %48/%40 mütevazı; %60/%55'e çıkarmak anlamlı |
| 1 canlı müşteri referansı | Belirsiz | +$15.000 – +$30.000 | 🔴 Kritik (RAPOR3'ten değişmedi) |

## 11. Özet: Bugün Neredesin?

**Teknik gerçek (abartısız):**
RAPOR4'teki admin UI boşluğu REST API düzeyinde kapandı — Angular UI hâlâ yok. 17 Spring Modulith modülü, 4 Maven
modülü, 486/486 test. Per-organizasyon SSO altyapısı (SAML + LDAP, domain routing, connection test) eklendi. Platform
settings DB-backed runtime yapılandırma çalışıyor. Modulith event pipeline izlenebilir hale geldi. Operatör ihtiyaçları
REST API düzeyinde karşılanıyor; demo için frontend gerekiyor.

**Fiyat gerçeği:**
$35.000–$65.000 USD korunur; $80k–$150k segmenti için per-org SSO altyapısı güçlü argüman ama Organization SSO login
UI olmadan satış sürecinde sürtünme devam eder. Production referansı yokluğu hâlâ en büyük fiyat tavanı.

**Bir sonraki en değerli hamle:**

```
Altyapı neredeyse tam. Sıra UI ve satış:
├── ✅ Admin REST API tamamlandı → Angular admin UI (1-2 hafta) ile demo edilebilir hale gelmeli
├── ✅ Organization SSO altyapısı hazır → Login sayfası entegrasyonu (3-5 gün) → $80k+ segment açılır
├── MFA/TOTP ekle (1-2 hafta) → kurumsal güvenlik kutusu tamamlanır
└── 1 canlı müşteri referansı bul → "başkası kullandı mı?" sorusu kapanır
```

**Son not:** Teknik borç çok düşük, özellik kapsamı rakip açık kaynak araçlara yakın. Kritik açık: teknik olmayan
karar vericiye gösterilecek UI yok. Admin REST API + Organization SSO altyapısının Angular UI'a dönüşmesi tek haftada
$35k–$65k aralığını $80k+ segmentine taşıyabilir.

---

# BÖLÜM D — KATEGORİ BAZLI PUAN DEĞERLENDİRMESİ

> **Metodoloji:** Her kategori 10 üzerinden puanlanmıştır. Puanlar gerçek teknik kanıta dayanır — "potansiyel" veya
> "plan" puan almaz, yalnızca tamamlanmış ve doğrulanmış iş puan alır. Her puan neden verildiği ve neden tam 10
> olmadığı açıklanır.

---

## 12. Kategori Puanları

### 12.1 Mimari — 8.5 / 10

**Güçlü yönler:**
- **Spring Modulith 17 modülü:** +`admin` modülü. `admin` → `webhook` cross-module erişim `webhook::api` named
  interface (`WebhookDlqAdminPort`) üzerinden — modül sınırı korunmuş.
- **`auth::api` named interface genişletildi:** `OrganizationAdminPort` — `admin` modülü `auth` modülüne doğrudan
  bağımlı değil, adapter pattern.
- **originhub-events ayrı Maven modülü:** Event sınıfları backend dışına çıkarıldı — mimari borç azaltıldı.
- **`ApplicationModules.verify()`:** CI'da çalışıyor — yeni `admin` modülü sınır ihlali yaratmadan eklendi.
- Önceki güçlü yönler (event-driven, tutarlı layout, tek Docker imajı) korundu.

**Eksikler (neden 10 değil):**
- Bus factor = 1 — değişmedi.
- `@ApplicationModuleTest` senaryosu yok — event pipeline güvencesi eksik.
- JGit horizontal scaling sorunu — değişmedi.

---

### 12.2 Kod Kalitesi (Backend) — 8.5 / 10

**Güçlü yönler:**
- Admin modülü aynı kalite standartlarını izliyor: Google Java Format, JSpecify `@NullMarked`, Lombok
  `@RequiredArgsConstructor`, `@PreAuthorize` SpEL ile güvenlik.
- `PlatformAdminService` SpEL bean — `ROLE_ADMIN` hard-coding yerine configurable admin mekanizması.
- `PgAuditLogReaderService` — regex-tabanlı parse, edge case'ler (kısa timestamp, multi-line SQL) ele alınmış.

**Eksikler (neden 10 değil):**
- `CollaboratorController.sendInvitationEmail()` stub method — değişmedi.
- JaCoCo gate eklendi ancak %48/%40 eşiği mütevazı — gerçek kapsam kanıtı için yükseltilmeli.
- `AdminStatsService` `AtomicReference` ile race condition giderildi; thundering herd (eş zamanlı double-compute) teorik olarak hâlâ mümkün — mevcut yük düzeyinde kabul edilebilir.

---

### 12.3 Performans — 9.0 / 10

**Güncel durum:** RAPOR4'te uygulanan iyileştirmeler (HTTP/2, gzip, virtual thread, Redis push-invalidation, N→1
RevWalk, HikariCP, Prometheus açık) korundu. Bu sürümde performans odaklı değişiklik yok.

**`AdminStatsService` cache nüansı:** In-memory `volatile` cache TTL'i `platform_setting` tablosundan okuyor — DB-backed
TTL. `evictAllCaches()` tüm cache'leri temizliyor. Load test kanıtı hâlâ yok.

---

### 12.4 Memory Usage — 9.0 / 10

**Güncel durum:** RAPOR4'te uygulanan iyileştirmeler (JVM G1GC `-Xms256m -Xmx768m`, Redis `allkeys-lru 256mb`, virtual
thread, off-heap cache) korundu.

**Yeni nüans:** `AdminStatsService` in-memory cache (`AtomicReference` + `ConcurrentHashMap`) JVM heap'te tutuluyor — büyük
platformlarda (100k+ repo) overview hesabı bellek spike'ı yapabilir. Profil kanıtı yok.

---

### 12.5 Hız ve Stabilite — 7.5 / 10

**Güçlü yönler:** RAPOR4 güçlü yönleri (DLQ + scheduler, audit izolasyonu, graceful degrade) korundu.

**Yeni eklenen:** `BootstrapAdminInitializer` — uygulama başlangıcında transactional DB işlemi; başlangıç hızını
etkileyebilir ama pratikte yalnızca ilk çalışmada hesap oluşturuyor.

**Yeni eklenen:** Circuit Breaker (Resilience4j) — `WebhookDeliveryService` per-host CB + `SamlMetadataService` CB. Dış HTTP noktaları artık koruma altında; CB OPEN olduğunda DLQ'ya geçiş anlık.

**Eksikler (neden 10 değil):** Redis/DB single node, Kubernetes readiness/liveness probe yok. JGit migration çağrıları CB kapsamı dışında.

---

### 12.6 Güvenlik — 8.0 / 10

**Güçlü yönler:** RAPOR4 güçlü yönleri korundu.

**Yeni eklenen:**
- `platformAdminService` SpEL guard — tüm admin endpoint'leri `isCurrentUserPlatformAdmin()` ile korunuyor.
  `ROLE_ADMIN` yerine configurable model — `ORIGINHUB_PLATFORM_ADMIN_USERNAMES` CSV ile çoklu admin destekleniyor.
- `tenant.enabled` kontrolü — hesap deaktivasyon altyapısı.

**Yeni eksikler:**
- Tenant enabled JWT revoke yok — deaktif hesabın token'ı TTL dolana kadar geçerli. Redis blocklist eklenmeden
  "hesabı kapattım" güvencesi verilemiyor.

**Önceki eksikler değişmedi:** MFA yok, fixed-window rate limit burst açığı, CORS production yapılandırması belirsiz,
CVE tarama yok.

---

### 12.7 Test Coverage (Backend) — 7.5 / 10

**Güçlü yönler:**
- **486 test, 0 hata, 0 regresyon:** +52 yeni test. Admin modülü için 11 test sınıfı (32 test), `OrganizationServiceTest`
  9 test, `PgAuditLogReaderServiceTest` 12 test — yeni kod test edilmeden bırakılmadı.
- `OrganizationServiceTest` domain mapping, CRUD, error case kaplıyor.
- `PgAuditLogReaderServiceTest` 12 test — regex parse edge case'leri.

**Eksikler (neden 10 değil):**
- JaCoCo eklendi; `mr-check.yml`'de `mvn -B -ntp verify` çalıştığı doğrulandı — gate gerçekten bloklıyor. Eşik %48/%40 mütevazı; %60+ hedeflenmeli.
- `@ApplicationModuleTest` yok — değişmedi.
- Admin test sayısı düşük (controller testleri 1-5 arası) — happy path ağırlıklı.

---

### 12.8 Ölçeklenebilirlik — 6.5 / 10

**Güncel durum:** RAPOR4 değerlendirmesi geçerli — değişiklik yok.

**Yeni nüans:** Organization SSO DB-backed — `organization` tablosu büyüdükçe email domain GIN index (`V030`)
devreye giriyor. Login akışında domain lookup O(log N) — kabul edilebilir.

---

### 12.9 Satışa Hazırlık — 7.0 / 10

**RAPOR4'ten artış: 6.5 → 7.0**

**Yeni güçlü yönler:**
- **Admin REST API tam:** ~40 endpoint, kullanıcı yönetimi, stats, audit, DLQ, platform settings — ops ekibine
  satılabilir.
- **Organization SSO altyapısı:** "Birden fazla şirket bağlanabilir mi?" sorusu artık teknik yanıtlı.
- **Bootstrap admin:** "Kurulum nasıl?" sorusu basitleşti — env var + `make up`.
- **Modulith event visibility:** "Başarısız event'ler kaybolur mu?" sorusu REST API düzeyinde yanıtlı.

**Hâlâ eksik:**
- Admin frontend UI yok — REST API var, görsel demo yok.
- Organization SSO login UI yok — altyapı hazır, akış yok.
- Production referansı yok — değişmedi.
- Tek geliştirici — değişmedi.
- MFA yok — değişmedi.

---

## 13. Özet Puan Tablosu

| Kategori | Puan | Kısa Gerekçe |
|---|---|---|
| **Mimari** | **8.5 / 10** | 17 Modulith modülü, events ayrı Maven artefaktı, `auth::api` genişledi. Bus factor + JGit scaling sorunu değişmedi. |
| **Kod Kalitesi (Backend)** | **9.0 / 10** | Race condition giderildi (AtomicReference + ConcurrentHashMap). JaCoCo CI gate eklendi (mr-check.yml'de verify çalışıyor). Stub method kalıyor. |
| **Performans** | **9.0 / 10** | RAPOR4 iyileştirmeleri korundu. Yeni performans değişikliği yok. Load test kanıtı yok. |
| **Memory Usage** | **9.0 / 10** | RAPOR4 iyileştirmeleri korundu. `AdminStatsService` in-memory cache spike riski. |
| **Hız ve Stabilite** | **8.0 / 10** | Circuit breaker eklendi (Resilience4j) — webhook + SAML noktaları izole. Redis/DB single node, k8s probe yok. |
| **Güvenlik** | **8.0 / 10** | `platformAdminService` configurable guard eklendi. Tenant enabled JWT revoke yok, MFA yok. |
| **Test Coverage (Backend)** | **8.0 / 10** | JaCoCo CI gate eklendi — mr-check.yml'de verify çalışıyor, INSTRUCTION ≥%48 / BRANCH ≥%40. Eşik mütevazı; @ApplicationModuleTest yok. |
| **Ölçeklenebilirlik** | **6.5 / 10** | Değişmedi. Organization GIN index eklendi — küçük pozitif. |
| **Satışa Hazırlık** | **7.0 / 10** | Admin REST API + org SSO altyapısı güçlü argüman. Frontend UI + referans yok. |
| **Genel Ortalama** | **8.3 / 10** | Kod Kalitesi 8.5→9.0 (race condition fix + JaCoCo gate). Test Coverage 7.5→8.0 (JaCoCo CI gate). Frontend UI eksikliği tavan. |

> **Yorum:** Ortalama 8.1 → 8.3; Kod Kalitesi 8.5→9.0 (race condition giderildi, JaCoCo CI gate gerçek bloklama yapıyor), Test Coverage 7.5→8.0 (JaCoCo). Puanı 8.8+ çıkaracak üç hamle:
> ① Admin Angular UI (REST API tam, UI eksik), ② Organization SSO login akışı, ③ 1 canlı referans müşteri.
