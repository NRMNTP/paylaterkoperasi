# PRD.md — PayLater Koperasi Pesantren

**Product Requirements Document**
Sistem manajemen kredit/PayLater untuk koperasi pondok pesantren (Kopontren Darul 'Ulum).

Status: Migrasi dari MIT App Inventor → rencana native Android (Kotlin, dikerjakan via Claude Code/Android Studio). Backend Google Apps Script **tetap dipakai, tidak berubah**.

---

## 1. LATAR BELAKANG & TUJUAN

### 1.1 Masalah yang Dipecahkan
Koperasi pondok pesantren (Kopontren Darul 'Ulum) menjalankan sistem "PayLater" — santri bisa berhutang untuk kebutuhan harian dengan limit tertentu, dan kasir mencatat transaksi hutang/pembayaran secara manual. Sebelumnya sistem ini dijalankan di atas **AppSheet**, namun versi gratisnya tidak cukup (butuh upgrade berbayar untuk fitur yang dibutuhkan).

### 1.2 Tujuan Proyek
Membangun aplikasi Android mandiri (APK) yang:
- Gratis sepenuhnya (tanpa biaya platform/lisensi)
- Menggantikan seluruh fungsi AppSheet lama
- Tampilan modern, minimalis, hijau-putih (identitas Kopontren)
- Dipakai oleh **kasir** sebagai pengguna utama (operasional harian)
- Terhubung ke Google Sheets sebagai database, lewat Google Apps Script sebagai API perantara

### 1.3 Riwayat Teknologi
1. **Awal:** Google Sheets dengan rumus kompleks (array formula: MAP, LAMBDA, LET, REDUCE, SCAN) untuk perhitungan limit, status, dsb.
2. **Percobaan 1:** AppSheet (gratis) — terbentur batasan fitur tanpa upgrade berbayar.
3. **Dipertimbangkan:** Glide — ditolak karena versi gratis butuh sinkronisasi berbayar.
4. **Dipilih & dibangun:** MIT App Inventor (frontend visual blok) + Google Apps Script (backend API) + Google Sheets (database) — gratis penuh.
5. **Migrasi saat ini:** dari App Inventor → Android native (Kotlin), karena App Inventor mencapai batas kemampuannya (lihat §8 Known Issues & Limitations).

---

## 2. PENGGUNA & PERAN

| Peran | Akses |
|---|---|
| **Kasir** | Login, lihat data santri, catat transaksi (hutang/bayar), lihat laporan, lihat tagihan |
| **Pengurus** | Sama seperti kasir, kemungkinan akses laporan lebih luas (level "Jabatan" dibedakan di database, belum ada pembatasan fitur spesifik per jabatan di versi ini) |

Login berbasis tabel `Data Kasir` (Username + Password + Jabatan), tanpa role-based access control granular — baru sebatas pencatatan jabatan.

---

## 3. ARSITEKTUR SISTEM

```
┌─────────────────────────────────────────────┐
│  APLIKASI ANDROID (rencana: Kotlin native)  │  <- Presentation Layer
│  Login, Dashboard, Data Santri, Transaksi,  │
│  Detail Santri, Form Transaksi, dst         │
└──────────────────┬──────────────────────────┘
                   │  HTTP GET (query string)
                   ▼
┌─────────────────────────────────────────────┐
│  GOOGLE APPS SCRIPT — Web App (doGet)       │  <- API / Business Logic Layer
│  9 endpoint, format request-response JSON   │
└──────────────────┬──────────────────────────┘
                   │  SpreadsheetApp API
                   ▼
┌─────────────────────────────────────────────┐
│  GOOGLE SHEETS — 8 sheet                    │  <- Data Layer
│  Data Kasir, Data Santri, Catatan Transaksi,│
│  Home, Lap Harian/Mingguan/Bulanan, dst     │
└─────────────────────────────────────────────┘
```

**Prinsip penting:** backend (Apps Script + Sheets) bersifat **platform-agnostic** — tidak peduli frontend-nya App Inventor atau Android native, karena komunikasi murni lewat HTTP GET + JSON. Ini yang membuat migrasi frontend memungkinkan tanpa menyentuh backend sama sekali.

---

## 4. BACKEND — GOOGLE APPS SCRIPT API

### 4.1 Identitas Resource
```
Spreadsheet ID : 19HqN-edinNOjBHreuxk9ZVLI_icSyLWdvVVD6uXRW1c
Web App URL    : https://script.google.com/macros/s/AKfycbxfQTXiiIBi3uvMNTVe93gwV8EYLyQC5D_UAycPN05iLXvCM4-3h2O2NGQPU6RYjOwl/exec
File backend   : Code.gs (sudah final untuk Tahap 1-6, lihat repo/lampiran)
```

### 4.2 Pola Umum
- Semua request: `GET {URL}?action={nama_endpoint}&param1=...&param2=...`
- Semua response: JSON, minimal berisi `{ "status": "success" | "error", ... }`
- Routing terpusat di `function doGet(e)` — membaca `e.parameter.action`, memanggil fungsi terkait, membungkus hasil jadi `ContentService` JSON

### 4.3 Daftar Endpoint

| Endpoint | Parameter | Fungsi | Response Kunci |
|---|---|---|---|
| `login` | `username`, `password` | Validasi kasir terhadap sheet Data Kasir | `username`, `jabatan` |
| `getSantri` | — | Daftar seluruh santri aktif (filter baris kosong & "DONT DELETE") | `data[]` (nama, level, limit, hutangAktif, sisaLimit, status, totalLunas, telatTerparah, poinTepat) |
| `getDetailSantri` | `nama` | Profil lengkap 1 santri + riwayat transaksinya | `profil{}`, `riwayat[]` |
| `getTransaksi` | `filter` (All/Hutang/Bayar), `limit` (default 40) | Daftar transaksi, **dibatasi jumlah** untuk performa mobile | `data[]`, `total`, `totalSemua` |
| `tambahTransaksi` | `nama`, `nominal`, `kasir` | Catat hutang baru — dengan validasi limit & status Beku | `message` |
| `bayarHutang` | `nama`, `nominal`, `kasir` | Catat pembayaran | `message` |
| `getLaporan` | `tipe` (Harian/Mingguan/Bulanan) | Data laporan dari sheet rekap terkait | `data[]` |
| `getTagihan` | — | Daftar santri dengan hutang aktif + urgensi jatuh tempo | `data[]` (terurut: telat → kritis → segera → normal) |
| `getDashboard` | — | Ringkasan agregat untuk layar utama | `dashboard{}` (totalSantri, santriBeku, totalPiutang, santriOverLimit, dst) |

### 4.4 Fungsi Helper Penting
- `getSheetData(namaSheet)` — baca seluruh range sheet
- `arrayToObjects(data)` — ubah array 2D jadi array of object, header di-trim
- `formatTanggal(value)` — **robust**, menangani 3 kemungkinan tipe nilai tanggal dari Sheets: objek `Date` (paling umum untuk kolom datetime), string sudah berformat ("DD/MM/YYYY" atau ada "/"), dan serial number Excel/Sheets
- `formatRupiah(angka)` — format ke "Rp X.XXX"
- `getTodaySerial()` / `getNowSerial()` — untuk perhitungan tanggal jatuh tempo & timestamp

### 4.5 Validasi Bisnis di Backend
- `tambahTransaksi` menolak jika: nama kosong, nominal ≤ 0, santri tidak ditemukan, **status akun Beku**, atau **nominal melebihi sisa limit**
- `bayarHutang` menolak jika: nominal ≤ 0, atau santri tidak punya hutang aktif berstatus "Hutang"

---

## 5. DATABASE — GOOGLE SHEETS (8 SHEET)

### 5.1 Data Kasir
| Kolom | Tipe | Catatan |
|---|---|---|
| Username | manual | |
| Password | manual | plaintext (keterbatasan disengaja untuk simplisitas internal) |
| Jabatan | manual | |

### 5.2 Data Santri (13 kolom, Primary Key: Nama Santri)
| Kolom | Sumber | Catatan |
|---|---|---|
| Level | manual | |
| Level_ | formula | auto, dari riwayat |
| Limit | manual | |
| Limit_ | formula | **paling kompleks** — MAP+LAMBDA+LET+REDUCE+SCAN, hitung volume loyalitas + poin + sanksi per segmen waktu |
| Total Hutang Aktif | formula | SUMIF dari Catatan Transaksi |
| Sisa Limit | formula | Limit − Hutang Aktif |
| Status | formula | "Aktif" / "Beku" — otomatis cek jatuh tempo terlewat |
| Total Lunas | formula | |
| Total Bayar Awal | formula | |
| Telat terparah | formula | MAX hari telat historis |
| Tanggal Bebas Beku | manual | |
| Poin Tepat Waktu | formula | COUNTIFS |

### 5.3 Catatan Transaksi (14 kolom)
**PENTING — nama kolom asli (case-sensitive, ditemukan lewat debugging, BUKAN tebakan):**
```
1.  Nama Santri
2.  Nominal
3.  Tanggal transaksi          <- "t" kecil pada "transaksi"
4.  Tanggal Jatuh Tempo
5.  Tanggal Bayar
6.  status                     <- huruf kecil semua
7.  Status kedisplinan         <- ejaan asli (typo dipertahankan, BUKAN "kedisiplinan")
8.  Jumlah Hari Telat
9.  Kategori Waktu
10. Username Kasir
11. Saldo Berjalan
12. Sisa Tagihan
13. Hari Telat
14. Telat Berjalan
```
⚠️ **Catatan migrasi krusial:** kesalahan asumsi nama kolom (mengira "Status" huruf besar, "Tanggal Transaksi" huruf besar semua) adalah sumber bug besar di masa lalu (filter transaksi gagal total). Kode baru di Claude Code **WAJIB** memakai nama persis di atas, atau menambah lapisan normalisasi nama kolom (case-insensitive matching) di awal proses ETL data Sheets.

Kolom `Tanggal transaksi` dan `Tanggal Jatuh Tempo` berisi **objek datetime asli Sheets** (bukan string atau serial polos) — backend `formatTanggal()` sudah menangani ini, pertahankan logikanya bila backend di-refactor.

Kolom tujuh diisi formula:
- `Tanggal Jatuh Tempo` = otomatis dari hutang belum lunas pertama + 7 hari (array formula MAP+SCAN+FILTER)
- `status kedisplinan`, `Jumlah Hari Telat`, `Kategori Waktu` = BYROW formula, kategori: ≤2 jam = "Awal Waktu", ≤7 jam = "Tepat Waktu", >7 jam = "Telat"

Baris bertanda **"DONT DELETE"** adalah anchor formula array — wajib difilter di setiap query (`!= "" && != "DONT DELETE"`).

### 5.4 Sheet Lainnya
Home (navigasi AppSheet lama, tidak relevan untuk Android), Lap Harian, Menu Laporan, Lap Mingguan, Lap Bulanan.

### 5.5 Aturan Bisnis Limit per Level
```
Pemula     : Rp 10.000
Terpercaya : Rp 20.000 – 30.000
Prioritas  : Rp 40.000
VIP        : Rp 50.000
```
Sanksi keterlambatan menurunkan limit otomatis (via formula `Limit_`). Status "Beku" = ada hutang aktif yang sudah melewati tanggal jatuh tempo.

### 5.6 Keterbatasan Google Sheets Mobile (ditemukan saat development awal)
- `COUNTIFS`/`SUMPRODUCT` dengan variabel `MAP LAMBDA` gagal di versi mobile
- Hasil `FILTER` tidak bisa disimpan sebagai variabel `LET` perantara
- Workaround: `SUM(IF(...))` di-inline penuh, atau kolom bantu (helper columns N–R)
- Paste formula multi-baris di mobile bisa korup — wajib format satu baris

---

## 6. FITUR APLIKASI (FUNCTIONAL REQUIREMENTS)

### 6.1 Login
- Input username + password → validasi ke endpoint `login`
- Simpan sesi (username, jabatan) di local storage perangkat
- Redirect ke Dashboard bila sukses; tampilkan pesan error spesifik bila gagal

### 6.2 Dashboard (Homescreen)
- Header: avatar kasir (dapat diklik → info akun + logout) + sapaan + nama kasir
- 4 kartu ringkasan real-time dari `getDashboard`: Total Santri, Total Piutang, Akun Beku, Over Limit
- 4 menu navigasi: Data Santri, Catatan Transaksi, Laporan, Tagihan Santri

### 6.3 Data Santri
- List seluruh santri (card): nama, level, info hutang/sisa limit, indikator status (Aktif = hijau, Beku = merah, seluruh teks card berubah merah saat Beku)
- **Live search** by nama (filter instan saat mengetik, tanpa request ulang ke server)
- Klik nama santri → buka Detail Santri
- Pull-to-refresh atau tombol refresh manual

### 6.4 Detail Santri
- Profil lengkap: level, status akun, limit, hutang aktif, sisa limit, total lunas, telat terparah
- Riwayat transaksi santri tersebut

### 6.5 Catatan Transaksi
- List transaksi (card): nama, nominal, tanggal, badge status (Hutang = merah, Bayar = hijau)
- **Tab filter**: Semua / Hutang / Bayar — direkomendasikan filter **client-side** (lihat §8.3) dari data yang sudah di-load, bukan request API berulang per tab
- Live search by nama
- Tombol "+" (FAB, mengambang kanan-bawah) → buka Form Transaksi
- Data dibatasi (mis. 40 transaksi terbaru) untuk menjaga performa — opsi "muat lebih banyak" adalah peningkatan yang disarankan untuk versi native

### 6.6 Form Tambah Transaksi
- Input nama santri: **kombinasi ketik bebas + autocomplete/dropdown** dari daftar santri (mencegah typo nama) + opsi pilih cepat dari daftar transaksi terbaru
- Validasi: nama wajib ada di daftar santri resmi (bukan ketik bebas tanpa validasi), nominal wajib > 0
- Input nominal
- Tombol "TAMBAH" memicu alur konfirmasi 2 langkah:
  1. **Popup pilih jenis**: Hutang atau Bayar
  2. **Popup konfirmasi ringkasan**: tampilkan nama, nominal, jenis → tombol Batal / Ya Simpan
- Setelah konfirmasi → panggil `tambahTransaksi` atau `bayarHutang` sesuai pilihan
- Tampilkan hasil (sukses/gagal) dan reset form

> **Keputusan desain penting:** form sengaja **tidak** memakai dua tombol terpisah ("Hutang" / "Bayar") langsung di layar utama form, karena rawan salah tekan oleh kasir. Alur konfirmasi berlapis dipilih khusus untuk mengurangi risiko human error pada operasi yang berdampak finansial.

### 6.7 Tagihan Santri *(backend sudah siap, UI belum dibangun — Tahap 7)*
- List santri dengan hutang aktif, terurut berdasarkan urgensi (telat → kritis ≤2 hari → segera ≤5 hari → normal)

### 6.8 Laporan *(backend sudah siap, UI belum dibangun — Tahap 8)*
- Laporan Harian / Mingguan / Bulanan, kemungkinan dengan chart batang

---

## 7. DESAIN VISUAL (RINGKASAN — detail penuh di DESIGN.md terpisah)

- **Identitas warna:** hijau utama `#2E7D32`, gradasi `#1B5E20 → #2E7D32 → #43A047`, dasar putih/abu `#F5F5F5`
- **Logo:** Kopontren Darul 'Ulum, tersedia 3 varian olahan (siluet putih untuk di atas hijau, badge berwarna dalam lingkaran putih, versi transparan lengkap)
- **Gaya Login:** card putih "melayang" bersudut membulat + bayangan, di atas background gradasi hijau, logo + judul di atas card
- **Gaya Dashboard:** header hijau dengan avatar bulat kasir, grid 2×2 kartu ringkasan, grid 2×2 menu navigasi dengan warna pastel berbeda per kartu
- **Gaya Card List** (Data Santri/Transaksi): card putih, indikator warna status (dot/teks merah-hijau), nama santri berfungsi sekaligus sebagai elemen tap-to-detail

---

## 8. KNOWN ISSUES, KETERBATASAN, & PELAJARAN DARI APP INVENTOR

Bagian ini krusial untuk dibaca sebelum menulis ulang di Android native — agar kesalahan yang sama tidak terulang.

### 8.1 Bug Backend yang Sudah Diperbaiki (jangan diulang)
1. **Case-sensitivity nama kolom** — kode sempat memakai `"Status"` padahal kolom asli `"status"` (huruf kecil); juga `"Tanggal Transaksi"` vs asli `"Tanggal transaksi"`. Solusi final: akses langsung dengan nama kolom **persis seperti di §5.3**, atau bangun layer normalisasi (lowercase + trim) di awal.
2. **Spasi tersembunyi pada nama kolom** ("Nama Santri " dengan trailing space) — solusi: selalu `.trim()` saat membaca header sheet.
3. **Tanggal jadi NaN** — disebabkan kolom datetime di Sheets dikembalikan sebagai objek `Date` oleh Apps Script API, bukan string/serial. Fungsi `formatTanggal` backend sudah menangani 3 kasus (Date object, string ber-"/", serial number) — pertahankan logika ini di endpoint manapun yang dipakai ulang/ditulis ulang.
4. **OutOfMemoryError** di sisi klien lama (App Inventor) akibat me-render >1000 item sekaligus dari total >3800 baris transaksi di database. **Wajib** pagination atau limit di level API (sudah diterapkan: default `limit=40` pada `getTransaksi`) — pertimbangkan infinite-scroll/pagination proper di Android native menggantikan limit statis ini.

### 8.2 Keterbatasan App Inventor yang Jadi Alasan Migrasi
- Tidak ada dukungan native untuk card list dinamis yang performan — solusi App Inventor (ekstensi DynamicComponents + skema JSON) rapuh, sulit di-debug, dan rawan crash pada dataset besar
- Tidak ada overlay/Z-index — tidak mungkin membuat elemen klik menutupi card sepenuhnya (workaround: button transparan jadi BAGIAN dari card, bukan overlay penuh)
- Tidak ada gradient/border-radius/shadow native — disiasati dengan gambar PNG pre-rendered (lihat §7)
- Variable scope membingungkan (local vs global) menyebabkan banyak bug "variable not bound"
- Tidak ada package manager / dependency proper, semua ekstensi `.aix` manual
- Debugging sangat lambat: error message generik (mis. "no method named X in class Boolean") tidak menunjuk lokasi blok yang salah, harus ditelusuri manual lewat screenshot

### 8.3 Rekomendasi Spesifik untuk Implementasi Android Native
- **Filter tab (Semua/Hutang/Bayar) sebaiknya client-side** — load sekali dari API, filter lokal di memori. Mengurangi request berulang & mempercepat UX. (Di App Inventor versi terakhir, ini adalah solusi yang berhasil setelah filter server-side awalnya gagal karena bug nama kolom.)
- **RecyclerView + DiffUtil** untuk list Data Santri & Transaksi — gantikan pendekatan "hapus semua card lalu render ulang" yang dipakai App Inventor (mahal & rawan flicker)
- **Pagination/infinite scroll** untuk Catatan Transaksi (3800+ baris) — gantikan limit statis 40
- **Local caching** (Room DB atau DataStore) untuk data santri — mempercepat buka app & mendukung mode hampir-offline
- **Autocomplete nama santri** di Form Transaksi sebaiknya pakai komponen native (`AutoCompleteTextView` atau custom Compose) — jangan replikasi pendekatan manual ListView+TextChanged yang dipakai App Inventor
- **State management eksplisit** untuk alur popup berlapis (pilih jenis → konfirmasi) — di Android native ini jauh lebih natural lewat ViewModel/state machine dibanding trik "flag variable" yang terpaksa dipakai di App Inventor untuk membedakan callback popup

---

## 9. STATUS PENGEMBANGAN SAAT MIGRASI

| Tahap | Cakupan | Status |
|---|---|---|
| 1 | Backend Apps Script (9 endpoint) | ✅ Selesai, stabil |
| 2 | Setup App Inventor + koneksi API | ✅ Selesai (tidak relevan lagi pasca migrasi) |
| 3 | Login Screen | ✅ Selesai (App Inventor) — desain jadi acuan |
| 4 | Homescreen Dashboard | ✅ Selesai (App Inventor) — desain jadi acuan |
| 5 | Modul Data Santri | ✅ Selesai (App Inventor) — desain & logika jadi acuan |
| 6 | Modul Catatan Transaksi + Form Tambah | ⚠️ Hampir selesai — list & filter berfungsi, Form Transaksi tersusun namun **masih ada bug aktif** (error `UsedIDs`/komponen salah-sambung di prosedur hapus-card; perlu diverifikasi ulang transaksi benar-benar tersimpan ke Sheets end-to-end) |
| 7 | Modul Tagihan Santri | ⬜ Backend siap (`getTagihan`), UI belum dibangun |
| 8 | Modul Laporan | ⬜ Backend siap (`getLaporan`), UI belum dibangun |
| 9 | Fitur tambahan & animasi (splash screen, transisi, optimasi loading) | ⬜ Belum dimulai |
| 10 | Build & rilis APK final | ⬜ Belum dimulai |

**Keputusan migrasi:** Tahap 1 (backend) dipertahankan 100%. Tahap 2 tidak relevan untuk Android native. Tahap 3–6 ditulis ulang dari nol di Kotlin, dengan desain visual (§7) dan struktur data (§4–5) sebagai spesifikasi acuan — bukan kode yang dipindah langsung (App Inventor tidak menghasilkan source code yang bisa diporting).

---

## 10. NON-FUNCTIONAL REQUIREMENTS

- **Platform:** Android (target minimum SDK perlu ditentukan — sarankan API 24+ untuk jangkauan luas pesantren dengan perangkat beragam)
- **Konektivitas:** aplikasi memerlukan koneksi internet aktif untuk semua operasi (tidak ada mode offline di versi App Inventor; pertimbangkan caching dasar di versi native)
- **Performa:** waktu muat Data Santri (~37 baris) dan Transaksi (40 dari 3800+ baris) harus di bawah 2-3 detik pada jaringan normal
- **Keamanan:** password tersimpan plaintext di Sheets (risiko diketahui, diterima untuk konteks internal skala kecil — bukan prioritas untuk diperbaiki kecuali diminta)
- **Biaya:** seluruh stack harus tetap di tier gratis (Google Sheets, Apps Script, hosting APK mandiri)

---

## 11. LAMPIRAN — RESOURCE PENTING

```
Spreadsheet ID     : 19HqN-edinNOjBHreuxk9ZVLI_icSyLWdvVVD6uXRW1c
Web App URL (live) : https://script.google.com/macros/s/AKfycbxfQTXiiIBi3uvMNTVe93gwV8EYLyQC5D_UAycPN05iLXvCM4-3h2O2NGQPU6RYjOwl/exec
Backend source     : Code.gs (final Tahap 1-6, sudah tervalidasi — lihat file terpisah)
Akun uji           : Habib / 123, Nurman / 123
Desain visual detail : DESIGN.md (Login Screen & Homescreen)
```

---

*Dokumen ini disusun untuk mendukung migrasi pengembangan dari MIT App Inventor ke Android native (Kotlin) yang dikerjakan via Claude Code. Tujuannya memastikan seluruh konteks bisnis, struktur data, dan pelajaran teknis dari fase pengembangan sebelumnya tidak hilang dalam transisi.*
