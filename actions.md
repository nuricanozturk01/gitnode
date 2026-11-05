# GitNode Actions: GitHub uyumluluğu

Hedef: GitHub'da çalışan bir workflow GitNode'da değişmeden çalışsın; UI ve loglar GitHub gibi olsun.
Bu dosya yapılanı, **geriye kalanı** ve her biri için planı tutar. Kod değişiklikleri commit edildi; bu dosyanın kendisi son güncellemeyle birlikte commit bekliyor.

## Önce bilinmesi gerekenler

- `uses:` içeren bir **job** (reusable workflow) çalıştırılmaz. Sessizce geçmesin diye job **başarısız** olur, nedeni job'un
  log'unda yazar, ona `needs` ile bağlı job'lar atlanır (aşağıda "Yapılanlar").

## Yapılanlar

| Alan | Durum |
|---|---|
| Runner: `::group::`, `::error::`, `::warning::`, `::notice::`, `::debug::`, `::add-mask::`, `::set-output::`, `::save-state::`, `::stop-commands::`, `::echo::` | ✅ (`gitnode-runner/internal/log`) |
| Runner: stdout/stderr aynı akışta, satır tamponlu, Docker log çoklama (`stdcopy`) düzeltmesi | ✅ |
| Env: `GITHUB_ACTOR`, `GITHUB_EVENT_PATH`(+dosya), `GITHUB_API_URL`, `GITHUB_HEAD_REF/BASE_REF`, `GITHUB_RUN_ATTEMPT`, `GITHUB_ACTION`, `RUNNER_NAME`, `RUNNER_TOOL_CACHE`, `GITNODE_RUN_ID/JOB_ID/REPO_ID` | ✅ |
| `GITHUB_STEP_SUMMARY` → UI'da job summary (markdown) | ✅ |
| Job `outputs` ve `needs.<job>.outputs.<x>` | ✅ |
| Job `continue-on-error`, `defaults`, mutlak `working-directory` | ✅ |
| Matrix: sayı/bool/iç içe değerler, `include`/`exclude`, **dinamik matrix** (`fromJSON(needs.x.outputs.y)`) | ✅ |
| Context: `github.actor`, `github.event.*`, `head_ref`, `base_ref`, `run_attempt`, `server_url`, `api_url` | ✅ |
| `runs-on` etiketleri: `self-hosted`, OS, mimari otomatik; küçük harf; GitHub-hosted isim çevirisi **yok** (bilerek) | ✅ |
| `services:` (Docker ağı, health-check bekleme, port yayınlama, `job.services.<ad>.ports['5432']`) ve `container:` (run adımları job container'ında) | ✅ |
| `actions/cache` (+ `/restore`, `/save`), `actions/upload-artifact`, `actions/download-artifact` (glob, `!` dışlama, gizli dosya kuralı, `~`) | ✅ baştan yazıldı |
| Runner token'ı ile artifact/cache erişimi (`JwtAuthenticationFilter`, `ActionsStorageAccess`) ve repo sahipliği kontrolü | ✅ |
| `join()` / `contains()` dizi desteği (hem runner hem sunucu ifade motoru) | ✅ |
| `workflow_run` trigger'ı (3 seviye zincir sınırı) | ✅ |
| Re-run (hepsi / başarısızlar / tek job) | ✅ |
| Reusable workflow çağrısı (`jobs.<id>.uses`) sessizce geçmez: job başarısız olur, log'unda `##[error] Reusable workflows are not supported yet: ...` yazar, bağlı job'lar atlanır, run kuyrukta takılmadan `failure` ile kapanır | ✅ geçici koruma |
| Bir run, kuyruğa alındığı andaki workflow dosyasını çalıştırır (`workflow_run.workflow_content`) | ✅ |
| Geçersiz workflow dosyası dispatch'te açık hata verir | ✅ |
| UI: GitHub düzeni (sidebar, filtreler, run listesi, özet, job/step, graph), log grupları, annotations, timestamp, job summary, canlı süre, `prefers-reduced-motion`'da dönen spinner | ✅ |
| Sayfalama: run listesi ve workflow listesi (backend + frontend + e2e) | ✅ |
| DB index'leri (`V074`), büyük/küçük harf yüzünden kullanılmayan 3 index düzeltildi | ✅ |
| CI e2e: admin parolası yoksa senaryo atlanır, secret'tan okunur | ✅ |
| Docker build-push: `cache-from`/`cache-to` (`type=gha,mode=max`) virgülden bölünmüyor; `type=gha` uyarıyla atlanır | ✅ |
| Eşleşen runner yoksa iş log'unda "Waiting for a runner" adımı (`runs-on` hatası UI'da görünür) | ✅ |
| İptal: çalışan job'a runner'a `JOB_CANCELLED` gider, runner süreç grubunu öldürür (`internal/proc`), adımlar kapanır, runner BUSY'den çıkar, geç gelen "tamamlandı" iptali ezmez; `cancel-in-progress` aynı yolu kullanır (`JobCancellation`) | ✅ |
| `GET /api/actions/steps/{stepId}/logs` repo erişimini denetler (public repo herkese, private owner/collaborator) | ✅ |
| İstemci kopması (SSE) artık "Unhandled exception" loglamaz | ✅ |
| Runner Docker imajı (`gitnode-runner/Dockerfile`, `.gitnode/workflows/deploy-runner.yml`); UI ve docs `docker run` komutunu gösterir | ✅ (imaj Repsy'e henüz gönderilmedi) |
| Docs ve README'de "Known limitations": reusable workflow | ✅ |
| `paths` / `paths-ignore` (push ve PR): değişen dosyalar JGit ile (`WorkflowParserService.changedFiles`, `changedFilesOfPullRequest`), glob kuralları `PathFilters`; dosyalar bulunamazsa filtre uygulanmaz | ✅ |
| `pull_request.types` (`opened`, `synchronize`, `reopened`, `closed`; varsayılan ilk üçü); `PullRequestReopenedEvent` eklendi, `closed` birleştirilmiş PR için de çalışır | ✅ |
| `push` olayında `before` sha (`RepoPushedEvent.beforeSha`) ve event payload'unda `before` | ✅ |
| `create` / `delete` trigger'ları (git push, UI ve API ile branch/tag; `RepoRefCreatedEvent`, `RepoRefDeletedEvent`) | ✅ |
| `release` trigger'ı (`created`, `published`, `released`, `prereleased`; `ReleaseChangedEvent`); workflow başına tek run | ✅ |
| UI: run filtresi ve alt başlıkta `workflow_run`, `create`, `delete`, `release` | ✅ |
| Uyumluluk dokümanı `docs/actions-compatibility.md`; README ve docs sayfasında "Known limitations" | ✅ |

Doğrulama: backend `./mvnw -o -pl gitnode-backend -am verify` (1118 test, checkstyle, jacoco, mimari), Go `golangci-lint` + `go test ./...`,
frontend `eslint` + `ng test` (50) + `ng build`, e2e API (164), uyumluluk derlemi (11 workflow).

## Geriye kalanlar ve planları

Tek kalan özellik reusable workflow (aşağıdaki 1. madde).

### 1. Reusable workflow (`workflow_call`): en kritik (ertelendi: yaklaşık 2026-12-05'te yapılacak)

**Sorun:** `jobs.<id>.uses: ./.gitnode/workflows/x.yml` ayrıştırılıyor ama çalıştırılmıyor (şimdilik açık hata ile başarısız oluyor).

**Plan**
- Model: `OnTriggerModel.workflowCall` (`inputs`, `outputs`, `secrets`); `JobModel.uses/with/secrets` zaten var.
- Genişletme (`WorkflowTriggerService.createJobs`): çağıran job için çağrılan dosya aynı sha'dan okunur
  (`WorkflowParserService`'e tek dosya okuyan metot). İç job'lar `caller/inner` anahtarı ve `caller / inner` adıyla aynı run'a eklenir.
  İç `needs` önek alır; iç needs'i olmayan job'lar çağıranın `needs`'ine bağlanır.
- Migration `V075`: `workflow_job.call_inputs` (jsonb), `call_content` (çağrılan workflow'un kopyası), `virtual_job` (boolean).
- Çağıran job sanaldır (runner'a gitmez): iç job'ların hepsi bitince toplu sonuçla (failure > cancelled > skipped > success) tamamlanır,
  `on.workflow_call.outputs.<ad>.value` (`${{ jobs.<id>.outputs.<x> }}`) değerlendirilip job `outputs` olur.
- `inputs.*` bağlamı iç job'larda `with:` değerlerinden (+ varsayılanlar) gelir: `JobAdvancer.baseContext` ve `JobDispatcher.buildExpressionContext`.
- İş tanımı çözümü çağrılan içeriği de kapsamalı: `JobAdvancer.jobDefinition`, `JobDispatcher.resolveJobContext`, `buildEnvMap` (workflow düzeyi `env`/`defaults` çağrılan dosyadan).
- İç içe çağrı (derinlik ≤ 4), yalnızca yerel yol. `secrets:` şimdilik `inherit` gibi davranır.
- Çağıranda `if:` ve matrix ilk sürümde desteklenmez; açık hata verir.
- Test: genişletme, toplu sonuç, outputs, `inputs` bağlamı birim testleri; `testdata/compat/workflows/reusable.yml`.
- **Mevcut durum:** koruma yapıldı (`WorkflowTriggerService.createUnsupportedCallJob`). Bu özellik gelince o metot kaldırılır,
  `ReusableWorkflowGuardTest` gerçek genişletme testleriyle değiştirilir.

### Tamamlananlar (eski 2, 3, 4)

`paths` / `paths-ignore`, `pull_request.types`, `create` / `delete` / `release` ve uyumluluk dokümanı yapıldı ("Yapılanlar" tablosuna bak).
Bilerek bırakılanlar: `paths` ve `paths-ignore` aynı olayda birlikte verilirse yalnız `paths` uygulanır; tag push'larında path filtresi yoktur;
PR `labeled`/`edited`/... ve release `edited`/`deleted`/`unpublished` türleri hiç tetiklenmez; `release` aynı anda birden çok türe uyarsa workflow başına tek run (ilk eşleşen tür).

## Bilinen sınırlar (şu an böyle çalışıyor)

- `container:` olan job'larda `uses:` action'ları runner makinesinde çalışır; yalnızca `run:` adımları container içindedir.
  Workspace container'a **aynı yolla** bağlanır, bu yüzden dosya yolları iki tarafta aynıdır.
- `actions/download-artifact` `name` olmadan (tüm artifact'lar) desteklenmez.
- `github.token` / `secrets.GITHUB_TOKEN` sağlanmaz.
- `ubuntu-latest` gibi GitHub-hosted etiketler çevrilmez; iş, o etiketi taşıyan runner'ı bekler.
- Eski `workflow-detail` sayfası yeni arayüze taşınmadı (artık linklenmiyor).
- Workflow listesi her sayfa isteğinde git ağacından yeniden ayrıştırılır (sayfalama bellek içi dilimleme).
- `POST /api/actions/steps/{stepId}/logs` (runner'ın log yazması): herhangi bir geçerli runner token'ı herhangi bir step'e yazabilir; token step'in job'una bağlanmıyor.
- `JobDispatcher` / `JobAdvancer` içinde job tanımı çözümü run düzeyindeki workflow'a bakar (reusable workflow eklenirken değişecek).

## Nasıl doğrulanır

```bash
# Backend: derleme, test, checkstyle, jacoco, mimari testi
./mvnw -o -pl gitnode-backend -am verify

# Runner
cd gitnode-runner && golangci-lint run ./... && go test ./...

# Frontend
cd gitnode-frontend && npx eslint src && npx ng test --watch=false && npx ng build

# E2E (API)
cd e2e && GITNODE_API_BASE_URL=http://127.0.0.1:8080 E2E_ADMIN_PASSWORD=... npx playwright test -c playwright.config.ts --project=api

# Uyumluluk derlemi: çalışan backend + kayıtlı bir runner gerekir
python3 gitnode-runner/testdata/compat/run.py http://127.0.0.1:8080 <access-token> <kullanıcı> [workflow-adı ...]
VERBOSE=1 ...   # her step'in logunu da basar
```

Runner kaydı: `POST /api/actions/runners/registration-token`, sonra
`gitnode-runner start --server-url <url> --token <ghrt_...> --labels self-hosted --executor shell`.
Runner kayıtta `self-hosted`, OS ve mimari etiketlerini kendisi ekler (`linux`, `x64`).

CI e2e (`.github/workflows/e2e.yaml`) canlı API'ye karşı koşar; admin gerektiren senaryolar için
`E2E_ADMIN_USERNAME` ve `E2E_ADMIN_PASSWORD` repo secret'ları tanımlanmalıdır (yoksa senaryo atlanır).

## Yarın nereden devam

1. Tek kalan iş: reusable workflow (yaklaşık 2026-12-05). Sormadan ekleme yok.
2. Çalışan ortam gerekirse: Postgres/Redis container'ları (`gn-verify-pg` :15433, `gn-verify-redis` :16379), backend :8080, runner, `ng serve` :4200.
   Kapandıysa "Nasıl doğrulanır" bölümündeki komutlarla ve `gitnode-demo-environment` hafıza notundaki ortam değişkenleriyle yeniden açılır.
3. Kod commit edildi (`git log`); yalnızca `actions.md` güncellemesi commit bekliyor.

## Yeni migration'lar (hepsi uygulandı)

`V068` run alanları (commit mesajı, attempt) · `V069` runner varsayılan etiketleri · `V070` job `outputs` ·
`V071` run `event_payload` · `V072` job `matrix_expression` · `V073` run `workflow_content` · `V074` Actions index'leri.
