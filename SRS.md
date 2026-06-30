# SRS.md — Software Requirements Specification

**Sistem:** PayLater Koperasi Pesantren (Kopontren Darul 'Ulum)
**Versi dokumen:** 1.0 — disusun pada titik migrasi App Inventor → Android Native (Kotlin via Claude Code)
**Dokumen terkait:** `PRD.md` (latar belakang & ringkasan fitur), `DESIGN.md` (spesifikasi visual)

Standar acuan struktur: IEEE 830 (disederhanakan untuk konteks proyek skala kecil-menengah).

---

## 1. PENDAHULUAN

### 1.1 Tujuan Dokumen
Memberikan spesifikasi teknis presisi — bukan sekadar deskripsi fitur — agar implementasi ulang di Android native dapat dilakukan tanpa ambiguitas terhadap: struktur data, kontrak API (request/response persis), aturan bisnis (dinyatakan sebagai kondisi formal), penanganan error, dan kriteria penerimaan tiap fungsi.

### 1.2 Ruang Lingkup
Sistem terdiri dari dua bagian yang dipisah tegas:
- **SUT (System Under Transformation):** aplikasi klien Android — akan ditulis ulang sepenuhnya dari basis App Inventor ke Kotlin native.
- **Sistem Eksternal Tetap:** backend Google Apps Script + Google Sheets — **tidak termasuk dalam scope penulisan ulang**, hanya dikonsumsi sebagai REST-like API melalui HTTP GET.

### 1.3 Definisi & Istilah
| Istilah | Arti |
|---|---|
| Santri | Anggota koperasi (siswa pesantren) yang memiliki akun PayLater |
| Kasir | Pengguna aplikasi (staf koperasi) yang mencatat transaksi |
| Hutang | Transaksi pengambilan barang/uang oleh santri, dicatat sebagai status "Hutang" |
| Bayar | Transaksi pelunasan hutang oleh santri, dicatat sebagai status "Bayar" |
| Limit | Batas maksimum hutang aktif yang boleh dimiliki santri, ditentukan oleh Level |
| Sisa Limit | `Limit − Total Hutang Aktif`; bisa negatif (over-limit) |
| Beku | Status akun santri yang dibekukan otomatis karena ada hutang melewati jatuh tempo |
| Jatuh Tempo | Tanggal batas pelunasan hutang, dihitung otomatis +7 hari dari hutang aktif tertua belum lunas |
| DONT DELETE | Penanda baris anchor formula array di Google Sheets — harus difilter di semua query, bukan data nyata |

---

## 2. DESKRIPSI UMUM SISTEM

### 2.1 Perspektif Produk
Sistem berdiri sendiri (bukan bagian dari produk lebih besar), namun **bergantung penuh** pada infrastruktur Google (Sheets + Apps Script) yang berada di luar kendali tim pengembangan aplikasi — perubahan struktur sheet oleh pihak lain dapat mematahkan kontrak data tanpa peringatan kompilasi (risiko inheren, lihat §7.4).

### 2.2 Fungsi Utama (ringkas — detail per fungsi di §5)
1. Autentikasi kasir
2. Tampilan dashboard ringkasan
3. Manajemen & pencarian data santri
4. Pencatatan transaksi (hutang & pembayaran) dengan validasi bisnis
5. Pelaporan periodik
6. Pemantauan tagihan jatuh tempo

### 2.3 Kelas & Karakteristik Pengguna
Pengguna tunggal: **Kasir/Pengurus koperasi**. Asumsi: melek teknologi dasar (terbiasa smartphone), tidak perlu pelatihan teknis mendalam, bekerja dalam kondisi mungkin terburu-buru (transaksi harian berulang) — sehingga UI harus **meminimalkan risiko kesalahan input finansial** (lihat aturan konfirmasi berlapis di §5.4).

### 2.4 Batasan Umum
- Tidak ada mode offline (request gagal total tanpa koneksi internet)
- Tidak ada sistem role/permission granular (semua kasir punya akses sama)
- Password disimpan plaintext di backend (risiko diterima, di luar scope perbaikan kecuali diminta eksplisit)
- Tidak ada audit log terstruktur selain kolom "Username Kasir" per transaksi

### 2.5 Asumsi & Dependensi
- Backend Apps Script tetap berjalan di URL yang dikonfigurasi (lihat §9 Lampiran)
- Struktur kolom Google Sheets **tidak berubah nama/urutan** tanpa koordinasi (sangat krusial — lihat §4.5)
- Perangkat target memiliki akses internet aktif saat digunakan

---

## 3. KONTRAK DATA — MODEL & SKEMA

### 3.1 Entity: Santri
Sumber: sheet `Data Santri`. Endpoint terkait: `getSantri`, `getDetailSantri`.

| Field (JSON response) | Tipe | Wajib | Deskripsi | Sumber Kolom Sheet |
|---|---|---|---|---|
| `nama` | string | ya | Nama lengkap, PK logis | Nama Santri |
| `level` | string | ya | Pemula \| Terpercaya \| Prioritas \| VIP | Level (manual) atau Level_ (auto) — Level diprioritaskan |
| `limit` | number | ya | Limit hutang dalam Rupiah | Limit atau Limit_ |
| `hutangAktif` | number | ya | Total hutang belum lunas saat ini | Total Hutang Aktif |
| `sisaLimit` | number | ya | `limit − hutangAktif`, bisa negatif | Sisa Limit |
| `status` | string | ya | "Aktif" \| "Beku" | Status |
| `totalLunas` | number | ya | Akumulasi total yang sudah dilunasi | Total Lunas |
| `telatTerparah` | number | ya | Jumlah hari telat terburuk historis | Telat terparah |
| `poinTepat` | number | ya | Poin reward ketepatan waktu | Poin Tepat Waktu |
| `bebasBeku` | string (DD/MM/YYYY) | tidak | Tanggal akun dibebaskan dari Beku (hanya di `getDetailSantri`) | Tanggal Bebas Beku |

**Aturan filter (wajib di setiap query):**
```
nama != "" AND nama != "DONT DELETE"
```

### 3.2 Entity: Transaksi
Sumber: sheet `Catatan Transaksi`. Endpoint terkait: `getTransaksi`, `getDetailSantri` (riwayat).

| Field (JSON response) | Tipe | Wajib | Deskripsi | Sumber Kolom Sheet (NAMA PERSIS) |
|---|---|---|---|---|
| `nama` | string | ya | Nama santri terkait | `Nama Santri` |
| `nominal` | number | ya | Jumlah uang transaksi | `Nominal` |
| `tanggalTrx` | string (DD/MM/YYYY) | tidak | Tanggal transaksi dibuat | `Tanggal transaksi` ⚠️ huruf "t" kecil |
| `tanggalJatuhTempo` | string (DD/MM/YYYY) | tidak | Hanya terisi jika status Hutang | `Tanggal Jatuh Tempo` |
| `tanggalBayar` | string (DD/MM/YYYY) | tidak | Hanya terisi jika status Bayar | `Tanggal Bayar` |
| `status` | string | ya | "Hutang" \| "Bayar" | `status` ⚠️ **huruf kecil semua, bukan "Status"** |
| `statusDisiplin` | string | tidak | Hasil evaluasi ketepatan waktu | `Status kedisplinan` ⚠️ ejaan asli dipertahankan (bukan "kedisiplinan") |
| `kategoriWaktu` | string | tidak | "Awal Waktu" \| "Tepat Waktu" \| "Telat" | `Kategori Waktu` |
| `hariTelat` | number | tidak | Jumlah hari keterlambatan | `Jumlah Hari Telat` |
| `kasir` | string | tidak | Username kasir pencatat | `Username Kasir` |

⚠️ **PERINGATAN KRITIS UNTUK IMPLEMENTASI ULANG:**
Nama kolom di atas adalah **hasil verifikasi langsung terhadap header sheet asli** (bukan asumsi/dokumentasi lama), ditemukan setelah bug produksi serius (filter status selalu kosong). Implementasi baru **WAJIB**:
1. Menggunakan nama kolom **persis seperti tabel di atas**, ATAU
2. Membangun layer normalisasi header (lowercase + trim, lalu mapping eksplisit) sebelum parsing apapun

**Aturan filter (wajib):**
```
Nama Santri != "" AND Nama Santri != "DONT DELETE"
```

**Aturan sorting default:** descending berdasarkan `Tanggal transaksi` (terbaru dahulu).

**Penanganan tipe data tanggal (krusial):** kolom tanggal di Google Sheets API (`SpreadsheetApp.getValues()`) dapat mengembalikan salah satu dari 3 bentuk:
1. Objek `Date` JavaScript (paling umum untuk kolom yang diformat sebagai datetime di Sheets)
2. String yang sudah mengandung karakter "/" (format manual)
3. Serial number (Excel/Sheets epoch, basis 1899-12-30)

Fungsi parsing **wajib** menangani ketiganya secara eksplisit — kegagalan menangani kasus 1 (Date object) adalah sumber bug "tanggal jadi kosong/NaN" yang pernah terjadi di produksi.

### 3.3 Entity: Kasir
Sumber: sheet `Data Kasir`.

| Field | Tipe | Sumber Kolom |
|---|---|---|
| `username` | string | Username |
| `password` | string | Password (plaintext) |
| `jabatan` | string | Jabatan |

### 3.4 Entity: Dashboard (agregat, tidak persisten)
Computed di backend dari Entity Santri + Transaksi.

| Field | Tipe | Formula |
|---|---|---|
| `totalSantri` | number | COUNT(santri valid) |
| `santriAktif` | number | COUNT(status="Aktif") |
| `santriBeku` | number | COUNT(status="Beku") |
| `totalPiutang` | number | SUM(hutangAktif semua santri) |
| `santriOverLimit` | number | COUNT(sisaLimit < 0) |
| `santriHampirJT` | number | COUNT(santri dengan hutang status="Hutang" DAN `0 ≤ (tanggalJatuhTempo − hariIni) ≤ 2`) |
| `hutangHariIni` | number | SUM(nominal transaksi hari ini, status="Hutang") |
| `bayarHariIni` | number | SUM(nominal transaksi hari ini, status="Bayar") |
| `transaksiHariIni` | number | COUNT(transaksi dengan tanggalTrx = hari ini) |

### 3.5 Entity: Tagihan (agregat per santri, computed)
Hanya santri dengan `hutangAktif > 0`.

| Field | Tipe | Deskripsi |
|---|---|---|
| `nama`, `level`, `hutangAktif`, `statusAkun`, `telatTerparah` | — | Sama seperti Entity Santri |
| `jatuhTempo` | string | Tanggal jatuh tempo terdekat dari transaksi Hutang aktif |
| `hariSisa` | number (bisa negatif) | `jatuhTempo − hariIni` dalam hari |
| `urgensi` | enum | Lihat aturan klasifikasi di §5.6 |

---

## 4. KONTRAK API — SPESIFIKASI ENDPOINT

**Base URL:** lihat §9 Lampiran.
**Protokol:** HTTP GET, parameter via query string.
**Format response:** JSON, selalu mengandung `status: "success" | "error"`.
**Format error standar:**
```json
{ "status": "error", "message": "<pesan deskriptif untuk ditampilkan ke pengguna>" }
```

### 4.1 `login`
```
GET ?action=login&username={string}&password={string}
```
**Validasi server:**
- `username` dan `password` wajib tidak kosong (trim)
- Pencocokan: `username` case-insensitive, `password` case-sensitive (exact match)

**Response sukses:**
```json
{ "status": "success", "username": "Nurman", "jabatan": "Pengurus", "message": "Login berhasil" }
```
**Response gagal:** `{ "status": "error", "message": "Username atau password salah" }`

---

### 4.2 `getSantri`
```
GET ?action=getSantri
```
**Response sukses:**
```json
{ "status": "success", "data": [ {Entity Santri}, ... ], "total": 37 }
```

---

### 4.3 `getDetailSantri`
```
GET ?action=getDetailSantri&nama={string}
```
**Validasi server:** `nama` wajib tidak kosong; santri harus ditemukan (case-insensitive match) atau return error.

**Response sukses:**
```json
{
  "status": "success",
  "profil": { Entity Santri + bebasBeku },
  "riwayat": [ {Entity Transaksi tanpa field "nama"}, ... ],
  "totalTransaksi": 12
}
```
**Response gagal:** `{ "status": "error", "message": "Santri tidak ditemukan" }`

---

### 4.4 `getTransaksi`
```
GET ?action=getTransaksi&filter={All|Hutang|Bayar}&limit={number, default=40}
```
**Aturan filter status:** perbandingan **case-insensitive** terhadap kolom `status` (lihat §3.2 untuk nama kolom persis).

**Response sukses:**
```json
{ "status": "success", "data": [ {Entity Transaksi}, ... ], "total": 40, "totalSemua": 3815 }
```
`total` = jumlah item dalam `data` (setelah limit). `totalSemua` = jumlah total sebelum limit diterapkan (untuk keperluan UI "menampilkan 40 dari 3815").

**Catatan implementasi klien:** disarankan filter tab (Semua/Hutang/Bayar) dilakukan **di sisi klien** dari hasil `filter=All` yang sudah di-cache, bukan request berulang ke server per tab — lebih cepat dan mengurangi beban (lihat §7.2).

---

### 4.5 `tambahTransaksi`
```
GET ?action=tambahTransaksi&nama={string}&nominal={number}&kasir={string}
```
**Validasi server berurutan (berhenti di kegagalan pertama):**
1. `nama` tidak kosong → else error "Nama santri wajib diisi"
2. `nominal` tidak kosong dan dapat dikonversi ke number → else error "Nominal wajib diisi"
3. `nominal > 0` → else error "Nominal harus lebih dari 0"
4. Santri dengan `nama` (case-insensitive) ditemukan → else error "Santri tidak ditemukan"
5. `status` santri **bukan** "Beku" → else error "Akun santri ini sedang BEKU. Tidak bisa tambah hutang."
6. `nominal ≤ sisaLimit` santri saat ini → else error "Nominal melebihi sisa limit. Sisa limit: Rp {sisaLimit}"

**Efek samping (jika semua validasi lolos):** insert baris baru ke sheet `Catatan Transaksi` dengan: Nama Santri, Nominal, Tanggal transaksi=sekarang, Tanggal Jatuh Tempo=kosong (diisi formula), Tanggal Bayar=kosong, status="Hutang", Username Kasir.

**Response sukses:**
```json
{ "status": "success", "message": "Hutang berhasil ditambahkan", "detail": { "nama": "...", "nominal": 0, "kasir": "..." } }
```

---

### 4.6 `bayarHutang`
```
GET ?action=bayarHutang&nama={string}&nominal={number}&kasir={string}
```
**Validasi server:**
1. `nama` tidak kosong
2. `nominal > 0`
3. Santri (case-insensitive) memiliki **minimal 1** transaksi dengan `status="Hutang"` aktif → else error "Santri ini tidak punya hutang aktif"

**Efek samping:** insert baris baru ke `Catatan Transaksi`: Nama Santri, Nominal, Tanggal transaksi=sekarang, Tanggal Jatuh Tempo=kosong, Tanggal Bayar=sekarang, status="Bayar", Username Kasir.

**Catatan desain backend:** sistem **tidak** mengubah/menghapus baris hutang lama saat dibayar — pembayaran dicatat sebagai baris baru terpisah berstatus "Bayar". Perhitungan `hutangAktif`/`sisaLimit` di Entity Santri adalah hasil agregat formula (SUMIF) atas seluruh riwayat, bukan field yang di-update langsung.

**Response sukses:**
```json
{ "status": "success", "message": "Pembayaran berhasil dicatat", "detail": { "nama": "...", "nominal": 0, "kasir": "..." } }
```

---

### 4.7 `getLaporan`
```
GET ?action=getLaporan&tipe={Harian|Mingguan|Bulanan}
```
**Response sukses:**
```json
{ "status": "success", "tipe": "Harian", "data": [ { "periode": "DD/MM/YYYY", "hutang": 0, "bayar": 0, "sisaPiutang": 0 }, ... ], "total": 30 }
```
Data diurutkan terbaru dahulu (reversed dari urutan sheet).

---

### 4.8 `getTagihan`
```
GET ?action=getTagihan
```
**Response sukses:**
```json
{ "status": "success", "data": [ {Entity Tagihan}, ... ], "total": 12 }
```
Terurut berdasarkan urgensi: `telat → kritis → segera → normal` (lihat aturan klasifikasi §5.6).

---

### 4.9 `getDashboard`
```
GET ?action=getDashboard
```
**Response sukses:**
```json
{ "status": "success", "dashboard": { Entity Dashboard } }
```

---

## 5. SPESIFIKASI FUNGSIONAL DETAIL

### 5.1 FR-01: Autentikasi
**Input:** username, password (dari form UI).
**Proses:**
1. Validasi client-side: kedua field tidak boleh kosong (sebelum request dikirim, untuk hemat bandwidth)
2. Panggil `login`
3. Jika sukses: simpan `username` dan `jabatan` ke penyimpanan lokal perangkat (persisten antar sesi aplikasi)
4. Navigasi ke Dashboard

**Acceptance Criteria:**
- AC1: Login dengan kredensial valid → masuk Dashboard, nama kasir tampil benar di header
- AC2: Login dengan password salah → pesan error tampil, tidak ada navigasi
- AC3: Submit form kosong → validasi lokal mencegah request API terkirim
- AC4: Sesi tetap login setelah aplikasi ditutup-buka kembali (selama belum logout eksplisit)

---

### 5.2 FR-02: Dashboard
**Proses:** saat layar dibuka, panggil `getDashboard` dan render 4 kartu metrik + load nama kasir dari penyimpanan lokal.

**Acceptance Criteria:**
- AC1: Keempat angka (Total Santri, Total Piutang, Akun Beku, Over Limit) sesuai dengan response API, format Rupiah benar untuk nominal
- AC2: Klik avatar kasir → tampil info (nama, jabatan) + opsi Logout
- AC3: Logout → hapus data sesi lokal, kembali ke layar Login
- AC4: Klik tiap 4 menu navigasi → berpindah ke layar modul terkait

---

### 5.3 FR-03: Data Santri (List & Pencarian)
**Proses:**
1. Saat layar dibuka, panggil `getSantri`, simpan hasil di state/memori lokal layar
2. Render sebagai list (card) — wajib pakai komponen list ber-performa (RecyclerView/LazyColumn), **bukan** pendekatan render-ulang-semua-elemen
3. Live search: filter `data` yang sudah di-load **secara lokal** (tanpa request API ulang) berdasarkan substring `nama` (case-insensitive), ter-update setiap perubahan teks input
4. Klik 1 item → navigasi ke Detail Santri dengan parameter `nama`

**Aturan tampilan indikator status:**
```
JIKA status == "Beku" MAKA seluruh teks pada card ditampilkan warna merah (termasuk nama, level, info)
SELAIN ITU tampilkan warna normal (hijau/abu sesuai desain)
```

**Acceptance Criteria:**
- AC1: Jumlah card yang tampil sama dengan `total` dari response API
- AC2: Mengetik di search box langsung menyaring list tanpa delay/loading terlihat (operasi lokal, instan)
- AC3: Mengosongkan search box mengembalikan list lengkap
- AC4: Card dengan status "Beku" tampil dengan skema warna merah secara konsisten di semua elemen teksnya
- AC5: Klik card membawa ke Detail Santri dengan data yang sesuai (bukan data santri lain)

---

### 5.4 FR-04: Catatan Transaksi (List, Filter, Tambah)

#### 5.4.1 List & Filter
**Proses:**
1. Saat layar dibuka, panggil `getTransaksi?filter=All&limit=40`, simpan di state lokal
2. Tab "Semua/Hutang/Bayar" memfilter data yang sudah di-load **secara lokal** berdasarkan field `status` (case-insensitive match)
3. Live search bekerja di atas hasil filter tab yang aktif, berdasarkan `nama`

**Acceptance Criteria:**
- AC1: Tab "Semua" menampilkan seluruh data ter-load
- AC2: Tab "Hutang" hanya menampilkan item dengan `status` setara "Hutang" (case-insensitive)
- AC3: Tab "Bayar" hanya menampilkan item dengan `status` setara "Bayar" (case-insensitive)
- AC4: Berganti tab tidak memicu request jaringan baru (operasi lokal)
- AC5: Tanggal pada tiap card tampil dalam format DD/MM/YYYY yang valid, tidak pernah "NaN" atau string kosong untuk transaksi yang memang punya tanggal di sumber data

#### 5.4.2 Form Tambah Transaksi
**Proses input nama santri:**
1. Field teks bebas dengan **autocomplete** dari daftar santri yang sudah di-load (endpoint `getSantri`)
2. Opsional: daftar pilihan cepat dari N nama unik di transaksi terbaru (dari `getTransaksi?limit=10`)
3. **Validasi wajib:** nilai akhir pada field nama **harus** persis cocok dengan salah satu nama di daftar santri resmi — input bebas yang tidak match wajib ditolak sebelum submit

**Proses submit (alur konfirmasi berlapis — WAJIB, bukan opsional UX):**
```
1. Validasi lokal: nama tidak kosong DAN ada di daftar santri DAN nominal > 0
   -> gagal: tampilkan pesan error inline, JANGAN lanjut
2. Tampilkan dialog pilihan: "Hutang" atau "Bayar"
   -> dibatalkan: hentikan alur, JANGAN kirim apapun
3. Tampilkan dialog konfirmasi berisi ringkasan (nama, nominal, jenis terpilih)
   dengan opsi "Batal" / "Ya, Simpan"
   -> Batal: hentikan alur, JANGAN kirim apapun
   -> Ya, Simpan: lanjut ke langkah 4
4. Panggil endpoint sesuai jenis: "Hutang" -> tambahTransaksi, "Bayar" -> bayarHutang
5. Tampilkan hasil (toast/snackbar sukses ATAU dialog error dengan pesan dari server)
6. Jika sukses: reset seluruh field form ke kondisi awal
```

**Acceptance Criteria:**
- AC1: Tidak mungkin submit transaksi tanpa melewati kedua dialog konfirmasi (jenis + ringkasan)
- AC2: Nama santri yang tidak ada di database (typo/tidak valid) wajib ditolak validasi lokal, tidak pernah sampai ke API
- AC3: Pesan error dari server (mis. "Akun Beku", "melebihi sisa limit") ditampilkan apa adanya ke kasir
- AC4: Setelah sukses simpan, form bersih (siap input transaksi berikutnya) dan list transaksi ter-refresh otomatis
- AC5: Verifikasi end-to-end: transaksi yang disimpan via form benar-benar muncul sebagai baris baru di Google Sheets dengan seluruh kolom terisi sesuai §4.5/§4.6

---

### 5.5 FR-05: Detail Santri
**Proses:** terima parameter `nama`, panggil `getDetailSantri`, render profil + riwayat transaksi.

**Acceptance Criteria:**
- AC1: Seluruh field profil (level, status, limit, hutang aktif, sisa limit, total lunas, telat terparah) tampil sesuai data API
- AC2: Riwayat transaksi terurut dan menampilkan info esensial (tanggal, nominal, status) per item

---

### 5.6 FR-06: Tagihan Santri *(belum diimplementasi di App Inventor — spesifikasi untuk versi baru)*
**Aturan klasifikasi urgensi (dari backend, formal):**
```
JIKA hariSisa TIDAK ADA (tidak ada hutang dengan jatuh tempo) MAKA urgensi = "normal"
SELAIN ITU JIKA hariSisa < 0 MAKA urgensi = "telat"
SELAIN ITU JIKA hariSisa <= 2 MAKA urgensi = "kritis"
SELAIN ITU JIKA hariSisa <= 5 MAKA urgensi = "segera"
SELAIN ITU urgensi = "normal"
```
**Rekomendasi UI:** kode warna berbeda per urgensi (mis. merah=telat, oranye=kritis, kuning=segera, hijau/netral=normal), list terurut sesuai urutan urgensi dari API.

**Acceptance Criteria:**
- AC1: Hanya santri dengan `hutangAktif > 0` yang muncul di list
- AC2: Urutan list konsisten dengan urutan urgensi dari server (telat paling atas)

---

### 5.7 FR-07: Laporan *(belum diimplementasi di App Inventor — spesifikasi untuk versi baru)*
**Acceptance Criteria:**
- AC1: Tiga tipe periode (Harian/Mingguan/Bulanan) dapat dipilih dan menampilkan data berbeda sesuai tipe
- AC2: Data terurut terbaru dahulu

---

## 6. ANTARMUKA EKSTERNAL

### 6.1 Antarmuka Perangkat Keras
Tidak ada kebutuhan khusus selain perangkat Android standar dengan konektivitas data.

### 6.2 Antarmuka Perangkat Lunak
| Sistem | Jenis Interaksi | Protokol |
|---|---|---|
| Google Apps Script Web App | Klien memanggil sebagai REST-like API | HTTP GET, response JSON |
| Google Sheets | Tidak diakses langsung oleh klien — selalu melalui Apps Script | — |

### 6.3 Antarmuka Pengguna
Lihat `DESIGN.md` untuk spesifikasi visual lengkap (palet warna, struktur layar, properti komponen Login Screen & Homescreen).

---

## 7. ATRIBUT KUALITAS SISTEM

### 7.1 Performa
- Render list Data Santri (~37 item) harus tidak terasa lag pada perangkat kelas menengah
- Render list Transaksi harus menerapkan pembatasan/pagination (server membatasi 40 per panggilan dari total 3800+ baris) — **klien wajib mendukung pola ini**, idealnya ditingkatkan menjadi infinite-scroll proper
- Operasi filter tab & search wajib **lokal** (tanpa round-trip jaringan) untuk responsivitas instan

### 7.2 Reliabilitas & Penanganan Galat
- Semua pemanggilan API wajib dibungkus penanganan error (timeout, koneksi gagal, response bukan JSON valid) dengan pesan yang dapat dipahami pengguna non-teknis
- Kegagalan jaringan tidak boleh menyebabkan crash aplikasi
- Data finansial (transaksi) **tidak boleh** terkirim ganda akibat double-tap — wajib ada debounce/disable tombol selama request berlangsung

### 7.3 Usability
- Alur input transaksi finansial wajib melalui konfirmasi berlapis (§5.4.2) — ini adalah **hard requirement**, bukan preferensi kosmetik, mengingat histori risiko kesalahan input oleh kasir yang menjadi alasan keputusan desain ini
- Pesan error dari backend ditampilkan apa adanya (backend sudah menghasilkan pesan ramah pengguna dalam Bahasa Indonesia)

### 7.4 Maintainability
- **Risiko tertinggi sistem:** kontrak nama kolom Google Sheets (§3.2) dapat berubah oleh pengelola sheet tanpa sepengetahuan tim aplikasi, menyebabkan kegagalan silent (data kosong, bukan error eksplisit). Mitigasi yang direkomendasikan: backend menambahkan validasi header saat startup/deploy (membandingkan header aktual vs daftar yang diharapkan, return error eksplisit jika tidak cocok) — **belum diimplementasikan**, dicatat sebagai technical debt.
- Endpoint diagnostik tambahan (`cekHeader`, `cekStatus` — dibuat ad-hoc saat debugging produksi) sebaiknya dipertahankan secara permanen di backend sebagai alat bantu observability, bukan dihapus.

### 7.5 Portabilitas
Backend sepenuhnya agnostik terhadap teknologi frontend (terbukti dari riwayat migrasi App Inventor → native tanpa perubahan backend). Kontrak di §4 berlaku untuk frontend apapun yang menggantikan App Inventor di masa depan.

---

## 8. MATRIKS TRACEABILITY (Fitur ↔ Endpoint ↔ Status)

| Fitur (FR) | Endpoint Digunakan | Status Backend | Status Frontend (App Inventor, akan ditulis ulang) |
|---|---|---|---|
| FR-01 Login | `login` | ✅ Stabil | ✅ Selesai |
| FR-02 Dashboard | `getDashboard` | ✅ Stabil | ✅ Selesai |
| FR-03 Data Santri | `getSantri` | ✅ Stabil | ✅ Selesai |
| FR-04 Transaksi (list+filter) | `getTransaksi` | ✅ Stabil (sudah melalui beberapa perbaikan kritis) | ✅ Selesai |
| FR-04 Transaksi (tambah) | `tambahTransaksi`, `bayarHutang` | ✅ Stabil | ⚠️ Tersusun, ada bug aktif belum diverifikasi tuntas end-to-end |
| FR-05 Detail Santri | `getDetailSantri` | ✅ Stabil | ✅ Selesai |
| FR-06 Tagihan | `getTagihan` | ✅ Siap | ⬜ Belum dibangun |
| FR-07 Laporan | `getLaporan` | ✅ Siap | ⬜ Belum dibangun |

---

## 9. LAMPIRAN

### 9.1 Resource & Kredensial
```
Spreadsheet ID : 19HqN-edinNOjBHreuxk9ZVLI_icSyLWdvVVD6uXRW1c
Web App URL    : https://script.google.com/macros/s/AKfycbxfQTXiiIBi3uvMNTVe93gwV8EYLyQC5D_UAycPN05iLXvCM4-3h2O2NGQPU6RYjOwl/exec
Akun uji       : Habib / 123 (Kasir), Nurman / 123 (Pengurus)
```

### 9.2 Backend Source
File `Code.gs` final (mencakup seluruh 9 endpoint, helper functions, dan perbaikan bug yang dijelaskan di §3.2/§7.4) disediakan sebagai lampiran terpisah dari dokumen ini — wajib dipakai sebagai-adanya, **bukan ditulis ulang dari nol**, kecuali ada kebutuhan eksplisit untuk refactor backend.

### 9.3 Dokumen Terkait
- `PRD.md` — latar belakang produk, ringkasan fitur, riwayat keputusan non-teknis
- `DESIGN.md` — spesifikasi visual Login Screen & Homescreen (struktur komponen, warna, aset)

---

*Dokumen ini bersifat hidup (living document) — wajib diperbarui setiap kali kontrak data backend berubah, fitur baru ditambahkan, atau bug kelas baru ditemukan dan diperbaiki.*
