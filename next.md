Yüksek değer, mantıklı sıradaki işler
- CODEOWNERS: Dosya yoluna göre otomatik reviewer ataması ve "owner onayı zorunlu" branch protection kuralı. Mevcut branch protection ve reviewer altyapısına doğrudan oturur.
- Merge queue: Protected branch'e merge'leri sıraya koyar, her birini güncel main üstünde test eder.
- Auto-merge: PR'a "check'ler geçince otomatik merge et" işareti koyulur. Required checks zaten var.
- Signed commit doğrulama: GPG/SSH imza rozeti, branch protection'da "signed commits zorunlu" seçeneği.
- Rulesets / daha ince bypass: Şu anki bypass sadece repo owner için sabit kodlu. GitHub'daki gibi rol veya kullanıcı bazlı bypass listesi, tag koruması, force-push ve silme engeli eklenebilir.
- Required status checks için "branch up to date" kuralı: Merge'den önce PR'ın hedefle güncel olmasını zorunlu kılar.
- ork'tan upstream'e PR (büyük iş: izin modeli, branch'lerin farklı repo'dan okunması).

Orta
- Kod arama: Repo içi ve global dosya içeriği araması. Bu GitHub'ın en çok kullanılan özelliklerinden biri.
- Dependabot benzeri bağımlılık güncelleme PR'ları ve secret scanning: Push'ta sızan token'ları yakalar (push protection).
- Code scanning / SARIF yükleme: Actions çıktısını PR üstünde annotation olarak gösterir.
- Packages / container registry: npm, Maven ve Docker için. Self-hosted kullanımda çok işe yarar.
- Pages: Branch'ten statik site yayınlama.
- Wiki: Repo başına git-backed wiki.
- Discussions: Issue'dan ayrı, forum tarzı tartışma alanı.
- Reusable workflows / composite actions ve environments: Deploy onayı, environment secret'ları, concurrency grupları.
- Release notları otomasyonu: Merge edilen PR'lardan changelog üretimi.

Küçük ama fark edilen
- Issue/PR template'leri, .github benzeri org geneli default'lar.
- Reaksiyonlar (emoji), @mention bildirimleri, PR'da su uygula).
- Draft PR'dan "ready for review" akışı, PR'da dosya "viewed" işareti, diff'te split/unified görünüm.
- Organizations ve teams (repo izinlerini takım bazlı yor bazlı görünüyor.
- Contribution graph ve profil README'si.
- Repo insights: commit, contributor ve traffic grafikl
- Archive/transfer repo, Git LFS, repo şablonları (template repo).
- Notification için e-posta digest'i ve Slack/Teams ent
