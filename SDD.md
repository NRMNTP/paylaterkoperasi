# SDD.md — Software Design Document

**Sistem:** PayLater Koperasi Pesantren (Kopontren Darul 'Ulum)
**Target:** Implementasi Android Native (Kotlin), menggantikan MIT App Inventor
**Dokumen turunan dari:** `PRD.md` (latar belakang), `SRS.md` (spesifikasi kontrak data & fungsional), `DESIGN.md` (spesifikasi visual)

Dokumen ini adalah **blueprint implementasi konkret** — keputusan arsitektur, struktur kode, dan desain komponen yang dipakai langsung sebagai acuan penulisan kode di Claude Code/Android Studio.

---

## 1. TUJUAN & PRINSIP DESAIN

### 1.1 Tujuan Dokumen
Menerjemahkan spesifikasi fungsional (SRS) menjadi keputusan desain teknis konkret: pattern arsitektur, struktur package, desain class, skema penyimpanan lokal, dan strategi penanganan setiap risiko teknis yang sudah teridentifikasi dari pengalaman App Inventor.

### 1.2 Prinsip Desain yang Memandu Keputusan
Prinsip ini lahir langsung dari pelajaran pahit di App Inventor (SRS §8.2-8.3) dan dipakai sebagai filter setiap keputusan teknis di dokumen ini:

1. **List besar wajib pakai diffing, bukan render-ulang** — App Inventor menghapus-lalu-membuat-ulang seluruh card setiap refresh; ini penyebab utama OutOfMemoryError pada 3800+ baris transaksi.
2. **Filter & search wajib lokal (in-memory), bukan round-trip API** — pelajaran dari bug filter server-side yang gagal total karena ketidakcocokan nama kolom; filter lokal juga lebih cepat dan lebih sedikit titik kegagalan.
3. **State eksplisit, bukan flag global tersembunyi** — alur popup berlapis di App Inventor terpaksa pakai variabel global "modeRequest"/"statusPopup" karena keterbatasan platform; di Kotlin ini wajib jadi `sealed class`/`enum` state yang type-safe.
4. **Parsing data eksternal wajib defensif** — kolom Sheets bisa berupa Date object, string, atau serial number untuk field yang "sama"; parser tidak boleh asumsi satu bentuk.
5. **Validasi finansial berlapis di klien DAN server** — form transaksi tidak boleh mengandalkan validasi server saja; UX wajib mencegah kesalahan sebelum request terkirim.

---

## 2. ARSITEKTUR PERANGKAT LUNAK

### 2.1 Pola Arsitektur: MVVM + Repository Pattern

```
┌─────────────────────────────────────────────────────────┐
│                      UI LAYER                            │
│  Activity/Fragment/Compose Screen  ◄──observe──  ViewModel│
│         │ (user events)                    │ (state)     │
└─────────┼──────────────────────────────────┼─────────────┘
          ▼                                  ▲
┌─────────────────────────────────────────────────────────┐
│                   DOMAIN/VIEWMODEL LAYER                 │
│  ViewModel — memegang UiState, memanggil Repository,     │
│  tidak tahu menahu soal Retrofit/Room secara langsung     │
└─────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────┐
│                   REPOSITORY LAYER                        │
│  Satu Repository per domain (Auth, Santri, Transaksi...) │
│  Sumber kebenaran tunggal — UI tidak pernah panggil API   │
│  langsung, selalu lewat Repository                        │
└──────────────┬─────────────────────────┬──────────────────┘
               ▼                         ▼
┌──────────────────────────┐  ┌──────────────────────────┐
│   REMOTE DATA SOURCE      │  │   LOCAL DATA SOURCE       │
│   Retrofit + OkHttp        │  │   DataStore (sesi login)  │
│   → Google Apps Script API │  │   Room (cache, opsional)  │
└──────────────────────────┘  └──────────────────────────┘
```

**Alasan pemilihan pattern:**
- MVVM dipilih karena lifecycle-aware (`ViewModel` survive rotasi layar), dan secara alami menyediakan tempat eksplisit untuk state yang dulu "terpaksa" jadi variabel global di App Inventor (prinsip desain §1.2 poin 3).
- Repository Pattern mengisolasi seluruh detail "bagaimana data didapat" (network call, format query string aneh dari Apps Script, parsing tanggal defensif) dari UI — UI hanya bicara dengan model domain yang sudah bersih.

### 2.2 Stack Teknologi
| Layer | Pilihan | Alasan |
|---|---|---|
| Bahasa | Kotlin | Standar modern Android |
| UI Framework | Jetpack Compose | Direkomendasikan untuk proyek baru; lebih cepat untuk membangun ulang UI card-based (Data Santri, Transaksi) dibanding XML+RecyclerView konvensional |
| Networking | Retrofit2 + OkHttp3 | Standar de-facto, mendukung query string parameter dengan rapi |
| Serialisasi JSON | kotlinx.serialization atau Moshi | Parsing response Apps Script |
| Local Storage (sesi) | Jetpack DataStore (Preferences) | Pengganti TinyDB App Inventor — modern, async-safe |
| Local Cache (opsional, FR-03/FR-04) | Room | Untuk caching daftar santri/transaksi, mendukung mode hampir-offline |
| Dependency Injection | Hilt | Standar Android modern, memudahkan testing |
| Async | Kotlin Coroutines + Flow | Penanganan request API & state reaktif |
| Image/Asset | Aset PNG dari `DESIGN.md` (logo, gradient, dsb) dipakai ulang sebagai drawable resource, ATAU direplikasi sebagai native Compose modifier (gradient brush, rounded corner) — **direkomendasikan opsi kedua** agar tidak bergantung gambar statis |

### 2.3 Struktur Package

```
com.kopontren.paylater/
│
├── data/
│   ├── remote/
│   │   ├── ApiService.kt              // interface Retrofit, satu method per endpoint
│   │   ├── ApiClient.kt               // konfigurasi Retrofit/OkHttp, base URL
│   │   └── dto/                       // Data Transfer Object — bentuk JSON mentah
│   │       ├── SantriDto.kt
│   │       ├── TransaksiDto.kt
│   │       ├── DashboardDto.kt
│   │       ├── TagihanDto.kt
│   │       └── ApiResponse.kt         // wrapper generic { status, message, data }
│   │
│   ├── local/
│   │   ├── SessionManager.kt          // DataStore — username, jabatan, token sesi
│   │   └── db/                        // (opsional) Room — cache santri/transaksi
│   │       ├── AppDatabase.kt
│   │       ├── SantriDao.kt
│   │       └── entity/SantriEntity.kt
│   │
│   ├── repository/
│   │   ├── AuthRepository.kt
│   │   ├── SantriRepository.kt
│   │   ├── TransaksiRepository.kt
│   │   ├── DashboardRepository.kt
│   │   ├── TagihanRepository.kt
│   │   └── LaporanRepository.kt
│   │
│   └── mapper/
│       └── DtoMappers.kt              // DTO -> Domain Model, termasuk parseTanggalDefensif()
│
├── domain/
│   ├── model/                          // Domain model bersih (bukan DTO mentah)
│   │   ├── Santri.kt
│   │   ├── Transaksi.kt
│   │   ├── Dashboard.kt
│   │   ├── Tagihan.kt
│   │   └── UrgensiTagihan.kt           // enum: TELAT, KRITIS, SEGERA, NORMAL
│   └── usecase/                        // opsional, untuk logika lintas-repository
│       └── ValidasiTransaksiUseCase.kt // validasi nama-ada-di-daftar, nominal>0, dll
│
├── ui/
│   ├── login/
│   │   ├── LoginScreen.kt
│   │   ├── LoginViewModel.kt
│   │   └── LoginUiState.kt
│   ├── dashboard/
│   │   ├── DashboardScreen.kt
│   │   ├── DashboardViewModel.kt
│   │   └── DashboardUiState.kt
│   ├── santri/
│   │   ├── list/
│   │   │   ├── SantriListScreen.kt
│   │   │   ├── SantriListViewModel.kt
│   │   │   └── SantriListUiState.kt
│   │   └── detail/
│   │       ├── SantriDetailScreen.kt
│   │       └── SantriDetailViewModel.kt
│   ├── transaksi/
│   │   ├── list/
│   │   │   ├── TransaksiListScreen.kt
│   │   │   ├── TransaksiListViewModel.kt
│   │   │   └── TransaksiListUiState.kt
│   │   └── form/
│   │       ├── FormTransaksiScreen.kt
│   │       ├── FormTransaksiViewModel.kt
│   │       └── FormTransaksiUiState.kt  // sealed class state popup (lihat §5.3)
│   ├── tagihan/
│   │   ├── TagihanScreen.kt
│   │   └── TagihanViewModel.kt
│   ├── laporan/
│   │   ├── LaporanScreen.kt
│   │   └── LaporanViewModel.kt
│   ├── components/                      // komponen reusable lintas-layar
│   │   ├── SantriCard.kt
│   │   ├── TransaksiCard.kt
│   │   ├── GradientBackground.kt        // pengganti bg_gradasi.png
│   │   ├── FloatingCard.kt              // pengganti card_melayang.png
│   │   ├── ConfirmationDialog.kt        // dialog konfirmasi generik
│   │   └── AutocompleteTextField.kt     // dropdown+ketik untuk nama santri
│   └── theme/
│       ├── Color.kt                     // palet dari DESIGN.md §1
│       ├── Typography.kt
│       └── Theme.kt
│
├── navigation/
│   └── NavGraph.kt                      // Jetpack Navigation Compose
│
└── di/
    ├── NetworkModule.kt                 // Hilt — provide Retrofit, OkHttp
    └── RepositoryModule.kt
```

---

## 3. DESAIN LAPISAN DATA (DATA LAYER)

### 3.1 ApiService — Kontrak Retrofit

```kotlin
interface ApiService {
    @GET(".")
    suspend fun login(
        @Query("action") action: String = "login",
        @Query("username") username: String,
        @Query("password") password: String
    ): LoginResponseDto

    @GET(".")
    suspend fun getSantri(
        @Query("action") action: String = "getSantri"
    ): SantriListResponseDto

    @GET(".")
    suspend fun getDetailSantri(
        @Query("action") action: String = "getDetailSantri",
        @Query("nama") nama: String
    ): SantriDetailResponseDto

    @GET(".")
    suspend fun getTransaksi(
        @Query("action") action: String = "getTransaksi",
        @Query("filter") filter: String = "All",
        @Query("limit") limit: Int = 40
    ): TransaksiListResponseDto

    @GET(".")
    suspend fun tambahTransaksi(
        @Query("action") action: String = "tambahTransaksi",
        @Query("nama") nama: String,
        @Query("nominal") nominal: Long,
        @Query("kasir") kasir: String
    ): SimpanTransaksiResponseDto

    @GET(".")
    suspend fun bayarHutang(
        @Query("action") action: String = "bayarHutang",
        @Query("nama") nama: String,
        @Query("nominal") nominal: Long,
        @Query("kasir") kasir: String
    ): SimpanTransaksiResponseDto

    @GET(".")
    suspend fun getLaporan(
        @Query("action") action: String = "getLaporan",
        @Query("tipe") tipe: String
    ): LaporanResponseDto

    @GET(".")
    suspend fun getTagihan(
        @Query("action") action: String = "getTagihan"
    ): TagihanListResponseDto

    @GET(".")
    suspend fun getDashboard(
        @Query("action") action: String = "getDashboard"
    ): DashboardResponseDto
}
```
**Catatan desain:** base URL diarahkan ke endpoint Apps Script (lihat SRS §9.1), path kosong (`"."`) karena seluruh routing terjadi via parameter `action` di backend — **bukan** REST path konvensional.

### 3.2 Parsing Tanggal Defensif (mapper wajib)

Ini langsung menerjemahkan SRS §3.2 (penanganan 3 bentuk tanggal) ke kode. **Karena backend Apps Script sudah memformat tanggal sebagai string `DD/MM/YYYY` sebelum dikirim sebagai JSON**, klien Kotlin sebenarnya menerima string yang sudah aman — namun mapper tetap wajib defensif terhadap kemungkinan string kosong atau format tak terduga:

```kotlin
fun parseTanggalDefensif(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    return try {
        // Format dari backend: DD/MM/YYYY
        LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (e: DateTimeParseException) {
        null // jangan crash — field tanggal opsional di banyak kasus (lihat SRS §3.2)
    }
}
```

### 3.3 Response Wrapper Generic

```kotlin
@Serializable
data class ApiResponse<T>(
    val status: String,              // "success" | "error"
    val message: String? = null,
    val data: T? = null
)

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<T>()
    data class NetworkError(val throwable: Throwable) : ApiResult<Nothing>()
}
```
Semua pemanggilan Repository membungkus hasil Retrofit ke `ApiResult` — UI Layer tidak pernah menangani exception mentah, sejalan dengan prinsip desain §1.2 poin 5 (penanganan error eksplisit, bukan implisit).

### 3.4 SessionManager (pengganti TinyDB)

```kotlin
class SessionManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val KEY_USERNAME = stringPreferencesKey("username")
    private val KEY_JABATAN = stringPreferencesKey("jabatan")

    val sessionFlow: Flow<Session?> = dataStore.data.map { prefs ->
        val username = prefs[KEY_USERNAME] ?: return@map null
        val jabatan = prefs[KEY_JABATAN] ?: ""
        Session(username, jabatan)
    }

    suspend fun saveSession(username: String, jabatan: String) {
        dataStore.edit { it[KEY_USERNAME] = username; it[KEY_JABATAN] = jabatan }
    }

    suspend fun clearSession() {
        dataStore.edit { it.clear() }
    }
}

data class Session(val username: String, val jabatan: String)
```

---

## 4. DESAIN REPOSITORY (per domain)

### 4.1 Pola Umum Tiap Repository
```kotlin
class SantriRepository @Inject constructor(
    private val api: ApiService
) {
    // Cache in-memory sederhana — sumber untuk filter/search LOKAL (prinsip desain §1.2 poin 2)
    private var cachedSantri: List<Santri> = emptyList()

    suspend fun getSantriList(forceRefresh: Boolean = false): ApiResult<List<Santri>> {
        if (!forceRefresh && cachedSantri.isNotEmpty()) {
            return ApiResult.Success(cachedSantri)
        }
        return try {
            val response = api.getSantri()
            if (response.status == "success") {
                cachedSantri = response.data.orEmpty().map { it.toDomain() }
                ApiResult.Success(cachedSantri)
            } else {
                ApiResult.Error(response.message ?: "Gagal memuat data santri")
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        }
    }

    // Filter LOKAL — tidak ada panggilan API, sesuai prinsip desain
    fun filterByNama(keyword: String): List<Santri> =
        cachedSantri.filter { it.nama.contains(keyword, ignoreCase = true) }

    fun getNamaList(): List<String> = cachedSantri.map { it.nama }

    fun isNamaValid(nama: String): Boolean = cachedSantri.any { it.nama.equals(nama, ignoreCase = true) }
}
```

### 4.2 TransaksiRepository — Filter Tab Lokal

```kotlin
class TransaksiRepository @Inject constructor(
    private val api: ApiService
) {
    private var cachedTransaksi: List<Transaksi> = emptyList()
    var totalSemua: Int = 0
        private set

    suspend fun loadTransaksi(limit: Int = 40): ApiResult<List<Transaksi>> {
        return try {
            val response = api.getTransaksi(filter = "All", limit = limit)
            if (response.status == "success") {
                cachedTransaksi = response.data.orEmpty().map { it.toDomain() }
                totalSemua = response.totalSemua ?: 0
                ApiResult.Success(cachedTransaksi)
            } else {
                ApiResult.Error(response.message ?: "Gagal memuat transaksi")
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        }
    }

    // FILTER TAB — 100% lokal, TIDAK PERNAH panggil API per-tab
    // (ini adalah perbaikan langsung dari bug App Inventor di mana filter server-side
    //  gagal akibat ketidakcocokan nama kolom — lihat SRS §3.2, §7.2)
    fun filterByStatus(status: TabFilter): List<Transaksi> = when (status) {
        TabFilter.ALL -> cachedTransaksi
        TabFilter.HUTANG -> cachedTransaksi.filter { it.status.equals("Hutang", ignoreCase = true) }
        TabFilter.BAYAR -> cachedTransaksi.filter { it.status.equals("Bayar", ignoreCase = true) }
    }

    fun searchByNama(list: List<Transaksi>, keyword: String): List<Transaksi> =
        if (keyword.isBlank()) list else list.filter { it.nama.contains(keyword, ignoreCase = true) }

    suspend fun tambahHutang(nama: String, nominal: Long, kasir: String): ApiResult<String> =
        kirimTransaksi { api.tambahTransaksi(nama = nama, nominal = nominal, kasir = kasir) }

    suspend fun bayarHutang(nama: String, nominal: Long, kasir: String): ApiResult<String> =
        kirimTransaksi { api.bayarHutang(nama = nama, nominal = nominal, kasir = kasir) }

    private suspend fun kirimTransaksi(
        call: suspend () -> SimpanTransaksiResponseDto
    ): ApiResult<String> = try {
        val response = call()
        if (response.status == "success") {
            ApiResult.Success(response.message ?: "Transaksi berhasil disimpan")
        } else {
            ApiResult.Error(response.message ?: "Transaksi gagal disimpan")
        }
    } catch (e: IOException) {
        ApiResult.NetworkError(e)
    }
}

enum class TabFilter { ALL, HUTANG, BAYAR }
```

---

## 5. DESAIN STATE MANAGEMENT (ViewModel)

### 5.1 Pola UiState Umum
Setiap layar punya `data class XxxUiState` immutable, di-expose via `StateFlow`, dan ViewModel adalah satu-satunya yang boleh mengubahnya.

```kotlin
data class SantriListUiState(
    val isLoading: Boolean = false,
    val santriList: List<Santri> = emptyList(),
    val searchQuery: String = "",
    val errorMessage: String? = null
)
```

### 5.2 SantriListViewModel

```kotlin
@HiltViewModel
class SantriListViewModel @Inject constructor(
    private val repository: SantriRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SantriListUiState())
    val uiState: StateFlow<SantriListUiState> = _uiState.asStateFlow()

    init { loadSantri() }

    fun loadSantri() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.getSantriList()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, santriList = result.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Periksa koneksi internet Anda")
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        // FILTER LOKAL — instan, tanpa loading state (sesuai SRS FR-03 AC2)
        _uiState.update {
            it.copy(
                searchQuery = query,
                santriList = if (query.isBlank()) repository.getSantriList0Cached()
                             else repository.filterByNama(query)
            )
        }
    }
}
```

### 5.3 FormTransaksiViewModel — State Machine Popup Berlapis

Ini adalah penerjemahan langsung dari prinsip desain §1.2 poin 3 — menggantikan variabel global `statusPopup`/`jenisTrx` App Inventor dengan `sealed class` type-safe:

```kotlin
sealed class FormTransaksiDialogState {
    object None : FormTransaksiDialogState()
    object PilihJenis : FormTransaksiDialogState()
    data class Konfirmasi(val jenis: JenisTransaksi) : FormTransaksiDialogState()
}

enum class JenisTransaksi { HUTANG, BAYAR }

data class FormTransaksiUiState(
    val namaInput: String = "",
    val nominalInput: String = "",
    val saranNama: List<String> = emptyList(),       // hasil autocomplete
    val namaTerbaruList: List<String> = emptyList(),  // shortcut transaksi terbaru
    val dialogState: FormTransaksiDialogState = FormTransaksiDialogState.None,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class FormTransaksiViewModel @Inject constructor(
    private val santriRepository: SantriRepository,
    private val transaksiRepository: TransaksiRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FormTransaksiUiState())
    val uiState: StateFlow<FormTransaksiUiState> = _uiState.asStateFlow()

    fun onNamaChange(value: String) {
        _uiState.update {
            it.copy(
                namaInput = value,
                saranNama = if (value.isBlank()) emptyList()
                            else santriRepository.filterByNama(value).map { s -> s.nama }
            )
        }
    }

    fun onPilihSaranNama(nama: String) {
        _uiState.update { it.copy(namaInput = nama, saranNama = emptyList()) }
    }

    fun onNominalChange(value: String) {
        _uiState.update { it.copy(nominalInput = value.filter { c -> c.isDigit() }) }
    }

    // Langkah 1: validasi lokal lalu munculkan dialog pilih jenis
    // (SRS §5.4.2 — wajib lolos validasi sebelum dialog manapun muncul)
    fun onTambahClicked() {
        val state = _uiState.value
        val nama = state.namaInput.trim()
        val nominal = state.nominalInput.toLongOrNull()

        val error = when {
            nama.isEmpty() -> "Nama santri wajib diisi"
            !santriRepository.isNamaValid(nama) -> "Santri tidak ditemukan. Pilih dari daftar."
            nominal == null || nominal <= 0 -> "Nominal harus lebih dari 0"
            else -> null
        }

        if (error != null) {
            _uiState.update { it.copy(errorMessage = error) }
            return
        }

        _uiState.update { it.copy(errorMessage = null, dialogState = FormTransaksiDialogState.PilihJenis) }
    }

    // Langkah 2: user pilih Hutang/Bayar -> tampilkan konfirmasi ringkasan
    fun onJenisDipilih(jenis: JenisTransaksi) {
        _uiState.update { it.copy(dialogState = FormTransaksiDialogState.Konfirmasi(jenis)) }
    }

    fun onDialogDibatalkan() {
        _uiState.update { it.copy(dialogState = FormTransaksiDialogState.None) }
    }

    // Langkah 3: user konfirmasi "Ya, Simpan" -> kirim ke API
    fun onKonfirmasiDisetujui(jenis: JenisTransaksi) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, dialogState = FormTransaksiDialogState.None) }

            val kasir = sessionManager.sessionFlow.first()?.username ?: "Kasir"
            val nama = _uiState.value.namaInput.trim()
            val nominal = _uiState.value.nominalInput.toLong()

            val result = when (jenis) {
                JenisTransaksi.HUTANG -> transaksiRepository.tambahHutang(nama, nominal, kasir)
                JenisTransaksi.BAYAR -> transaksiRepository.bayarHutang(nama, nominal, kasir)
            }

            when (result) {
                is ApiResult.Success -> _uiState.update {
                    FormTransaksiUiState(successMessage = result.data) // reset form total (SRS AC4)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = result.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = "Gagal terhubung ke server")
                }
            }
        }
    }
}
```

**Keunggulan desain ini dibanding App Inventor:** kompiler Kotlin memaksa setiap cabang `when (dialogState)` di Compose ditangani secara eksplisit — tidak mungkin lupa kondisi seperti yang berulang kali terjadi di App Inventor (`statusPopup` lupa direset, kondisi if-elseif salah urut, dsb).

---

## 6. DESAIN UI — KOMPONEN KUNCI (Jetpack Compose)

### 6.1 GradientBackground (pengganti `bg_gradasi.png`)
```kotlin
@Composable
fun GradientBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(HijauGelap, HijauUtama, HijauTerang) // DESIGN.md §1
                )
            ),
        content = content
    )
}
```
**Keputusan desain:** gradient dibuat native via `Brush`, **bukan** memakai gambar PNG statis seperti App Inventor — lebih ringan, scalable sempurna di semua ukuran layar, dan mudah diubah.

### 6.2 FloatingCard (pengganti `card_melayang.png`)
```kotlin
@Composable
fun FloatingCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(top = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp), content = content)
    }
}
```
**Keputusan desain:** `elevation` dan `RoundedCornerShape` native Compose menggantikan trik gambar bayangan pre-rendered — pelajaran langsung dari keterbatasan App Inventor yang dicatat di SRS §8.2 ("Tidak ada gradient/border-radius/shadow native").

### 6.3 SantriCard
```kotlin
@Composable
fun SantriCard(santri: Santri, onClick: () -> Unit) {
    val isBeku = santri.status.equals("Beku", ignoreCase = true)
    val warna = if (isBeku) MerahError else Color.Unspecified

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isBeku) "🔴" else "🟢")
                Spacer(Modifier.width(8.dp))
                Text(santri.nama, fontWeight = FontWeight.Bold, color = warna, modifier = Modifier.weight(1f))
                Text(santri.level, color = if (isBeku) warna else HijauUtama)
            }
            Text(
                "Hutang: Rp ${santri.hutangAktif} · Sisa: Rp ${santri.sisaLimit}",
                fontSize = 12.sp,
                color = if (isBeku) warna else Color.Gray
            )
        }
    }
}
```
**Keputusan desain:** seluruh card adalah satu `clickable` Composable — area klik mencakup **seluruh card**, bukan hanya elemen nama seperti keterbatasan App Inventor (SRS §8.2: "tidak mungkin membuat elemen klik menutupi card sepenuhnya"). Ini sekaligus memperbaiki UX yang diminta pengguna saat pengembangan App Inventor namun tidak bisa dipenuhi sempurna di platform lama.

### 6.4 AutocompleteTextField (Form Transaksi)
```kotlin
@Composable
fun AutocompleteTextField(
    value: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
    onSuggestionPicked: (String) -> Unit,
    label: String
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
        if (suggestions.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                LazyColumn {
                    items(suggestions) { nama ->
                        Text(
                            nama,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionPicked(nama) }
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
```

---

## 7. SEQUENCE DIAGRAM — ALUR KRITIS

### 7.1 Alur Login
```
User          LoginScreen        LoginViewModel       AuthRepository      API
 │                 │                    │                    │             │
 │ isi form, tap   │                    │                    │             │
 │ "Masuk" ───────►│                    │                    │             │
 │                 │ onLoginClicked() ─►│                    │             │
 │                 │                    │ validasi lokal     │             │
 │                 │                    │ (tidak kosong)     │             │
 │                 │                    │ login(u,p) ───────►│             │
 │                 │                    │                    │ GET login ─►│
 │                 │                    │                    │◄── JSON ────│
 │                 │                    │◄── ApiResult ──────│             │
 │                 │                    │ saveSession()      │             │
 │                 │◄── uiState: navigateToHome ──────────────│             │
 │◄── pindah ke Dashboard ──│                                 │             │
```

### 7.2 Alur Tambah Transaksi (Hutang) — Konfirmasi Berlapis
```
User      FormScreen       FormViewModel         SantriRepo    TransaksiRepo   API
 │            │                  │                    │              │          │
 │ ketik nama │                  │                    │              │          │
 │ + nominal ►│ onTambahClicked()►│                    │              │          │
 │            │                  │ isNamaValid()? ────►│              │          │
 │            │                  │◄── true/false ──────│              │          │
 │            │                  │ [valid] set dialogState=PilihJenis │          │
 │            │◄── dialog: Hutang/Bayar ───────────────│              │          │
 │ pilih      │                  │                    │              │          │
 │ "Hutang" ─►│ onJenisDipilih() ►│                    │              │          │
 │            │                  │ set dialogState=Konfirmasi(HUTANG) │          │
 │            │◄── dialog: ringkasan + Ya/Batal ───────│              │          │
 │ tap        │                  │                    │              │          │
 │ "Ya" ─────►│ onKonfirmasiDisetujui()►│              │              │          │
 │            │                  │ tambahHutang() ─────────────────►│           │
 │            │                  │                    │              │ GET ────►│
 │            │                  │                    │              │◄─ JSON ──│
 │            │                  │◄── ApiResult.Success ──────────────│         │
 │            │                  │ reset form, tampilkan toast sukses │         │
 │            │◄── uiState: successMessage ───────────│              │          │
 │◄── toast "Hutang berhasil ditambahkan" ──│                        │          │
```

**Catatan kritis:** sequence ini secara eksplisit menunjukkan **tidak ada jalan pintas** dari "tap Tambah" langsung ke API tanpa melalui dua dialog — ini memenuhi SRS §5.4.2 AC1 sebagai hard requirement, dan secara struktural tidak bisa dilewati karena `dialogState` adalah satu-satunya pemicu pemanggilan repository (lihat §5.3).

---

## 8. SKEMA NAVIGASI

```kotlin
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object SantriList : Screen("santri_list")
    object SantriDetail : Screen("santri_detail/{nama}") {
        fun createRoute(nama: String) = "santri_detail/$nama"
    }
    object TransaksiList : Screen("transaksi_list")
    object FormTransaksi : Screen("form_transaksi")
    object Tagihan : Screen("tagihan")
    object Laporan : Screen("laporan")
}
```
**Keputusan desain:** Jetpack Navigation Compose dengan route berbasis string mengganti `open another screen` App Inventor — parameter (mis. `nama` santri terpilih) dikirim **langsung lewat route**, bukan dititipkan ke penyimpanan lokal lebih dulu seperti trik `DBLokal.StoreValue("santriDipilih")` yang terpaksa dipakai di App Inventor karena keterbatasan perpindahan layar platform tersebut.

---

## 9. STRATEGI MIGRASI DARI APP INVENTOR

### 9.1 Yang Dipindah Apa Adanya
- **Backend Code.gs** — tidak disentuh sama sekali (lihat SRS §9.2)
- **Kontrak data & API** — SRS §3-4 jadi sumber kebenaran tunggal untuk DTO/model
- **Keputusan UX kritis** — alur konfirmasi berlapis form transaksi (lahir dari kebutuhan nyata mencegah human error, bukan preferensi kosmetik)
- **Palet warna & identitas visual** — DESIGN.md §1

### 9.2 Yang Ditulis Ulang dengan Pendekatan Berbeda
| Aspek App Inventor | Pendekatan Baru | Alasan |
|---|---|---|
| DynamicComponents + Schema JSON untuk card | Compose `LazyColumn` + Composable card biasa | Native, jauh lebih ringan, tidak rawan crash |
| `replace all text` placeholder manual | Data binding Compose langsung | Type-safe, tidak ada parsing string rapuh |
| Variable global untuk state popup | `sealed class` state di ViewModel | Type-safe, dipaksa compiler untuk exhaustive handling |
| `DBLokal.StoreValue` untuk kirim parameter antar layar | Navigation route argument | Eksplisit, tidak ada "data tersembunyi" antar layar |
| Button transparan untuk simulasi klik card | `Modifier.clickable` pada seluruh Card | Native, area klik penuh, tanpa workaround |
| Gambar PNG untuk gradient/shadow/rounded corner | Compose `Brush`, `elevation`, `RoundedCornerShape` | Native, scalable, tidak perlu asset generation |
| `any Button.Click` global handler + filter manual by Text | Lambda `onClick` spesifik per Composable | Tidak ada false-positive klik (bug yang pernah terjadi: FAB ketangkap handler list, lihat riwayat debugging) |
| Filter Hutang/Bayar via API request ulang | Filter in-memory di Repository | Lihat prinsip desain §1.2 poin 2 |

### 9.3 Fitur Belum Dibangun (Prioritas Lanjutan)
Sesuai SRS §8 Matriks Traceability:
1. **FR-06 Tagihan Santri** — backend siap, desain Composable mengikuti pola `SantriCard` dengan tambahan badge urgensi berwarna (§5.6 SRS)
2. **FR-07 Laporan** — backend siap, kemungkinan butuh komponen chart (rekomendasi: library `Vico` atau `MPAndroidChart` wrapper Compose)
3. **Splash screen & animasi** — gunakan `androidx.core.splashscreen` API standar Android 12+, jauh lebih sederhana dibanding upaya animasi manual yang sempat direncanakan di App Inventor namun ditunda

### 9.4 Verifikasi Wajib Sebelum Dianggap "Setara" dengan App Inventor
Sesuai SRS §5.4.2 AC5 — verifikasi end-to-end submit transaksi (form → API → muncul sebagai baris baru valid di Google Sheets) **wajib diulang** sebagai bagian dari Definition of Done modul Transaksi versi Android native, mengingat status fitur ini masih "bug aktif belum tuntas" di versi App Inventor (lihat SRS §8 Traceability Matrix).

---

## 10. PENANGANAN ERROR & EDGE CASE (checklist desain)

| Skenario | Penanganan Desain |
|---|---|
| Tidak ada koneksi internet saat request apapun | `ApiResult.NetworkError` → tampilkan pesan "Periksa koneksi internet Anda", tombol retry |
| Response API bukan JSON valid / server error 5xx | Tertangkap di lapisan Retrofit/OkHttp interceptor → diteruskan sebagai `NetworkError` |
| User double-tap tombol submit transaksi | `isSubmitting` di UiState men-disable tombol selama request berlangsung (SRS §7.2) |
| Nama santri di-input tapi tidak ada di daftar (typo) | Validasi lokal di `onTambahClicked()` — ditolak sebelum dialog manapun muncul |
| Field tanggal kosong dari API (transaksi tanpa jatuh tempo, mis. status Bayar) | `parseTanggalDefensif()` mengembalikan `null`, UI menampilkan "-" atau menyembunyikan baris, bukan crash |
| Daftar santri/transaksi kosong (belum ada data) | Empty state UI eksplisit ("Belum ada data"), bukan layar kosong tanpa penjelasan |
| Sesi login hilang/expired (jika backend menambah validasi token di masa depan) | `SessionManager.sessionFlow` null → auto-redirect ke Login (belum relevan untuk versi saat ini karena backend tidak pakai token, dicatat sebagai extensibility point) |

---

## 11. LAMPIRAN

### 11.1 Dependensi Gradle (ringkasan, versi disesuaikan saat implementasi)
```kotlin
dependencies {
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("androidx.datastore:datastore-preferences")
    implementation("com.squareup.retrofit2:retrofit")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization")
    implementation("com.squareup.okhttp3:logging-interceptor")
    implementation("com.google.dagger:hilt-android")
    kapt("com.google.dagger:hilt-compiler")
    // Opsional (caching):
    implementation("androidx.room:room-runtime")
    implementation("androidx.room:room-ktx")
}
```

### 11.2 Dokumen Terkait
- `PRD.md` — latar belakang produk & ringkasan fitur
- `SRS.md` — spesifikasi kontrak data, API, dan fungsional detail (sumber kebenaran untuk DTO & business rule)
- `DESIGN.md` — spesifikasi visual Login Screen & Homescreen (sumber kebenaran untuk palet warna & layout)

---

*Dokumen ini adalah blueprint implementasi — setiap keputusan di dalamnya dapat ditelusuri balik ke pelajaran konkret dari fase pengembangan App Inventor sebelumnya (dirujuk silang ke SRS §7-8). Perubahan signifikan pada keputusan arsitektur di sini wajib diperbarui bersamaan dengan SRS jika berdampak pada kontrak data/fungsional.*
