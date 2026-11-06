# GitNode: Kalite / Performans / Ölçeklenebilirlik / Bellek → 8+ Planı

Kaynak: AI analizi (Code Quality 7, Performance 7, Scalability 7, Memory 8, Architecture 8). Hedef: **Architecture 10**, diğerleri **≥ 9**.
Not: "10/10" bir AI puanıdır, garanti edilemez; bu yüzden her eksen için **kanıtlanabilir kabul kriteri** var (CI'da kırılan kural, test, rapor). Kriterlerin hepsi geçerse skorun tavana yakın çıkması beklenir.
Bu doküman yarın kaldığımız yerden devam etmek için yazıldı. Her görev: ID, dosyalar, adımlar, kabul kriteri.
Durum kutuları: `[ ]` yapılmadı, `[~]` kısmen, `[x]` bitti.

Kural (memory): istenmeyen özellik ekleme; boşluk bulursan not al, sor; gerçek çalıştırmayla doğrula (`make dev-backend`, `e2e/`).

---

## 0. Kod taramasının sonucu: AI analizinin nelerde haklı / haksız olduğu

Önceki taslağımdaki bazı varsayımlar yanlıştı; aşağıdaki tablo koddan doğrulandı.

| Konu | Gerçek durum | Sonuç |
|---|---|---|
| Java / Boot | Java 25, `spring.threads.virtual.enabled: true` zaten açık | Sanal thread görevi **yok**, sadece doğrulama |
| Async | 10 `@Async`; `AiAsyncConfig`'te ai için 2 executor + 1 varsayılan "app" executor (max 16). Webhook/audit/repo listener'ları varsayılana düşüyor | Ayrıştırma + izleme gerekli (P3) |
| Event güvenilirliği | `spring-modulith-events-jdbc` + `gitnode-events` modülü var (event publication registry) | Yeni outbox yazma; sadece `@Async` listener'ları Modulith'e bağla |
| Dağıtık kilit | `shared/lock/DistributedLockService` (Redis `SET NX` + Lua release) var; **sadece 3 yerde** kullanılıyor: `PrAutoMergeScheduler`, `WebhookDlqRetryScheduler`, `BootstrapAdminInitializer` | ShedLock eklemek **gerekmez**; mevcut servisi `@Scheduled` için bir aspect/annotation'a sar (S1) |
| Runner dispatch | `JobDispatcher` iş atamasını **Redis Pub/Sub**'la yapıyor (`DISPATCH_CHANNEL_PREFIX + runnerId`, `RunnerDispatchListener`), seçim sorgusu `FOR UPDATE SKIP LOCKED` | Runner atama çok-instance'a **zaten hazır**; yeniden yazma yok. Eksik olan sadece SSE log/durum yayını (S2) |
| SSE | `SseEmitterRegistry` (log satırı), `RunStatusSseRegistry`, `NotificationSseRegistry` süreç içi `ConcurrentHashMap`; yayıncı (`WorkflowExecutionService`, `NotificationService`) doğrudan yerel registry'yi çağırıyor | 2 instance'ta kullanıcı başka instance'taysa **olay kaybolur** (S2, en büyük ölçek açığı) |
| Cron'lar | `WorkflowScheduler` (her dakika) çoklu instance'ta **çift run üretir**; `ArtifactStoreService`/`CacheStoreService` 02:00 temizlik, `AiReviewStaleRecoveryScheduler`, `CommitContributionBackfill` kilitsiz | S1 |
| Rate limit | Redis tabanlı `RateLimitService` var (10 controller'da) | Zaten dağıtık; INCR+EXPIRE atomik değil (S6) |
| JaCoCo | **Eşik var**: INSTRUCTION %48, BRANCH %40 (`gitnode-backend/pom.xml`); entities/dtos/mappers/configs/listeners dışlanmış | "Eşik yok" yanlıştı; eşiği yükselt (Q3) |
| Actuator | `health,info,metrics,circuitbreakers` açık, probes açık; **Prometheus yok** | `micrometer-registry-prometheus` + `prometheus` endpoint (P1) |
| Circuit breaker | Resilience4j + `DistributedCircuitBreakerGuard` var | Koru |
| DB | Hikari max 15, `open-in-view: false`, 127 index, 8 EntityGraph/JOIN FETCH, `findAll()` 1 yerde, `FetchType.EAGER` 1 yerde | Küçük temizlik + sorgu sayısı testi (P2) |
| Git/dosya durumu | Bare repo'lar `gitnode.git.repo-root` altında yerel disk; artifact (`/artifacts`), release asset, cache aynı kökte | Çok-node için paylaşımlı hacim zorunlu; karar D1 |
| Hata yönetimi | 61 `catch (Exception/Throwable)`; tek `ErrorHandler`; `ignored`/`_` ile **yutulan** ~12 yer | Q1 |
| Test oranı | 846 main / 149 test. Zayıf modüller (main→test): `ai` 47→6, `auth` 55→9, `repo` 42→5, `shared` 42→4, `commit` 15→1, `collaborator` 13→1, `discussion` 19→1, `wiki` 7→1, `githttp` 6→1, `filecomment` 8→1, `branch` 18→3 | Q3 |
| Büyük sınıflar | `WorkflowTriggerService` 1388, `PullRequestService` 1015, `JobDispatcher` 814, `ExpressionEngine` 646, `WorkflowExecutionService` 603, `TaskService` 577, `IssueService` 572 | Q5 |
| Bellek: üst sınır yok | `TreeNonTxService:130,172` `loader.getBytes()` (boyut kontrolsüz), `FileCommentService:284`, `FileDiffParser:196` `new RawText(loader.getBytes())`, `CodebaseFileSampler:328`, `SnippetFileStorageService:55,87` `Files.readString`; `spring.servlet.multipart.max-file-size: 5GB` | M1, M2 |
| Bellek: iyi olanlar | `CodeOwnersService` 1 MB, `WikiService` 512 KB, `PrConflictService` 512 KB, `CodebaseFileSampler` 8 KB sınırı; JVM `MaxRAMPercentage=75` | Kalıp olarak yaygınlaştır |

---

## 1. Fazlar ve sıra

```
Faz A (temel, ölç)   : P1 metrikler → Q3 kapsam taban → M3 bellek görünürlüğü
Faz B (ölçek kritik) : S1 cron kilitleri → S2 SSE fan-out → S3 yerel cache sınırları
Faz C (kalite)       : Q1 hata modeli → Q2 log/MDC → Q4 ölü kod → Q5 sınıf bölme
Faz D (performans)   : P2 sorgu/N+1 → P3 executor → P4 yük testi + git/HTTP ayarı
Faz E (bellek)       : M1 üst sınırlar → M2 akış (streaming) → M4 kota/yük testi
Faz F (dağıtım)      : S4 depolama soyutlaması → S5 compose scale + 2-instance e2e → S6 küçük sertleştirme
Faz G (mimari → 10)  : A1 sınırları kilitle → A2 identity/repo yüzeyini daralt → A3 uydu modüller → A4 collaborator → A5 döngü/çift yön → A6 kural seti + ADR
```
Mimari işler (Faz G) diğer fazlarla **paralel** gidebilir; ama Q5 (sınıf bölme) ve S2 (SSE) ile aynı dosyalara dokunduğunda sırayla birleştir.

Her görev ayrı PR; `mr-check.yml` yeşil olmadan birleşmez.

---

## 2. FAZ A: Ölçüm temeli

### P1: Prometheus metrikleri + dashboard `[iptal]`  (~1 g)
- `gitnode-backend/pom.xml`: `io.micrometer:micrometer-registry-prometheus`.
- `application.yaml` → `management.endpoints.web.exposure.include` içine `prometheus` ekle. **Güvenlik:** endpoint yalnız admin/iç ağ; `SecurityConfig`'te `/actuator/prometheus` için ayrı kural (test yaz: anonim 401/403).
- Özel metrikler (Micrometer `MeterRegistry`):
  - Executor kuyruğu: `ThreadPoolTaskExecutor` bean'lerini `ExecutorServiceMetrics.monitor` ile sar.
  - Git alt işlemleri/JGit: `gitnode.git.operation` timer (tag: `op=clone|fetch|diff|blob`).
  - SSE: `gitnode.sse.emitters` gauge (registry başına).
  - Cache: Redis cache hit/miss (`CacheConfig`'te `enableStatistics()`).
  - Dispatch: `gitnode.dispatch.queue.depth`, `gitnode.dispatch.latency`.
  - JVM bellek/GC/`jvm.buffer.*` zaten otomatik.
- `config/grafana/gitnode.json` dashboard + `config/prometheus.yml`; `docker-compose.yml` altına opsiyonel `profiles: [observability]`.
- **Kabul:** `curl /actuator/prometheus` metrik döner; dashboard'da Hikari, executor, GC, heap, SSE paneli dolu.

### Q3 (taban adımı): Kapsam ölçümü `[x]`  (~0.5 g)
- `mvn verify` sonrası modül bazlı JaCoCo raporu al, tabloyu `docs/` veya bu dosyaya yaz (başlangıç değerleri).
- CI'da PR yorumu/artifact olarak `jacoco.xml` yükle (`mr-check.yml`).
- **Kabul:** her modülün başlangıç kapsamı bu dosyada kayıtlı.


**Q3 taban (2026-11-06, `mvn verify`, INSTRUCTION / BRANCH %):** toplam 59/54 ·
actions 72/67 · admin 88/66 · ai 32/27 · auth 52/29 · branch 42/34 · collaborator 68/86 · commit 80/56 · discussion 46/55 · filecomment 76/76 · githttp 62/70 · identity 74/59 · issue 58/65 · migration 84/83 · notification 7/12 · pr 39/37 · profile 42/52 · repo 53/58 · shared 34/24 · snippet 70/79 · ssh 47/57 · tag 58/52 · task 37/35 · team 87/94 · tree 79/61 · webhook 69/67 · wiki 82/74.
(Önceki "zayıf modül" listesi dosya sayısına dayanıyordu; gerçek en zayıflar: notification, ai, shared, pr, task, branch, auth-branch.) CI artık `jacoco-report` artifact yüklüyor.

### M3: Bellek görünürlüğü `[x]`  (~0.5 g)
- Prod için JVM bayrakları (`Dockerfile` `ENTRYPOINT`): `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/gitnode`, `-Xlog:gc*:file=...` (veya `-XX:+ExitOnOutOfMemoryError`; hangisi seçilecek D4).
- Direct buffer/Netty/JGit pencere önbelleği (`WindowCache`) için ayar: JGit `WindowCacheConfig` (`packedGitLimit`, `deltaBaseCacheLimit`) açıkça ayarla, yoksa varsayılan 10 MB × repo sayısı değil ama toplam sınırı belirsiz → sabitle ve belgele.
- `jcmd`/JFR nasıl alınır rehberi (`CONTRIBUTING.md` kısa bölüm).
- **Kabul:** OOM'da heap dump düşüyor (konteyner içi deneme), GC log okunabiliyor.

---

## 3. FAZ B: Ölçeklenebilirlik kritik yol

### S1: `@Scheduled` için dağıtık kilit `[x]` (WorkflowScheduler zaten per-minute Redis dedup yapıyor; webhook DLQ manuel kilidi @Transactional nedeniyle bırakıldı)  (~1.5 g)
Yeni bağımlılık **yok**; `DistributedLockService` kullanılır.

1. `shared/lock/` altında `@SchedulerLock(name, ttl)` annotation + `SchedulerLockAspect` (`@Around`):
   - `tryLock("sched:" + name, owner, ttl)`; alamazsa metot atlanır (debug log).
   - `finally` → `unlock`. TTL, işin en uzun süresinden büyük; uzun işlerde (`CommitContributionBackfill`) kilidi yenileme (`renew`) metodu ekle (Lua `PEXPIRE` yalnız owner eşitse).
   - Redis erişilemezse **çalıştırma** kararı: iş idempotent mi? Idempotent değilse atla, idempotent ise çalış (annotation parametresi `failOpen`).
2. Metot başına karar tablosu:

| Metot | Kilit | Not |
|---|---|---|
| `WorkflowScheduler` (cron/dk) | **gerekli**, `lockAtMostFor=55s` | çift run üretir; ek olarak run oluşturmada `(workflow_id, scheduled_at)` benzersiz kısıt (migration) |
| `ArtifactStoreService` 02:00, `CacheStoreService` 02:30 | gerekli | paylaşımlı diskte aynı dosyaları silme yarışı |
| `AiReviewStaleRecoveryScheduler` | gerekli | çift kurtarma → çift AI çağrısı (maliyet) |
| `CommitContributionBackfill` | gerekli + renew | uzun iş |
| `JobDispatcher.housekeeping` | gerekli | `recordWaitingReason` "bir kez yaz" varsayımı yarışa açık |
| `JobDispatcher.dispatch` | gereksiz | `SKIP LOCKED` ile güvenli |
| `JobDispatcher.evictExpiredYamlCache`, 3× SSE `evict` | gereksiz | yerel bellek temizliği, her instance çalıştırmalı |
| `RefreshTokenService` 03:17 | opsiyonel | idempotent silme; kilit yükü azaltır |
| `PrAutoMergeScheduler`, `WebhookDlqRetryScheduler` | **mevcut elle kilidi aspect'e taşı** | tekrar kodu kalksın |

3. Test: iki `SchedulerLockAspect` örneği aynı Redis'e (Testcontainers) aynı anda; sadece biri çalışır. Kilit çökmede TTL ile düşer.
- **Kabul:** `WorkflowScheduler` iki instance'ta aynı dakikada tek run üretir (e2e/integration testi).

### S2: SSE olaylarını instance'lar arası yayma (Redis Pub/Sub) `[ ]`  (~3 g)
Sorun: log satırı runner'ın bağlı olduğu instance A'da gelir (`WorkflowExecutionService:427,438`), tarayıcı SSE'si instance B'de açık olabilir.

Tasarım (mevcut `RedisMessageListenerContainer` kalıbını yeniden kullan):
1. `shared/` altında `ClusterEventBus` arayüzü + `RedisClusterEventBus`: `publish(topic, key, payload)`, `subscribe(topic, handler)`. Her mesajda `originInstanceId` (UUID, startup'ta) — kendi mesajını tekrar işleme.
2. Üç registry'de ayrım:
   - `subscribe()` / emitter yönetimi → **yerel kalır**.
   - `broadcast/notify/complete` → artık `ClusterEventBus.publish`; her instance dinleyip **yerel** emitter'lara iletir. Yayıncı sınıflar (`WorkflowExecutionService`, `JobCancellation`, `NotificationService`, `NotificationEventListener`) registry yerine `publish` çağırır veya registry'nin `broadcast` metodu içeride publish yapar (imza değişmez → çağıranlara dokunulmaz; **önerilen**, diff küçük).
3. Kanallar: `sse:step:<stepId>`, `sse:run:<runId>`, `sse:notif:<userId>`. Tek pattern listener: `sse:*`.
4. Log satırı hacmi yüksek → Pub/Sub'a her satır yerine **yalnız o step'e abone olan instance'lar için** gönder: abonelik kaydı Redis `SET sse:sub:step:<id>` (TTL) tut; yayıncı abone yoksa publish etmez. (Ölç: P1 metrikleri ile; gereksizse basit tut.)
5. Yeniden bağlanma: SSE `Last-Event-ID` desteği — log satırı numarası (`lineNum`) zaten var; istemci kaçırdığını DB'den çekebilsin (mevcut log endpoint'i ile birleştir). Kontrol et, yoksa not al ve sor.
- **Kabul:** 2 instance + LB; tarayıcı B'de, runner A'da; log satırları ve run durumu canlı akar. Birim: `ClusterEventBus` fake ile registry davranışı; entegrasyon: Testcontainers Redis ile iki context.

### S3: Süreç içi cache/durum sınırları `[ ]`  (~0.5 g)
- `JobDispatcher.yamlCache` (ConcurrentHashMap + 5 dk el yapımı TTL + scheduled evict) → **Caffeine** (`maximumSize`, `expireAfterWrite`). Bağımlılık: `com.github.ben-manes.caffeine:caffeine` (Boot zaten yönetir).
- `DispatchDiagnostics.lastWarned` → Caffeine (`expireAfterWrite 1h`, `maximumSize`); `retainOnly` mantığı kalkar.
- `RunnerSessionRegistry`: yerel kalır (WS bağlantısı doğası gereği yerel). Ek: "runner hangi instance'ta" bilgisi gerekmez, çünkü dispatch kanalı runnerId bazlı ve **bağlı olan instance** dinler (doğrula: `RunnerDispatchListener` bağlı olmayan runner için mesajı sessizce düşürüyor mu? Düşürüyorsa sorun yok, çünkü bağlı instance alır).
- **Kabul:** yukarıdaki iki map'te sınırsız büyüme yok (birim test: > max giriş ekle, boyut ≤ max).

---

## 4. FAZ C: Kod kalitesi

### Q1: Hata modeli ve `catch` temizliği `[ ]`  (~3-4 g)
1. `shared/errorhandling/exceptions` mevcut tipleri envantere al; eksikler: `ExternalServiceException`, `ConflictException`. `ErrorHandler`'ı **RFC 7807 `ProblemDetail`** + `traceId` döndürecek şekilde güncelle (frontend/e2e sözleşmesi bozulmasın: önce mevcut hata gövdesini e2e'de kilitle, sonra geçiş; geçiş kararı D3).
2. 61 `catch` sınıflandırma tablosu (kategori → eylem):
   - **Sınır, kalabilir** (scheduler döngüsü, executor/Async, WS handler, SSH sunucu, Slack appender, audit aspect): `log.error(msg, ex)` (stack dahil) + sayaç metriği. Dosyalar: `JobDispatcher:279,297`, `RunnerWebSocketHandler:74,241`, `GitNodeSshServer:130`, `WebhookDlqRetryScheduler:135`, `AiCodeReviewRunner:53`, `CommitContributionBackfill:76`, `AuditAspect:58,83,98`, `AuditLogService:55`, `SlackAppender:73`, `DistributedLockService:53`, `CachePatternEvictor:41`.
   - **Yutulan (`ignored`, `_`)**: `WorkflowRunController:183`, `LogStreamController:93`, `RunnerWebSocketHandler:241`, `NotificationSseRegistry:63`, `AuditAspect:83`, `TagReleaseMigrationListener:108`, `RepoAccessInterceptor:113`, `RepoMigrationService:135`, `SnippetCommentController:92`, `SnippetController:226`. Her biri için: neden yutuluyor yorumu veya `IOException`/`IllegalStateException` gibi dar tip; gerçekten gerekmiyorsa kaldır.
   - **Geniş ama dar tipe indirilebilir** (çoğu): auth (`Saml*`, `Ldap*`) → `GeneralSecurityException|IOException|SAMLException`; `SshKeyService:144`; `TreeController:162`; `Webhook*`; `Secret VaultService:140,152` → `GeneralSecurityException` (şifre çözme hatası **yutulmamalı**, yanlış anahtarda net hata ver); `UserAiSettingsService:167,319,334`.
   - **Migration listener'ları** (`PullRequestMigrationListener` 5, `RepoMigrationService`, `GitMigrationService`, `GitHubMigrationService`): dış servis hatası → `ExternalServiceException`, kullanıcıya anlamlı mesaj, durum DB'ye yazılır.
3. Checkstyle: `IllegalCatch` aç (`config/checkstyle.xml`); sınır yerlerde `@SuppressWarnings("checkstyle:IllegalCatch")` + tek satır gerekçe. SpotBugs `REC_CATCH_EXCEPTION`, `DE_MIGHT_IGNORE` etkin.
4. Test: `ErrorHandler` her istisna tipi → HTTP kodu + gövde şeması (parametrik test).
- **Kabul:** `IllegalCatch` ihlali = sadece bildirimli sınır yerleri (≤ 20); yutulan catch = 0.

### Q2: Loglama standardı `[ ]`  (~1 g)
- MDC filtresi: `requestId`/`traceId`, `userId`; actions tarafı `runId/jobId/repoId`; `@Async`/executor'larda MDC taşıyan `TaskDecorator` (P3 ile ortak).
- Log desenine MDC alanları (`logback-cloud.xml` + yerel desen).
- Maskeleme testi: token/secret/Authorization asla loglanmaz (`SecretVaultService`, `HttpGitAuthenticationFilter`, runner token).
- `logging.level.dev.gitnode.os: warn` prod için kaba; INFO'yu modül bazlı seçici aç (D5).
- **Kabul:** bir isteğin tüm log satırları aynı `traceId` taşır.

### Q3: Test kapsamı `[ ]`  (~6-8 g, sürekli)
Eşik **artımlı** yükselir; ani sıçrama PR akışını tıkar.

| Sprint | INSTRUCTION | BRANCH |
|---|---|---|
| taban (şimdi) | 48 | 40 |
| +1 | 55 | 46 |
| +2 | 62 | 52 |
| +3 | 68 | 58 |
| hedef | ≥ 70 | ≥ 60 |

Öncelik sırası (risk × boş test):
1. `auth` (55→9): JWT/refresh, SAML/LDAP builder'lar, `HttpGitAuthenticationFilter`, `RunnerHandshakeInterceptor`.
2. `repo` (42→5), `githttp` (6→1), `branch` (`ProtectedBranchReceiveHook`): yetki ve push yolu.
3. `actions` çekirdek: `ExpressionEngine`, `WorkflowTriggerService`, `MatrixExpander`, `JobDispatcher` (SKIP LOCKED + etiket eşleme; Testcontainers).
4. `pr` merge/yetki (`PullRequestService`, `PrConflictService`), `commit` (`FileDiffParser`).
5. `ai` (47→6): provider soyutlaması fake ile; sınır/devre kesici davranışı.
6. `collaborator`, `discussion`, `wiki`, `filecomment`: en az servis+yetki testi.
- Testcontainers (Postgres + Redis) için ortak `@IntegrationTest` temeli; CI süresi izlenir (yavaşlarsa `failsafe`'e ayır).
- Dışlamalar (`listeners`, `configs`) gözden geçir: `listeners/**` aslında iş mantığı taşıyor (`PullRequestMigrationListener`) → dışlamayı kaldır, kapsam gerçekçi olsun.
- PIT mutation testi yalnız `ExpressionEngine` ve `auth` için (nightly, bloklamaz).
- **Kabul:** eşik tablosu sprint sprint `pom.xml`'e işlenir; hedef sonunda ≥ 70/60.

### Q4: Ölü kod temizliği `[ ]`  (~1 g)
- `mvn dependency:analyze` (kullanılmayan bağımlılık): aday `eddsa`, `guava`, `commons-codec` kullanımı doğrula.
- Kullanılmayan public sınıf/metot: IDE "unused declaration" + `jdeps`, SpotBugs `UPM_UNCALLED_PRIVATE_METHOD`, `URF_UNREAD_FIELD`.
- Kullanılmayan endpoint: frontend + `e2e/api` çağrılarıyla çapraz kontrol (graphify grafiği yardımcı olabilir: `graphify-out/` varsa).
- Kullanılmayan Flyway nesneleri (tablo/kolon) **silinmez**, yalnız rapor edilir (D6).
- **Kabul:** rapor + silinen kod PR'ı; `dependency:analyze` temiz.

### Q5: Büyük sınıfları bölme `[ ]`  (~5 g)
Davranış değişmez; önce karakterizasyon testi, sonra çıkarma.
- `WorkflowTriggerService` (1388) → `TriggerMatcher` (event/branch/path filtre), `ScheduleTriggerService`, `RunFactory` (run+job oluşturma), `WorkflowRunTriggerService` (`workflow_run` zinciri).
- `PullRequestService` (1015) → `PullRequestMergeService`, `PullRequestReviewService`, `PullRequestMetadataService` (label/milestone).
- `JobDispatcher` (814) → `RunnerSelector`, `JobPayloadBuilder` (container/services/expression context), `DispatchHousekeeping`.
- `WorkflowExecutionService` (603), `ExpressionEngine` (646), `TaskService`, `IssueService`: ikincil.
- Checkstyle `FileLength` ≤ 500, `MethodLength` ≤ 60 (başta uyarı, bölme bitince hata).
- ArchUnit (`GitNodeArchitectureTest`): modül sınırları korunuyor mu yeniden koş; yeni sınıflar doğru pakette.
- **Kabul:** 500 satır üstü sınıf = 0; testler aynı; e2e yeşil.

---

## 5. FAZ D: Performans

### P2: Sorgu performansı / N+1 `[ ]`  (~3 g)
- Dev/test profilinde `hibernate.generate_statistics=true`; testte **sorgu sayısı assert** (liste uçları: repo listesi, PR/issue listesi, bildirimler, actions run listesi). Yardımcı: Hibernate `Statistics#getPrepareStatementCount` (yeni bağımlılık gerektirmez).
- Liste servisleri DTO projeksiyonu veya `EntityGraph`; yalnız gereken kolon. 8 mevcut kullanım örnek kalıp.
- `JobDispatcher.housekeeping`/`reportStuckJobs`: `runnerRepository.findAll()` her dakikada tüm runner'lar; kabul edilebilir ama `findAllById` + `findAll` tekrarları tek sorguya indirilebilir (ölç, gerekmiyorsa dokunma).
- `FetchType.EAGER` (1) ve `findAll()` (1) yerlerini bul, sınırla/kaldır.
- `pg_stat_statements` etkinleştir (compose `postgres` komutu), en yavaş/en sık 20 sorguyu `EXPLAIN (ANALYZE, BUFFERS)` ile incele; eksik index için yeni Flyway migration (`CREATE INDEX CONCURRENTLY`; Flyway `executeInTransaction=false` gerektirir → ayrı script). `pg_stat_user_indexes` ile hiç kullanılmayan index'leri **raporla** (D6).
- Hibernate: `jdbc.batch_size=50`, `order_inserts`, `order_updates`; toplu yazılan yerler için (audit, notification) doğrula.
- **Kabul:** listelerde sorgu sayısı sayfa boyutundan bağımsız; en yavaş 20 sorgu raporu bu dosyaya eklendi.

### P3: Async/executor ayrıştırma `[ ]`  (~2 g)
- Tek `AsyncConfig` (mevcut `AiAsyncConfig`'i genişlet/yeniden adlandır): `webhookExecutor`, `auditExecutor`, `repoEventExecutor`, `migrationExecutor`; her biri sınırlı kuyruk + `CallerRunsPolicy` yalnız güvenli yerde (audit için **düşür+say**, webhook için kuyruk+DLQ).
- `TaskDecorator`: MDC + `SecurityContext` taşıma. `AsyncUncaughtExceptionHandler` → log + metrik.
- Qualifier'sız `@Async` kalmasın (ArchUnit kuralı yaz).
- Kritik olay dinleyicileri (`RepoCreatedEventListener`, `RepoRenamedEventListener`, `CacheInvalidationListener`): kaybolursa tutarsızlık yaratıyor mu? Yaratıyorsa `@Async @EventListener` → `@ApplicationModuleListener` (Modulith publication registry, tekrar deneme). Hangileri kritik: kod okuyup listele, sonra karar (D2).
- Sanal thread açık olduğundan `@Async` + `ThreadPoolTaskExecutor` yerine `SimpleAsyncTaskExecutor(virtual)` düşünülebilir; **ölçmeden değiştirme** (P4 sonrası).
- **Kabul:** executor kuyruk/aktif gauge'ları dashboard'da; qualifier'sız `@Async` = 0.

### P4: Yük testi, git ve HTTP ayarı `[ ]`  (~3 g)
- k6 senaryoları (`e2e/load/`): login, repo listesi, PR listesi, tree/blob, diff, smart-HTTP `git clone/fetch`, issue arama, SSE bağlantı sayısı. Taban p50/p95/p99 bu dosyaya yaz.
- Git işlemleri: JGit/`ProcessBuilder` (1 yer) için eşzamanlılık sınırı (`Semaphore`, yapılandırılabilir) + zaman aşımı; sınır aşılırsa 503 + `Retry-After`.
- HTTP: `ETag`/`If-None-Match` (blob, tree, commit — immutable SHA'da `Cache-Control: immutable`). Tomcat `max-threads` ve `accept-count` (şu an 50) yük testiyle belirle; sanal thread'le thread sayısı kritik değil ama Hikari 15 darboğaz olur → `connection-timeout` ve havuz boyutunu ölçerek ayarla.
- **Kabul:** hedef: kritik 10 uçta p95 en az %30 iyileşme ya da SLO'ya uyum (SLO'yu yük testi sonrası D7'de belirle).

---

## 5b. FAZ G: Mimari (8 → 10)

AI'nın bulguları: "servisler arası sıkı bağ, belirsiz modül sınırı; arayüz tanımla, soyutlama ekle". Repo zaten modüler monolite çevriliyor
(Modulith `verify()`, `*.api` portları, `gitnode-events`, ArchUnit R1-R5; bkz. memory `gitnode-architecture-refactor`). Eksik kalanlar koddan ölçüldü:

| Ölçüm | Değer | Anlam |
|---|---|---|
| `identity.services` başka modüllerden import | **54** | identity'nin iç servisleri sızıyor (port `identity.api` varken) |
| `identity.entities` / `identity.repositories` dışarıdan | **18 / 16** | entity ve repo sızıyor (R2 sadece `..api..` için, R5 muaf modülleri izin veriyor) |
| `repo.dtos` dışarıdan | **30** | dto'lar fiilen ikinci bir yayın yüzeyi; `named interface` olarak tanımsız |
| `repo.events`, `repo.cache`, `repo.repositories`, `repo.services`, `repo.entities` dışarıdan | 5 / 4 / 3 / 1 / 1 | küçük ama sınır deliği |
| `allowedDependencies` geniş joker | `identity :: *`, `repo :: *` (admin), `shared :: *` | Modulith "kapalı" ama içeri her şeyi açıyor |
| Muaf modüller (R5) | `repo, identity, githttp, ssh, auth, profile, admin` | repo/identity repository'lerine doğrudan erişebilirler |
| Karar bekleyen modüller | `auth`, `profile`, `admin` ("identity uyduları"), `collaborator` (refactor yapılmadı) | |
| En sık bağ | `pr→repo` 16, `actions→shared` 16, `auth→identity` 15, `pr→identity` 13, `actions→identity` 12, `actions→repo` 10, `pr→commit` 9 | `pr` ve `actions` en bağımlı modüller (en çok sınır riski) |
| Frozen ArchUnit | `archunit_store` içinde 3 satırlık `stored.rules`, boş ihlal dosyası | dondurulmuş borç ≈ 0 (iyi); `refreeze=false` korunmalı |
| Dokümantasyon | memory `ARCHITECTURE.md` ve `docs/DEVAM_NOTU.md` diyor ama çalışma ağacında **yok** (commitlenmemiş/silinmiş olabilir) | önce bulunacak, yoksa yeniden üretilecek (A0) |

### A0: Başlangıç doğrulaması `[ ]`  (~0.5 g)
- `ARCHITECTURE.md`, `docs/DEVAM_NOTU.md`, `docs/tools/gen-architecture-md.py` nerede? (`git log --all -- docs ARCHITECTURE.md`, `git stash list`). Bulunamazsa Documenter çıktısından (`target/spring-modulith-docs`) yeniden üret ve **sor** (silinmiş olabilir).
- `mvn -q -pl gitnode-backend test -Dtest=GitNodeArchitectureTest` → mevcut durum yeşil mi? Çıktıyı taban olarak kaydet.
- Eski koda karşı e2e baz çizgisi (memory kuralı: önce eski kodla e2e, sonra refactor; kayıt kısıtı 20/5dk olduğu için tek paylaşılan lifecycle spec).

### A1: Sınırları kırılmaz kılma `[x]` (2026-11-06: `ModuleBoundaryRulesTest` R6-R11, hepsi `FreezingArchRule`. Dondurulmuş taban: R6 = 660 satır (yabancı modül iç paketine bağımlılık), R11 = 1 (`SecurityConfig.samlSuccessHandler` @Autowired alan), R7/R8/R9/R10 = 0. Ör. R6 kaydı: `auth.configs.JwtAuthenticationFilter` → `identity.repositories.TenantRepository` + `identity.services.JwtUtils` (katman sızıntısı; A2/A3'te `identity.api` portuna taşınacak). Frozen liste yalnız azalır, A2-A3'te satır silinir)  (~1 g)
Önce *ölç ve kilitle*, sonra daralt (regresyon olmasın):
- Mevcut sızıntıları ArchUnit `FreezingArchRule` ile **dondur** (yeni ihlal eklenemez), her daraltma PR'ında dondurulmuş listeden satır sil.
- Yeni kurallar:
  - **R6** modül dışı kod yalnız `..api..`, `..events..`, açık `@NamedInterface` paketlerine bağımlı olabilir (iç `services/entities/repositories/mappers/controllers/configs` yasak). Muafiyet: şimdilik yok, ihlaller dondurulur.
  - **R7** `..api..` paketi `entities`, `repositories`, `controllers` kullanmaz (R2'nin genişletilmişi, repo `..repositories..` dahil).
  - **R8** döngüsel modül bağımlılığı yok (`SlicesRuleDefinition.slices().matching("dev.gitnode.os.(*)..").should().beFreeOfCycles()`).
  - **R9** `shared` hiçbir domain modülüne bağımlı değil.
  - **R10** controller → repository yok (R1 var), `@Transactional` yalnız `services/`.
  - **R11** field injection (`@Autowired` alan) yok, constructor injection.
- Kuralları `ApplicationModules.verify()` ile **ayrı** test sınıfında koş ki hata mesajı net olsun.
- **Kabul:** kurallar CI'da; dondurulmuş ihlal sayısı bu dosyaya yazılı (taban), sadece azalır.

### A2: `identity` ve `repo` yüzeyini daralt `[x]` (2026-11-06; kalan işler A3'e devredildi — aşağıdaki not)
Hedef: diğer modüller `identity` ve `repo`'yu **yalnız port/api/event üzerinden** kullansın.
1. **identity.services (54 import)**: kullananları gruplayıp hangi yeteneğin eksik olduğunu çıkar (kullanıcı arama, tenant/slug çözümü, parola, anahtar vb.). Her yetenek için `identity.api` altında port (örn. `TenantQueryPort`, `GitCredentialPort`, `AuthenticatedUser` zaten var) + gerekirse yeni: `UserLookupPort`, `TenantProfilePort`. Tüketiciler portlara geçer; `identity.services` paket-özel (package-private sınıflar) yapılır.
2. **identity.entities/repositories (18/16)**: tüketiciler entity yerine `*View`/record DTO (api'de) alır. R5 muafiyetinden `profile`, `auth`, `admin` çıkarılır (A3'e bağlı).
3. **repo.dtos (30)**: iki seçenek: (a) `@NamedInterface("dtos")` ile yayın yüzeyi olarak **bilinçli** açmak ve dto'ları yalnız değer-nesnesi (record, entity yok) tutmak, (b) `repo.api` altına taşıyıp dto'ları sahip modülde kalmasını sağlamak. Önerim (a): taşıma maliyeti düşük, sınır açık yazılı olur. Kural: bu paket entity/`@JsonIgnore` JPA tipi içermez (R7).
4. `repo.events`, `repo.cache`, `repo.repositories`, `repo.services`, `repo.entities` dışarıdan kullanımları (toplam ~14 dosya): her biri için port ya da olay; `repo.cache` kullanan modüller `repo` cache anahtarlarını bilmemeli → `CacheInvalidationPort` veya olay.
5. `allowedDependencies` joker temizliği: `identity :: *`, `repo :: *`, `shared :: *` → açık liste (`identity :: api`, `repo :: api`, `repo :: dtos`, `shared :: errorhandling-exceptions` vb.). `shared` için named interface'ler: `exceptions`, `git-provider`, `cache`, `lock`, `ratelimit`, `audit` ayrı ayrı; modül yalnız ihtiyacını beyan eder.
6. Her adım sonrası: `clean test-compile`, mimari test, e2e (memory uyarısı: `Set.of` tekrarlı eleman istisnası).
- **Kabul:** `identity.services|entities|repositories|mappers` ve `repo.services|entities|repositories|cache` dışarıdan 0 import; joker `::*` sayısı 0 (shared hariç, o da named).

**A2 sonucu (2026-11-06):**
- `identity.api.TokenUserResolver` portu: `JwtUtils` implement ediyor; 19 modülde 41 dosya artık bu portu kullanıyor. Bu modüllerden `identity :: security` izni kalktı.
- **Tüm `::*` jokerleri kaldırıldı.** Her modülün `allowedDependencies` listesi Modulith'ten çıkarılan gerçek kullanıma göre açık yazıldı (kullanılmayan izinler de düştü). Artık yeni bir bağımlılık eklemek package-info'yu değiştirmeyi gerektirir.
- Yeni kural R12: `repo.dtos` entity/repository kullanmaz (ihlal 0).
- R6 donmuş liste 660 → 404. Kalan 404 satırın tamamı: `auth`, `profile`, `admin` (A3) ve `repo → Tenant` (`Repo.owner` JPA ilişkisi: `Repo`, `RepoMapper`, `RepoService`, `RepoMigrationService`; sahip modül ilişkisi, UUID'ye çevirmek ayrı/riskli iş, A3 sonrası değerlendirilecek).
- Not: `identity` alt paketleri (`dto, model, persistence, security, mapping, utils`) hâlâ named interface; satellite modüller (auth/profile/admin/repo) bunları kullanıyor. A3 bunları kapatacak.

### A3: Uydu modüller `auth`, `profile`, `admin` `[x]` (2026-11-06, A2 ile birlikte yapıldı)
Karar bekliyor (D9). Öneri:
- `auth` (kimlik doğrulama: JWT, OAuth2, SAML, LDAP): **`identity`'nin alt modülü olarak değil, ayrı modül kalsın**, ama yalnız `identity.api` ve `auth.api` yayın yüzeyi; `identity`'ye bağımlılığı port üzerinden (`UserCredentialPort`, `ExternalIdentityPort`). Bugün `auth→identity` 15 import.
- `profile` (katkı grafiği, backfill): `identity` ve `repo.api`'ye port; kendi tablolarına sahip. R5 muafiyetinden çıkar.
- `admin` (yönetim paneli): **tüm modüllere okuma yönlü** bir "composition/read-model" modülü; domain modüllerinin `api` portlarından veri çeker, iç servis/repo'ya inmez. `repo :: *` ve `identity :: *` izinleri bu yüzden en geniş olan yer; admin için ayrı `*.api.AdminQueryPort` seti tanımla (her modül kendi admin görünümünü sunar). `admin` hiçbir modül tarafından import edilmez (R12).
- Sonuç: R5 muaf listesi `repo, identity, githttp, ssh` ile kısalır; sonra sadece `repo`/`identity` (sahipleri).

**A2+A3 sonucu (2026-11-06) — R6 donmuş liste 660 → 0, tüm donmuş listeler boş:**
- `identity.api`: `TenantAccount` (entity görünümü), `TenantAccountStore` (repository portu), `PasswordHashing`, `TokenIssuer`/`TokenUserResolver`/`TokenService`, `TenantInfoMapper`. `auth`, `profile`, `admin` yalnız bunları (ve `identity :: dto`/`utils`) kullanır; `Tenant`/`TenantRepository`/`JwtUtils`/`PasswordHasher` artık dışarıdan görünmez.
- `repo.api.RepoAdminPort` (admin için okuma portu). `admin` repo iç sınıflarına dokunmaz.
- JPA ilişkileri UUID'ye çevrildi: `Repo.owner` → `ownerId`, `auth.Account.tenant` → `tenantId`. Sahip bilgisi `RepoOwnerResolver` + `RepoInfoAssembler` (MapStruct `@Context` ile toplu çözüm, sayfa başına tek sorgu). JPQL'de entity join (`JOIN Tenant o ON o.id = r.ownerId`), Java tipi bağımlılığı yok. Üretilen `RepoMapperImpl`/`TenantMapperImpl` elle okundu, tüm alanlar eşleşiyor.
- `allowedDependencies` jokersiz ve açık. Yeni `AllowedDependenciesSourceTest` kaynak import'larını izin listesiyle karşılaştırır (Modulith bayt kodunda `CacheNames` gibi sabitler inline olduğu için `shared :: cache` eksikliğini kaçırıyordu; 7 modülde eklendi, IDE'nin gösterdiği hata buydu).
- R5 muafiyeti yalnız `repo` ve `identity`. R10 `services/adapters/listeners/repositories/events` için; R11 sıfır (`SecurityConfig` `ObjectProvider` kullanıyor); R12 `repo.dtos` entity içermez.
- Hesap silme denetimi: e2e ile veri üretilip hesap API'den silindi; tüm tablolarda silinen kullanıcı/repo kimliğine referans 0, disk dizinleri silindi. Bu denetim `workflow_run/definition/cache/secret`, `runner*`, `webhook_dead_letters` tablolarında FK olmadığını ve yetim satır kaldığını ortaya çıkardı: **V075** migration (önce yetimleri siler, sonra `ON DELETE CASCADE`/`SET NULL` FK'lar + eksik indeksler). Kalıcı test: `e2e/api/auth/account-delete.spec.ts`.
- Cache düzeltmeleri: `WikiService.save` self-invocation (`load` özel metoduna ayrıldı), `PrFinder.countOpen` (package-private, hiç çağrılmıyordu, cache çalışmıyordu) ve boşuna evict eden `REPO_PR_OPEN_COUNT` kaldırıldı. Başka self-invocation/`@Async` tuzağı yok (taramayla doğrulandı).
- Gerçek çalıştırmada bulunup düzeltilenler (birim testler yakalamıyordu): `OrganizationRepository` metodunun toplu yeniden adlandırmayla bozulması (uygulama açılmıyordu), `SchedulerLockAspect` argüman bağlama hatası.
- e2e sonucu: API 165/165, senaryo 132 + merge-lock 3, UI 64, admin UI 5 (LDAP/SAML kasıtlı kapalı). Bellek: backend `-Xmx512m` ile tüm paketi geçti. Bir koşuda tek bir senaryo testi flaky göründü (6+4 tekrarda ve 4 tam koşuda tekrarlanmadı).
- Ek düzeltme: `RepoStorageService.initRepo` idempotent yapıldı (asenkron `RepoCreatedEventListener` her e2e koşusunda `Repository already exists` hatası basıyordu; artık log'da 0 ERROR).
- Bilinçli/bilgi notları: sahibi silinen kayıtta başkasının verisine bağlı kolonlar `SET NULL` (örn. issue assignee, PR merged_by) — başkasının verisini silmemek için. `notification.entity_id` çok biçimli (tablo yok), silinmiş varlığa işaret eden bildirimler kalabilir.

### A4: `collaborator` modülünü bitir `[x]` (zaten izoleydi: dışarıdan kimse iç sınıflarını import etmiyor, port uygulaması modülde, JPA ilişkisi yok; yapılan: `permissions` EAGER koleksiyonuna `@BatchSize(50)`; port uygulamasını `adapters`'a taşımak kozmetik olduğu için yapılmadı)  (~2 g)
- Refactor yapılmamış tek domain modülü (13 dosya; `repo.api`, `repo.dtos`, `identity.api`, `team.api` zaten izinli).
- `CollaboratorAccessPort` (`repo.api` içinde tanımlı) uygulaması burada; repo→collaborator yönlü bağ olay/port ile ters çevrilmiş mi doğrula.
- Aynı A2 kuralları: entity/repository sızıntısı yok; `events :: collaborator` sözleşmesi.

### A5: Çift yönlü bağ / döngü avı `[x]` (döngü yok; R13: 8 katmanlı tablo `ModuleBoundaryRulesTest.LAYERS`, modül yalnız alt katmana bağımlı; mutasyonla doğrulandı. Tüm ArchUnit kuralları artık sıkı, `FreezingArchRule` ve `archunit_store` kaldırıldı)  (~2 g)
- Tarama betiği (zsh'de `[ a \< b ]` hata veriyor; **bash** ile yaz) ile modül çiftleri arası iki yönlü import listesi çıkar; `pr↔repo`, `actions↔repo`, `pr↔commit/branch` şüpheli.
- Her çift için yön seç: alt seviye modül (repo/identity/commit/branch) üst seviyeyi (pr/actions/issue) **bilmez**; üst→alt port, alt→üst olay.
- Katman sıralaması belgele ve ArchUnit `layeredArchitecture` ile zorla:
  `shared` (çekirdek) < `identity` < `repo` < `commit, branch, tree` < `pr, issue, tag, task, wiki, snippet, discussion, filecomment, team, collaborator` < `actions, webhook, notification, ai, migration` < `auth, profile, admin` (kenar).
- **Kabul:** R8 (döngü yok) yeşil, dondurulmuş ihlal yok.

### A6: Kural seti, ADR ve doküman `[~]` (yapıldı: `docs/adr/0001-modular-monolith-rules.md` + CONTRIBUTING "Adding a module"; ARCHITECTURE.md üretimi karar gereği iptal; kalan: `@ApplicationModuleTest` izolasyon testleri — Spring bağlamı + DB gerektirir, ayrı iş)  (~1.5 g)
- `docs/adr/` altında kısa ADR'ler: (1) modül sınırı = `api`+`events`+named interface, (2) olay mı port mu karar ağacı (senkron cevap gerekiyorsa port; "oldu" bildirimi ise olay), (3) `shared` kapalı çekirdek, (4) JPA ilişki yerine UUID id, (5) uydu modül politikası.
- `ARCHITECTURE.md` üretimini (Documenter/PlantUML) `make architecture` hedefine bağla; CI'da güncel mi diye diff kontrolü (`mr-check.yml`).
- Yeni modül şablonu: `CONTRIBUTING.md` içinde "yeni modül nasıl eklenir" (package-info + `api` + izin listesi + test).
- Modül başına **bağımsız test dilimi**: `@ApplicationModuleTest` ile en az çekirdek modüllerde (`repo`, `pr`, `actions`, `webhook`) modül izolasyon testi (başka modüller mock/olay).
- İleri adım (opsiyonel, D10): Maven alt-modüllere bölme (`gitnode-identity`, `gitnode-repo` ...) yalnız bağımlılığı derleme zamanında zorlamak için; **şimdi kapsam dışı**, A1-A5 bittiğinde yeniden değerlendir.

### Mimari "10" için kabul listesi (hepsi gerekli)
1. Modül dışı kod `services/entities/repositories/mappers/configs/controllers` import etmez (R6; dondurulmuş ihlal = 0).
2. `allowedDependencies` içinde joker yok; her bağımlılık named interface ile beyan.
3. Modül döngüsü yok; katman sırası ArchUnit ile zorlanıyor.
4. Muaf modül listesi yalnız `repo` ve `identity` (sahipler).
5. Tüm domain modülleri aynı kalıpta (`collaborator` dahil).
6. Senkron etkileşim = port (`api`), asenkron/yan etki = olay; kural dokümante ve test edilmiş.
7. ADR + üretilmiş `ARCHITECTURE.md` + CI diff kontrolü.
8. Çekirdek modüllerde `@ApplicationModuleTest` izolasyon testleri.
9. e2e paketi her adımdan sonra yeşil (davranış değişmedi kanıtı).

---

## 6. FAZ E: Bellek

AI analizinin tek somut önerisi "üretimde izle" idi (M3 + P1 bunu karşılar). Aşağısı koddan çıkan gerçek riskler.

### M1: Her okuma için üst sınır `[x]` (blob preview 10MB, workflow YAML 1MB, snippet 10MB, commit diff 50 dosya: hepsi `gitnode.limits.*` property, varsayılanlar kodda; raw endpoint streaming; PR diff dosya sınırı UX etkilediği için eklenmedi; 200MB gerçek-çalıştırma denemesi yapılmadı; dal/etiket/release/PR-commit listeleri opsiyonel page/size + `gitnode.limits.list-max-items` (1000) + X-Total-Count, yanıt şekli değişmedi)
`ObjectLoader.getBytes()` büyük blob'da tüm içeriği heap'e alır (`getBytes()` JGit'te `LargeObjectException` atmaz; `getCachedBytes(limit)` atar).
- `TreeNonTxService:130,172`, `FileCommentService:284`, `FileDiffParser:196`, `CodebaseFileSampler:328`: `loader.getCachedBytes(MAX)` ya da `loader.isLarge()` kontrolü; büyükse "dosya çok büyük" yanıtı (blob için akış, M2).
- Ortak sabit: `shared/git` altında `GitLimits` (blob önizleme, diff, dosya yorumu, AI örnekleme sınırları) + `gitnode.limits.*` property'leri; mevcut dağınık sabitleri (`CodeOwners` 1 MB, `Wiki` 512 KB, `PrConflict` 512 KB) buraya topla.
- `SnippetFileStorageService` `Files.readString`: dosya boyutu sınırı (`Files.size` önce) + snippet yazma tarafında sınır doğrula.
- `commit` diff: dosya sayısı/satır/byte sınırı (çok büyük diff'te kes + "truncated" bayrağı). Kontrol et, yoksa ekle.
- Her yerde `findAll()`/sayfasız liste yoksa sayfalama zorunlu (`Pageable` max 100 kalıbı: `WorkflowDefinitionService.MAX_PAGE_SIZE`); sayfa boyutu sınırlayan ortak argüman çözücü.
- **Kabul:** 200 MB blob'lu repoda tree/blob/diff uçları OOM'a yol açmaz (yük testi/e2e). Her sınır için birim test.

### M2: Akış (streaming) `[ ]`  (~2 g)
- Blob indirme/raw: `StreamingResponseBody` + `loader.openStream()` (heap'e kopyalama yok). `TreeController` zaten stream kullanıyor (`OutputStream`); hangi uç tamponluyor kontrol et.
- Artifact / cache / release asset yükleme-indirme: `InputStream → dosya` kopyası sabit tamponla; `ByteArrayOutputStream` kullanımı yok (doğrulandı: yalnız `PrConflictService` (sınırlı) ve SSH anahtar kodlama).
- **multipart:** `max-file-size: 5GB` / `max-request-size: 6GB` global; asıl yol dosyayı diske yazar ama Tomcat geçici dizin dolabilir → `spring.servlet.multipart.location` ayrı hacim + `file-size-threshold`; artifact/cache/release ayrı kota (`max-bytes` mevcut yalnız release asset'te; artifact için `GITNODE_ACTIONS_ARTIFACTS_MAX_BYTES`, per-repo toplam kota). **Yeni özellik sayılabilir → eklemeden önce sor (D8).**
- Log yutma: runner'dan gelen log satırı uzunluğu sınırı (tek satır MB'larca olabilir) → `RunnerWebSocketHandler` max mesaj boyutu (`WebSocketContainer` `setMaxTextMessageBufferSize`) ve satır kesme.
- **Kabul:** 1 GB artifact yükleme/indirme sırasında heap artmaz (jcmd/JFR ile doğrula).

### M4: Bellek yük testi ve bütçe `[ ]`  (~1 g)
- P4 senaryolarıyla 30 dk soak testi: heap-after-GC trendi düz mü, SSE emitter sayısı abone sayısıyla orantılı mı (sızıntı kontrolü: kapanmayan emitter → `onCompletion/onTimeout/onError` zaten var; `Map` kaydı temizleniyor mu ölç).
- Konteyner limiti: `MaxRAMPercentage=75` + direct memory için `-XX:MaxDirectMemorySize`; compose'da `mem_limit` dokümante.
- **Kabul:** soak raporu bu dosyada; sızıntı yok.

---

## 7. FAZ F: Çok-instance dağıtım

### D1 kararı → S4: Depolama soyutlaması `[ ]`  (tasarım ~1 g, uygulama ayrı karar)
Karar önerim: **iki kademe**.
1. **Şimdi (kapsam içi):** artifact/release-asset/cache için `StorageBackend` arayüzü (`local` | `s3`) — `gitnode.actions.artifacts.store-type` alanı zaten var (`local`); `s3` uygulaması (MinIO uyumlu) ayrı PR. Böylece yalnız bare git repo'lar diskte kalır.
2. **Bare repo'lar:** çok-node için **paylaşımlı RWX hacim** (NFS/CephFS/EFS) belgele; repo başına yazma kilidi: JGit zaten `.lock` dosyalarıyla ref güncellemesini korur; **NFS üzerinde atomik rename/lock semantiğini** doğrula (test planı). Repo→node sabitleme (Gitaly benzeri) bu planın **dışında**, ayrı mimari karar; yalnız tasarım notu.
- `renameRepo`/`deleteRepo` (`RepoStorageService`) çok-instance'ta yarış: kilit anahtarı `repo:<id>` ile `DistributedLockService`.

### S5: Scale compose + 2-instance e2e `[ ]`  (~2 g)
- `docker-compose.scale.yml`: `gitnode` ×2 (aynı `gitnode-repos` hacmi), `nginx`/`traefik` LB, Postgres, Redis; SSE için `proxy_buffering off`, WS upgrade; `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase: 30s`.
- LB'de sticky gerekmiyor (JWT durumsuz, SSE/WS S2 ile dağıtık). Runner WS bir instance'a bağlı; runner yeniden bağlanma mantığı runner tarafında (Go) var mı kontrol et (instance ölürse).
- `.github/workflows/e2e.yaml`: ek job `e2e-scale` (2 instance) nightly.
- Senaryolar: (a) instance öldür → SSE istemcisi yeniden bağlanır, (b) runner'ı instance A'ya bağla, run'u B'den tetikle → job çalışır, log B'de akar, (c) `WorkflowScheduler` tek run üretir, (d) iki instance'tan aynı repoya eşzamanlı push.
- **Kabul:** yukarıdaki dört senaryo yeşil.

### S6: Küçük sertleştirmeler `[ ]`  (~1 g)
- `RateLimitService`: `INCR` + `EXPIRE` ayrı çağrı → arada çökerse TTL'siz anahtar kalır; tek Lua betiği ile atomik yap. Test.
- Hikari: `pool × instance sayısı < postgres max_connections` formülü dokümante; PgBouncer notu. Uzun işler (`ai`, `migration`, `profile backfill`) için bağlantıyı uzun tutmama kontrolü.
- Okuma-ağırlıklı uçlarda `@Transactional(readOnly = true)` denetimi (script: `@Service` public metot taraması).
- Büyük tablolar (audit, notification, run logları) için saklama/arşiv politikası (silme işi S1 kilitli cron ile). Yeni özellik sayılır → önce sor (D8).

---

## 8. Kararlar (2026-11-06 kesinleşti)

**Kesin:** D1 paylaşımlı RWX hacim · D2 evet (Repo*Listener, CacheInvalidationListener → Modulith) · D3 evet, tek PR · D5 `warn` kalsın · D6 sadece raporla · D8 kota/arşiv EKLENMEZ (M2 kota, S6 saklama çıktı) · D9 ayrı modül + port · D10 şimdi hayır · D11 named interface `dtos` · D12 `ARCHITECTURE.md`/`DEVAM_NOTU`/`KOD_HARITASI` gereksiz, A0 ve A6'daki üretim işi iptal (ADR kalır) · **Prometheus/Grafana (P1) iptal** · M3 bitti (Dockerfile bayrakları, `JGitWindowCacheConfig`, CONTRIBUTING notu).
Kalan açık: D4 (dump+çık uygulandı), D7 (yük testi sonrası).

Aşağıdaki tablo özgün öneri listesidir:

| ID | Soru | Önerim |
|---|---|---|
| D1 | Bare repo'lar çok-node: paylaşımlı hacim mi, repo sabitleme mi? | Paylaşımlı RWX hacim + artifact'lar S3'e; sabitleme kapsam dışı |
| D2 | `@Async` listener'lar hangileri kritik (kaybolmamalı)? | `Repo*Listener`, `CacheInvalidationListener` → Modulith listener; `AuditLogService` best-effort |
| D3 | Hata gövdesi `ProblemDetail`'e geçsin mi (frontend kırılır mı)? | Evet, ama önce frontend/e2e sözleşmesini kilitle, geçişi tek PR'da yap |
| D4 | OOM'da süreç çöksün mü (`ExitOnOutOfMemoryError`) yoksa heap dump mı? | İkisi: dump al + çık (orkestratör yeniden başlatır) |
| D5 | Prod log seviyesi (`dev.gitnode.os: warn`) çok kısıtlı mı? | Modül bazlı INFO (actions, webhook, auth) |
| D6 | Kullanılmayan DB nesnesi/index silinsin mi? | Sadece raporla, silme ayrı onay |
| D7 | Performans SLO'ları? | Yük testi tabanından sonra belirle (p95 hedefi) |
| D8 | Kota/arşiv politikası (M2, S6) yeni özellik; ekleyelim mi? | Önce sor, onay yoksa eklenmez |
| D9 | `auth`, `profile`, `admin` uydu modül kararı | `auth` ve `profile` ayrı modül + port; `admin` okuma yönlü kompozisyon modülü, kimse import etmez |
| D10 | Maven alt-modüllere bölme? | Şimdi hayır; A1-A5 sonrası değerlendir |
| D11 | `repo.dtos`: named interface mi, `api`'ye taşı mı? | Named interface (`dtos`), record-only kuralıyla |
| D12 | `ARCHITECTURE.md` / `docs/DEVAM_NOTU.md` nerede (çalışma ağacında yok)? | A0'da bul; bulunamazsa sor |

---

## 9. Tahmini efor ve bağımlılık

| Görev | Efor | Bağımlılık |
|---|---|---|
| P1 metrikler | 1 g | — |
| Q3 taban + CI raporu | 0.5 g | — |
| M3 bellek görünürlüğü | 0.5 g | — |
| S1 cron kilitleri | 1.5 g | — |
| S2 SSE fan-out | 3 g | P1 |
| S3 Caffeine sınırları | 0.5 g | — |
| Q1 hata modeli | 3-4 g | D3 |
| Q2 log/MDC | 1 g | — |
| Q4 ölü kod | 1 g | — |
| Q5 sınıf bölme | 5 g | Q3 (karakterizasyon testleri) |
| Q3 test yazımı (sürekli) | 6-8 g | — |
| P2 sorgu/N+1 | 3 g | P1 |
| P3 executor | 2 g | Q2, D2 |
| P4 yük testi + ayar | 3 g | P1 |
| M1 üst sınırlar | 2 g | — |
| M2 streaming | 2 g | M1, D8 |
| M4 soak | 1 g | P4, M3 |
| S4 depolama soyutlaması | 1 g tasarım | D1 |
| S5 scale compose + e2e | 2 g | S1, S2, S3 |
| S6 sertleştirme | 1 g | D8 |
| A0 doğrulama | 0.5 g | D12 |
| A1 kurallar (R6-R11) + dondurma | 1 g | A0 |
| A2 identity/repo yüzeyi daralt | 4-5 g | A1, D11 |
| A3 auth/profile/admin | 3 g | A2, D9 |
| A4 collaborator | 2 g | A2 |
| A5 döngü avı + katman kuralı | 2 g | A2 |
| A6 ADR + doküman + modül testleri | 1.5 g | A5 |
| **Toplam** | **~55 gün** (paralelleşebilir; mimari ~14 gün) | |

### Yarın başlangıç sırası (öneri)
1. D1-D12 cevapları (15 dk). Önce D12 / A0: eksik `ARCHITECTURE.md` ve `docs/DEVAM_NOTU.md` durumu.
2. **PR-1:** P1 + Q3 taban + M3 (ölçüm; riski düşük).
3. **PR-2:** S1 (cron kilidi; çift run hatasını kapatır).
4. **PR-3:** M1 (blob/diff üst sınırları; olası OOM'u kapatır).
5. **PR-4:** A1 (mimari kurallar + dondurma; sonraki mimari işleri güvenli kılar).
6. **PR-5:** S2 (SSE fan-out; 2 instance'ın ön şartı).
7. Sonra A2 → A3/A4 → A5 → A6, ardından Q/P fazları.

---

## 10. Başarı ölçütleri (skoru 9+ yapan şey)

| Eksen | Kanıt |
|---|---|
| Code Quality | `IllegalCatch` yalnız bildirimli yerlerde; yutulan catch 0; JaCoCo ≥ 70/60 CI'da zorunlu; 500+ satır sınıf 0; ölü kod raporu temiz; tek hata şeması |
| Performance | Prometheus/Grafana canlı; N+1 testle korunuyor; executor'lar ayrık ve izleniyor; k6 tabanı ve %30 iyileşme raporu |
| Scalability | 2-instance e2e yeşil; cron tek yürütme; SSE instance-bağımsız; yerel cache sınırlı; depolama soyutlaması + paylaşımlı hacim belgesi |
| Architecture | R6-R11 + katman + döngü kuralları CI'da; dışarıdan iç paket import 0; joker `::*` 0; muaf modül yalnız `repo`/`identity`; ADR + üretilmiş `ARCHITECTURE.md`; modül izolasyon testleri; tüm modüller aynı kalıpta |
| Memory | Tüm blob/diff/dosya okumaları sınırlı; büyük dosyalar akışla; heap dump + GC log; soak testi sızıntısız |

Skor yeniden doğrulaması: işler bitince aynı AI analizi tekrar çalıştırılır; bu tablo kanıt listesi olarak verilir.
