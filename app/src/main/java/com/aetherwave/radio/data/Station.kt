package com.aetherwave.radio.data

import java.io.Serializable

data class Station(
    val id: String,
    var name: String,
    var streamUrl: String,
    var logoUrl: String? = null,
    var group: String = "Lo-Fi & Chill",
    var description: String? = null,
    var bitrate: String? = "128 kbps",
    var codec: String = "MP3",
    var country: String? = null,
    var isFavorite: Boolean = false,
    var lastPlayedAt: Long? = null,
    var customOrder: Int = 0
) : Serializable

enum class ViewMode {
    GRID,
    LIST
}

enum class SortOption {
    CUSTOM,
    NAME_ASC,
    NAME_DESC,
    RECENTLY_PLAYED,
    FAVORITES_FIRST
}
