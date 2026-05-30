# Tags ve Releases — Çalışma Raporu

## Genel Bakış

OriginHub'da Tags ve Releases özelliği GitHub'dakine benzer şekilde çalışır. Tag'ler git
referansları (refs/tags/), Release'ler ise bunlara bağlı metadata kayıtlarıdır (veritabanında).

---

## Tag Nedir?

Bir tag, git geçmişindeki belirli bir commit'i işaret eden referanstır. İki türü vardır:

| Tür | Açıklama |
|-----|----------|
| **Lightweight** | Sadece commit SHA'sına işaret eden ref. Mesaj içermez. |
| **Annotated** | Tagger kimliği, tarih ve mesaj içeren ayrı bir git nesnesi (tag object). |

Tag oluşturma isteğinde `message` alanı dolu gönderilirse annotated, boş bırakılırsa lightweight
tag oluşturulur.

---

## Release Nedir?

Release; bir tag'e bağlı başlık, açıklama (Markdown), draft/prerelease bayrakları ve yayınlanma
tarihinden oluşan bir kayıttır. Bir release mutlaka var olan bir tag'e bağlıdır.

### Release Durumları

| Durum | Açıklama |
|-------|----------|
| **Draft** | Taslak. Yalnızca repo sahibi görür. Yayınlanma tarihi yok. |
| **Pre-release** | Test/RC sürümü. Herkese görünür ama "latest" sayılmaz. |
| **Published** | Tam sürüm. `/releases/latest` endpoint'i bunu döner. |

---

## API Endpoint'leri

### Tags

```
GET    /api/repos/{owner}/{repo}/tags           → tüm tag'leri listele
GET    /api/repos/{owner}/{repo}/tags/{tag}     → tek tag getir
POST   /api/repos/{owner}/{repo}/tags           → yeni tag oluştur
DELETE /api/repos/{owner}/{repo}/tags/{tag}     → tag ve bağlı release'i sil
```

**Tag oluşturma isteği:**
```json
{
  "name": "v1.0.0",
  "sha": "abc1234",       // opsiyonel; boş ise HEAD kullanılır
  "message": "v1.0.0"    // opsiyonel; dolu ise annotated tag
}
```

### Releases

```
GET    /api/repos/{owner}/{repo}/releases              → tüm release'leri listele
GET    /api/repos/{owner}/{repo}/releases/latest       → en son tam release
GET    /api/repos/{owner}/{repo}/releases/tag/{tag}    → tag'e göre release
GET    /api/repos/{owner}/{repo}/releases/{id}         → ID'ye göre release
POST   /api/repos/{owner}/{repo}/releases              → yeni release oluştur
PATCH  /api/repos/{owner}/{repo}/releases/{id}         → release güncelle
DELETE /api/repos/{owner}/{repo}/releases/{id}         → release sil
```

**Release oluşturma isteği:**
```json
{
  "tagName": "v1.0.0",
  "name": "Version 1.0.0",
  "body": "## What's new\n- Feature A\n- Bug fix B",
  "isDraft": false,
  "isPrerelease": false,
  "createNewTag": false,      // true ise önce tag oluşturulur
  "targetCommitish": "main",  // createNewTag=true ise kullanılır
  "tagMessage": ""            // createNewTag=true, annotated tag için
}
```

---

## Languages Özelliği

Repo ana sayfasında (`/`) dil çubuğu görünür. Bu çubuk:

1. Default branch'teki tüm dosyaları recursive olarak tarar.
2. Her dosyanın uzantısına bakarak dil belirler (`LanguageExtensionUtils`).
3. `plaintext` olarak tanımlanamayan dosyaları dışarıda bırakır.
4. Her dil için toplam byte sayısını hesaplar ve yüzde oranını döner.

```
GET /api/repos/{owner}/{repo}/languages?branch=main
→ [{ "language": "java", "bytes": 45000, "percentage": 72.3 }, ...]
```

---

## Migration Desteği

GitHub'dan migration yapılırken **Tags & Releases** seçeneği işaretlenirse:

1. GitHub API'den release listesi çekilir (`GET /repos/{owner}/{repo}/releases`).
2. Her release için tag mevcutsa release kaydı oluşturulur.
3. Tag henüz git reposunda yoksa önce tag oluşturulur, ardından release kaydı eklenir.

---

## Edge Case'ler

| Durum | Davranış |
|-------|----------|
| Aynı isimde tag zaten var | `409 Conflict` hatası döner |
| SHA bulunamıyor | `404 Not Found` hatası döner |
| Boş repo (commit yok) | Tag listesi boş döner; tag oluşturmak `500` ile başarısız olur |
| Tag silinirse bağlı release de silinir | Cascade ile otomatik temizlenir |
| Draft release'ler | Yalnızca repo sahibine görünür (frontend filtrelemesi) |
| Tag ile release farklı kişiler tarafından silinmeye çalışılır | `403 Forbidden` döner |
| GitHub migration'da release tag'i repo'da yoksa | Tag önce oluşturulur, ardından release kayıt altına alınır |
| PR migration'da branch zaten varsa | Mevcut branch kullanılır, hata yutulur |
| PR migration'da kaynak branch yoksa | Default branch (main) üzerinden oluşturulur |

---

## Özet Akışlar

### Yeni Release Oluşturma (Tag Mevcut)

```
POST /releases
  └─ TagNonTxService.get() → tag var mı doğrula
  └─ ReleaseTxService.create() → DB'ye kaydet
  └─ publishedAt = şimdiki zaman (draft değilse)
```

### Yeni Release Oluşturma (Tag Yok)

```
POST /releases (createNewTag: true)
  └─ TagNonTxService.create() → git ref oluştur
  └─ ReleaseTxService.create() → DB'ye kaydet
```

### Tag Silme

```
DELETE /tags/{tag}
  └─ JGit RefUpdate.delete()
  └─ ReleaseTxService.deleteByTagName() → ilişkili release DB'den silinir
```
