package com.aetherwave.radio.ui

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aetherwave.radio.data.DefaultStations
import com.aetherwave.radio.data.SortOption
import com.aetherwave.radio.data.Station
import com.aetherwave.radio.data.ViewMode
import com.aetherwave.radio.service.AlarmReceiver
import com.aetherwave.radio.service.RadioPlaybackService
import com.aetherwave.radio.util.BluetoothHelper
import com.aetherwave.radio.util.SleepTimerManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

data class SavedThemePreset(
    val id: String,
    val name: String,
    val accentHex: String,
    val backgroundHex: String,
    val cardHex: String,
    val fontStyle: String = "sans",
    val fontColorHex: String = "auto"
)

enum class SettingsSubPage {
    PLAYER, APPEARANCE, EQUALIZER, SPECTRUM_VISUALIZER,
    GENRE_MANAGER, CLOUD_BACKUP, ALARM_CLOCK, BLUETOOTH, ABOUT
}

data class ScrapedRadioStation(
    val name: String, val url: String, val favicon: String?,
    val tags: String?, val country: String?, val codec: String?, val bitrate: Int?
)

data class RadioUiState(
    val currentStation: Station? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isDeckOpen: Boolean = false,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOption: SortOption = SortOption.CUSTOM,
    val searchQuery: String = "",
    val selectedGroup: String = "All",
    val themeColorHex: String = "#F59E0B",
    val backgroundColorHex: String = "#090D16",
    val cardColorHex: String = "#111726",
    val themeFontStyle: String = "sans",
    val themeFontColorHex: String = "auto",
    val themeMode: String = "dark",
    val isMonetEnabled: Boolean = false,
    val isMuted: Boolean = false,
    val eqPreset: String = "Flat",
    val eqBands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val bassBoost: Float = 0.5f,
    val virtualizer: Float = 0.3f,
    // Spectrum
    val spectrumStyle: String = "Bars",
    val spectrumHeight: Int = 85, // Box height
    val spectrumBarHeightScale: Float = 1.0f, // 0.3 to 1.0 (bar max height)
    val spectrumSensitivity: Float = 1.2f,
    val spectrumColorScheme: String = "Accent",
    val spectrumSpeed: String = "Normal",
    // Deck Theme (Independent from main theme)
    val deckAccentHex: String = "#F59E0B",
    val deckBackgroundHex: String = "#0B101D",
    // Other
    val syncRoomCode: String = "RADIO-A89K",
    val isAutoSync: Boolean = false,
    val isArrangeMode: Boolean = false,
    val isSettingsPageOpen: Boolean = false,
    val currentSettingsSubPage: SettingsSubPage? = null,
    val isGenreSelectMode: Boolean = false,
    val isGenreArrangeMode: Boolean = false,
    val selectedGenresForDeletion: Set<String> = emptySet(),
    val alarmEnabled: Boolean = false,
    val alarmHour: Int = 7,
    val alarmMinute: Int = 30,
    val alarmStationId: String? = null,
    val bufferSize: String = "Medium",
    val stopOnHeadsetDisconnect: Boolean = true,
    val autoResumeOnBluetooth: Boolean = false,
    val highQualityAudio: Boolean = true,
    val openedSettingsFromDeck: Boolean = false,
    val fftBars: FloatArray = FloatArray(64),
    val useRealFft: Boolean = false,
    val savedThemePresets: List<SavedThemePreset> = emptyList(),
    val activeThemePresetId: String? = null
)

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("aetherwave_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    val bluetoothHelper = BluetoothHelper(application)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _stations = MutableStateFlow<List<Station>>(loadSavedStations())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val _genres = MutableStateFlow<List<String>>(loadSavedGenres())
    val genres: StateFlow<List<String>> = _genres.asStateFlow()

    private val savedStationId = prefs.getString("last_played_station_id", null)
    private val initialStation = _stations.value.firstOrNull { it.id == savedStationId }
    private var pendingPlayStation: Station? = null

    private val _searchSuggestions = MutableStateFlow<List<ScrapedRadioStation>>(emptyList())
    val searchSuggestions: StateFlow<List<ScrapedRadioStation>> = _searchSuggestions.asStateFlow()

    private val _isScraping = MutableStateFlow(false)
    val isScraping: StateFlow<Boolean> = _isScraping.asStateFlow()

    private var scrapeJob: Job? = null
    private var fftJob: Job? = null

    private val _uiState = MutableStateFlow(
        RadioUiState(
            currentStation = initialStation,
            themeColorHex = prefs.getString("theme_color", "#F59E0B") ?: "#F59E0B",
            backgroundColorHex = prefs.getString("bg_color", "#090D16") ?: "#090D16",
            cardColorHex = prefs.getString("card_color", "#111726") ?: "#111726",
            themeFontStyle = prefs.getString("theme_font", "sans") ?: "sans",
            themeFontColorHex = prefs.getString("theme_font_color", "auto") ?: "auto",
            themeMode = prefs.getString("theme_mode", "dark") ?: "dark",
            isMonetEnabled = prefs.getBoolean("monet_enabled", false),
            selectedGroup = prefs.getString("selected_genre_group", "All") ?: "All",
            viewMode = ViewMode.valueOf(prefs.getString("view_mode", ViewMode.GRID.name) ?: ViewMode.GRID.name),
            sortOption = SortOption.valueOf(prefs.getString("sort_option", SortOption.CUSTOM.name) ?: SortOption.CUSTOM.name),
            spectrumStyle = prefs.getString("spec_style", "Bars") ?: "Bars",
            spectrumHeight = prefs.getInt("spec_height", 85),
            spectrumBarHeightScale = prefs.getFloat("spec_bar_scale", 1.0f),
            spectrumSensitivity = prefs.getFloat("spec_sens", 1.2f),
            spectrumColorScheme = prefs.getString("spec_color", "Accent") ?: "Accent",
            spectrumSpeed = prefs.getString("spec_speed", "Normal") ?: "Normal",
            deckAccentHex = prefs.getString("deck_accent", "#F59E0B") ?: "#F59E0B",
            deckBackgroundHex = prefs.getString("deck_bg", "#0B101D") ?: "#0B101D",
            syncRoomCode = prefs.getString("sync_room", "RADIO-A89K") ?: "RADIO-A89K",
            alarmEnabled = prefs.getBoolean("alarm_enabled", false),
            alarmHour = prefs.getInt("alarm_hour", 7),
            alarmMinute = prefs.getInt("alarm_minute", 30),
            alarmStationId = prefs.getString("alarm_station_id", null),
            bufferSize = prefs.getString("buffer_size", "Medium") ?: "Medium",
            stopOnHeadsetDisconnect = prefs.getBoolean("stop_on_disconnect", true),
            autoResumeOnBluetooth = prefs.getBoolean("auto_resume_bt", false),
            highQualityAudio = prefs.getBoolean("high_quality", true),
            savedThemePresets = loadSavedPresets(),
            activeThemePresetId = prefs.getString("active_preset_id", null)
        )
    )
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    private var originalVolumeBeforeFade = 0.85f

    val sleepTimerManager = SleepTimerManager(
        onTickVolumeFade = { ratio -> mediaController?.volume = originalVolumeBeforeFade * ratio },
        onTimerFinished = {
            pausePlayback()
            mediaController?.volume = originalVolumeBeforeFade
        }
    )

    init {
        initMediaController(application)
        observeFft()
    }

    private fun observeFft() {
        fftJob?.cancel()
        fftJob = viewModelScope.launch {
            RadioPlaybackService.fftState.collect { bins ->
                _uiState.value = _uiState.value.copy(fftBars = bins)
            }
        }
        viewModelScope.launch {
            RadioPlaybackService.hasRealFft.collect { hasReal ->
                _uiState.value = _uiState.value.copy(useRealFft = hasReal)
            }
        }
    }

    fun syncPlayerState() {
        mediaController?.let { player ->
            val isPlaying = player.isPlaying
            val currentMediaId = player.currentMediaItem?.mediaId
            val matched = _stations.value.firstOrNull { it.id == currentMediaId }
                ?: _uiState.value.currentStation
                ?: _stations.value.firstOrNull { it.id == prefs.getString("last_played_station_id", null) }
            if (matched != null) {
                _uiState.value = _uiState.value.copy(
                    currentStation = matched, isPlaying = isPlaying,
                    isLoading = player.playbackState == Player.STATE_BUFFERING
                )
            }
        }
    }

    private fun initMediaController(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, RadioPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.value = _uiState.value.copy(isPlaying = isPlaying, isLoading = false)
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _uiState.value = _uiState.value.copy(isLoading = playbackState == Player.STATE_BUFFERING)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("RadioViewModel", "Player error", error)
                        _uiState.value = _uiState.value.copy(isPlaying = false, isLoading = false)
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val mediaId = mediaItem?.mediaId
                        if (!mediaId.isNullOrEmpty()) {
                            val st = _stations.value.firstOrNull { it.id == mediaId }
                            if (st != null) {
                                _uiState.value = _uiState.value.copy(currentStation = st)
                                prefs.edit().putString("last_played_station_id", st.id).apply()
                            }
                        }
                    }
                })
                syncPlayerState()
                pendingPlayStation?.let { playStation(it); pendingPlayStation = null }
            } catch (e: Exception) {
                Log.e("RadioViewModel", "MediaController error", e)
            }
        }, MoreExecutors.directExecutor())
    }

    val allFilterGroups: StateFlow<List<String>> = combine(_stations, _genres) { stationList, genreList ->
        val result = linkedSetOf<String>()
        result.add("All")
        result.add("Favorites")
        result.addAll(genreList)
        stationList.forEach { s ->
            if (s.group.isNotBlank() && s.group != "All" && s.group != "Favorites") result.add(s.group)
        }
        result.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All", "Favorites") + DefaultStations.GROUPS.filter { it != "All" && it != "Favorites" })

    val filteredAndSortedStations: StateFlow<List<Station>> = combine(_stations, _uiState) { stationsList, state ->
        val query = state.searchQuery.trim().lowercase()
        val group = state.selectedGroup
        var list = stationsList.filter { st ->
            val matchQuery = query.isEmpty() || st.name.lowercase().contains(query) || st.group.lowercase().contains(query) || (st.country?.lowercase()?.contains(query) ?: false)
            val matchGroup = when {
                group.equals("All", ignoreCase = true) -> true
                group.equals("Favorites", ignoreCase = true) -> st.isFavorite
                else -> st.group.equals(group, ignoreCase = true)
            }
            matchQuery && matchGroup
        }
        list = when (state.sortOption) {
            SortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            SortOption.FAVORITES_FIRST -> list.sortedByDescending { it.isFavorite }
            SortOption.RECENTLY_PLAYED -> list
            SortOption.CUSTOM -> list
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playStation(station: Station) {
        if (station.streamUrl.isBlank()) return
        _uiState.value = _uiState.value.copy(currentStation = station, isLoading = true)
        prefs.edit().putString("last_played_station_id", station.id).apply()
        try {
            val controller = mediaController
            if (controller != null) {
                controller.setMediaItem(RadioPlaybackService.buildMediaItem(station))
                controller.prepare()
                controller.play()
            } else {
                pendingPlayStation = station
            }
        } catch (e: Exception) {
            Log.e("RadioViewModel", "playStation error", e)
            _uiState.value = _uiState.value.copy(isLoading = false, isPlaying = false)
        }
    }

    fun pausePlayback() {
        try { mediaController?.pause(); _uiState.value = _uiState.value.copy(isPlaying = false) } catch (e: Exception) {}
    }

    fun resumePlayback() {
        try {
            if (mediaController?.currentMediaItem == null && _uiState.value.currentStation != null) {
                playStation(_uiState.value.currentStation!!)
            } else {
                mediaController?.play()
                _uiState.value = _uiState.value.copy(isPlaying = true)
            }
        } catch (e: Exception) {}
    }

    fun playNext() {
        val list = filteredAndSortedStations.value
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == _uiState.value.currentStation?.id }
        playStation(list[if (idx == -1 || idx >= list.size - 1) 0 else idx + 1])
    }

    fun playPrevious() {
        val list = filteredAndSortedStations.value
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == _uiState.value.currentStation?.id }
        playStation(list[if (idx <= 0) list.size - 1 else idx - 1])
    }

    fun toggleMute() {
        val next = !_uiState.value.isMuted
        if (next) { mediaController?.volume = 0f; _uiState.value = _uiState.value.copy(isMuted = true) }
        else { mediaController?.volume = originalVolumeBeforeFade; _uiState.value = _uiState.value.copy(isMuted = false) }
    }
    
    private fun pushEqToService() {
        val s = _uiState.value
        prefs.edit()
            .putString("eq_bands_json", gson.toJson(s.eqBands.toFloatArray()))
            .putFloat("bass_boost", s.bassBoost)
            .putFloat("virtualizer", s.virtualizer)
            .apply()
        try {
            val i = Intent(getApplication(), RadioPlaybackService::class.java).apply {
                action = RadioPlaybackService.ACTION_SET_EQ
                putExtra("eq_bands", s.eqBands.toFloatArray())
                putExtra("bass_boost", s.bassBoost)
                putExtra("virtualizer", s.virtualizer)
            }
            getApplication<Application>().startService(i)
        } catch (_: Exception) { }
    }

    fun setEqPreset(preset: String) {
        val bands = when (preset) {
            "Bass Boost" -> listOf(6f, 4f, 1f, 0f, -1f)
            "Vocal Clarity" -> listOf(-2f, 2f, 5f, 3f, 1f)
            "Club/Dance" -> listOf(5f, 3f, -1f, 3f, 4f)
            "Rock" -> listOf(4f, -1f, -2f, 2f, 5f)
            "Classical" -> listOf(4f, 2f, 0f, 3f, 4f)
            "Jazz" -> listOf(3f, 1f, 3f, 1f, 2f)
            else -> listOf(0f, 0f, 0f, 0f, 0f)
        }
        _uiState.value = _uiState.value.copy(eqPreset = preset, eqBands = bands)
        pushEqToService()
    }

    fun setEqBand(bandIndex: Int, value: Float) {
        if (bandIndex in 0 until 5) {
            val updated = _uiState.value.eqBands.toMutableList()
            updated[bandIndex] = value.coerceIn(-12f, 12f)
            _uiState.value = _uiState.value.copy(eqBands = updated, eqPreset = "Custom")
            pushEqToService()
        }
    }

    fun setBassBoost(level: Float) {
        _uiState.value = _uiState.value.copy(bassBoost = level.coerceIn(0f, 1f))
        pushEqToService()
    }

    fun setVirtualizer(level: Float) {
        _uiState.value = _uiState.value.copy(virtualizer = level.coerceIn(0f, 1f))
        pushEqToService()
    }

    fun toggleFavorite(stationId: String) {
        _stations.value = _stations.value.map { if (it.id == stationId) it.copy(isFavorite = !it.isFavorite) else it }
        saveStationsToPrefs(_stations.value)
        if (_uiState.value.currentStation?.id == stationId) {
            _uiState.value = _uiState.value.copy(currentStation = _uiState.value.currentStation?.copy(isFavorite = !(_uiState.value.currentStation?.isFavorite ?: false)))
        }
    }

    fun toggleArrangeMode() { _uiState.value = _uiState.value.copy(isArrangeMode = !_uiState.value.isArrangeMode) }
    fun openDeck() { _uiState.value = _uiState.value.copy(isDeckOpen = true) }
    fun closeDeck() { _uiState.value = _uiState.value.copy(isDeckOpen = false) }

    fun openSettingsPage(subPage: SettingsSubPage? = null, fromDeck: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            isSettingsPageOpen = true,
            currentSettingsSubPage = subPage,
            isDeckOpen = if (fromDeck) true else false
        )
    }

    fun closeSettingsPage() {
        if (_uiState.value.openedSettingsFromDeck) {
            _uiState.value = _uiState.value.copy(isSettingsPageOpen = false, currentSettingsSubPage = null, openedSettingsFromDeck = false, isDeckOpen = true)
        } else if (_uiState.value.currentSettingsSubPage != null) {
            _uiState.value = _uiState.value.copy(currentSettingsSubPage = null)
        } else {
            _uiState.value = _uiState.value.copy(isSettingsPageOpen = false, isGenreSelectMode = false, isGenreArrangeMode = false, selectedGenresForDeletion = emptySet())
        }
    }

    fun navigateToSubPage(subPage: SettingsSubPage) { _uiState.value = _uiState.value.copy(currentSettingsSubPage = subPage) }

    fun setSpectrumSettings(style: String? = null, height: Int? = null, barHeightScale: Float? = null, sensitivity: Float? = null, colorScheme: String? = null, speed: String? = null) {
        val s = _uiState.value
        val ns = style ?: s.spectrumStyle
        val nh = height ?: s.spectrumHeight
        val nb = barHeightScale ?: s.spectrumBarHeightScale
        val nse = sensitivity ?: s.spectrumSensitivity
        val nc = colorScheme ?: s.spectrumColorScheme
        val nsp = speed ?: s.spectrumSpeed
        _uiState.value = s.copy(spectrumStyle = ns, spectrumHeight = nh, spectrumBarHeightScale = nb, spectrumSensitivity = nse, spectrumColorScheme = nc, spectrumSpeed = nsp)
        prefs.edit().putString("spec_style", ns).putInt("spec_height", nh).putFloat("spec_bar_scale", nb).putFloat("spec_sens", nse).putString("spec_color", nc).putString("spec_speed", nsp).apply()
    }

    fun setThemeColor(hex: String) { _uiState.value = _uiState.value.copy(themeColorHex = hex, activeThemePresetId = null); prefs.edit().putString("theme_color", hex).remove("active_preset_id").apply() }
    fun setBackgroundColor(hex: String) { _uiState.value = _uiState.value.copy(backgroundColorHex = hex, activeThemePresetId = null); prefs.edit().putString("bg_color", hex).remove("active_preset_id").apply() }
    fun setCardColor(hex: String) { _uiState.value = _uiState.value.copy(cardColorHex = hex, activeThemePresetId = null); prefs.edit().putString("card_color", hex).remove("active_preset_id").apply() }
    fun setFontStyle(style: String) { _uiState.value = _uiState.value.copy(themeFontStyle = style, activeThemePresetId = null); prefs.edit().putString("theme_font", style).remove("active_preset_id").apply() }
    fun setFontColor(hex: String) { _uiState.value = _uiState.value.copy(themeFontColorHex = hex, activeThemePresetId = null); prefs.edit().putString("theme_font_color", hex).remove("active_preset_id").apply() }
    
    fun setDeckTheme(accentHex: String? = null, bgHex: String? = null) {
        val s = _uiState.value
        val na = accentHex ?: s.deckAccentHex
        val nb = bgHex ?: s.deckBackgroundHex
        _uiState.value = s.copy(deckAccentHex = na, deckBackgroundHex = nb)
        prefs.edit().putString("deck_accent", na).putString("deck_bg", nb).apply()
    }

    fun setThemeMode(mode: String) { _uiState.value = _uiState.value.copy(themeMode = mode); prefs.edit().putString("theme_mode", mode).apply() }
    fun setMonetEnabled(enabled: Boolean) { _uiState.value = _uiState.value.copy(isMonetEnabled = enabled); prefs.edit().putBoolean("monet_enabled", enabled).apply() }
    fun setSelectedGroup(group: String) { _uiState.value = _uiState.value.copy(selectedGroup = group); prefs.edit().putString("selected_genre_group", group).apply() }
    fun setViewMode(mode: ViewMode) { _uiState.value = _uiState.value.copy(viewMode = mode); prefs.edit().putString("view_mode", mode.name).apply() }
    fun setSortOption(option: SortOption) { _uiState.value = _uiState.value.copy(sortOption = option); prefs.edit().putString("sort_option", option.name).apply() }
    fun setSearchQuery(query: String) { _uiState.value = _uiState.value.copy(searchQuery = query) }

    fun saveCurrentAsPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val s = _uiState.value
        val newPreset = SavedThemePreset(
            id = "preset_${System.currentTimeMillis()}", name = trimmed,
            accentHex = s.themeColorHex, backgroundHex = s.backgroundColorHex,
            cardHex = s.cardColorHex, fontStyle = s.themeFontStyle, fontColorHex = s.themeFontColorHex
        )
        val updated = s.savedThemePresets + newPreset
        _uiState.value = s.copy(savedThemePresets = updated, activeThemePresetId = newPreset.id)
        savePresetsToPrefs(updated)
        prefs.edit().putString("active_preset_id", newPreset.id).apply()
    }

    fun applyPreset(id: String) {
        val preset = _uiState.value.savedThemePresets.firstOrNull { it.id == id } ?: return
        _uiState.value = _uiState.value.copy(
            themeColorHex = preset.accentHex, backgroundColorHex = preset.backgroundHex,
            cardColorHex = preset.cardHex, themeFontStyle = preset.fontStyle,
            themeFontColorHex = preset.fontColorHex, activeThemePresetId = preset.id
        )
        prefs.edit()
            .putString("theme_color", preset.accentHex).putString("bg_color", preset.backgroundHex)
            .putString("card_color", preset.cardHex).putString("theme_font", preset.fontStyle)
            .putString("theme_font_color", preset.fontColorHex).putString("active_preset_id", preset.id)
            .apply()
    }

    fun deletePreset(id: String) {
        val updated = _uiState.value.savedThemePresets.filter { it.id != id }
        _uiState.value = _uiState.value.copy(
            savedThemePresets = updated,
            activeThemePresetId = if (_uiState.value.activeThemePresetId == id) null else _uiState.value.activeThemePresetId
        )
        savePresetsToPrefs(updated)
        if (_uiState.value.activeThemePresetId == null) prefs.edit().remove("active_preset_id").apply()
    }

    private fun loadSavedPresets(): List<SavedThemePreset> {
        val json = prefs.getString("theme_presets_json", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<SavedThemePreset>>() {}.type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }
    private fun savePresetsToPrefs(list: List<SavedThemePreset>) { prefs.edit().putString("theme_presets_json", gson.toJson(list)).apply() }

    fun setBufferSize(size: String) { _uiState.value = _uiState.value.copy(bufferSize = size); prefs.edit().putString("buffer_size", size).apply() }
    fun setStopOnHeadsetDisconnect(enabled: Boolean) { _uiState.value = _uiState.value.copy(stopOnHeadsetDisconnect = enabled); prefs.edit().putBoolean("stop_on_disconnect", enabled).apply() }
    fun setHighQualityAudio(enabled: Boolean) { _uiState.value = _uiState.value.copy(highQualityAudio = enabled); prefs.edit().putBoolean("high_quality", enabled).apply() }
    fun setAutoResumeOnBluetooth(enabled: Boolean) { _uiState.value = _uiState.value.copy(autoResumeOnBluetooth = enabled); prefs.edit().putBoolean("auto_resume_bt", enabled).apply() }
    fun setSyncRoomCode(code: String) { _uiState.value = _uiState.value.copy(syncRoomCode = code); prefs.edit().putString("sync_room", code).apply() }
    fun generateSyncRoomCode() { val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; setSyncRoomCode("RADIO-" + (1..4).map { chars.random() }.joinToString("")) }

    fun reorderStations(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val cur = _stations.value.toMutableList()
        if (fromIndex in cur.indices && toIndex in cur.indices) {
            cur.add(toIndex, cur.removeAt(fromIndex)); _stations.value = cur; saveStationsToPrefs(cur)
        }
    }
    fun reorderGenres(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val cur = _genres.value.toMutableList()
        if (fromIndex in cur.indices && toIndex in cur.indices) {
            cur.add(toIndex, cur.removeAt(fromIndex)); _genres.value = cur; saveGenresToPrefs(cur)
        }
    }

    fun exportStationsJson(): String = gson.toJson(_stations.value)
    fun importStationsJson(jsonStr: String): Boolean = try {
        val imported: List<Station> = gson.fromJson(jsonStr, object : TypeToken<List<Station>>() {}.type)
        if (imported.isNotEmpty()) {
            val merged = (_stations.value + imported).distinctBy { it.id }
            _stations.value = merged; saveStationsToPrefs(merged); true
        } else false
    } catch (_: Exception) { false }

    fun restoreDefaultStations() { _stations.value = DefaultStations.STATIONS; saveStationsToPrefs(DefaultStations.STATIONS) }

    fun testAlarmInSeconds(seconds: Int = 5) {
        val ctx = getApplication<Application>()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val target = _uiState.value.alarmStationId ?: _stations.value.firstOrNull()?.id
        val i = Intent(ctx, AlarmReceiver::class.java).apply { action = "com.aetherwave.radio.ACTION_ALARM_TRIGGER"; putExtra("station_id", target) }
        val pi = PendingIntent.getBroadcast(ctx, 1002, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val time = System.currentTimeMillis() + seconds * 1000L
        try {
            val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, com.aetherwave.radio.MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(time, show), pi)
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi) else am.setExact(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    fun setAlarm(enabled: Boolean, hour: Int, minute: Int, stationId: String?) {
        _uiState.value = _uiState.value.copy(alarmEnabled = enabled, alarmHour = hour, alarmMinute = minute, alarmStationId = stationId)
        prefs.edit().putBoolean("alarm_enabled", enabled).putInt("alarm_hour", hour).putInt("alarm_minute", minute).putString("alarm_station_id", stationId).apply()
        val ctx = getApplication<Application>()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val i = Intent(ctx, AlarmReceiver::class.java).apply { action = "com.aetherwave.radio.ACTION_ALARM_TRIGGER"; putExtra("station_id", stationId) }
        val pi = PendingIntent.getBroadcast(ctx, 1001, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (enabled) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
            }
            try {
                val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, com.aetherwave.radio.MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis, show), pi)
            } catch (_: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) else am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        } else am.cancel(pi)
    }
    fun renameGenre(old: String, new: String) {
        val t = new.trim(); if (t.isBlank() || t == old || _genres.value.contains(t)) return
        _genres.value = _genres.value.map { if (it == old) t else it }; saveGenresToPrefs(_genres.value)
        _stations.value = _stations.value.map { if (it.group == old) it.copy(group = t) else it }; saveStationsToPrefs(_stations.value)
        if (_uiState.value.selectedGroup == old) setSelectedGroup(t)
    }
    fun addGenre(name: String) { val t = name.trim(); if (t.isNotBlank() && !_genres.value.contains(t)) { _genres.value = _genres.value + t; saveGenresToPrefs(_genres.value) } }
    fun deleteGenre(name: String) { _genres.value = _genres.value.filter { it != name }; saveGenresToPrefs(_genres.value); if (_uiState.value.selectedGroup == name) setSelectedGroup("All") }
    fun deleteSelectedGenres() { val del = _uiState.value.selectedGenresForDeletion; _genres.value = _genres.value.filter { !del.contains(it) }; saveGenresToPrefs(_genres.value); if (del.contains(_uiState.value.selectedGroup)) setSelectedGroup("All"); _uiState.value = _uiState.value.copy(selectedGenresForDeletion = emptySet(), isGenreSelectMode = false) }
    fun toggleGenreSelectMode() { _uiState.value = _uiState.value.copy(isGenreSelectMode = !_uiState.value.isGenreSelectMode, selectedGenresForDeletion = emptySet()) }
    fun toggleGenreArrangeMode() { _uiState.value = _uiState.value.copy(isGenreArrangeMode = !_uiState.value.isGenreArrangeMode) }
    fun toggleGenreSelection(genre: String) { val cur = _uiState.value.selectedGenresForDeletion.toMutableSet(); if (cur.contains(genre)) cur.remove(genre) else cur.add(genre); _uiState.value = _uiState.value.copy(selectedGenresForDeletion = cur) }
    fun moveGenre(genre: String, direction: String) {
        val list = _genres.value.toMutableList(); val idx = list.indexOf(genre); if (idx == -1) return
        if (direction == "up" && idx > 0) { list.removeAt(idx); list.add(idx - 1, genre) } else if (direction == "down" && idx < list.size - 1) { list.removeAt(idx); list.add(idx + 1, genre) }
        _genres.value = list; saveGenresToPrefs(list)
    }

    fun saveStation(station: Station) {
        val cur = _stations.value.toMutableList(); val idx = cur.indexOfFirst { it.id == station.id }
        if (idx != -1) cur[idx] = station else cur.add(0, station)
        _stations.value = cur; saveStationsToPrefs(cur)
        if (_uiState.value.currentStation?.id == station.id) _uiState.value = _uiState.value.copy(currentStation = station)
    }
    fun deleteStation(stationId: String) {
        val updated = _stations.value.filter { it.id != stationId }; _stations.value = updated; saveStationsToPrefs(updated)
        if (_uiState.value.currentStation?.id == stationId) { pausePlayback(); _uiState.value = _uiState.value.copy(currentStation = updated.firstOrNull()) }
    }
    fun moveStation(stationId: String, direction: String) {
        val cur = _stations.value.toMutableList(); val idx = cur.indexOfFirst { it.id == stationId }; if (idx == -1) return
        if (direction == "up" && idx > 0) { val it2 = cur.removeAt(idx); cur.add(idx - 1, it2) } else if (direction == "down" && idx < cur.size - 1) { val it2 = cur.removeAt(idx); cur.add(idx + 1, it2) }
        _stations.value = cur; saveStationsToPrefs(cur)
    }

    fun queryRadioBrowserSuggestions(keyword: String) {
        val t = keyword.trim(); if (t.length < 2) { _searchSuggestions.value = emptyList(); return }
        scrapeJob?.cancel(); scrapeJob = viewModelScope.launch(Dispatchers.IO) {
            delay(280); _isScraping.value = true
            try {
                val enc = URLEncoder.encode(t, "UTF-8"); val url = URL("https://de1.api.radio-browser.info/json/stations/byname/$enc?limit=10&order=votes&reverse=true")
                val conn = url.openConnection() as HttpURLConnection; conn.connectTimeout = 5000; conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "AetherWaveRadioApp/1.0")
                if (conn.responseCode == 200) {
                    val txt = conn.inputStream.bufferedReader().use { it.readText() }
                    val raw: List<Map<String, Any>> = gson.fromJson(txt, object : TypeToken<List<Map<String, Any>>>() {}.type)
                    _searchSuggestions.value = raw.mapNotNull { item ->
                        val n = item["name"] as? String ?: return@mapNotNull null
                        val u = (item["url_resolved"] as? String) ?: (item["url"] as? String) ?: return@mapNotNull null
                        ScrapedRadioStation(n.trim(), u.trim(), (item["favicon"] as? String)?.takeIf { it.isNotBlank() }, (item["tags"] as? String)?.takeIf { it.isNotBlank() }, (item["country"] as? String)?.takeIf { it.isNotBlank() }, (item["codec"] as? String)?.takeIf { it.isNotBlank() }, (item["bitrate"] as? Number)?.toInt()?.takeIf { it > 0 })
                    }
                }
            } catch (e: Exception) {} finally { _isScraping.value = false }
        }
    }
    fun clearSuggestions() { _searchSuggestions.value = emptyList() }

    private fun loadSavedStations(): List<Station> {
        val j = prefs.getString("stations_json", null)
        if (!j.isNullOrEmpty()) try { return gson.fromJson(j, object : TypeToken<List<Station>>() {}.type) } catch (_: Exception) {}
        return DefaultStations.STATIONS
    }
    private fun saveStationsToPrefs(list: List<Station>) { prefs.edit().putString("stations_json", gson.toJson(list)).apply() }
    private fun loadSavedGenres(): List<String> {
        val j = prefs.getString("custom_genres_json", null)
        if (!j.isNullOrEmpty()) try { return gson.fromJson(j, object : TypeToken<List<String>>() {}.type) } catch (_: Exception) {}
        return DefaultStations.GROUPS.filter { it != "All" && it != "Favorites" }
    }
    private fun saveGenresToPrefs(list: List<String>) { prefs.edit().putString("custom_genres_json", gson.toJson(list)).apply() }

    override fun onCleared() {
        super.onCleared(); fftJob?.cancel(); controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
