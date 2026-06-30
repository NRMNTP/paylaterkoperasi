# DESIGN.md — PayLater Koperasi Pesantren

Dokumentasi desain visual aplikasi, mencakup **Login Screen (Screen1)** dan **Homescreen**. Dibuat di MIT App Inventor, tema warna hijau-putih minimalis modern, terinspirasi identitas Kopontren Darul 'Ulum.

---

## 1. Palet Warna

| Nama | Hex | Integer (App Inventor) | Penggunaan |
|---|---|---|---|
| Hijau Utama | `#2E7D32` | `-13730510` | Header, tombol utama, aksen aktif |
| Hijau Gelap | `#1B5E20` | — | Bagian atas gradasi |
| Hijau Terang | `#43A047` | — | Bagian bawah gradasi |
| Hijau Muda (subtitle) | `#C8E6C9` | — | Teks subtitle di header hijau |
| Putih | `#FFFFFF` | `-1` | Background card, teks di atas hijau |
| Abu Latar | `#F5F5F5` / `#F0FAF0` | — | Background screen, input field |
| Abu Teks | `#555555` / `#757575` | `-9079435` | Label sekunder |
| Abu Terang (teks non-aktif) | `#888888` | — | Footer, keterangan kecil |
| Merah (error/Beku) | `#D32F2F` | `-2937041` | Pesan error, status Beku |

---

## 2. Aset Gambar yang Digunakan

| File | Fungsi |
|---|---|
| `bg_gradasi.png` | Background gradasi hijau (atas gelap → bawah terang), dipasang sebagai `BackgroundImage` Screen1 |
| `card_melayang.png` | Card putih sudut membulat + bayangan (shadow), efek "melayang" |
| `logo_putih.png` | Logo Kopontren Darul 'Ulum versi siluet putih — dipakai di header (kontras di atas hijau) |
| `logo_badge.png` | Logo asli berwarna dalam badge lingkaran putih (alternatif) |
| `logo_lengkap_transparan.png` | Logo + tulisan, background transparan (cadangan untuk splash screen) |
| `avatar_user.png` | Ikon avatar kasir (siluet user hijau dalam lingkaran putih), header Homescreen |

**Catatan teknis penting:** logo asli berwarna hijau akan *menyatu/tidak terlihat* bila ditaruh di atas background hijau. Solusinya logo dikonversi ke versi siluet **putih** khusus untuk konteks header hijau.

---

## 3. LOGIN SCREEN (Screen1)

### 3.1 Tujuan
Layar pertama yang dilihat kasir. Berisi input username/password dan branding aplikasi. Desain meniru kartu (card) melayang di atas latar gradasi hijau — gaya umum aplikasi finansial modern.

### 3.2 Struktur Komponen

```
Screen1
├── WebAPI (Web, non-visible)
├── DBLokal (TinyDB, non-visible)
├── Notif (Notifier, non-visible)
├── LayoutHeader (VerticalArrangement)
│   ├── LogoApp (Image — logo_putih.png)
│   ├── LabelAppName ("PayLater Koperasi")
│   └── LabelSubtitle ("Koperasi Darul Ulum")
├── SpasiHeaderCard (Label kosong, jarak antar header & card)
└── LayoutCard (VerticalArrangement, card melayang)
    └── LayoutCardInner (VerticalArrangement, padding dalam)
        ├── PadAtas (Label kosong)
        ├── LabelWelcome ("Selamat Datang 👋")
        ├── LabelUsername ("Username")
        ├── InputUsername (TextBox)
        ├── LabelPassword ("Password")
        ├── InputPassword (TextBox)
        ├── BtnLogin ("MASUK")
        ├── LabelError (pesan error, tersembunyi default)
        └── PadBawah (Label kosong)
```

### 3.3 Properti Kunci

**Screen1**
```
BackgroundImage : bg_gradasi.png
AlignHorizontal : Center
AlignVertical   : Top
```

**LayoutHeader**
```
BackgroundColor : None (transparan, gradasi tembus)
Height          : 280 px
AlignHorizontal : Center
AlignVertical   : Center
```

**LogoApp**
```
Picture : logo_putih.png
Height  : 90 px
Width   : 90 px
```

**LabelAppName**
```
FontSize  : 26
FontBold  : True
TextColor : White
```

**LabelSubtitle**
```
FontSize  : 13
TextColor : #C8E6C9
```

**LayoutCard** (efek melayang)
```
Image           : card_melayang.png
BackgroundColor : None
Width           : 90 percent
Height          : Automatic
AlignHorizontal : Center
```

**LayoutCardInner** (padding dalam card)
```
Width : 85 percent   (sisa ruang jadi padding kiri-kanan)
```
Padding atas-bawah dibuat lewat `PadAtas` / `PadBawah` (Label kosong, Height 20px).

**InputUsername / InputPassword**
```
Height          : 45 px
BackgroundColor : #F5F5F5
Hint            : "Masukkan username..." / "Masukkan password..."
```

**BtnLogin**
```
Text            : MASUK
BackgroundColor : #2E7D32
TextColor       : White
FontBold        : True
Shape           : Rounded
Height          : 50 px
```

**LabelError**
```
TextColor : #D32F2F
Visible   : False (default, muncul saat login gagal)
```

### 3.4 Alur Fungsional Singkat
1. User isi username & password → tekan **MASUK**
2. Validasi kosong di sisi app
3. Request ke endpoint `login` (Apps Script)
4. Sukses → simpan `username`/`jabatan` ke TinyDB → pindah ke **Homescreen**
5. Gagal → tampilkan `LabelError` dengan pesan dari server

---

## 4. HOMESCREEN

### 4.1 Tujuan
Dashboard utama setelah login. Menampilkan ringkasan data real-time (jumlah santri, piutang, akun beku, over limit) dan menu navigasi ke 4 modul utama.

### 4.2 Struktur Komponen

```
Homescreen
├── WebAPI, DBLokal, Notif (non-visible)
├── HeaderBaru (HorizontalArrangement, hijau)
│   ├── PadKiri (Label kosong, 15px)
│   ├── BtnAvatar (Button bulat — avatar_user.png, bisa diklik)
│   ├── PadTengah (Label kosong, 12px)
│   └── LayoutTeks (VerticalArrangement)
│       ├── LabelSalam ("Assalamualaikum 👋")
│       └── LabelNamaKasir (nama kasir login)
├── LayoutRingkasan (VerticalArrangement, putih)
│   ├── LabelJudulRingkasan ("📊 Ringkasan Hari Ini")
│   ├── LayoutBaris1 (HorizontalArrangement)
│   │   ├── CardSantri   → LabelAngkaSantri + LabelKetSantri
│   │   └── CardPiutang  → LabelAngkaPiutang + LabelKetPiutang
│   └── LayoutBaris2 (HorizontalArrangement)
│       ├── CardBeku      → LabelAngkaBeku + LabelKetBeku
│       └── CardOverLimit → LabelAngkaOverLimit + LabelKetOverLimit
├── LabelMenuUtama ("MENU UTAMA")
├── LayoutMenu1 (HorizontalArrangement)
│   ├── CardMenuSantri    (icon 👥 + teks "Data Santri" + Button transparan)
│   └── CardMenuTransaksi (icon 📋 + teks "Catatan Transaksi" + Button transparan)
└── LayoutMenu2 (HorizontalArrangement)
    ├── CardMenuLaporan (icon 📊 + teks "Laporan" + Button transparan)
    └── CardMenuTagihan (icon 🔔 + teks "Tagihan Santri" + Button transparan)
```

### 4.3 Properti Kunci

**HeaderBaru**
```
BackgroundColor : #2E7D32
Height          : 90 px
AlignHorizontal : Left
AlignVertical   : Center
```

**BtnAvatar**
```
Image           : avatar_user.png
Text            : (kosong)
Width / Height  : 55 px
Shape           : Oval
BackgroundColor : None
```
*Diklik → menampilkan info kasir (nama, jabatan) + opsi Logout via `ShowChooseDialog`.*

**LabelSalam**
```
FontSize  : 16
FontBold  : True
TextColor : White
```

**LabelNamaKasir**
```
FontSize  : 13
TextColor : #C8E6C9
```

**Card Ringkasan** (CardSantri, CardPiutang, CardBeku, CardOverLimit)
```
Width      : 45 percent (dua per baris)
Height     : 80 px
AlignHorizontal/Vertical : Center
BackgroundColor : varian — putih/hijau muda tergantung kategori
```
Tiap card berisi 1 label angka besar (FontSize ~22, FontBold) + 1 label keterangan kecil (FontSize ~11, abu).

**Card Menu** (CardMenuSantri, dst.)
```
Width  : 45 percent
Height : 100 px
BackgroundColor : warna pastel berbeda tiap kartu (mis. hijau muda, biru muda, ungu muda, kuning muda)
```
Berisi: Label ikon emoji besar (FontSize ~28) + Label teks menu (FontBold) + 1 Button transparan menutupi card (karena `VerticalArrangement` tidak memiliki event Click bawaan).

### 4.4 Alur Fungsional Singkat
1. `Homescreen.Initialize` → ambil nama kasir dari TinyDB → tampilkan di `LabelNamaKasir`
2. Request ke endpoint `getDashboard` → isi 4 angka ringkasan
3. Klik avatar → popup info kasir + tombol Logout (clear TinyDB → kembali ke Screen1)
4. Klik salah satu dari 4 card menu → pindah ke screen terkait (`DataSantri`, `Transaksi`, `Laporan`, `Tagihan`)

---

## 5. Catatan Desain & Keputusan Penting

- **Card "melayang"** dicapai dengan kombinasi `Width: 90 percent` (jarak dari tepi layar) + gambar `card_melayang.png` bersudut membulat dan berbayangan, karena App Inventor tidak punya properti border-radius/shadow native.
- **Padding dalam card** disiasati dengan `Width` persentase pada container dalam + Label kosong sebagai spacer vertikal (App Inventor tidak punya properti padding langsung).
- **Tombol transparan di atas card** dipakai sebagai pengganti event Click pada `VerticalArrangement`/`HorizontalArrangement` yang tidak mendukung klik langsung.
- **Logo butuh 2 versi warna** (putih untuk di atas hijau, berwarna asli untuk di atas putih) — keputusan ini diambil setelah logo asli "hilang" saat diuji di atas background hijau solid.
- Tema keseluruhan: **hijau (#2E7D32) sebagai warna identitas utama**, putih sebagai warna dasar konten, dengan aksen merah khusus untuk status peringatan/error/Beku.

---

*Dokumen ini mencakup Login Screen sampai Homescreen. Modul Data Santri, Transaksi, dan lainnya akan didokumentasikan secara terpisah seiring progres pengembangan.*
