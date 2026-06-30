# CLAUDE.md

Instruksi kerja untuk Claude Code di repositori ini. Dibaca otomatis di awal setiap sesi — perlakukan sebagai konteks wajib, bukan opsional.

---

## 1. KONTEKS PROYEK (RINGKAS)

Aplikasi Android native (Kotlin) untuk sistem PayLater koperasi pondok pesantren (Kopontren Darul 'Ulum). Ini adalah **migrasi/penulisan ulang** dari prototype yang sebelumnya dibangun di MIT App Inventor — prototype lama sudah cukup matang secara fungsional (5-6 modul berjalan) tapi App Inventor mencapai batas kemampuannya secara teknis (lihat §6).

**Backend TIDAK ikut ditulis ulang.** Google Apps Script (`Code.gs`) + Google Sheets sudah final, stabil, dan teruji lewat pemakaian nyata. Tugas di repo ini murni membangun klien Android baru yang mengonsumsi API yang sudah ada.

Baca **wajib sebelum mulai coding apa pun**, urutan ini:
1. `PRD.md` — latar belakang & kenapa proyek ini ada
2. `SRS.md` — kontrak data presisi & spesifikasi fungsional (**sumber kebenaran untuk semua field API & business rule**)
3. `SDD.md` — arsitektur teknis & struktur kode yang harus diikuti
4. `DESIGN.md` — spesifikasi visual (warna, layout, komponen)

Jangan menebak struktur data atau aturan bisnis. Semuanya sudah didokumentasikan presisi di `SRS.md` — itu hasil dari debugging panjang yang mahal, bukan dokumentasi seadanya.

---

## 2. ATURAN MUTLAK (NON-NEGOTIABLE)

Aturan ini lahir langsung dari bug produksi nyata yang pernah terjadi. Melanggarnya akan mengulang kesalahan yang sudah pernah menyebabkan kegagalan sistem.

### 2.1 Nama Kolom Google Sheets — JANGAN PERNAH DITEBAK
Backend membaca kolom dari sheet `Catatan Transaksi` dengan nama **persis case-sensitive** berikut (lihat `SRS.md §3.2` untuk tabel lengkap):
```
status                  ← huruf kecil semua, BUKAN "Status"
Tanggal transaksi        ← "t" kecil pada kata kedua, BUKAN "Tanggal Transaksi"
Status kedisplinan       ← ejaan asli dipertahankan, BUKAN "kedisiplinan"
```
Kesalahan asumsi nama kolom ini pernah menyebabkan filter status gagal total di produksi (data selalu kosong, tanpa error eksplisit — silent failure). Jika kamu menulis ulang atau menyentuh backend dengan alasan apa pun, **verifikasi ulang nama kolom langsung dari header sheet aktual**, jangan percaya dokumentasi lama atau asumsi penamaan yang "masuk akal".

### 2.2 Tanggal dari Google Sheets API Punya 3 Bentuk Berbeda
`SpreadsheetApp.getValues()` bisa mengembalikan kolom tanggal sebagai: objek `Date`, string ber-"/", atau serial number. Kode manapun yang memproses tanggal dari backend **wajib** menangani ketiga kasus eksplisit — kegagalan menangani kasus objek `Date` adalah penyebab bug "tanggal jadi NaN/kosong" yang pernah terjadi. Lihat `Code.gs` fungsi `formatTanggal()` sebagai referensi penanganan yang sudah benar — jangan sederhanakan logika ini tanpa memahami kenapa setiap cabang ada di sana.

### 2.3 Perbandingan String Status — Selalu Trim + Case-Insensitive
Setiap kali membandingkan field `status` (Hutang/Bayar) atau `statusAkun` (Aktif/Beku) di kode klien, **wajib** `.trim()` dan bandingkan case-insensitive. Jangan asumsikan data dari sheet selalu rapi — riwayat proyek penuh dengan whitespace tersembunyi dan inkonsistensi huruf besar/kecil dari input manual bertahun-tahun.

### 2.4 Filter Tab & Search WAJIB Client-Side
Untuk daftar Transaksi dan Data Santri: load data sekali dari API, lakukan filter tab (Semua/Hutang/Bayar) dan pencarian nama **secara lokal di memori**, bukan request API berulang. Ini bukan preferensi performa — ini keputusan yang lahir dari kegagalan nyata server-side filtering akibat bug nama kolom (§2.1), dan client-side filtering terbukti jadi solusi yang stabil. Lihat `SDD.md §6.3, §6.5` untuk pola implementasi (`derived state` dari `UiState`).

### 2.5 Alur Submit Transaksi Finansial WAJIB Konfirmasi Berlapis
Form tambah Hutang/Bayar **tidak boleh** punya tombol submit langsung tanpa konfirmasi. Alur wajib: validasi lokal → dialog pilih jenis (Hutang/Bayar) → dialog konfirmasi ringkasan → baru kirim ke API. Ini hard requirement dari `SRS.md §5.4.2`, lahir dari kebutuhan nyata mencegah kasir salah input pada operasi yang berdampak finansial langsung. Jangan menyederhanakan alur ini atas nama UX yang "lebih cepat" tanpa diskusi eksplisit dengan pemilik proyek.

### 2.6 Endpoint API Tidak Diubah Tanpa Diskusi
Kontrak 9 endpoint di `SRS.md §4` sudah stabil dan dipakai. Jangan menambah/mengubah parameter atau struktur response backend secara sepihak saat mengerjakan klien — jika klien butuh data tambahan, catat sebagai kebutuhan baru dan diskusikan dulu, jangan langsung edit `Code.gs`.

---

## 3. KONVENSI KERJA DI REPO INI

### 3.1 Struktur Kode
Ikuti struktur package di `SDD.md §4` apa adanya. Jangan reorganisasi struktur folder atas inisiatif sendiri di tengah jalan — kalau ada alasan kuat untuk mengubahnya, jelaskan dulu sebelum eksekusi besar-besaran.

### 3.2 Stack Teknologi Sudah Diputuskan
Kotlin + Jetpack Compose + MVVM + Retrofit + Coroutines + Hilt + Navigation Compose + DataStore (lihat `SDD.md §2` untuk alasan tiap pilihan). Ini bukan keputusan yang perlu dipertimbangkan ulang di awal setiap sesi — kerjakan sesuai stack ini kecuali ada blocker teknis nyata yang membuatnya tidak mungkin.

### 3.3 DTO vs Domain Model — Jangan Disatukan
Selalu pisahkan bentuk JSON mentah (DTO) dari model yang dipakai UI (Domain Model), dengan fungsi mapping eksplisit (`toDomain()`). Lihat `SDD.md §5.1`. Ini bukan over-engineering untuk proyek sekecil ini — pemisahan ini yang membuat penanganan kasus seperti §2.3 di atas terjadi di **satu tempat saja**, bukan tersebar di seluruh codebase.

### 3.4 State Management — Pakai Tipe Eksplisit, Jangan Flag Boolean/String Manual
Untuk alur multi-tahap (terutama Form Transaksi), gunakan `sealed interface`/`sealed class` sebagai state machine eksplisit (lihat `SDD.md §6.4`). **Jangan** kembali ke pola variable flag string manual (`var currentStep = "step1"`) — itu persis pola yang gagal berulang kali di implementasi App Inventor sebelumnya dan sengaja digantikan.

### 3.5 Error Handling
Repository selalu bungkus hasil dalam `Result<T>`, tidak pernah lempar exception mentah ke ViewModel. ViewModel selalu sediakan `errorMessage: String?` di UiState untuk ditampilkan ke pengguna. Tidak ada operasi jaringan yang boleh menyebabkan crash — App Inventor lama tidak punya jaring pengaman ini dan sering force-close di tangan pengguna nyata (kasir di lapangan, bukan developer).

### 3.6 Commit & Perubahan Bertahap
Proyek ini punya riwayat masalah serius akibat perubahan besar tanpa validasi bertahap (App Inventor: instruksi blok kompleks yang gagal berulang kali tanpa testing incremental). Saat mengimplementasikan modul baru:
- Selesaikan dan verifikasi satu modul/fitur sampai benar sebelum pindah ke modul berikutnya
- Untuk logika kompleks (terutama validasi form transaksi, parsing tanggal, filter status), tulis unit test SEBELUM atau BERSAMAAN dengan implementasi — jangan ditunda ke akhir
- Jangan membuat perubahan luas ke banyak file sekaligus tanpa menjelaskan dulu rencana perubahannya

---

## 4. PERILAKU YANG DIHARAPKAN DARI CLAUDE CODE

### 4.1 Validasi Sebelum Klaim Selesai
Jangan menyatakan suatu fitur "selesai" atau "seharusnya berfungsi" tanpa benar-benar memverifikasi: build berhasil, test relevan lulus (jika ada), dan logika sudah ditelusuri ulang terhadap kontrak di `SRS.md`. Riwayat proyek ini penuh dengan kasus "logic ini harusnya benar" yang ternyata gagal di tangan pengguna nyata — jangan ulangi pola itu, sekarang dalam bentuk asumsi yang tidak tervalidasi terhadap kode sungguhan.

### 4.2 Tanya Sebelum Menebak pada Hal Berdampak Besar
Untuk keputusan yang sulit dibatalkan (struktur database lokal, penghapusan/refactor besar, perubahan kontrak API) — tanya dulu, jangan asumsikan. Untuk hal kecil/reversibel (penamaan variabel lokal, struktur file dalam satu modul) — boleh putuskan sendiri dan jelaskan alasannya secara singkat.

### 4.3 Rujuk Silang ke Dokumen, Jangan Duplikasi Penjelasan
Saat menjelaskan keputusan implementasi, rujuk ke bagian spesifik di `SRS.md`/`SDD.md`/`DESIGN.md` (mis. "sesuai SRS §5.4.2") alih-alih menjelaskan ulang dari nol. Dokumen-dokumen itu adalah sumber kebenaran — kode dan penjelasan harus konsisten dengannya, bukan menciptakan narasi paralel.

### 4.4 Update Dokumen Saat Keputusan Berubah
`SDD.md §10` mencantumkan keputusan yang sengaja ditunda. Begitu salah satu dikerjakan (mis. mulai membangun modul Laporan atau Tagihan), **update dokumen terkait** — jangan biarkan dokumentasi basi sementara kode berjalan ke arah berbeda. Dokumentasi yang tidak sinkron dengan kode lebih berbahaya daripada tidak ada dokumentasi sama sekali.

### 4.5 Bahasa
Komunikasi dengan pemilik proyek dalam Bahasa Indonesia (mengikuti seluruh riwayat komunikasi proyek ini). Penamaan kode (variabel, fungsi, kelas) tetap dalam Bahasa Inggris mengikuti konvensi Kotlin/Android standar — kecuali istilah domain spesifik yang lebih jelas dalam Bahasa Indonesia (mis. `Santri`, `Hutang`, `Beku` sebagai nama kelas/enum, sesuai yang sudah dipakai konsisten di `SRS.md`).

---

## 5. APA YANG TIDAK BOLEH DISENTUH TANPA IZIN EKSPLISIT

- `Code.gs` (backend) — stabil, dipakai sistem berjalan nyata di lapangan. Perubahan di sini berdampak langsung ke operasional koperasi, bukan sekadar kode eksperimen.
- Struktur sheet Google Sheets (nama kolom, urutan, sheet baru) — perubahan apa pun berisiko mematahkan kontrak data secara silent (lihat §2.1).
- Kredensial & URL produksi (lihat `SRS.md §9.1`) — jangan d-hardcode tersebar di banyak file, sentralisasi di `util/Constants.kt` sesuai `SDD.md §4`.

---

## 6. RINGKASAN KENAPA PROYEK INI BERMIGRASI (UNTUK KONTEKS, BUKAN UNTUK DIPERDEBATKAN ULANG)

App Inventor sudah mencapai batas: tidak ada list dinamis yang performan secara native (workaround ekstensi pihak ketiga yang rapuh), tidak ada overlay/Z-index (memaksa trik visual canggung), variable scope yang membingungkan menyebabkan banyak bug "not bound", debugging sangat lambat karena pesan error generik tanpa lokasi pasti, dan tidak ada dependency management proper. Detail lengkap di `SRS.md §8`. Keputusan migrasi sudah final — fokus sekarang adalah eksekusi yang benar di Kotlin native, bukan mempertanyakan ulang App Inventor vs native.

---

*File ini adalah kontrak kerja, bukan sekadar catatan. Jika instruksi di sini tampak bertentangan dengan permintaan spesifik dari pemilik proyek dalam suatu sesi, klarifikasi dulu — jangan diam-diam mengikuti salah satu tanpa menyebutkan adanya potensi konflik.*
