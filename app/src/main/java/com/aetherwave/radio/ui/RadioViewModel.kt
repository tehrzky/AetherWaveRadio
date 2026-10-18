package com.aetherwave.radio.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aetherwave.radio.data.DefaultStations
import com.aetherwave.radio.data.SortOption
import com.aetherwave.radio.data.Station
import com.aetherwave.radio.data.ViewMode
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
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

enum class SettingsSubPage {
    PLAYER,
    APPEARANCE,
    EQUALIZER,
    GENRE_MANAGER,
    CLOUD_BACKUP,
    ALARM_CLOCK,
    BLUETOOTH,
    ABOUT
}

data class ScrapedRadioStation(
    val name: String,
    val url: String,
    val favicon: String?,
    val tags: String?,
    val country: String?,
    val codec: String?,
    val bitrate: Int?
)

data class RadioUiState(
    val currentStation: Station? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOption: SortOption = SortOption.CUSTOM,
    val searchQuery: String = "",
    val selectedGroup: String = "All",
    val themeColorHex: String = "#F59E0B",
    val themeMode: String = "dark", // "dark", "light", "system"
    val isMuted: Boolean = false,
    val eqPreset: String = "Flat",
    val eqBands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val bassBoost: Float = 0.5f,
    val virtualizer: Float = 0.3f,
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
    val highQualityAudio: Boolean = true
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

    // Scraped Radio Browser autocomplete suggestions
    private val _searchSuggestions = MutableStateFlow<List<ScrapedRadioStation>>(emptyList())
    val searchSuggestions: StateFlow<List<ScrapedRadioStation>> = _searchSuggestions.asStateFlow()

    private val _isScraping = MutableStateFlow(false)
    val isScraping: StateFlow<Boolean> = _isScraping.asStateFlow()

    private var scrapeJob: Job? = null

    private val _uiState = MutableStateFlow(
        RadioUiState(
            currentStation = initialStation,
            themeColorHex = prefs.getString("theme_color", "#F59E0B") ?: "#F59E0B",
            themeMode = prefs.getString("theme_mode", "dark") ?: "dark",
            selectedGroup = prefs.getString("selected_genre_group", "All") ?: "All",
            viewMode = ViewMode.valueOf(prefs.getString("view_mode", ViewMode.GRID.name) ?: ViewMode.GRID.name),
            sortOption = SortOption.valueOf(prefs.getString("sort_option", SortOption.CUSTOM.name) ?: SortOption.CUSTOM.name),
            syncRoomCode = prefs.getString("sync_room", "RADIO-A89K") ?: "RADIO-A89K",
            alarmEnabled = prefs.getBoolean("alarm_enabled", false),
            alarmHour = prefs.getInt("alarm_hour", 7),
            alarmMinute = prefs.getInt("alarm_minute", 30),
            alarmStationId = prefs.getString("alarm_station_id", null),
            bufferSize = prefs.getString("buffer_size", "Medium") ?: "Medium",
            stopOnHeadsetDisconnect = prefs.getBoolean("stop_on_disconnect", true),
            autoResumeOnBluetooth = prefs.getBoolean("auto_resume_bt", false),
            highQualityAudio = prefs.getBoolean("high_quality", true)
        )
    )
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    private var originalVolumeBeforeFade = 0.85f

    val sleepTimerManager = SleepTimerManager(
        onTickVolumeFade = { ratio ->
            val target = originalVolumeBeforeFade * ratio
            mediaController?.volume = target
        },
        onTimerFinished = {
            pausePlayback()
            mediaController?.volume = originalVolumeBeforeFade
        }
    )

    init {
        initMediaController(application)
        startAlarmMonitor()
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
                    currentStation = matched,
                    isPlaying = isPlaying,
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
                        _uiState.value = _uiState.value.copy(
                            isPlaying = isPlaying,
                            isLoading = false
                        )
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = playbackState == Player.STATE_BUFFERING
                        )
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    // Alarm Clock Monitor (checks every 20 seconds)
    private fun startAlarmMonitor() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(20000)
                val state = _uiState.value
                if (state.alarmEnabled && !state.isPlaying) {
                    val cal = Calendar.getInstance()
                    val h = cal.get(Calendar.HOUR_OF_DAY)
                    val m = cal.get(Calendar.MINUTE)
                    if (h == state.alarmHour && m == state.alarmMinute) {
                        val st = _stations.value.firstOrNull { it.id == state.alarmStationId }
                            ?: _stations.value.firstOrNull()
                        if (st != null) {
                            withContext(Dispatchers.Main) {
                                playStation(st)
                            }
                        }
                    }
                }
            }
        }
    }

    // Dynamic genre categories combining defaults and user custom genres
    val allFilterGroups: StateFlow<List<String>> = combine(
        _stations,
        _genres
    ) { stationList, genreList ->
        val result = linkedSetOf<String>()
        result.add("All")
        result.add("Favorites")
        result.addAll(genreList)
        stationList.forEach { s ->
            if (s.group.isNotBlank() && s.group != "All" && s.group != "Favorites") {
                result.add(s.group)
            }
        }
        result.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All", "Favorites") + DefaultStations.GROUPS.filter { it != "All" && it != "Favorites" })

    val filteredAndSortedStations: StateFlow<List<Station>> = combine(
        _stations,
        _uiState
    ) { stationsList, state ->
        var list = stationsList

        // Search query filter
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                it.group.lowercase().contains(q) ||
                it.streamUrl.lowercase().contains(q) ||
                (it.country != null && it.country!!.lowercase().contains(q))
            }
        }

        // Group filter
        if (state.selectedGroup == "Favorites") {
            list = list.filter { it.isFavorite }
        } else if (state.selectedGroup != "All") {
            list = list.filter { it.group.equals(state.selectedGroup, ignoreCase = true) }
        }

        // Sorting
        when (state.sortOption) {
            SortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            SortOption.RECENTLY_PLAYED -> list.sortedByDescending { it.lastPlayedAt ?: 0L }
            SortOption.FAVORITES_FIRST -> list.sortedWith(
                compareByDescending<Station> { it.isFavorite }.thenBy { it.customOrder }
            )
            SortOption.CUSTOM -> list.sortedBy { it.customOrder }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultStations.STATIONS)

    fun playStation(station: Station) {
        if (_uiState.value.currentStation?.id == station.id) {
            if (_uiState.value.isPlaying) {
                pausePlayback()
            } else {
                resumePlayback()
            }
            return
        }

        station.lastPlayedAt = System.currentTimeMillis()
        saveStationsToPrefs(_stations.value)
        prefs.edit().putString("last_played_station_id", station.id).apply()

        _uiState.value = _uiState.value.copy(
            currentStation = station,
            isPlaying = true,
            isLoading = true
        )

        mediaController?.let { player ->
            val mediaItem = RadioPlaybackService.buildMediaItem(station)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }

    fun pausePlayback() {
        mediaController?.pause()
        _uiState.value = _uiState.value.copy(isPlaying = false)
    }

    fun resumePlayback() {
        if (mediaController?.currentMediaItem == null && _uiState.value.currentStation != null) {
            playStation(_uiState.value.currentStation!!)
        } else {
            mediaController?.play()
            _uiState.value = _uiState.value.copy(isPlaying = true)
        }
    }

    fun playNext() {
        val currentList = filteredAndSortedStations.value
        if (currentList.isEmpty()) return
        val index = currentList.indexOfFirst { it.id == _uiState.value.currentStation?.id }
        val nextIndex = if (index == -1 || index >= currentList.size - 1) 0 else index + 1
        playStation(currentList[nextIndex])
    }

    fun playPrevious() {
        val currentList = filteredAndSortedStations.value
        if (currentList.isEmpty()) return
        val index = currentList.indexOfFirst { it.id == _uiState.value.currentStation?.id }
        val prevIndex = if (index <= 0) currentList.size - 1 else index - 1
        playStation(currentList[prevIndex])
    }

    fun toggleMute() {
        val nextMuted = !_uiState.value.isMuted
        if (nextMuted) {
            mediaController?.volume = 0f
            _uiState.value = _uiState.value.copy(isMuted = true)
        } else {
            mediaController?.volume = originalVolumeBeforeFade
            _uiState.value = _uiState.value.copy(isMuted = false)
        }
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
    }

    fun setEqBand(bandIndex: Int, value: Float) {
        if (bandIndex in 0 until 5) {
            val updated = _uiState.value.eqBands.toMutableList()
            updated[bandIndex] = value.coerceIn(-12f, 12f)
            _uiState.value = _uiState.value.copy(eqBands = updated, eqPreset = "Custom")
        }
    }

    fun setBassBoost(level: Float) {
        _uiState.value = _uiState.value.copy(bassBoost = level.coerceIn(0f, 1f))
    }

    fun setVirtualizer(level: Float) {
        _uiState.value = _uiState.value.copy(virtualizer = level.coerceIn(0f, 1f))
    }

    fun toggleFavorite(stationId: String) {
        _stations.value = _stations.value.map {
            if (it.id == stationId) it.copy(isFavorite = !it.isFavorite) else it
        }
        saveStationsToPrefs(_stations.value)
        if (_uiState.value.currentStation?.id == stationId) {
            _uiState.value = _uiState.value.copy(
                currentStation = _uiState.value.currentStation?.copy(
                    isFavorite = !(_uiState.value.currentStation?.isFavorite ?: false)
                )
            )
        }
    }

    fun toggleArrangeMode() {
        _uiState.value = _uiState.value.copy(isArrangeMode = !_uiState.value.isArrangeMode)
    }

    fun openSettingsPage(subPage: SettingsSubPage? = null) {
        _uiState.value = _uiState.value.copy(
            isSettingsPageOpen = true,
            currentSettingsSubPage = subPage
        )
    }

    fun closeSettingsPage() {
        if (_uiState.value.currentSettingsSubPage != null) {
            _uiState.value = _uiState.value.copy(currentSettingsSubPage = null)
        } else {
            _uiState.value = _uiState.value.copy(
                isSettingsPageOpen = false,
                isGenreSelectMode = false,
                isGenreArrangeMode = false,
                selectedGenresForDeletion = emptySet()
            )
        }
    }

    fun navigateToSubPage(subPage: SettingsSubPage) {
        _uiState.value = _uiState.value.copy(currentSettingsSubPage = subPage)
    }

    fun toggleGenreSelectMode() {
        val next = !_uiState.value.isGenreSelectMode
        _uiState.value = _uiState.value.copy(
            isGenreSelectMode = next,
            isGenreArrangeMode = false,
            selectedGenresForDeletion = emptySet()
        )
    }

    fun toggleGenreArrangeMode() {
        val next = !_uiState.value.isGenreArrangeMode
        _uiState.value = _uiState.value.copy(
            isGenreArrangeMode = next,
            isGenreSelectMode = false,
            selectedGenresForDeletion = emptySet()
        )
    }

    fun toggleGenreSelection(genre: String) {
        val current = _uiState.value.selectedGenresForDeletion.toMutableSet()
        if (current.contains(genre)) {
            current.remove(genre)
        } else {
            current.add(genre)
        }
        _uiState.value = _uiState.value.copy(selectedGenresForDeletion = current)
    }

    fun deleteSelectedGenres() {
        val toDelete = _uiState.value.selectedGenresForDeletion
        if (toDelete.isNotEmpty()) {
            val updated = _genres.value.filter { !toDelete.contains(it) }
            _genres.value = updated
            saveGenresToPrefs(updated)
            if (toDelete.contains(_uiState.value.selectedGroup)) {
                setSelectedGroup("All")
            }
            _uiState.value = _uiState.value.copy(
                selectedGenresForDeletion = emptySet(),
                isGenreSelectMode = false
            )
        }
    }

    fun moveStation(stationId: String, direction: String) {
        val list = _stations.value.sortedBy { it.customOrder }.toMutableList()
        val index = list.indexOfFirst { it.id == stationId }
        if (index == -1) return
        val targetIndex = if (direction == "up") index - 1 else index + 1
        if (targetIndex in 0 until list.size) {
            val temp = list[index].customOrder
            list[index].customOrder = list[targetIndex].customOrder
            list[targetIndex].customOrder = temp
            _stations.value = list
            saveStationsToPrefs(list)
        }
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
        prefs.edit().putString("view_mode", mode.name).apply()
    }

    fun setSortOption(option: SortOption) {
        _uiState.value = _uiState.value.copy(sortOption = option)
        prefs.edit().putString("sort_option", option.name).apply()
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    // Remembers the default opened genre/group!
    fun setSelectedGroup(group: String) {
        _uiState.value = _uiState.value.copy(selectedGroup = group)
        prefs.edit().putString("selected_genre_group", group).apply()
    }

    fun setThemeColor(colorHex: String) {
        _uiState.value = _uiState.value.copy(themeColorHex = colorHex)
        prefs.edit().putString("theme_color", colorHex).apply()
    }

    fun setThemeMode(mode: String) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun setAlarm(enabled: Boolean, hour: Int, minute: Int, stationId: String?) {
        _uiState.value = _uiState.value.copy(
            alarmEnabled = enabled,
            alarmHour = hour,
            alarmMinute = minute,
            alarmStationId = stationId
        )
        prefs.edit()
            .putBoolean("alarm_enabled", enabled)
            .putInt("alarm_hour", hour)
            .putInt("alarm_minute", minute)
            .putString("alarm_station_id", stationId)
            .apply()
    }

    fun setPlayerSetting(key: String, value: Any) {
        when (key) {
            "buffer_size" -> {
                val v = value as String
                _uiState.value = _uiState.value.copy(bufferSize = v)
                prefs.edit().putString("buffer_size", v).apply()
            }
            "stop_on_disconnect" -> {
                val v = value as Boolean
                _uiState.value = _uiState.value.copy(stopOnHeadsetDisconnect = v)
                prefs.edit().putBoolean("stop_on_disconnect", v).apply()
            }
            "auto_resume_bt" -> {
                val v = value as Boolean
                _uiState.value = _uiState.value.copy(autoResumeOnBluetooth = v)
                prefs.edit().putBoolean("auto_resume_bt", v).apply()
            }
            "high_quality" -> {
                val v = value as Boolean
                _uiState.value = _uiState.value.copy(highQualityAudio = v)
                prefs.edit().putBoolean("high_quality", v).apply()
            }
        }
    }

    fun addGenre(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank() && !_genres.value.any { it.equals(trimmed, ignoreCase = true) }) {
            val updated = _genres.value + trimmed
            _genres.value = updated
            saveGenresToPrefs(updated)
        }
    }

    fun editGenreName(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotBlank() && !trimmed.equals(oldName, ignoreCase = true)) {
            val updated = _genres.value.map { if (it.equals(oldName, ignoreCase = true)) trimmed else it }
            _genres.value = updated
            saveGenresToPrefs(updated)

            // Update any stations with this genre
            val updatedStations = _stations.value.map {
                if (it.group.equals(oldName, ignoreCase = true)) it.copy(group = trimmed) else it
            }
            _stations.value = updatedStations
            saveStationsToPrefs(updatedStations)

            if (_uiState.value.selectedGroup.equals(oldName, ignoreCase = true)) {
                setSelectedGroup(trimmed)
            }
        }
    }

    fun deleteGenre(name: String) {
        val updated = _genres.value.filter { !it.equals(name, ignoreCase = true) }
        _genres.value = updated
        saveGenresToPrefs(updated)
        if (_uiState.value.selectedGroup.equals(name, ignoreCase = true)) {
            setSelectedGroup("All")
        }
    }

    fun moveGenre(name: String, direction: String) {
        val list = _genres.value.toMutableList()
        val index = list.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (index == -1) return
        val target = if (direction == "up") index - 1 else index + 1
        if (target in list.indices) {
            val temp = list[index]
            list[index] = list[target]
            list[target] = temp
            _genres.value = list
            saveGenresToPrefs(list)
        }
    }

    // Radio Browser API scraper for stream URL auto-suggest
    fun queryRadioStations(query: String) {
        scrapeJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _searchSuggestions.value = emptyList()
            _isScraping.value = false
            return
        }

        scrapeJob = viewModelScope.launch(Dispatchers.IO) {
            delay(280) // debounce
            _isScraping.value = true
            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val url = URL("https://de1.api.radio-browser.info/json/stations/byname/$encoded?limit=12")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.setRequestProperty("User-Agent", "AetherWaveRadioApp/1.0")

                if (conn.responseCode == 200) {
                    val stream = conn.inputStream.bufferedReader().use { it.readText() }
                    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                    val list: List<Map<String, Any>> = gson.fromJson(stream, type)

                    val results = list.mapNotNull { map ->
                        val name = map["name"] as? String
                        val streamUrl = (map["url_resolved"] as? String) ?: (map["url"] as? String)
                        if (!name.isNullOrBlank() && !streamUrl.isNullOrBlank()) {
                            ScrapedRadioStation(
                                name = name.trim(),
                                url = streamUrl.trim(),
                                favicon = (map["favicon"] as? String)?.takeIf { it.isNotBlank() },
                                tags = (map["tags"] as? String)?.takeIf { it.isNotBlank() },
                                country = (map["country"] as? String)?.takeIf { it.isNotBlank() },
                                codec = (map["codec"] as? String)?.takeIf { it.isNotBlank() },
                                bitrate = (map["bitrate"] as? Number)?.toInt()
                            )
                        } else null
                    }
                    _searchSuggestions.value = results
                }
            } catch (e: Exception) {
                // Silently fallback if offline
            } finally {
                _isScraping.value = false
            }
        }
    }

    fun clearSuggestions() {
        _searchSuggestions.value = emptyList()
    }

    fun autoDetectCodec(url: String): String {
        val lower = url.lowercase().trim()
        return when {
            lower.contains(".m3u8") || lower.contains("/hls") || lower.contains("format=hls") -> "HLS"
            lower.contains(".aac") || lower.contains("format=aac") -> "AAC"
            lower.contains(".ogg") -> "OGG"
            else -> "MP3"
        }
    }

    fun autoDetectBitrate(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("320") -> "320 kbps"
            lower.contains("256") -> "256 kbps"
            lower.contains("192") -> "192 kbps"
            lower.contains("96") -> "96 kbps"
            lower.contains("64") -> "64 kbps"
            else -> "128 kbps"
        }
    }

    fun saveStation(updated: Station) {
        val existingIndex = _stations.value.indexOfFirst { it.id == updated.id }
        val updatedList = _stations.value.toMutableList()
        if (existingIndex != -1) {
            updatedList[existingIndex] = updated
        } else {
            updatedList.add(0, updated)
        }
        _stations.value = updatedList
        saveStationsToPrefs(updatedList)

        if (updated.group.isNotBlank() && updated.group != "All" && updated.group != "Favorites") {
            addGenre(updated.group)
        }

        if (_uiState.value.currentStation?.id == updated.id) {
            _uiState.value = _uiState.value.copy(currentStation = updated)
        }
    }

    fun deleteStation(stationId: String) {
        if (_uiState.value.currentStation?.id == stationId) {
            pausePlayback()
            _uiState.value = _uiState.value.copy(currentStation = null)
        }
        val updatedList = _stations.value.filter { it.id != stationId }
        _stations.value = updatedList
        saveStationsToPrefs(updatedList)
    }

    fun setSyncRoomCode(code: String) {
        val clean = code.trim().uppercase()
        _uiState.value = _uiState.value.copy(syncRoomCode = clean)
        prefs.edit().putString("sync_room", clean).apply()
    }

    fun setAutoSync(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isAutoSync = enabled)
    }

    fun exportToJson(): String {
        return gson.toJson(_stations.value)
    }

    fun exportToM3u(): String {
        val sb = StringBuilder("#EXTM3U\n")
        _stations.value.forEach { s ->
            sb.append("#EXTINF:-1 tvg-logo=\"${s.logoUrl ?: ""}\" group-title=\"${s.group}\",${s.name}\n")
            sb.append("${s.streamUrl}\n")
        }
        return sb.toString()
    }

    fun importStations(imported: List<Station>, mode: String) {
        val newList = if (mode == "replace") {
            imported
        } else {
            val existingIds = _stations.value.map { it.id }.toSet()
            _stations.value + imported.filter { !existingIds.contains(it.id) }
        }
        _stations.value = newList
        saveStationsToPrefs(newList)

        imported.forEach { s ->
            if (s.group.isNotBlank() && s.group != "All" && s.group != "Favorites") {
                addGenre(s.group)
            }
        }
    }

    private fun loadSavedStations(): List<Station> {
        val json = prefs.getString("stations_json", null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<Station>>() {}.type
                val parsed: List<Station> = gson.fromJson(json, type)
                if (parsed.isNotEmpty()) return parsed
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return DefaultStations.STATIONS
    }

    private fun saveStationsToPrefs(list: List<Station>) {
        val json = gson.toJson(list)
        prefs.edit().putString("stations_json", json).apply()
    }

    private fun loadSavedGenres(): List<String> {
        val json = prefs.getString("genres_json", null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                return gson.fromJson(json, type)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return DefaultStations.GROUPS.filter { it != "All" && it != "Favorites" }
    }

    private fun saveGenresToPrefs(list: List<String>) {
        val json = gson.toJson(list)
        prefs.edit().putString("genres_json", json).apply()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
