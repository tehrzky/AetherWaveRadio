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
    val volume: Float = 0.85f,
    val isMuted: Boolean = false,
    val eqPreset: String = "bass",
    val syncRoomCode: String = "RADIO-A89K",
    val isAutoSync: Boolean = false
)

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("aetherwave_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    val bluetoothHelper = BluetoothHelper(application)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _stations = MutableStateFlow<List<Station>>(loadSavedStations())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val _customGroups = MutableStateFlow<List<String>>(loadSavedGroups())
    val customGroups: StateFlow<List<String>> = _customGroups.asStateFlow()

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
            _uiState.value = _uiState.value.copy(volume = target)
        },
        onTimerFinished = {
            pausePlayback()
            mediaController?.volume = originalVolumeBeforeFade
            _uiState.value = _uiState.value.copy(volume = originalVolumeBeforeFade)
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

    val allGroups: StateFlow<List<String>> = combine(
        _stations,
        _customGroups
    ) { stationList, customList ->
        val set = linkedSetOf<String>()
        set.addAll(DefaultStations.GROUPS)
        stationList.forEach { if (it.group.isNotBlank()) set.add(it.group) }
        set.addAll(customList)
        set.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultStations.GROUPS)

    val filteredAndSortedStations: StateFlow<List<Station>> = combine(
        _stations,
        _uiState
    ) { stationsList, state ->
        var list = stationsList

        // Search filter
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
            list = list.filter { it.group == state.selectedGroup }
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

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        mediaController?.volume = clamped
        _uiState.value = _uiState.value.copy(volume = clamped, isMuted = false)
    }

    fun toggleMute() {
        val nextMuted = !_uiState.value.isMuted
        if (nextMuted) {
            originalVolumeBeforeFade = _uiState.value.volume
            mediaController?.volume = 0f
            _uiState.value = _uiState.value.copy(isMuted = true)
        } else {
            mediaController?.volume = originalVolumeBeforeFade
            _uiState.value = _uiState.value.copy(isMuted = false, volume = originalVolumeBeforeFade)
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

    fun setSelectedGroup(group: String) {
        _uiState.value = _uiState.value.copy(selectedGroup = group)
    }

    fun setThemeColor(colorHex: String) {
        _uiState.value = _uiState.value.copy(themeColorHex = colorHex)
        prefs.edit().putString("theme_color", colorHex).apply()
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

    private fun loadSavedGroups(): List<String> {
        val json = prefs.getString("custom_groups", null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                return gson.fromJson(json, type)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
