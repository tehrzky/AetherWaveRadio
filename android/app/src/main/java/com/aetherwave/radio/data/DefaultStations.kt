package com.aetherwave.radio.data

object DefaultStations {
    val STATIONS = listOf(
        Station(
            id = "station-lofi-girl",
            name = "Lofi Girl Radio",
            streamUrl = "https://play.streamafrica.net/lofigirl",
            logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=300&auto=format&fit=crop&q=80",
            group = "Lo-Fi & Chill",
            description = "Beats to relax and study to 24/7",
            bitrate = "192 kbps",
            codec = "MP3",
            country = "International",
            isFavorite = true,
            customOrder = 0
        ),
        Station(
            id = "station-soma-groove-salad",
            name = "SomaFM: Groove Salad",
            streamUrl = "https://ice2.somafm.com/groovesalad-128-mp3",
            logoUrl = "https://somafm.com/img3/groovesalad-400.jpg",
            group = "Ambient",
            description = "A nicely chilled plate of ambient/downtempo beats",
            bitrate = "128 kbps",
            codec = "MP3",
            country = "USA",
            isFavorite = true,
            customOrder = 1
        ),
        Station(
            id = "station-swiss-jazz",
            name = "Radio Swiss Jazz",
            streamUrl = "https://stream.srg-ssr.ch/m/rsj/mp3_128",
            logoUrl = "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=300&auto=format&fit=crop&q=80",
            group = "Jazz & Soul",
            description = "Pure uninterrupted Jazz, Blues, and Soul from Switzerland",
            bitrate = "128 kbps",
            codec = "MP3",
            country = "Switzerland",
            isFavorite = true,
            customOrder = 2
        ),
        Station(
            id = "station-soma-defcon",
            name = "SomaFM: DEF CON Radio",
            streamUrl = "https://ice1.somafm.com/defcon-128-mp3",
            logoUrl = "https://somafm.com/img3/defcon-400.jpg",
            group = "Electronic & Synth",
            description = "Music for Hacking. Official DEF CON stream",
            bitrate = "128 kbps",
            codec = "MP3",
            country = "USA",
            isFavorite = false,
            customOrder = 3
        ),
        Station(
            id = "station-nightwave-plaza",
            name = "Nightwave Plaza",
            streamUrl = "https://radio.plaza.one/mp3",
            logoUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=300&auto=format&fit=crop&q=80",
            group = "Electronic & Synth",
            description = "24/7 Nostalgic Vaporwave, Future Funk, and Retro Synth",
            bitrate = "128 kbps",
            codec = "MP3",
            country = "Global",
            isFavorite = true,
            customOrder = 4
        ),
        Station(
            id = "station-bbc-world-service",
            name = "BBC World Service News",
            streamUrl = "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service",
            logoUrl = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=300&auto=format&fit=crop&q=80",
            group = "News & Talk",
            description = "Authoritative international news and reports",
            bitrate = "96 kbps",
            codec = "AAC",
            country = "UK",
            isFavorite = false,
            customOrder = 5
        )
    )

    val GROUPS = listOf(
        "All",
        "Favorites",
        "Lo-Fi & Chill",
        "Electronic & Synth",
        "Jazz & Soul",
        "Classical",
        "Ambient",
        "Rock & Indie",
        "News & Talk"
    )
}
