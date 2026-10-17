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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RadioUiState(
    val currentStation: Station? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOption: SortOption = SortOption.CUSTOM,
    val searchQuery: String = "",
    val selectedGroup: String = "All",
    val themeColorHex: String = "#F59E0B",
    val isMuted: Boolean = false,
    val eqPreset: String = "bass",
    val syncRoomCode: String = "RADIO-A89K",
    val isAutoSync: Boolean = false,
    val isEditMode: Boolean = false,
    val isSettingsPageOpen: Boolean = false,
    val activeSettingsTab: Int = 0
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

    private val _uiState = MutableStateFlow(
        RadioUiState(
            themeColorHex = prefs.getString("theme_color", "#F59E0B") ?: "#F59E0B",
            viewMode = ViewMode.valueOf(prefs.getString("view_mode", ViewMode.GRID.name) ?: ViewMode.GRID.name),
            sortOption = SortOption.valueOf(prefs.getString("sort_option", SortOption.CUSTOM.name) ?: SortOption.CUSTOM.name),
            syncRoomCode = prefs.getString("sync_room", "RADIO-A89K") ?: "RADIO-A89K"
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
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
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
        _uiState.value = _uiState.value.copy(eqPreset = preset)
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

    fun toggleEditMode() {
        _uiState.value = _uiState.value.copy(isEditMode = !_uiState.value.isEditMode)
    }

    fun openSettingsPage(tabIndex: Int = 0) {
        _uiState.value = _uiState.value.copy(isSettingsPageOpen = true, activeSettingsTab = tabIndex)
    }

    fun closeSettingsPage() {
        _uiState.value = _uiState.value.copy(isSettingsPageOpen = false)
    }

    fun setSettingsTab(index: Int) {
        _uiState.value = _uiState.value.copy(activeSettingsTab = index)
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

    fun reorderStation(fromIndex: Int, toIndex: Int) {
        val list = _stations.value.sortedBy { it.customOrder }.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            list.forEachIndexed { i, s -> s.customOrder = i }
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

    fun setSelectedGroup(group: String) {
        _uiState.value = _uiState.value.copy(selectedGroup = group)
    }

    fun setThemeColor(colorHex: String) {
        _uiState.value = _uiState.value.copy(themeColorHex = colorHex)
        prefs.edit().putString("theme_color", colorHex).apply()
    }

    fun addGenre(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank() && !_genres.value.any { it.equals(trimmed, ignoreCase = true) }) {
            val updated = _genres.value + trimmed
            _genres.value = updated
            saveGenresToPrefs(updated)
        }
    }

    fun deleteGenre(name: String) {
        val updated = _genres.value.filter { !it.equals(name, ignoreCase = true) }
        _genres.value = updated
        saveGenresToPrefs(updated)
        if (_uiState.value.selectedGroup.equals(name, ignoreCase = true)) {
            _uiState.value = _uiState.value.copy(selectedGroup = "All")
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

        // If newly used genre is not yet saved, add it
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

        // Also merge any new genres
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
                val parsed: List<String> = gson.fromJson(json, type)
                if (parsed.isNotEmpty()) return parsed
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
