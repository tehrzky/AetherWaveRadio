package com.aetherwave.radio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aetherwave.radio.data.DefaultStations
import com.aetherwave.radio.data.SortOption
import com.aetherwave.radio.data.Station
import com.aetherwave.radio.data.ViewMode
import com.aetherwave.radio.util.BluetoothHelper
import com.aetherwave.radio.util.SleepTimerManager
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
    val themeColorHex: String = "#F59E0B"
)

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothHelper = BluetoothHelper(application)

    private val _stations = MutableStateFlow<List<Station>>(DefaultStations.STATIONS)
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val _uiState = MutableStateFlow(RadioUiState())
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    val sleepTimerManager = SleepTimerManager(
        onTickVolumeFade = { /* Fade volume via player controller */ },
        onTimerFinished = { pausePlayback() }
    )

    val filteredAndSortedStations: StateFlow<List<Station>> = combine(
        _stations,
        _uiState
    ) { stationsList, state ->
        var list = stationsList

        // Filter by search
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                it.group.lowercase().contains(q) ||
                it.streamUrl.lowercase().contains(q)
            }
        }

        // Filter by group
        if (state.selectedGroup == "Favorites") {
            list = list.filter { it.isFavorite }
        } else if (state.selectedGroup != "All") {
            list = list.filter { it.group == state.selectedGroup }
        }

        // Sort
        when (state.sortOption) {
            SortOption.NAME_ASC -> list.sortedBy { it.name }
            SortOption.NAME_DESC -> list.sortedByDescending { it.name }
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
        _uiState.value = _uiState.value.copy(
            currentStation = station,
            isPlaying = true,
            isLoading = false
        )
    }

    fun pausePlayback() {
        _uiState.value = _uiState.value.copy(isPlaying = false)
    }

    fun resumePlayback() {
        _uiState.value = _uiState.value.copy(isPlaying = true)
    }

    fun toggleFavorite(stationId: String) {
        _stations.value = _stations.value.map {
            if (it.id == stationId) it.copy(isFavorite = !it.isFavorite) else it
        }
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }

    fun setSortOption(option: SortOption) {
        _uiState.value = _uiState.value.copy(sortOption = option)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSelectedGroup(group: String) {
        _uiState.value = _uiState.value.copy(selectedGroup = group)
    }

    fun saveStation(updated: Station) {
        val existing = _stations.value.any { it.id == updated.id }
        if (existing) {
            _stations.value = _stations.value.map { if (it.id == updated.id) updated else it }
        } else {
            _stations.value = listOf(updated) + _stations.value
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
        _stations.value = _stations.value.filter { it.id != stationId }
    }
}
