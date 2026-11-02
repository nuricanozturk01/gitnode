# GitHub benzerliği ve ürün boşlukları

Tarih: 31 Ekim 2026. Bu çalışma kaynak kod, şema, rota ve mevcut testlerin incelenmesine dayanır. Canlı uygulama ve ekranlar tarayıcıda incelenemediği için görsel değerlendirme şablon ve CSS ile sınırlıdır; erişilebilirlik, davranış ve güvenlik denetimi veya tam özellik envanteri değildir. “Bulunmadı” ifadesi incelenen kod yollarında saptanmadığını belirtir. GitHub karşılaştırmaları GitHub Docs'a dayanır.

## Uygulama durumu

31 Ekim 2026: Draft PR merge kontrolü, repo rename hata olayındaki owner, kullanıcı dizini hedefinin silinmesi, access/refresh token amaç ayrımı ve bağlı görev rotası düzeltildi. Bağlı özel proje görevlerinin yetkisiz gösterimi engellendi. Bildirim akışında devre dışı hesaplar için yeni abonelikler engellendi (mevcut bağlantıların hesap kapatılınca sonlandırılması ayrıca ele alınmalı); Docker frontend Node sürümü 24 ile hizalandı. PR güncelleme ve kapatma işlemlerine yazarlık/depo yetkisi kontrolü eklendi. PR yorum değişiklikleri URL içindeki depo ve PR ile sınırlandı. Backend testlerinin tamamı (704 test), Java biçim ve Checkstyle denetimi geçti. Yeni token türü nedeniyle eski oturumlar yeniden giriş yapmalıdır; refresh rotasyonu ve diğer maddeler açık kalmaktadır.

## Öncelikli bulgular

| Öncelik | Alan | Mevcut durum ve boşluk | Yapılacak iş |
|---|---|---|---|
| P0 — düzeltildi | Taslak PR | İlk incelemede UI taslak PR'ın birleştirilemeyeceğini söylüyor. `PullRequestController.merge` ve `PullRequestService.merge` içinde taslak kontrolü görünmüyor; servis yalnızca OPEN durumunu doğruluyor. Doğrudan API çağrısı ile doğrulama gerekli. | Backend'de draft kontrolü, API ve UI testleri. |
| P0 | Güvenli merge | Üç merge stratejisi var; fakat incelenen merge yolunda gerekli inceleme, CI kontrolü veya branch protection kuralı uygulanmıyor. Actions sonuçlarının görüntülenmesi zorunlu kontrol uygulanması değildir. | Sunucuda merkezi merge uygunluk kontrolü; atomic Git ref güncellemeleri ve eşzamanlılık testleri; Git HTTP/SSH push dahil korumalı branch kuralları. |
| P1 | PR review | Genel ve satır/dosya konumlu yorum modeli, diff ve yorum API'si var. Ayrı review talebi, onay/değişiklik isteği ve review kararları modeli saptanmadı. | Reviewer atama; approve/request changes/comment; review thread'leri, çözümleme ve güncel commit bazında onay durumu. |
| P1 | Issues | CRUD, durum, yorum, tek assignee ve project task bağlantısı var. Issue entity/DTO'sunda label, milestone, issue type ve çoklu assignee yok. | Label ve milestone yönetimi, gelişmiş filtreler, issue tipleri ve çoklu assignee; bağımsız permission ve migration testleri. |
| P1 | Tasarım temeli | Ana ve admin arayüzlerinde ayrı tema servisleri ve ayrı CSS temaları var; varsayılan koyu, 18px kök boyut ve grid zemin tanımlı. | Ortak tasarım tokenları ve bileşenler; tutarlı açık/koyu/sistem tercihi; tipografi, odak ve durum renkleri. |
| P1 | UI doğrulama | Playwright testleri ağırlıkla API ve Git senaryolarını kapsıyor; frontend'de az sayıda spec var. Canlı görsel test yapılmadı. | Temel kullanıcı yolculuklarında tarayıcı testleri, klavye ve ekran okuyucu kontrolü, responsive ve görsel regresyon kontrolleri. |

Kaynaklar: [PR merge](../gitnode-backend/src/main/java/dev/gitnode/os/pr/services/PullRequestService.java), [PR controller](../gitnode-backend/src/main/java/dev/gitnode/os/pr/controllers/PullRequestController.java), [issue modeli](../gitnode-frontend/src/app/domain/repository/models/issue.model.ts), [PR yorum modeli](../gitnode-frontend/src/app/domain/pull-request/models/pr-comment-info.model.ts), [tema](../gitnode-frontend/src/styles.css).

## Issue deneyimi

Mevcut işlevler: issue oluşturma, listeleme, açma/kapatma, güncelleme, silme, yorum CRUD, tek kişiye atama ve bağlı görevleri görme. GitHub'ın issue organizasyonu label, milestone, tür ve alt issue kavramlarını da içerir ([GitHub Docs](https://docs.github.com/en/issues/tracking-your-work-with-issues/learning-about-issues/about-issues)).

Önerilen eksikler ve sıralama:

1. Depo seviyesinde label ve milestone CRUD, issue'lara atama, listeleme ve API filtreleme; veri taşıma ve izinler.
2. Çoklu assignee ve kaldırma akışı; değişiklikleri kaydeden timeline. Issue ve PR arasında ortak metadata modeli.
3. Zengin Markdown editörü ve güvenli render, önizleme, mention, referans ve görev listesi; açıkça tanımlanmış izinler ve bildirimler.
4. PR ile issue bağlama, `Fixes #…` gibi kapanış ilişkileri ve merge sonrası otomatik kapanış; mevcut task bağlantısını koruma.
5. Issue şablonları, alt issue ve bağımlılık ilişkileri, toplu işlemler, gelişmiş arama. Bunlar temel iş akışından sonra ele alınmalı.

## Pull request deneyimi

Mevcut: draft alanı, branch seçimi, commits ve diff, genel/satır yorumları için veri alanları, Actions ve AI inceleme sekmeleri, merge/squash/rebase. Mevcut yeteneklerin tüm kenar durumları bu çalışmada test edilmedi. GitHub PR'larında review, kontroller, konuşmalar ve merge engelleri birlikte görünür ([GitHub PR](https://docs.github.com/en/pull-requests/reference/pull-requests), [review](https://docs.github.com/en/pull-requests/how-tos/review-pull-requests)).

- Review talebi, inceleme kararları, yeniden inceleme ve review geçmişi; kullanıcı yetkileri ve yeni commit sonrası geçerlilik kuralları.
- Satır yorumlarını konuşma dizileri olarak ele alma, yanıtlar, çözümleme, eski diff konumları ve önerilen kod değişikliği.
- Merge öncesi tek bir sunucu kaynaklı uygunluk özeti: taslak, çatışma, yetki, zorunlu review, çözülmemiş konuşma ve CI kontrolleri. UI yalnızca bu sonucu sunmalı.
- Branch protection/ruleset ve bypass yetkileri; doğrudan push, force push ve branch silme kontrollerini HTTP ve SSH dahil tüm girişlerde uygulama. GitHub'daki referanslar: [protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches) ve [status checks](https://docs.github.com/en/pull-requests/reference/status-checks).
- Sonraki aşamada CODEOWNERS, merge queue, otomatik merge, fork kaynaklı PR akışları ve conflict çözüm UX'i; mevcut kapsamları önce ayrıca doğrulanmalı.

## Diğer ürün alanları

- **Depo/kod:** Kod ve dosya arama, sembol arama, blame, kalıcı satır bağlantısı, büyük dosya ve ikili dosya deneyimi, varsayılan branch ve kurallar. Her maddenin mevcut desteği uygulama öncesinde ayrıca doğrulanmalı.
- **Projects:** Mevcut board ve task modelini issue/PR metadata'sıyla ilişkilendirmek; tablo/board görünümleri, filtreler, alanlar ve otomasyon açısından karşılaştırmak. GitHub Projects özelleştirilebilir alanlar ve görünümler sunar ([GitHub Docs](https://docs.github.com/en/issues/planning-and-tracking-with-projects/learning-about-projects/best-practices-for-projects)).
- **Actions:** Runner, workflow, log, secrets ve artifact altyapısı mevcut. GitHub Actions sözdizimi, event payload'ları, izinler, action uyumluluğu, secrets izolasyonu ve güvenilmeyen kodun yürütülmesi için ayrı uyumluluk ve tehdit modeli çalışması gerekir.
- **Bildirimler:** Mevcut bildirim/SSE altyapısını mention, atama, review talebi ve thread abonelikleriyle tamamlamak; çoklu instance davranışını ayrıca doğrulamak.
- **Organizasyon/izin:** Mevcut collaborator ve organizasyon özelliklerini ekip, rol, depo politikaları ve denetim kayıtlarıyla karşılaştırmak. Public/private erişimi tüm yeni API ve Git yollarında test etmek.
- **Ekosistem:** Discussions, packages, gelişmiş güvenlik taramaları ve GitHub API uyumluluğu büyük ayrı ürünlerdir. Hedef kapsamı belirlenmeden tam eşdeğerlik varsayılmamalı.

## UI yeniden tasarım yönü

Bu bölüm kod temelli bir başlangıç önerisidir; canlı ekran ve kullanıcı testiyle doğrulanmalıdır.

- GitHub'a aşina kullanıcının bulabileceği bilgi mimarisi: repo başlığı, kalıcı sekmeler, liste filtreleri, ayrıntı başlığı, zaman çizelgesi, yan metadata ve PR diff düzeni.
- Daha sakin, yoğunluğu ayarlanabilir ve içerik odaklı yüzeyler; büyük dekoratif arka plan ve gereksiz kart katmanları yerine anlamlı sınırlar. Açık ve koyu temaları aynı semantik tokenlarla tasarlamak; sistem tercihi ve kalıcı kullanıcı tercihi sağlamak.
- Ortak primitive'ler: buton, input, dropdown, dialog, tooltip, badge, tablo/liste, sekme, timeline, Markdown editor, avatar ve durum göstergesi. Ana uygulama ve admin paneli aynı temel kuralları paylaşmalı.
- Her sayfa için loading, boş, hata, yetkisiz ve başarılı durumlar; uzun adlar, küçük ekran, klavye kullanımı, görünür focus, kontrast ve reduced-motion davranışı.
- Önce repo ana sayfası, issue listesi/detayı ve PR listesi/detayı için desktop + mobil prototip; sonra ortak bileşenler ve diğer ekranlara kademeli geçiş. Tasarım doğrulanmadan tüm sayfaları aynı anda değiştirmemek.

## Önerilen uygulama sırası

1. P0 taslak merge doğrulaması ve kritik merge/yetki senaryoları için testler.
2. Tasarım sistemi ve repo shell; issue/PR ekranlarında tutarlı ilk uygulama.
3. Issue label/milestone/assignee ve filtreleme ile PR review modelleri.
4. Merge uygunluk API'si, branch koruma ve CI kontrol uygulaması; tüm erişim yollarında entegrasyon testleri.
5. Issue–PR bağlantıları, notification ve project entegrasyonu; ardından gelişmiş arama ve ekosistem özellikleri.

Her aşamada backend migration/API, yetki kontrolleri, UI durumları ve anlamlı testler birlikte teslim edilmeli. Efor ve kesin kapsam ancak ayrıntılı tasarım ve teknik incelemeden sonra tahmin edilmelidir.

## Mevcut sistemin GitHub'dan bağımsız teknik eksikleri

Bu bölüm koddan statik inceleme bulgularını içerir; test çalıştırılmadı. Öncelik, etki ve olasılığın ilk değerlendirmesidir.

### P0 — kimlik ve veri bütünlüğü

Aşağıdaki bulgular ilk incelemedeki durumu açıklar; uygulanan düzeltmeler ve açık kalan parçalar üstteki uygulama durumunda belirtilmiştir.

1. **Access ve refresh JWT'leri amaçlarına göre ayrılmıyor.** `JwtUtils.generateToken` ve `generateRefreshToken` aynı claim yapısını ve imzayı kullanıyor, yalnızca süreleri farklı; `JwtAuthenticationFilter` tür denetimi yapmıyor ve refresh endpoint'i de aynı genel doğrulamayı kullanıyor. Kod yoluna göre refresh token'ın normal API bearer token'ı, access token'ın da refresh girdisi olarak kabul edilmesi beklenir; uçtan uca testle doğrulanmalı. Token türü/audience kontrolü, refresh rotasyonu ve iptal/oturum yönetimi tasarlanmalı. [JwtUtils](../gitnode-backend/src/main/java/dev/gitnode/os/shared/auth/services/JwtUtils.java), [filtre](../gitnode-backend/src/main/java/dev/gitnode/os/auth/configs/JwtAuthenticationFilter.java), [refresh endpoint](../gitnode-backend/src/main/java/dev/gitnode/os/auth/controllers/AuthController.java).
2. **Geliştirme başlatma yolunda sabit sırlar var.** Makefile sabit JWT secret ve PostgreSQL parolası kullanıyor; Compose ayrıca veritabanını host portuna açıyor. Bunların geliştirme kolaylığı için olduğu varsayılabilir, ancak aynı ayarların paylaşımlı/üretim ortamına taşınması riskli. Ortama özel zorunlu secret, üretimde güvensiz varsayılanların reddi ve örneklerin açıkça ayrılması gerekir. [Makefile](../Makefile), [Compose](../docker-compose.yml).
3. **Repo yeniden adlandırma telafi olayı yanlış owner ile oluşturuluyor.** `RepoStorageService.renameRepo` hata durumunda `RepoRenameRollbackRequstedEvent(oldName, oldName, newRepoName)` çağırıyor; olayın ilk alanı `owner`, dinleyici bu alanı rollback'e geçiriyor. Dosya taşıma başarısızlığı simüle edilerek veri tabanı/disk tutarlılığı ve olayın transaction sınırları test edilmeli; parametre düzeltilmeli. [RepoStorageService](../gitnode-backend/src/main/java/dev/gitnode/os/shared/repo/services/RepoStorageService.java), [dinleyici](../gitnode-backend/src/main/java/dev/gitnode/os/repo/listeners/RepoRenamedEventListener.java), [olay](../gitnode-events/src/main/java/dev/gitnode/os/events/repo/RepoRenameRollbackRequstedEvent.java).
4. **Kullanıcı dizini yeniden adlandırılırken hedef dizin önceden siliniyor.** `renameBaseDir` hedefi `deleteRecursively` ile kaldırdıktan sonra eski dizini taşır. Hedefin gerçekten boş/ait ve güvenle silinebilir olduğu burada doğrulanmıyor; çakışma veya önceki hatalı durumlarda veri kaybı riski var. Başarısızlık enjeksiyonlu test, hedef kontrolü ve kurtarılabilir taşıma stratejisi gerekli. [RepoStorageService](../gitnode-backend/src/main/java/dev/gitnode/os/shared/repo/services/RepoStorageService.java).

### P1 — güvenilirlik ve işlev doğruluğu

5. **Issue ve PR numarası eşzamanlı oluşturmalarda yarışabilir.** İki servis de `max(number) + 1` kullanıyor. Issue şemasında repo/number unique kısıtı bulunduğundan eşzamanlı isteklerden biri çakışabilir; incelenen oluşturma yolunda kilit/retry görünmüyor. Depo bazlı atomik sayaç veya uygun kilit ve eşzamanlı entegrasyon testleri eklenmeli; PR şeması ayrıca test edilmeli. [IssueService](../gitnode-backend/src/main/java/dev/gitnode/os/issue/services/IssueService.java), [PullRequestService](../gitnode-backend/src/main/java/dev/gitnode/os/pr/services/PullRequestService.java), [issue şeması](../gitnode-backend/src/main/resources/db/migration/V005__issues.sql).
6. **Issue'daki bağlı görevin bağlantısı tanımlı rota ile uyuşmuyor.** Issue detay şablonu `/projects/:projectCode/tasks/:taskCode` bağlantısı oluşturuyor; router ise `/projects/:owner/:projectCode/tasks/:taskCode` bekliyor. Bağlantı verisine owner eklenip rota düzeltilmeli; UI yönlendirme testi yazılmalı. [şablon](../gitnode-frontend/src/app/features/repo/issues/issue-detail.page.html), [rotalar](../gitnode-frontend/src/app/app.routes.ts).
7. **Assignee kaldırma API'si belirsiz/eksik.** `IssueService.update` sadece `assigneeId != null` olduğunda atamayı değiştiriyor; null hem “değiştirme” hem “kaldır” olamadığından mevcut patch formuyla kaldırma görünmüyor. Açık kaldırma semantiği ve izin testleri eklenmeli. [IssueService](../gitnode-backend/src/main/java/dev/gitnode/os/issue/services/IssueService.java).
8. **Davet e-postası endpoint'i tamamlanmamış.** `send-invitation` doğrudan `UnsupportedOperationException` fırlatıyor. İşlev uygulanmalı veya kullanıcılara sunulmayıp açık bir desteklenmiyor yanıtı dönmeli. [CollaboratorController](../gitnode-backend/src/main/java/dev/gitnode/os/collaborator/controllers/CollaboratorController.java).
9. **Bildirim SSE çoklu instance ve transaction sınırları:** SSE aboneleri instance belleğinde; bildirim servisi kayıt yaptıktan hemen sonra yerel registry'ye gönderiyor. Başka instance'a bağlı istemci gerçek zamanlı olayı alamayabilir; ayrıca gönderim transaction commit'inden önce gerçekleşebilir. Kalıcı kayıt davranışı ile canlı iletimi ayırarak çoklu instance test edilmeli; commit sonrası ve instance'lar arası dağıtım uygulanmalı. [registry](../gitnode-backend/src/main/java/dev/gitnode/os/notification/services/NotificationSseRegistry.java), [servis](../gitnode-backend/src/main/java/dev/gitnode/os/notification/services/NotificationService.java).
10. **Olayların yeniden oynatımı ve telafi:** Yapılandırmada `republish-outstanding-events-on-restart: false`; bazı iş akışları asenkron olaylara dayanıyor. Yeniden başlatma veya listener başarısızlığında işlerin hangi operasyonla kurtarılacağı, idempotency ve DB/dosya sistemi tutarlılığı senaryo bazında netleştirilmeli. Bu ayar tek başına olay kaybı kanıtı değildir. [yapılandırma](../gitnode-backend/src/main/resources/application.yaml).
11. **Issue listesinde sorgu ölçeklenmesi:** Sayfadaki her issue için ayrıca yorum sayısı sorgulanıyor. Yük altında sorgu sayısı sayfa boyutuyla artar; toplu sayım/projection ve sorgu sayısını doğrulayan entegrasyon testi değerlendirilmeli. [IssueService](../gitnode-backend/src/main/java/dev/gitnode/os/issue/services/IssueService.java).

### P1 — kurulum, güvenlik ve işletim

12. **Docker frontend Node sürümüyle proje gereksinimi uyuşmuyor.** Dockerfile `node:22-alpine` kullanırken frontend `package.json` Node `>=24 <25` bildiriyor. Angular/pnpm derlemesinin bu imajda durumu test edilmeli ve Docker/CI/geliştirme sürümleri hizalanmalı. [Dockerfile](../Dockerfile), [package.json](../gitnode-frontend/package.json).
13. **PostgreSQL verisi için açık isimli volume yok.** Compose ve Makefile yalnızca PostgreSQL log volume'ünü bağlıyor; veri dizini için yönetilen isimli volume tanımlanmamış. İmajın anonim volume davranışı ve container kaldırma/yeniden oluşturma akışları veri korunmasını beklenmedik hale getirebilir. Açık veri volume'ü, yedekleme/geri yükleme ve yükseltme senaryoları tanımlanıp denenmeli. [Compose](../docker-compose.yml), [Makefile](../Makefile), [Postgres Dockerfile](../postgres/Dockerfile).
14. **Token'ların URL ve tarayıcı saklama yüzeyi:** OAuth ve SAML başarı işleyicileri access ve refresh token'larını yönlendirme query parametrelerine koyuyor; istemci bunları localStorage'da saklıyor. URL geçmişi/log/referrer ve XSS etkileri tehdit modeliyle değerlendirilmeli; kısa ömürlü tek kullanımlık kod alışverişi ve uygun cookie/session yaklaşımı incelenmeli. Mevcut durumda otomatik bir istismar testi yapılmadı. [OAuth handler](../gitnode-backend/src/main/java/dev/gitnode/os/auth/configs/CustomOauth2SuccessHandler.java), [SAML handler](../gitnode-backend/src/main/java/dev/gitnode/os/auth/configs/SamlAuthenticationSuccessHandler.java), [TokenService](../gitnode-frontend/src/app/core/auth/services/token.service.ts).
15. **Actions izolasyonu doğrulanmalı.** Runner shell ve Docker yürütücülerini destekliyor; örnek Compose runner Docker socket'i bağlıyor. Güvenilmeyen workflow'ların barındırıcıya, diğer işlere ve secret'lara erişimi için somut tehdit modeli, ayrı trust sınırları, kaynak limitleri ve kötü niyetli workflow testleri gerekli. Bu bulgu, mevcut yapılandırmada ispatlanmış bir kaçış iddiası değildir. [runner README](../gitnode-runner/README.md), [Compose](../docker-compose.yml).

### Ek bulgu — parola karmalama

Yerel hesap ve Git HTTP parolaları tuzlu SHA-256 ile doğrulanıyor. Parola için yavaş ve uyarlanabilir bir algoritmaya geçiş gerekir. Geçişte mevcut hash'ler desteklenmeli; başarılı girişte yeni biçime yükseltme, kayıt, parola değiştirme, bootstrap admin ve Git HTTP birlikte test edilmelidir. [AuthService](../gitnode-backend/src/main/java/dev/gitnode/os/auth/services/AuthService.java), [Git HTTP filtresi](../gitnode-backend/src/main/java/dev/gitnode/os/shared/git/http/HttpGitAuthenticationFilter.java).

### Ek bulgu — parola kurtarma

Parola kurtarma endpoint'leri genel API kimlik doğrulama kuralına takılıyor. Ayrıca `AuthService.getPasswordRecoveryCode` kodu oluşturuyor ancak e-posta gönderimi yer tutucu; kodun süresi ve tek seferlik güvenli iletimi için uçtan uca akış tamamlanmalı. Yalnızca endpoint'i herkese açmak çözüm değildir. [AuthService](../gitnode-backend/src/main/java/dev/gitnode/os/auth/services/AuthService.java), [SecurityConfig](../gitnode-backend/src/main/java/dev/gitnode/os/auth/configs/SecurityConfig.java).

### P2 — kalite ve bakım

16. Ana ve admin arayüzlerinde tema/CSS ve benzer auth akışları ayrı tutuluyor; zaman içinde tutarsızlık riski var. Ortak tasarım kütüphanesi ve paylaşılan erişilebilirlik kuralları oluşturulmalı.
17. API ve gerçek Git senaryoları mevcut; ancak UI journey, erişilebilirlik, eşzamanlılık, kesinti sonrası kurtarma, veri yedeği ve çoklu instance testleri genişletilmeli. SAML/LDAP E2E senaryoları belgede varsayılan workflow dışında belirtilmiş; gerçek CI kapsamı dağıtım hedefiyle uyumlu olmalı. [E2E senaryoları](../e2e/scenario/README.md).
18. Backend README'de `application-local.yaml.example` kopyalama adımı var; incelenen kaynak ağacında bu örnek dosya bulunmadı. Kurulum yönergeleri temiz ortamda baştan çalıştırılarak düzeltilmeli. [Backend README](../gitnode-backend/README.md).

### Güncellenmiş ilk teslim sırası

1. Token tür ayrımı ve üretim secret yapılandırması; repo rename/taşıma veri güvenliği ve draft merge kontrolü.
2. Kalıcı veritabanı yapılandırması, yedek/geri yükleme ve başarısızlık sonrası toparlanma testleri.
3. Issue/PR numaralandırması, bildirim dağıtımı, bağlı görev linki ve davet akışı.
4. UI tasarım sistemi ve temel ekranlar; ardından issue metadata, PR review ve korunmuş branch kuralları.

Bu öncelik sırası bulguların kod incelemesine dayanır; uygulamadan önce her kritik senaryo tekrarlanabilir testle doğrulanmalıdır.
