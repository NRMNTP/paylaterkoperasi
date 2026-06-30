# PayLater Koperasi Pesantren — Paket Migrasi

Paket dokumentasi lengkap untuk migrasi aplikasi PayLater Kopontren Darul 'Ulum dari prototype **MIT App Inventor** ke **Android Native (Kotlin)**, dikerjakan via Claude Code.

Backend (Google Apps Script + Google Sheets) **sudah final dan tidak ikut ditulis ulang** — hanya klien Android yang dibangun dari nol di repo target.

---

## Mulai Dari Sini

Jika kamu (manusia atau agen) baru pertama kali membuka repo ini, baca dengan urutan berikut:

| Urutan | File | Isi | Untuk Siapa |
|---|---|---|---|
| 1 | `CLAUDE.md` | Instruksi perilaku & aturan mutlak untuk agen yang mengerjakan repo | Dibaca otomatis oleh Claude Code di awal sesi |
| 2 | `AGENTS.md` | Pembagian wilayah kerja, peran, dan protokol eskalasi | Dibaca otomatis / saat kerja dipecah per-peran |
| 3 | `docs/PRD.md` | Latar belakang produk, kenapa proyek ini ada, riwayat keputusan | Konteks bisnis sebelum membaca spesifikasi teknis |
| 4 | `docs/SRS.md` | **Sumber kebenaran** kontrak data API, skema database, business rule presisi, acceptance criteria | Wajib dirujuk sebelum implementasi fitur apa pun |
| 5 | `docs/SDD.md` | Arsitektur teknis: struktur package, pola desain, keputusan stack Kotlin/Compose | Acuan struktur kode saat membangun |
| 6 | `docs/DESIGN.md` | Spesifikasi visual: palet warna, layout, struktur komponen Login & Homescreen | Acuan tampilan UI |

**Singkatnya:** `PRD` = kenapa, `SRS` = apa (presisi), `SDD` = bagaimana (teknis), `DESIGN` = tampilan, `CLAUDE.md` + `AGENTS.md` = aturan main.

---

## Struktur Folder

```
.
├── README.md              ← kamu di sini
├── CLAUDE.md               ← instruksi agen (root, dibaca otomatis)
├── AGENTS.md               ← pembagian peran (root, dibaca otomatis)
│
├── backend/
│   └── Code.gs              ← backend final, JANGAN diubah tanpa alasan eksplisit
│                               (lihat CLAUDE.md §5, AGENTS.md §2.1)
│
└── docs/
    ├── PRD.md                ← latar belakang & ringkasan fitur
    ├── SRS.md                 ← spesifikasi teknis presisi (SUMBER KEBENARAN)
    ├── SDD.md                 ← desain arsitektur untuk implementasi Kotlin
    ├── DESIGN.md               ← spesifikasi visual
    │
    ├── assets/                ← aset visual referensi (bukan untuk dipakai
    │   ├── logo_putih.png         langsung di app Compose — gradient/shadow
    │   ├── logo_badge.png         dibangun native, lihat SDD.md §7.2.
    │   ├── logo_lengkap_transparan.png  Logo tetap dipakai sebagai asset asli.)
    │   ├── bg_gradasi.png
    │   ├── card_melayang.png
    │   └── avatar_user.png
    │
    └── archive/                ← dokumen riwayat fase App Inventor (konteks
        ├── Blueprint_PayLater_Tahap1-5.docx   historis, BUKAN acuan aktif
        └── Dokumentasi_Database_PayLater.docx untuk implementasi baru —
                                                 SRS.md & SDD.md menggantikannya)
```

---

## Status Proyek Saat Migrasi Dimulai

| Komponen | Status |
|---|---|
| Backend (`Code.gs`, 9 endpoint) | ✅ Final, stabil, dipakai operasional nyata |
| Database (Google Sheets, 8 sheet) | ✅ Stabil — **nama kolom case-sensitive, lihat SRS.md §3.2** |
| Klien Android (Kotlin) | ⬜ Belum dimulai — ini tugas utama repo target |
| Modul prototype App Inventor yang sudah pernah berjalan | Login, Dashboard, Data Santri, Catatan Transaksi (list), Form Transaksi (sebagian, ada bug belum tuntas) — lihat `docs/SRS.md §9` untuk traceability lengkap |
| Modul yang backend-nya siap tapi UI belum pernah dibangun | Tagihan Santri, Laporan |

---

## Hal Paling Penting yang Harus Diketahui Sebelum Coding

1. **Nama kolom Google Sheets tidak intuitif** — kolom status bernama `status` (huruf kecil), bukan `Status`. Detail lengkap & alasan: `docs/SRS.md §3.2`, `CLAUDE.md §2.1`.
2. **Tanggal dari Sheets API bisa 3 bentuk berbeda** (objek Date, string, serial number) — wajib ditangani eksplisit. `CLAUDE.md §2.2`.
3. **Filter & search wajib client-side**, bukan request API berulang. `CLAUDE.md §2.4`.
4. **Form transaksi finansial wajib alur konfirmasi berlapis** — bukan tombol submit langsung. `CLAUDE.md §2.5`.
5. **Backend tidak disentuh sembarangan** — ini sistem yang sedang dipakai operasional nyata oleh koperasi pesantren. `AGENTS.md §2.1`.

Aturan lengkap dengan alasan di balik tiap satunya ada di `CLAUDE.md §2` — semuanya lahir dari bug produksi nyata di fase pengembangan sebelumnya, bukan praduga.

---

*Paket ini disusun untuk memastikan tidak ada konteks bisnis maupun pelajaran teknis yang hilang dalam transisi dari prototype App Inventor ke implementasi Android native.*
