package com.aetherwave.radio.data

data class ThemePreset(
    val id: String,
    val name: String,
    val colorHex: String
)

object DefaultStations {
    val STATIONS = listOf(
        Station(
            id = "station-lofi-girl",
            name = "Lofi Girl Radio",
            streamUrl = "https://play.streamafrica.net/lofigirl",
            logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400&auto=format&fit=crop&q=80",
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
            description = "A nicely chilled plate of ambient/downtempo beats and grooves",
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
            logoUrl = "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=400&auto=format&fit=crop&q=80",
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
            description = "Music for Hacking. The official DEF CON hacker conference stream",
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
            logoUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=400&auto=format&fit=crop&q=80",
            group = "Electronic & Synth",
            description = "24/7 Nostalgic Vaporwave, Future Funk, and Retro Synth",
            bitrate = "128 kbps",
            codec = "MP3",
            country = "Global",
            isFavorite = true,
            customOrder = 4
        ),
        Station(
            id = "station-soma-secret-agent",
            name = "SomaFM: Secret Agent",
            streamUrl = "https://ice2.somafm.com/secretagent-128-mp3",
            logoUrl = "https://somafm.com/img3/secretagent-400.jpg",
            group = "Jazz & Soul",
            description = "The soundtrack for your stylish, mysterious 1960s espionage lifestyle",
            bitrate = "128 kbps",
            codec = "MP3",
            country = "USA",
            isFavorite = false,
            customOrder = 5
        ),
        Station(
            id = "station-swiss-classic",
            name = "Radio Swiss Classic",
            streamUrl = "https://stream.srg-ssr.ch/m/rsc_de/mp3_128",
            logoUrl = "https://images.unsplash.com/photo-1520523839898-50712825e617?w=400&auto=format&fit=crop&q=80",
            group = "Classical",
            description = "Finest classical music without commercials or chatter",
            bitrate = "128 kbps",
            codec = "MP3",
            country = "Switzerland",
            isFavorite = false,
            customOrder = 6
        ),
        Station(
            id = "station-bbc-world-service",
            name = "BBC World Service News",
            streamUrl = "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service",
            logoUrl = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=400&auto=format&fit=crop&q=80",
            group = "News & Talk",
            description = "Authoritative international news, current affairs, and reports",
            bitrate = "96 kbps",
            codec = "AAC",
            country = "UK",
            isFavorite = false,
            customOrder = 7
        ),
        Station(
            id = "station-ibiza-global",
            name = "Ibiza Global Radio",
            streamUrl = "https://listens.ibizaglobalradio.com:8024/stream",
            logoUrl = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=400&auto=format&fit=crop&q=80",
            group = "Electronic & Synth",
            description = "Deep house, minimal, and electronic live from Ibiza",
            bitrate = "128 kbps",
            codec = "MP3",
            country = "Spain",
            isFavorite = false,
            customOrder = 8
        ),
        Station(
            id = "station-ambient-sleeping-pill",
            name = "Ambient Sleeping Pill",
            streamUrl = "https://radio.stereoscenic.com/asp-s",
            logoUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=400&auto=format&fit=crop&q=80",
            group = "Ambient",
            description = "Gentle, floating atmospheric music for sleep and focus",
            bitrate = "128 kbps",
            codec = "MP3",
            country = "USA",
            isFavorite = false,
            customOrder = 9
        ),
        Station(
            id = "station-kexp-indie",
            name = "KEXP 90.3 FM Seattle",
            streamUrl = "https://kexp.streamguys1.com/kexp128.mp3",
            logoUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=400&auto=format&fit=crop&q=80",
            group = "Rock & Indie",
            description = "Independent rock, alternative, and post-punk curation",
            bitrate = "128 kbps",
            codec = "MP3",
            country = "USA",
            isFavorite = false,
            customOrder = 10
        ),
        Station(
            id = "station-drone-zone",
            name = "SomaFM: Drone Zone",
            streamUrl = "https://ice4.somafm.com/dronezone-128-mp3",
            logoUrl = "https://somafm.com/img3/dronezone-400.jpg",
            group = "Ambient",
            description = "Atmospheric textures, ambient drones, and minimal soundscapes",
            bitrate = "128 kbps",
            codec = "MP3",
            country = "USA",
            isFavorite = false,
            customOrder = 11
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

    val THEMES = listOf(
        ThemePreset("studio-amber", "Studio Amber", "#F59E0B"),
        ThemePreset("cyberpunk-cyan", "Cyber Cyan", "#06B6D4"),
        ThemePreset("emerald-deck", "Emerald Phosphor", "#10B981"),
        ThemePreset("vaporwave-magenta", "Vapor Magenta", "#EC4899"),
        ThemePreset("electric-violet", "Electric Violet", "#8B5CF6"),
        ThemePreset("solar-orange", "Solar Orange", "#F97316"),
        ThemePreset("crimson-pulse", "Crimson Broadcast", "#EF4444"),
        ThemePreset("matrix-lime", "Matrix Lime", "#84CC16"),
        ThemePreset("arctic-blue", "Arctic Blue", "#38BDF8"),
        ThemePreset("pure-platinum", "Pure Platinum", "#E2E8F0")
    )
}
