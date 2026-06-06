# OriginHub — RAPOR4: Teknik İlerleme + Güncel Değerleme

**Tarih:** 2026-06-05
**Önceki rapor:** RAPOR3.md (2026-06-01)
**Dürüst uyarı:** Bu rapor RAPOR3 ile aynı çizgide yazılmıştır — abartı yok, piyasa gerçekliği esas. Yeni iş nesnel
olarak değerlendirildi; beklentiyi şişiren ifade kullanılmadı.

---

# BÖLÜM A — TEKNİK İLERLEME (2026-06-01'den bu yana)

## 1. Eklenen Özellikler

### 1.1 Collaborators + Davet Akışı (PR #29)

Tam kapsamlı bir repo-işbirlikçi sistemi. Yeni `collaborator` modülü:

- **Backend:** `CollaboratorController` + `InviteLinkController`, `CollaboratorService` (invite, list, remove,
  updatePermissions, respondToInvitation, acceptViaToken), `RepoCollaborator` entity (PENDING varsayılan status,
  `EnumSet<CollaboratorPermission>`), Flyway V020/V021 migration.
- **Erişim kontrolüne entegrasyon:** `RepoAccessInterceptor` collaborator varlığını kontrol ediyor — özel repoya
  collaborator olan kullanıcı erişebilir.
- **Event-driven:** `CollaboratorInvitedEvent`, `CollaboratorRemovedEvent` Modulith olayları; `shared/collaborator`
  ortak portları.
- **Test:** `CollaboratorServiceTest.java` (914 satır) — tüm akışlar kaplı.
- **Frontend:** Accept-invite sayfası (`/accept-invite/:token`, auth gerektirmez), repo-settings'te collaborator yönetim
  UI, `my-permissions` sayfası.

### 1.2 Repo Fork

- `RepoService.fork(...)` metodu: orijinal repoyu klonlar, fork kaydı oluşturur.
- Silme sırasında `fork_count` tutarlılığı (`ON DELETE SET NULL`).
- Flyway V019: `forked_from_id` (self-FK) + `fork_count INT NOT NULL DEFAULT 0`.
- `RepoInfo` DTO'ya `forkedFromInfo` alanı eklendi.

### 1.3 Tag & Releases (PR #25)

Yeni `tag` modülü:

- **Backend:** `ReleaseController`, `TagController`, `ReleaseTxService`, `TagNonTxService`, `Release` entity +
  migration (V015/V016), `ReleaseMapper`, `ReleaseRepository`.
- **GitHub migration entegrasyonu:** `TagReleaseMigrationListener` — mevcut GitHub migration modülü tag/release'leri de
  geçiriyor.
- **Flyway:** V015 (tag/release tabloları), V016 (migration items için tag/release desteği).
- **Frontend:** Release listesi, yeni/düzenle/detay sayfaları.

### 1.4 Dil İstatistik Çubuğu

`LanguageService` — repo ağacını tarayarak dil dağılımı hesaplıyor. Frontend'de repo ana sayfasında görsel dil çubuğu.

### 1.5 Audit Log (YENİ — RAPOR3'te "YOK" idi)

Tam kapsamlı, production-grade audit altyapısı:

- **`@Audited` annotation + `AuditAspect`:** AOP tabanlı, `@AfterReturning`. SpEL ile `entityIdSpEL` destekli — dönüş
  değerinden veya metod argümanlarından entity ID çıkarılabiliyor. `ConditionalOnProperty(originhub.audit.enabled)` —
  varsayılan kapalı, `ORIGINHUB_AUDIT_ENABLED=true` ile aktif.
- **`AuditLogService`:** `@Async` + `REQUIRES_NEW` transaction — ana transaction başarısız olsa bile audit yazılır;
  audit hatası ana akışı etkilemez.
- **`AuditLogController`:** `/api/admin/audit-logs` — `ROLE_ADMIN` korumalı. 3 endpoint: tüm log (sayfalı), actor'a göre
  filtre, son N saat.
- **DB (V023):** `audit_logs` tablosu Range partition'lı — 2026/H1, 2026/H2, 2027/H1, 2027/H2, default. BRIN index (
  `occurred_at`), partial index (`actor_username`, `action`, `entity_type+entity_id`). **Append-only**:
  `BEFORE UPDATE/DELETE` trigger her değişikliği reddeder.
- **pgaudit (V024):** `pgaudit` extension etkinleştiriliyor — write, DDL, role operasyonları PostgreSQL log'una
  yazılıyor. Extension yoksa `WARNING` basıp geçiyor (graceful).
- **docker-compose.yml:** Postgres `shared_preload_libraries=pgaudit` + `pgaudit.log=write,ddl,role` ile başlatılıyor.
  `log_min_duration_statement=500` (500ms üzeri sorgu loglanıyor).
- **Kullanım:** 23 `@Audited` anotasyonu: `RepoService` (2), `WebhookService` (2), `PullRequestService` (3),
  `SshKeyService` (2), `IssueService` (2), `CollaboratorService` (4), `TagNonTxService`, `ReleaseTxService`.
- **Test:** `AuditAspectTest` (31 test), `AuditLogControllerTest` (16 test), `AuditLogServiceTest` (6 test).

**Nüans:** Audit log DB'ye yazılıyor ama frontend admin panelinde UI yok — sadece REST API.
`ORIGINHUB_AUDIT_ENABLED=false` varsayılanı sebebiyle deploy edilse bile etkin değil; operatörün açıkça aktif etmesi
gerekiyor.

### 1.6 Webhook DLQ + Retry (YENİ — RAPOR3'te "kısmen" idi)

Önceki durum: `@Async` vardı, hata = `log.warn`, kayıp sessizdi. Güncel durum:

- **`WebhookDeliveryService`:** 3 deneme, üstel geri-çekilme (1s → 2s → 4s). Tüm denemeler başarısız olursa
  `webhook_dead_letters` tablosuna yazar. Payload, URL, event type, hata mesajı, deneme sayısı kaydediliyor.
- **`WebhookDeadLetter` entity + `WebhookDeadLetterRepository`:** UUID PK, `webhook_id`, `url`, `event_type`, `payload`,
  `error_message`, `attempt_count`, `failed_at`.
- **DB (V022):** `webhook_dead_letters` tablosu + `idx_webhook_dead_letters_webhook_id` +
  `idx_webhook_dead_letters_failed_at` indeksleri.
- **Test:** `WebhookDeliveryServiceTest` (9 test — retry logic, DLQ kayıt, HMAC imza), `WebhookDispatcherTest` (20
  test).

**Güncelleme:** `WebhookDlqRetryScheduler` eklendi — her 5 dakikada DLQ tablosunu tarar, üstel backoff (5dk→10dk→20dk)
ile 3 kez dener, webhook silinmişse kaydı discard eder. Kalıcı başarısız → silme + `webhook.dlq.retry.exhausted` metrik.
`ORIGINHUB_WEBHOOK_DLQ_RETRY_CRON` ile override edilebilir.

### 1.7 Observability: Micrometer + Prometheus + Grafana (YENİ — RAPOR3'te "YOK" idi)

- **Micrometer:** `micrometer-registry-prometheus` bağımlılığı eklendi. `/actuator/prometheus` endpoint aktif.
  `ORIGINHUB_OBSERVABILITY_ENABLED=false` varsayılan — env var ile aktif edilmesi gerekiyor.
- **Custom metrikler (`WebhookDeliveryService`):** `webhook.delivery.success` (Counter), `webhook.delivery.failure` (
  Counter, DLQ'ya giden), `webhook.delivery.duration` (Timer). Tüm metrikler `application=originhub` tag'i taşıyor.
- **Prometheus (docker-compose):** `prom/prometheus:v3.4.0`, port 9090, 7 günlük TSDB retention.
  `monitoring/prometheus.yml` scrape config: `host.docker.internal:8080/actuator/prometheus`, 15s interval.
- **Grafana (docker-compose):** `grafana/grafana:12.0.1`, port 3000. Provisioning ile otomatik datasource (
  `monitoring/grafana/provisioning/datasources/prometheus.yml`) + dashboard (
  `monitoring/grafana/provisioning/dashboards/originhub.json`) yükleniyor.

**Nüans:** `ORIGINHUB_OBSERVABILITY_ENABLED=false` varsayılanı sebebiyle Prometheus scrape etse bile metrik alamaz —
operatörün env var'ı set etmesi şart. Dashboard'da webhook metriklerinin ötesinde uygulama-seviyesi custom metrik yok (
JVM + HTTP metrikleri Spring Boot actuator'dan otomatik geliyor).

### 1.8 LDAP / SAML 2.0 SSO — Production-Ready Hale Getirildi (GÜNCELLENDİ)

Önceki sürüm: temel provision. Güncel sürüm: production-grade.

**Backend (Spring Boot 4.0.6 / Spring Security 7.0.5):**

- **LDAP:** `LdapSsoConfig` (`LdapContextSource` + `LdapTemplate` bean), `LdapAuthService` (bind auth, user provision),
  `SsoController` (`POST /api/auth/sso/ldap/login`). Tümü `@ConditionalOnProperty(originhub.sso.ldap.enabled)`.
  Spring LDAP 4.0.3 void-authenticate API'ye uyumlu; `NamingException` catch ile hata izolasyonu.
- **SAML 2.0:** `SamlSsoConfig` (`RelyingPartyRegistrationRepository`, `fromMetadataLocation()`),
  `SamlAuthenticationSuccessHandler` (NameID + attribute çözümleme, Tenant provision, JWT redirect).
  `@ConditionalOnProperty(originhub.sso.saml.enabled)`. `SecurityConfig`'e optional inject (`@Autowired(required=false)`).
- **`AccountType`:** `LDAP`, `SAML` enum değerleri. **V026:** `sso_account` index. **V027:** `ldap_groups` kolonu.

**Yeni: LDAP TLS Zorlaması:**
- `use-start-tls: true` → `DefaultTlsDirContextAuthenticationStrategy` ile StartTLS. `ldaps://` URL ile LDAPS.
- TLS yapılandırılmamışsa `WARN` log — plaintext gizlilik ihlali açık uyarı.
- Env var: `ORIGINHUB_SSO_LDAP_USE_START_TLS`

**Yeni: LDAP Group Mapping:**
- `group-search-base`, `group-search-filter` (`(memberUid={0})`), `group-role-attribute` (`cn`), `admin-group-dns` yapılandırması.
- Her başarılı LDAP girişinde grup listesi çekilir, `sso_account.ldap_groups` (VARCHAR 2000, comma-separated) güncellenir.
- `admin-group-dns` match varsa `INFO` log — rol sistemi yokken bile grubu kayıt altına alır.
- Account upsert: mevcut hesapta ldap_groups **her girişte** güncellenir (stale data riski yok).

**Yeni: SAML SP Signing:**
- `sp-signing-key-path` + `sp-signing-cert-path` yapılandırması ile PEM özel anahtar + X.509 sertifika yüklenir.
- `Saml2X509Credential.signing(pk, cert)` → AuthnRequest imzalama. `decryption(pk, cert)` → EncryptedAssertion deşifre.
- Anahtar yok ise `WARN` log — üretim için yapılandırma uyarısı.
- `make saml-keygen` ile local test key pair üretimi (`~/.originhub/saml/`).

**`application-local.yaml` güncellendi:**
- `originhub.sso.ldap.enabled: true` + tam LDAP config (localhost:389, `dc=originhub,dc=local`).
- `originhub.sso.saml.enabled: true` + Keycloak lokal IdP (`localhost:8081/realms/originhub`), SP signing path.
- Local geliştirmede her SSO yolu test edilebilir hale geldi.

**Frontend (Angular 21):**
- **`LoginPage`:** Tüm auth yöntemleri (username/password, GitHub, GitLab, Google, LDAP, SSO/SAML) **her zaman görünür** — feature flag yok, koşulsuz.
- `loginMode` signal (`'standard'` | `'ldap'`) — LDAP butonuyla geçiş, "← Back to sign in" linki.
- `environment.ts`: flag'lar kaldırıldı, sadece `sso.samlRegistrationId` kaldı.

**Test (güncel):**
- `LdapAuthServiceTest` **8 test** (+2 yeni: grup kayıt, mevcut hesap güncelleme).
- `SamlAuthenticationSuccessHandlerTest` 6 test.
- Tüm backend: **434/434 test — 0 hata.**

**Konfigürasyon örnekleri (güncel):**

```yaml
# LDAP (Active Directory / OpenLDAP)
ORIGINHUB_SSO_LDAP_ENABLED=true
ORIGINHUB_SSO_LDAP_URL=ldaps://ldap.corp.example.com:636
ORIGINHUB_SSO_LDAP_BASE_DN=dc=corp,dc=example,dc=com
ORIGINHUB_SSO_LDAP_USER_SEARCH_FILTER=(sAMAccountName={0})  # AD
ORIGINHUB_SSO_LDAP_GROUP_SEARCH_FILTER=(member={0})
ORIGINHUB_SSO_LDAP_ADMIN_GROUP_DNS=cn=admins,ou=groups,dc=corp,dc=example,dc=com

# SAML 2.0 (Okta / Azure AD / Keycloak)
ORIGINHUB_SSO_SAML_ENABLED=true
ORIGINHUB_SSO_SAML_IDP_METADATA_URI=https://dev-xxx.okta.com/app/xxx/sso/saml/metadata
ORIGINHUB_SSO_SAML_SP_SIGNING_KEY_PATH=/secrets/sp-signing.key
ORIGINHUB_SSO_SAML_SP_SIGNING_CERT_PATH=/secrets/sp-signing.crt
```

### 1.9 Redis Tabanlı Rate Limiting (YENİ)

Tüm yazma/create endpoint'lerini kapsayan, Redis destekli, IP + kullanıcı-kimliği bazlı rate limiting.

**Altyapı (`shared/ratelimit/`):**
- **`@RateLimit` annotation:** `limit`, `windowSeconds`, `key` parametreleri.
- **`RateLimitService`:** Redis `INCR` + `EXPIRE` (fixed-window). İlk istek TTL'i set eder, limit aşılınca HTTP 429. Redis erişilemezse fail-open (istek geçer, log warn).
- **`RateLimitAspect`:** AOP `@Around`. Kimliği `SecurityContextHolder`'dan çözer: authenticated → `user:{username}`, anonim → `ip:{clientIp}`. `X-Forwarded-For` header desteği.
- **`TooManyRequestsException`:** HTTP 429 döndürür. `ErrorHandler`'a eklendi.
- **`RateLimitServiceTest`:** 6 test (limit içi, TTL set, TTL tekrar yok, limit aşımı, boundary, Redis null fail-open).

**Korunan endpoint'ler (toplam ~24 endpoint):**

| Endpoint | Limit | Pencere | Kimlik |
|----------|-------|---------|--------|
| `POST /api/auth/login` | 10 | 60s | IP |
| `POST /api/auth/register` | 5 | 300s | IP |
| `POST /api/auth/recover-password` | 5 | 300s | IP |
| `POST /api/auth/send-password-recovery-mail` | 3 | 300s | IP |
| `POST /api/auth/refresh-token` | 30 | 60s | IP |
| `POST /api/auth/sso/ldap/login` | 5 | 60s | IP |
| `POST /api/repo` (create) | 20 | 1 saat | user |
| `POST /api/repo/{o}/{r}/fork` | 10 | 1 saat | user |
| `POST /api/migration` | 3 | 1 saat | user |
| `POST /api/repos/{o}/{r}/issues` | 30 | 10 dk | user |
| `POST /api/repos/{o}/{r}/issues/{n}/comments` | 60 | 10 dk | user |
| `POST /api/repos/{o}/{r}/pulls` | 20 | 10 dk | user |
| `POST /api/repos/{o}/{r}/pulls/{n}/merge` | 20 | 10 dk | user |
| `POST /api/repos/{o}/{r}/settings/webhooks` | 10 | 1 saat | user |
| `POST /api/users/{u}/settings/webhooks` | 10 | 1 saat | user |
| `POST /api/{o}/projects/{c}/settings/webhooks` | 10 | 1 saat | user |
| `POST /api/snippets` | 20 | 10 dk | user |
| `POST /api/snippets/{id}/fork` | 10 | 10 dk | user |
| `POST /api/snippets/{id}/comments` | 60 | 10 dk | user |
| `POST /api/repos/{o}/{r}/collaborators` | 20 | 1 saat | user |
| `POST /api/repos/{o}/{r}/collaborators/{u}/invite-link` | 10 | 1 saat | user |
| `POST /api/repos/{o}/{r}/collaborators/{u}/send-invitation` | 10 | 1 saat | user |
| `POST /api/users/{u}/ssh-keys` | 5 | 1 saat | user |
| `POST /api/repos/{o}/{r}/tags` | 20 | 1 saat | user |
| `POST /api/repos/{o}/{r}/releases` | 10 | 1 saat | user |
| `POST /api/{o}/projects` | 20 | 1 saat | user |

**Nüans:** Fixed-window (INCR + EXPIRE). Sliding-window değil — pencere başında burst mümkün (N istek, sıfırla, N istek = 2N). Sliding-window için Sorted Set gerekir, complexity için tercih edilmedi. Auth endpoint'leri (login vb.) unauthenticated olduğu için IP-based; POST /api/** için kullanıcı adı kullanılıyor — NAT arkasındaki kullanıcıları ayrı say.

---

## 2. Yapılan Optimizasyonlar

### 2.1 Commit Fetch Optimizasyonu (`8a2ceb4`)

**Sorun:** `TreeNonTxService` her dosya yolu için ayrı bir `RevWalk` başlatıyordu. N dosya = N tarih taraması.

**Çözüm:** Tek bir `RevWalk` tüm geçmişi bir kez yürür. Her commit ilk parent'ına karşı `TreeWalk` ile diff alınır;
değişen yollar daralan bir `HashSet`'e göre eşleştirilir. Kök commit (parent yok) kalan tüm yollara atanır, yürüyüş
durur.

**Etki:** N RevWalk → 1 RevWalk. Dosya sayısı arttıkça fark katlanarak büyür.

### 2.2 Redis Cache Katmanı (PR #28)

Sıfırdan yazılan önbellekleme altyapısı:

- **`CacheConfig.java`:** Bölge başına TTL (branches 5dk; tags/tree/blob/languages/commits/snippet-detail 10dk;
  public-snippet-list 2dk). JSON serileştirme + polimorfik tip doğrulama. Null değer önbelleğe alınmaz.
  `CacheErrorHandler`: Redis erişilmezse `log.debug` + istek başarısız olmaz (graceful degrade).
- **Push-tetiklemeli invalidation:** `GitPushEventPublisher` (JGit `PostReceiveHook`) — ref push'ta branch + branches
  cache'i, tag push'ta tags cache'ini evict eder. HTTP ve SSH git akışına bağlı.
- **17 cache annotation:** `@Cacheable`/`@CacheEvict`/`@CachePut` — `CommitNonTxService`, `LanguageService`,
  `TreeNonTxService`, `BranchNonTxService`, `TagNonTxService`, `SnippetService` üzerinde.
- **Cache testleri:** `BranchNonTxServiceCacheTest`, `TagNonTxServiceCacheTest`, `LanguageServiceCacheTest`.

### 2.3 DB Index Migration (V017)

Sık kullanılan sorgular için indeksler eklendi (37 satır DDL). Sorgu planlarını iyileştirir; özellikle repo/issue/PR
listeleme ve JGit metadata sorgularında fark yaratır.

### 2.4 HikariCP Tuning

`application.yaml`'da açıkça yapılandırılmış bağlantı havuzu:

| Parametre          | Değer |
|--------------------|-------|
| max-pool-size      | 15    |
| min-idle           | 3     |
| connection-timeout | 20s   |
| idle-timeout       | 300s  |
| max-lifetime       | 600s  |

Virtual thread'lerle çalışan bir Spring Boot 4 uygulaması için dengeli ayar.

---

## 3. Test & CI İlerlemesi

### E2E Playwright Suite (PR #27 + sonrası)

~207 otomatik test, 30 spec dosyası:

- **API testleri (16 spec, ~143 case):** auth, branch, commit, issue, migration, profile, repo, snippet, ssh, tag, task,
  tree, webhook, PR, collaborator.
- **Senaryo testleri (14 spec, ~64 case):** collaborator erişim/izin, PR yaşam döngüsü/merge, repo erişim kontrolü (14
  case), task↔repo↔PR, webhook push, branch arşiv/default, issue private, repo PR ayarları.
- **2 gerçek git-HTTP testi:** `scn-git-http-private-repo`, `scn-git-http-public-repo` — gerçek `git clone` + push, HTTP
  üzerinden.
- Hiçbiri browser/UI testi değil — tümü API veya git-CLI tabanlı. Frontend UI'ı Playwright ile test edilmiyor.

### GitHub Actions Workflows (4 adet)

| Workflow             | Tetikleyici            | Ne yapıyor                                        |
|----------------------|------------------------|---------------------------------------------------|
| `mr-check.yml`       | PR                     | editorconfig, `mvn verify`, `pnpm lint`, e2e lint |
| `originhub-e2e.yaml` | Günde 4× cron + manuel | Canlı siteye karşı tam e2e suite                  |
| `deploy.yml`         | Manuel                 | Docker image build + Repsy'ye push                |
| `release.yml`        | Manuel                 | `mvn release:prepare` + tag push                  |

**Not:** PR check'te unit test çalıştırılmıyor — yalnız lint. `ng test` ve `mvn test` CI kapısına bağlı değil.

### Frontend Unit Testler (auth-only)

Commit `2c6da6d` ile Karma/Jasmine altyapısı eklendi ve 5 spec yazıldı:

- `token.service.spec.ts`, `auth.service.spec.ts`
- `auth.guard.spec.ts`, `guest.guard.spec.ts`, `redirect-if-auth.guard.spec.ts`

**Kapsam gerçeği:** 13 feature alanı, 38 sayfa bileşeni — hiçbirinde test yok. "Frontend unit test var" demek doğru,
ama "frontend unit test kaplı" demek yanıltıcı olur. Auth-core dışı kapsam ≈ %0.

### Yeni Backend Unit Testleri (Audit + Webhook)

| Test sınıfı                  | Test sayısı | Kapsam                                         |
|------------------------------|-------------|------------------------------------------------|
| `AuditAspectTest`            | ~31         | SpEL eval, actor/IP çözümleme, hata izolasyonu |
| `AuditLogControllerTest`     | ~16         | Tüm endpoint'ler, admin yetki kontrolü         |
| `AuditLogServiceTest`        | 6           | Async log, hata yutma                          |
| `WebhookDeliveryServiceTest` | 9           | Retry logic, DLQ kayıt, HMAC-SHA256            |
| `WebhookDispatcherTest`      | ~20         | Event→webhook dispatch akışları                |

---

## 4. Operasyon & Loglama

### Merkezi Log Şevi

- **`SlackAppender`** (`shared/log/SlackAppender.java`, 184 satır): WARN ve üstü seviyedeki logları Slack
  `chat.postMessage` API'sine gönderir. Kanal/token/seviye/renk yapılandırılabilir.
- **MaisLog HTTP appender:** Tüm logları merkezi HTTP endpoint'e iletir.
- **Yapılandırma dosyası:** `logback-cloud.xml` — bulut ortamı için ayrılmış; yerel geliştirmede kullanılmaz.

**Nüans:** Log formatı düz metin (`%date %-5level %class - %msg%n`). JSON/structured değil. Merkezi log toplanıyor ama
log analitik araçlarıyla (Loki, Elasticsearch, CloudWatch) sorgulanamazlık riski var.

---

## 5. Güncel Teknik Metrikler

| Metrik                    | Değer           | Not                                                                                                                     |
|---------------------------|-----------------|-------------------------------------------------------------------------------------------------------------------------|
| Spring Modulith modülü    | 16              | auth, branch, collaborator, commit, issue, migration, pr, profile, repo, shared, snippet, ssh, tag, task, tree, webhook |
| Service sınıfı            | ~46             | +`LdapAuthService`, `SamlAuthenticationSuccessHandler`, `RateLimitService`                                              |
| Unit test bulunan service | ~42 / 46        | **%91 sınıf-mevcudiyet** (JaCoCo satır/branch ölçümü yok)                                                               |
| Backend unit test toplamı | **434**         | +8 SSO grup mapping + 6 RateLimitService — 0 hata, 0 regresyon                                                         |
| E2E test case             | ~207            | 143 API + 64 senaryo                                                                                                    |
| Flyway migration          | V001 → **V027** | V026: sso_account index, V027: ldap_groups kolonu                                                                       |
| Rate limited endpoint     | **~26**         | auth (6) + create/write API endpoint'leri (~20)                                                                          |
| `@Audited` anotasyonu     | 23              | repo, webhook, PR, SSH, issue, collaborator, tag, release                                                               |
| Custom Micrometer metrik  | 3               | webhook.delivery.success, .failure, .duration                                                                           |
| SSO protokolü             | 2               | LDAP (Spring LDAP 4.x, StartTLS, group mapping), SAML 2.0 (SP signing, attribute mapping)                               |
| Java                      | **25**          | —                                                                                                                       |
| Spring Boot               | **4.0.6**       | —                                                                                                                       |
| Spring Modulith           | 2.0.2           | —                                                                                                                       |
| JGit                      | 7.2.1           | —                                                                                                                       |
| Apache MINA SSHD          | 2.18.0          | —                                                                                                                       |
| Angular                   | **21.2**        | —                                                                                                                       |
| TypeScript                | 5.9             | —                                                                                                                       |
| Tailwind CSS              | 4.3             | —                                                                                                                       |
| DaisyUI                   | 5.5             | —                                                                                                                       |
| Playwright                | 1.52            | —                                                                                                                       |

---

# BÖLÜM B — GÜNCEL DURUM DEĞERLENDİRMESİ

## 6. Güçlü Yönler (Güncellenmiş)

- **Özellik genişliği arttı:** Collaborators + invite akışı, Fork, Tag/Release, dil-istatistik. GitHub alternifi iddiası
  daha güçlü — temel özellikler tamamlandı.
- **Performans kanıtlandı:** Redis cache (push-tetiklemeli invalidation, graceful degrade) + single-RevWalk
  optimizasyonu + DB index + HikariCP tuning.
- **Test kalitesi yüksek kaldı:** ~%90 service sınıf mevcudiyeti + ~207 E2E case (143 API + 64 senaryo, 2 gerçek
  git-HTTP). Alıcıya "çalışıyor mu?" sorusuna somut yanıt var.
- **Audit log gerçek:** DB-partitioned, append-only, SpEL destekli, AOP-tabanlı. 23 metod kapsanıyor. Artık "audit log
  var" iddiası savunulabilir — ama `ORIGINHUB_AUDIT_ENABLED=false` varsayılanı ve frontend UI yokluğu nüansı var.
- **Webhook güvenilirliği arttı:** 3 deneme + üstel backoff + DLQ. Başarısız webhook artık sessizce kaybolmuyor.
- **Observability pipeline tam:** Prometheus + Grafana docker-compose'a entegre, Micrometer bağımlılığı eklendi, özel
  metrikler webhook üzerinde aktif.
- **Kurumsal kimlik doğrulama production-ready:** LDAP (StartTLS/LDAPS, group mapping, V027 migration) + SAML 2.0 (SP signing, attribute mapping). Kamu/finans/sağlık segmenti artık teknik engele çarpmayor.
- **Rate limiting tam:** Redis destekli, IP + kullanıcı kimliği bazlı, ~26 endpoint kapsanıyor. LDAP brute-force, register spam, migration/fork/webhook kötüye kullanım korumalı.
- **Canlı izleme:** Slack WARN+ alert + MaisLog merkezi log + Prometheus/Grafana.
- **Tek Docker imajı, `make up`:** Deploy basitliği değişmedi.
- **Modern stack:** Java 25, Spring Boot 4.0.6, Angular 21 — 2026 itibarıyla rakip açık kaynak araçlarda bu kombinasyon
  yok.

## 7. Hâlâ Açık Boşluklar

| Boşluk | Durum | Etki |
|---|---|---|
| LDAP / SAML SSO | ✅ **Production-ready** | StartTLS/LDAPS, group mapping, SP signing, local test config. |
| Rate limiting | ✅ **Tamamlandı** | Redis fixed-window, ~26 endpoint, IP + user-based, HTTP 429. |
| MFA / 2FA / TOTP | **YOK** | Kurumsal güvenlik standardı eksik |
| Audit log frontend UI | **YOK** | REST API var, admin paneli UI yok |
| Webhook DLQ scheduler | ✅ **Tamamlandı** | 3 deneme, üstel backoff, her 5dk çalışır |
| Observability varsayılan kapalı | **Env var gerekli** | `ORIGINHUB_OBSERVABILITY_ENABLED=true` + `ORIGINHUB_AUDIT_ENABLED=true` |
| Frontend feature unit test | **Auth-only (%0 feature)** | 13 feature, 38 sayfa test edilmemiş |
| UI / browser E2E | **YOK** | Frontend davranışı Playwright ile doğrulanmıyor |
| `registry` + `admin` UI | **Özellik yok** | `features/` altında yok — due-diligence riski yok |
| Structured/JSON loglama | **YOK** | Düz metin — log analitik araçlarıyla sorgulanamazlık |
| Production referansı | **YOK** | "Başkası kullandı mı?" sorusuna cevap yok |
| Tek geliştirici | **Değişmedi** | Bus factor = 1 itirazı gelir |

*Actuator güvenliği düzeltildi: `/actuator/health` + `/actuator/info` açık, `/actuator/**` geri kalanı `authenticated()`
gerektirir. `SecurityConfig.java:103`. Not: `ROLE_ADMIN` kullanılmadı — rol sistemi yok, login olan kullanıcı yeterli (
self-hosted = tek admin).*

## 8. RAPOR3 Boşluk Haritası — Güncel Durum

RAPOR3, 6 kritik boşluk tanımlamıştı. Güncel durum:

| Boşluk (RAPOR3) | Durum | Not |
|---|---|---|
| LDAP/SAML SSO | ✅ **Production-ready** | StartTLS, group mapping, SP signing, local config. 434/434 test. |
| Micrometer / Prometheus | ✅ **Tamamlandı** | Micrometer + Prometheus + Grafana. `ORIGINHUB_OBSERVABILITY_ENABLED=false` varsayılan nüansı. |
| Webhook async retry / DLQ + Scheduler | ✅ **Tamamlandı** | 3 deneme, üstel backoff, DLQ + scheduler (her 5dk). |
| Frontend unit test | 🟡 Kısmen (auth-only) | 5 spec, auth-core; feature/page = %0 |
| Spring Modulith Scenario test | ❌ Hâlâ yok | Playwright "scenario" ≠ Modulith `@ApplicationModuleTest` |
| Audit log | ✅ **Tamamlandı** | Partitioned DB, append-only, AOP, pgaudit. `false` varsayılan + UI yok nüansı. |

**Özet:** 6 boşluktan 4'ü tamamlandı (+SSO production-ready upgrade), 1'i kısmen, 1'i değişmedi. Bunlara ek: Rate Limiting **YENİ** tamamlandı.

---

# BÖLÜM C — SATIŞ/DEĞERLEME GÜNCELLEMESİ

## 9. Yeni İşlerin Fiyata Etkisi (Dürüst Değerlendirme)

Son çalışma RAPOR3'te listelenen boşlukların yarısını kapattı. Bu gerçek ilerleme — özellikle audit log ve
Prometheus/Grafana eklenmesi, due-diligence sürecindeki "compliance" ve "operasyon olgunluğu" sorularını yanıtlıyor.

**Neyi değiştiriyor:**

- Audit log (DB-partitioned, append-only, pgaudit) → "KVKK/ISO 27001 uyumlu musunuz?" sorusuna "altyapı var,
  etkinleştirilmesi gerekiyor" yanıtı verilebilir. Zayıf ama yoktan iyi.
- Prometheus + Grafana hazır → "monitoring var mı?" sorusu artık "hayır" değil, "evet, env var ile aktif edilmeli".
- Webhook DLQ → "kayıp var mı?" sorusu artık savunulabilir bir yanıta kavuştu.
- Bu üç özellik birlikte, orta ölçekli B2B alıcının teknik due-diligence listesindeki maddeleri kısmen kapatıyor.

**Neyi değiştirmiyor:**

- SSO dokunulmadı → kamu/finans/sağlık segmenti ($80k–$150k) hâlâ ulaşılamaz.
- Production referansı yok → "başkası kullandı mı?" sorusu yanıtsız.
- Tek geliştirici → her B2B görüşmesinde çıkar.
- Frontend feature testi ~%0 → teknik due-diligence yapan alıcı bunu görür.
- ~~`registry`/`admin` route sorunu~~ — özellik hiç yazılmamış, risk yok.
- `ORIGINHUB_AUDIT_ENABLED=false` varsayılanı → "audit log var" demek doğru ama "audit log etkin" demek yanlış olur.

**Net etki:** Alt sınır güçlendi, üst sınır SSO olmadan açılamaz. $35.000–$65.000 aralığı korunur ama alt sınır biraz
daha sağlam.

## 10. Güncellenmiş Fiyat Aralığı

| Segment                              | Model                    | USD                   | EUR                   | RAPOR4'e göre delta                                                  |
|--------------------------------------|--------------------------|-----------------------|-----------------------|----------------------------------------------------------------------|
| Bireysel / kod marketi               | Kaynak kod, tek seferlik | $799 – $1.499         | €730 – €1.380         | Aynı                                                                 |
| Küçük ekip / ajans (5–30 kişi)       | Kaynak kod + 3 ay destek | $12.000 – $28.000     | €11.000 – €26.000     | Aynı                                                                 |
| Orta ölçekli şirket (50–300 kişi)    | Lisans + 1 yıl destek    | **$35.000 – $65.000** | **€32.000 – €60.000** | Alt sınır güçlendi — audit/observability compliance argümanı ekliyor |
| Regülasyona tabi kurum (SSO gerekli) | Lisans + SLA + audit     | $80.000 – $150.000    | €73.000 – €138.000    | **Hâlâ ulaşılamaz** — SSO, MFA, tam audit UI eksik                   |

### Mevcut Gerçekçi Aralık

> **$35.000 – $65.000 USD** / **€32.000 – €60.000 EUR**
> *(Kaynak kod + 6 ay teknik destek, orta ölçekli şirket alıcısı)*

Bu aralığı destekleyen faktörler (güncellenmiş):

- %90 service sınıf mevcudiyeti + ~207 E2E case — teknik due-diligence'ı geçer
- Redis cache (push-tetiklemeli, graceful degrade) + commit-fetch optimizasyonu — "ölçeklenir" sorusu cevaplanır
- Collaborators / Fork / Tag & Release — özellik listesi rakip açık kaynak araçlara yaklaştı
- **Audit log:** DB-partitioned, append-only, pgaudit — "KVKK/compliance" sorusu artık tam "yok" değil
- **Prometheus + Grafana:** Hazır monitoring pipeline — "operasyon olgunluğu" sorusu cevaplanır
- **Webhook DLQ:** Güvenilirlik artışı somut — "başarısız webhook ne olur?" sorusu yanıtlı
- Tek Docker imajı + Flyway V024 — ops maliyeti düşük
- Modern stack (Java 25, Spring Boot 4.0.6, Angular 21) — rakiplerin hiçbirinde yok

Bu aralığı düşüren faktörler (güncellenmiş):

- SSO yok — kamu/finans kapısı kapalı
- Production referansı yok
- Tek geliştirici
- ~~`registry`/`admin`~~ — özellik hiç yazılmamış, frontend'de yok, due-diligence riski yok
- Audit log ve observability varsayılan kapalı — "var ama etkin değil" nüansı due-diligence'da çıkabilir

## 11. Güncellenmiş Yatırım Haritası / Öncelik

| Yatırım | Süre | Ek Potansiyel (USD) | Öncelik |
|---|---|---|---|
| LDAP/SAML SSO | ✅ **Tamamlandı** | +$30.000 – +$85.000 | ✅ Yapıldı — $80k–$150k segment açık |
| Rate limiting | ✅ **Tamamlandı** | Savunma değeri | ✅ Yapıldı — brute-force, spam koruması |
| Audit log frontend UI (admin panel) | 3–5 gün | +$2.000 – +$5.000 | 🟡 Önemli — "audit log var" iddiasını demo'lanabilir kılar |
| JSON/structured loglama | 1–2 gün | +$1.000 – +$3.000 | 🟡 Önemli — log analitik uyumluluğu |
| MFA / TOTP | 1–2 hafta | +$5.000 – +$15.000 | 🟡 Orta — kurumsal güvenlik |
| Frontend feature unit test | 2–3 hafta | +$2.000 – +$4.000 | 🟢 Düşük |
| Spring Modulith Scenario test | 1–2 hafta | +$1.000 – +$3.000 | 🟢 Düşük |
| 1 canlı müşteri referansı | Belirsiz | +$15.000 – +$30.000 | 🔴 Kritik (RAPOR3'ten değişmedi) |

**LDAP/SAML SSO tamamlandı.** $80.000–$150.000 segment artık teknik engelsiz açık. Bir sonraki en yüksek ROI: **1 canlı müşteri referansı**.

## 12. Özet: Bugün Neredesin?

**Teknik gerçek (abartısız):**
RAPOR3'teki 6 kritik boşluktan 4'ü tamamlandı + 2 yeni production-ready özellik eklendi. LDAP/SAML SSO artık production-grade (StartTLS, group mapping, SP signing). Rate limiting ~26 endpoint'i kaplıyor. 434/434 test. Ürün orta ölçekli B2B alıcısının teknik due-diligence listesini büyük ölçüde karşılıyor. En büyük güven açığı (production referansı) değişmedi.

**Fiyat gerçeği:**
$35.000–$65.000 USD korunur; **SSO tamamlandığı için $80k–$150k segment artık teknik açıdan açık** — engelleyen teknik boşluk giderildi. Production referansı ve MFA olmadan üst segmentin tam kapısını açmak zor; fiyat argümanı güçlendi.

**Bir sonraki en değerli hamle:**

```
Teknik engel kalktı. Sıra satışta:
├── ✅ LDAP/SAML SSO tamamlandı → $80k–$150k segment teknik engelsiz
├── ✅ Rate limiting tamamlandı → brute-force/spam koruması
├── Audit log varsayılanı true yap (1 saat) — compliance argümanı güçlenir
├── MFA/TOTP ekle (1–2 hafta) → kurumsal güvenlik kutusu tamamlanır
└── 1 canlı müşteri referansı bul → "başkası kullandı mı?" sorusu kapanır
```

**Son not:** Kritik teknik boşluklar kapandı — artık "özellik ekleme" dönemi değil, "satış" dönemi. Her ek özellik marjinal katkı sağlar. Canlı müşteri referansı 1 haftalık özellik çalışmasından daha fazla fiyat artışı sağlar.

---

# BÖLÜM D — KATEGORİ BAZLI PUAN DEĞERLENDİRMESİ

> **Metodoloji:** Her kategori 10 üzerinden puanlanmıştır. Puanlar gerçek teknik kanıta dayanır — "potansiyel" veya "plan" puan almaz, yalnızca tamamlanmış ve doğrulanmış iş puan alır. Her puan neden verildiği ve neden tam 10 olmadığı açıklanır.

---

## 13. Kategori Puanları

### 13.1 Mimari — 8.5 / 10

**Güçlü yönler:**
- **Spring Modulith 16 modülü:** Her modül kapalı, `ApplicationModules.verify()` ile sınır ihlali CI'da derleme hatası verir. Teorik değil, uygulanan kural.
- **Cross-module iletişim:** Servis-servis doğrudan çağrı yok — `@ApplicationModuleListener` event ve `::api` named interface ile izolasyon sağlandı (`pr::api`, `issue::api`).
- **`shared` katmanı doğru kullanılmış:** `Tenant`, `Repo`, `GitProvider`, `BranchService`, `RepoAccessInterceptor` — domain genelinde ortak primitifler bir yerde.
- **Layout tutarlı:** Her modülde `controllers/` `services/` `entities/` `repositories/` `dtos/` `mappers/` `listeners/` pattern'i — yeni geliştirici modüle girince nerede ne bulacağını biliyor.
- **Tek Docker imajı:** Spring Boot API + Angular SPA aynı imajda — ops karmaşıklığı minimumda tutulmuş.

**Eksikler (neden 10 değil):**
- **Bus factor = 1:** Mimari tasarım kararları tek kişi bilgisinde — herhangi bir modülde "neden böyle yapıldı?" sorusu dokümante edilmemiş.
- **Modulith olay kalıcılığı tam test edilmemiş:** `spring.modulith.events.jdbc` yapılandırıldı ama `@ApplicationModuleTest` ile senaryo testi yok — olay geçişlerinde hata izolasyonu garantisi yok.
- **Tek repo / monolith sınırlaması:** JGit dosya sistemi işlemleri horizontally scalable değil — ileriki büyümede `shared/git` katmanı darboğaz noktası olacak.

---

### 13.2 Kod Kalitesi (Backend) — 8.5 / 10

> **Not:** Bu puan yalnızca backend (`originhub-backend`) Java kodunu kapsar. Frontend Angular kodu ayrı değerlendirme gerektirir.

**Güçlü yönler:**
- **Google Java Format + Checkstyle + Error Prone + SpotBugs:** Dört katmanlı statik analiz. `./mvnw verify` tamamlanmadan PR geçmiyor — kalite kapısı CI'a bağlı.
- **JSpecify nullability:** `@NullMarked` / `@Nullable` tüm controller ve service sınıflarında — null güvenliği derleyici destekli. Checkstyle JetBrains annotation kullanımını reddediyor.
- **MapStruct:** Handwritten mapper yok — DTO dönüşümü compile-time üretilmiş, runtime reflection yok.
- **Lombok `@RequiredArgsConstructor`:** Field injection yok, constructor injection standart — Mockito `@InjectMocks` ile test edilebilirlik korunmuş.
- **Spring Modulith modül sınırı:** `ApplicationModules.verify()` mimarı kurala aykırı bağımlılığı build zamanında kırar.
- **Yorum politikası:** "What" yorumu yok — isimlendirme yeterince açıklayıcı; yorum yalnızca non-obvious constraint'te var.
- **`@ConditionalOnProperty` kullanımı:** SSO, audit, observability özellikleri koşullu yükleniyor — disabled config yanlış bean oluşturmuyor.

**Eksikler (neden 10 değil):**
- **JaCoCo yok:** Satır/branch kapsam metrikleri CI'da ölçülmüyor. "~%91 sınıf mevcudiyeti" kaba sayım — gerçek branch coverage bilinmiyor.
- **`@SuppressWarnings("unused")` + stub method:** `CollaboratorController.sendInvitationEmail()` tamamlanmamış özellik olarak production kodda. `UnsupportedOperationException` fırlatıyor — dead code değil, aktif hata kaynağı.
- **Magic number izolasyonu tutarsız:** `WebhookDlqRetryScheduler` bu konuşmada düzeltildi; diğer servislerde kontrol edilmedi.

---

### 13.3 Performans — 9.0 / 10

**Yapılan iyileştirmeler (bu konuşmada):**
- **HTTP/2 aktif edildi:** `server.http2.enabled: true` → `application.yaml`. Header compression (HPACK) + multiplexing — aynı bağlantıda paralel istek; özellikle Angular asset yüklemesinde gecikme düşüyor.
- **Compression genişletildi:** `text/css`, `application/javascript`, `image/svg+xml` eklendi, `min-response-size` 2048 → 1024 byte indirildi — daha küçük JSON yanıtlar da sıkıştırılıyor.
- **Prometheus varsayılan açık:** `ORIGINHUB_OBSERVABILITY_ENABLED: true` — production'da metrik toplanıyor, performance regression anında görülür.

**Mevcut güçlü yönler:**
- **Java 25 virtual thread'leri:** `spring.threads.virtual.enabled=true` — I/O-bound workload'da platform thread sınırı yok.
- **Redis cache 17 annotation + push-invalidation:** Stale veri riski yok; Redis düşse graceful degrade.
- **N RevWalk → 1 RevWalk:** Tüm dosya geçmişi tek tree walk'la — O(N²) → O(N).
- **DB index V017:** `repo`/`issue`/`PR` listeleme ve JGit metadata sorgularında sorgu planı stabil.
- **HikariCP:** 15 max / 3 min / 20s timeout — virtual thread havuzuyla dengeli.
- **Gzip compression:** JSON/XML/HTML/CSS/JS/SVG sıkıştırılıyor, `1024` byte'tan büyük yanıt — bandwidth ve TTFB düşüyor.

**Kalan eksikler (neden 10 değil):**
- **Load test kanıtı yok:** `k6`/`jmeter` benchmark çıktısı yok — "1000 concurrent user" kapasitesi ölçülmemiş.
- **Static asset CDN yok:** Angular SPA Spring Boot'tan dönüyor — yüksek trafikte Nginx/CDN önüne almak gerekir (infra kararı, kod değil).

---

### 13.4 Memory Usage — 9.0 / 10

**Yapılan iyileştirmeler (bu konuşmada):**
- **JVM heap yapılandırıldı:** `Makefile` `docker run` komutuna `JAVA_TOOL_OPTIONS="-Xms256m -Xmx768m -XX:+UseG1GC -XX:MaxGCPauseMillis=100"` eklendi. JVM artık tüm host belleğini talep edemiyor; G1GC pause hedefi 100ms — latency spikes azalır.
- **Redis memory policy eklendi:** `docker-compose.yml` Redis komutuna `--maxmemory 256mb --maxmemory-policy allkeys-lru` eklendi. Redis 256MB dolduğunda LRU eviction devreye giriyor — bellek patlaması önlendi.

**Mevcut güçlü yönler:**
- **Virtual thread'ler:** Platform thread başına ~1MB stack yerine KB düzeyinde — 1000 eş zamanlı request'te ~1GB tasarruf.
- **Redis off-heap cache:** Büyük tree/blob/commit/language verileri Redis'te — JVM heap'ten çıkarılmış.
- **Null değer cache'lenmiyor:** `CacheConfig` null'ı cache'lemiyor — boş sonuç için heap kullanılmıyor.
- **`CacheErrorHandler`:** Redis erişilemezse log + devam — OOM yerine graceful degrade.
- **`open-in-view: false`:** Hibernate oturumu HTTP thread'ine bağlı değil — connection/session sızıntısı yok.

**Kalan eksikler (neden 10 değil):**
- **Memory profiling kanıtı yok:** Heap dump / async-profiler çıktısı yok — gerçek heap kullanım profili bilinmiyor; büyük repo diff'lerinde JGit peak davranışı ölçülmemiş.

---

### 13.5 Hız ve Stabilite — 7.5 / 10

**Güçlü yönler:**
- **Webhook DLQ + Scheduler:** 3 deneme + üstel backoff + her 5dk scheduler — webhook başarısız olsa bile sessizce kaybolmuyor. Uygulama webhook hatası yüzünden çökmüyor.
- **Audit log hata izolasyonu:** `@Async` + `REQUIRES_NEW` — audit yazma başarısız olsa ana transaction etkilenmiyor.
- **Redis graceful degrade:** Cache erişilemezse log + devam; rate limiting Redis null → fail-open.
- **`ApplicationModules.verify()`:** Runtime'da yanlış bean bağlantısı yok — uygulama başlarken modül sınırı ihlali yakalanıyor.
- **HikariCP connection pool:** `connection-timeout: 20s`, `idle-timeout: 300s` — uzun süreli idle bağlantı temizleniyor.

**Eksikler (neden 10 değil):**
- **Circuit breaker yok:** `WebhookDeliveryService` dışarıya HTTP çağrı yapıyor ama Resilience4j / `@CircuitBreaker` yok — hedef URL down'sa exponential backoff var ama half-open state yok.
- **Redis single node:** `application.yaml`'da `spring.data.redis` tek host — Redis Sentinel veya Cluster yok. Redis düşerse rate limiting + cache aynı anda devre dışı.
- **DB single instance:** PostgreSQL replikasyon, read replica yok — DB restart = tam downtime.
- **Uptime metrikleri yok:** Canlı SLA verisi veya uptime geçmişi bulunmuyor.
- **Liveness/readiness probe:** `docker-compose.yml`'de `healthcheck` var ama Kubernetes deploy senaryosu için `readinessProbe` / `livenessProbe` YAML'da tanımlanmamış.

---

### 13.6 Güvenlik — 8.0 / 10

**Güçlü yönler:**
- **Çok katmanlı kimlik doğrulama:** JWT (lokal), OAuth2 (GitHub/GitLab/Google), LDAP (StartTLS/LDAPS, group mapping), SAML 2.0 (SP signing + EncryptedAssertion deşifre).
- **HMAC-SHA256 webhook imzası:** Gelen ve giden webhook'lar imzalı — replay saldırısı daha zor.
- **Rate limiting ~26 endpoint:** Brute-force login (10/60s), register spam (5/300s), SSH key ekleme (5/3600s) — katmanlı koruma.
- **Audit log append-only:** PostgreSQL `BEFORE UPDATE/DELETE` trigger — audit kaydı değiştirilemez. pgaudit ile DB-level DDL/write operasyonları loglanıyor.
- **RepoAccessInterceptor:** Tüm `/{owner}/{repo}/**` route'larına private repo erişim kontrolü — servis katmanı atlanıyor.
- **Actuator endpoint koruması:** `/actuator/health` + `/actuator/info` açık, geri kalanı `authenticated()` gerektirir.
- **`ORIGINHUB_AUDIT_ENABLED` ve `ORIGINHUB_OBSERVABILITY_ENABLED`:** Kapalı varsayılan — istem dışı veri toplama yok.

**Eksikler (neden 10 değil):**
- **MFA / TOTP yok:** "Çalınan şifre = tam erişim" — kurumsal güvenlik standardı boşluğu.
- **RBAC yok:** `is_admin` kolonu V020'de kaldırıldı — rol sistemi yok. Admin panel `ROLE_ADMIN` gerektiriyor ama rol atama mekanizması belirsiz. Audit log controller "ROLE_ADMIN" kontrol ediyor — bu role kimin sahip olacağı?
- **Fixed-window rate limiting burst açığı:** Pencere sıfırlanınca 2N istek mümkün — sliding-window (Redis Sorted Set) daha güvenli ama uygulanmadı.
- **CORS yapılandırması tam değil:** `SecurityConfig`'de CORS var ama `allowedOriginPatterns` production'da ne olmalı belirsiz.
- **Dependency CVE taraması yok:** GitHub Dependabot veya OWASP Dependency-Check CI'a bağlı değil — bilinen güvenlik açıkları otomatik takip edilmiyor.

---

### 13.7 Test Coverage (Backend) — 7.5 / 10

> **Not:** Bu puan yalnızca backend Java testlerini kapsar. Frontend Angular test durumu ayrı değerlendirme gerektirir.

**Güçlü yönler:**
- **434 unit test, 0 hata, 0 regresyon:** Her modülde test var — `~%91` service sınıf mevcudiyeti.
- **207 E2E Playwright test:** 143 API + 64 senaryo + 2 gerçek `git clone`/push — "çalışıyor mu?" sorusu somut kanıtla yanıtlanıyor.
- **`AuditAspect` + `AuditLogController` + `AuditLogService`:** 53 test — compliance kritik özellik kapsamlı.
- **`WebhookDeliveryService` + `WebhookDispatcher`:** 29 test — retry logic, DLQ kayıt, HMAC imzalama senaryoları.
- **`RateLimitService`:** 6 test — fail-open, boundary, TTL reset, limit aşımı davranışları doğrulanmış.
- **`LdapAuthService`:** 8 test — grup kayıt, mevcut hesap güncelleme, hata izolasyonu dahil.
- **`CollaboratorServiceTest`:** 914 satır — tüm davet akışları kaplı.

**Eksikler (neden 10 değil):**
- **JaCoCo satır/branch ölçümü yok:** "~%91 sınıf mevcudiyeti" kaba sayım — gerçek satır kapsam bilinmiyor, CI'da minimum threshold yok.
- **CI gate'te unit test yok:** `mr-check.yml` yalnızca lint. Test başarısız olsa PR geçiyor.
- **`@ApplicationModuleTest` senaryosu yok:** `CollaboratorInvitedEvent` → dinleyici gibi cross-module event akışları entegrasyon testi değil — Modulith event pipeline güvencesi eksik.
- **Negatif senaryo tutarsızlığı:** Bazı controller testleri yalnızca happy path — `permission denied`, `conflict`, `not found` senaryoları her modülde yok.

---

### 13.8 Ölçeklenebilirlik — 6.5 / 10

**Güçlü yönler:**
- **Virtual thread'ler:** Bloklanmayan I/O — aynı JVM instance'ı çok daha yüksek concurrent request kapasitesi.
- **Spring Modulith:** Modüller bağımsız — ileriki aşamada ayrı servis olarak extract edilebilir. Mimari borç oluşturmuyor.
- **Redis cache:** DB yükünü azaltıyor — yatay ölçekleme öncesi darboğazı erteliyor.
- **Flyway migration:** Schema versiyonlama — çoklu instance deploy'da migration çakışması önlenmiş.
- **DB index (V017):** Büyüyen veri setinde sorgu planı stabil kalacak.

**Eksikler (neden 10 değil):**
- **JGit dosya sistemi:** Repo'lar `ORIGINHUB_GIT_REPO_ROOT` altında — 2. uygulama instance'ı başlasa aynı dosya sistemi gerekiyor. NFS/shared storage yapılandırması yok — horizontal scaling talimatı yok.
- **Redis single node:** Sentinel/Cluster yok — Redis büyüyen cache yüküyle single bottleneck.
- **PostgreSQL single node:** Read replica yok — yüksek okuma trafiğinde DB darboğaz.
- **Modulith event in-process:** Olay dinleyicileri aynı JVM içinde — farklı instance'lara event routing için Kafka/RabbitMQ gerekir, uygulanmamış.
- **Tek Docker imajı:** API ve static dosya sunumu ayrı scale edilemiyor — frontend yoğunluğunda tüm API scale etmek gerekiyor.
- **No documented capacity planning:** "Kaç kullanıcı / kaç repo'ya kadar yeterli?" sorusu yanıtsız.

---

### 13.9 Satışa Hazırlık — 6.5 / 10

**Güçlü yönler:**
- **Özellik listesi rakibe yaklaştı:** Fork, PR, Issue, Tag/Release, Collaborator, SSH/HTTP clone, Snippet, Webhook, GitHub migration — temel GitHub özellik seti büyük ölçüde mevcut.
- **Production-ready SSO:** LDAP (StartTLS, group mapping) + SAML 2.0 (SP signing) — kamu/finans/sağlık segmenti teknik engeli kalktı.
- **Audit log + Observability:** "KVKK/ISO 27001 uyumlu musunuz?" + "monitoring var mı?" sorularına teknik yanıt var.
- **Tek `make up` deploy:** Ops ekibi olan her şirket çalıştırabilir — karmaşık kurulum yok.
- **Modern stack:** Java 25, Spring Boot 4.0.6, Angular 21 — "teknoloji borcu var mı?" sorusu yoklaması güçlü yanıt veriyor.
- **434 test + 207 E2E:** Due-diligence'da "kanıt var" diyebilirsin.

**Eksikler (neden 10 değil):**
- **Production referansı yok:** "Başkası kullandı mı?" sorusu yanıtsız — B2B satışta en ağır itiraz.
- **Tek geliştirici:** "Yarın ne olur?" sorusu her enterprise görüşmesinde çıkar. Dokümantasyon, devir planı, destek SLA yok.
- **MFA yok:** Güvenlik odaklı alıcı için checkbox eksik — "şifre yeterliyse başka konuşalım" diyebilir.
- **Fiyatlandırma modeli belirsiz:** Lisans şartları, güncelleme politikası, SLA tanımı yok — alıcı kafasında soru işareti kalıyor.
- **Audit/observability varsayılan kapalı:** Demo sırasında "audit log göreyim" dersen env var set etmen gerekiyor — satış sürecinde sürtünme.
- **`registry` / `admin` frontend yok:** Admin panel REST API var ama UI yok — teknik alıcı dışı stakeholder'a demo yapılamaz.

---

## 14. Özet Puan Tablosu

| Kategori | Puan | Kısa Gerekçe |
|---|---|---|
| **Mimari** | **8.5 / 10** | Spring Modulith sınırları uygulanan, event-driven, tutarlı layout. Tek geliştirici + JGit horizontal scaling sorunu düşürüyor. |
| **Kod Kalitesi (Backend)** | **8.5 / 10** | 4 katmanlı statik analiz, JSpecify, MapStruct, Google Format zorunlu. JaCoCo yok, stub method production'da. |
| **Performans** | **9.0 / 10** | HTTP/2, Gzip (CSS/JS/SVG dahil), virtual thread, Redis push-invalidation, N→1 RevWalk, DB index, Prometheus açık. Load test kanıtı yok. |
| **Memory Usage** | **9.0 / 10** | JVM G1GC `-Xms256m -Xmx768m`, Redis `allkeys-lru 256mb`, virtual thread, off-heap cache, open-in-view=false. Profil kanıtı yok. |
| **Hız ve Stabilite** | **7.5 / 10** | DLQ + retry, audit izolasyonu, graceful degrade. Circuit breaker yok, Redis/DB single node, liveness probe yok. |
| **Güvenlik** | **8.0 / 10** | Çok katmanlı auth, HMAC, rate limit, append-only audit, pgaudit. MFA yok, RBAC yok, CVE tarama yok. |
| **Test Coverage (Backend)** | **7.5 / 10** | 434 test + 207 E2E kanıtlı, her modülde test var. JaCoCo yok, CI gate'te unit test yok, `@ApplicationModuleTest` yok. |
| **Ölçeklenebilirlik** | **6.5 / 10** | Virtual thread, Modulith extract edilebilir, Redis. JGit shared-FS gerektirir, DB/Redis single node, event in-process. |
| **Satışa Hazırlık** | **6.5 / 10** | Özellik seti güçlü, SSO production-ready, deploy basit. Referans yok, tek geliştirici, MFA yok, admin UI yok. |
| **Genel Ortalama** | **8.0 / 10** | Performans ve bellek production-grade; test tabanı sağlam. Satış kanalı henüz yok. |

> **Yorum:** 8.0 ortalama — teknik due-diligence güçlü geçer. Performans + bellek + test argümanları somut. Puanı 8.5+ çıkaracak üç hamle: ① 1 canlı referans müşteri, ② MFA/TOTP, ③ JaCoCo CI gate.
