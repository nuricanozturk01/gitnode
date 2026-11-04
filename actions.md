# GitNode Actions: GitHub uyumluluğu

Hedef: GitHub'da çalışan bir workflow GitNode'da değişmeden çalışsın; UI ve loglar GitHub gibi olsun.
Bu dosya yapılanı, **geriye kalanı** ve her biri için planı tutar. Hiçbir şey commit edilmedi.

## Önce bilinmesi gerekenler

- `uses:` içeren bir **job** (reusable workflow) şu an adımı olmadığı için sessizce başarılı görünür ve hiçbir şey yapmaz.
- `paths` / `paths-ignore` yok sayılır, workflow her push'ta çalışır.
- `on: release`, `create`, `delete` olan workflow'lar hiç tetiklenmez.

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
| Bir run, kuyruğa alındığı andaki workflow dosyasını çalıştırır (`workflow_run.workflow_content`) | ✅ |
| Geçersiz workflow dosyası dispatch'te açık hata verir | ✅ |
| UI: GitHub düzeni (sidebar, filtreler, run listesi, özet, job/step, graph), log grupları, annotations, timestamp, job summary, canlı süre, `prefers-reduced-motion`'da dönen spinner | ✅ |
| Sayfalama: run listesi ve workflow listesi (backend + frontend + e2e) | ✅ |
| DB index'leri (`V074`), büyük/küçük harf yüzünden kullanılmayan 3 index düzeltildi | ✅ |
| CI e2e: admin parolası yoksa senaryo atlanır, secret'tan okunur | ✅ |

Doğrulama: backend `./mvnw -o -pl gitnode-backend -am verify` (1115 test, checkstyle, jacoco, mimari), Go `golangci-lint` + `go test ./...`,
frontend `eslint` + `ng test` (50) + `ng build`, e2e API (164), uyumluluk derlemi (11 workflow).

## Geriye kalanlar ve planları

Sıralama olmazsa olmazdan başlar.

### 1. Reusable workflow (`workflow_call`): en kritik

**Sorun:** `jobs.<id>.uses: ./.gitnode/workflows/x.yml` ayrıştırılıyor ama çalıştırılmıyor.

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
- **Önerilen ek koruma:** özellik gelene kadar `uses:` olan job'u sessizce geçirmek yerine açık hata ile başarısız yapmak.

### 2. `paths` / `paths-ignore` ve `pull_request.types`

**Sorun:** `PushTriggerModel` ve `PullRequestTriggerModel` bu alanları tanımıyor, workflow her push'ta çalışır.

**Plan**
- Modele `paths`, `paths-ignore` (push ve PR), PR için `types` (varsayılan `opened`, `synchronize`, `reopened`).
- Değişen dosyalar: `WorkflowParserService`'e JGit ile `changedFiles(owner, repo, before, after)`; push için `before..after`,
  yeni branch'te varsayılan branch'e göre; PR için base..head. `RepoPushedEvent`'te önceki sha yoksa eklenir.
- Eşleme: mevcut `matchesGlob` (`**`, `*`, `!` dışlama). GitHub kuralı: `paths` varsa en az bir dosya eşleşmeli,
  `paths-ignore` varsa tüm dosyalar ignore'a uyuyorsa çalışmaz; ikisi aynı olayda birlikte kullanılamaz.
- PR `types`: `PullRequestCreatedEvent` dışında durum olaylarını (`PullRequestStatusChangedEvent`) dinleyip `closed`, `reopened` ayırmak gerekir.
- Test: filtre matrisi birim testi, JGit ile gerçek repo üzerinde değişen-dosya testi.

### 3. `release`, `create`, `delete` trigger'ları

**Plan**
- `create`/`delete`: `BranchCreatedEvent`, `BranchDeletedEvent` ve tag olayları zaten var; dinleyici + `WorkflowTriggerService` metodu + payload (`ref`, `ref_type`).
- `release`: yayın olayı `gitnode-events`'te yoksa eklenir (`RepoReleasePublishedEvent`), `types: [published, created, released, prereleased]`.
  Payload: `release.tag_name`, `release.name`, `release.prerelease`.
- Modele `release`, `create`, `delete`. Workflow'lar varsayılan branch'ten okunur (GitHub gibi).
- Test: model ayrıştırma, tür filtresi, tetikleme (`WorkflowRunTriggerTest` kalıbı).

### 4. Uyumluluk dokümanı

`docs/` altına "Desteklenenler / desteklenmeyenler" tablosu. Kaynak: bu dosyadaki tablolar ve uyumluluk derlemi.
Teknik olarak imkânsız olanlar açık yazılır: GitHub-hosted imajlardaki hazır araçlar, `GITHUB_TOKEN` ile GitHub.com API çağrıları,
Marketplace action'larının GitHub'a özel API'leri.

## Bilinen sınırlar (şu an böyle çalışıyor)

- `container:` olan job'larda `uses:` action'ları runner makinesinde çalışır; yalnızca `run:` adımları container içindedir.
  Workspace container'a **aynı yolla** bağlanır, bu yüzden dosya yolları iki tarafta aynıdır.
- `actions/download-artifact` `name` olmadan (tüm artifact'lar) desteklenmez.
- `github.token` / `secrets.GITHUB_TOKEN` sağlanmaz.
- `ubuntu-latest` gibi GitHub-hosted etiketler çevrilmez; iş, o etiketi taşıyan runner'ı bekler.
- Eski `workflow-detail` sayfası yeni arayüze taşınmadı (artık linklenmiyor).
- Workflow listesi her sayfa isteğinde git ağacından yeniden ayrıştırılır (sayfalama bellek içi dilimleme).
- `GET /api/actions/steps/{stepId}/logs` için repo erişim kontrolü yok; step UUID'sini bilen her oturum açmış kullanıcı okuyabilir.
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

## Yeni migration'lar (hepsi uygulandı)

`V068` run alanları (commit mesajı, attempt) · `V069` runner varsayılan etiketleri · `V070` job `outputs` ·
`V071` run `event_payload` · `V072` job `matrix_expression` · `V073` run `workflow_content` · `V074` Actions index'leri.
