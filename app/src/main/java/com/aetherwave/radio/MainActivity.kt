package com.aetherwave.radio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.aetherwave.radio.data.DefaultStations
import com.aetherwave.radio.data.SortOption
import com.aetherwave.radio.data.Station
import com.aetherwave.radio.data.ViewMode
import com.aetherwave.radio.ui.RadioViewModel
import com.aetherwave.radio.ui.theme.AetherWaveRadioTheme
import com.aetherwave.radio.ui.theme.DarkBackground
import com.aetherwave.radio.ui.theme.DarkSurface
import com.aetherwave.radio.ui.theme.DarkSurfaceElevated
import com.aetherwave.radio.ui.theme.parseHexColor
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val viewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            AetherWaveRadioTheme(accentColorHex = uiState.themeColorHex) {
                RadioAppScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioAppScreen(viewModel: RadioViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val stations by viewModel.filteredAndSortedStations.collectAsState()
    val allGroups by viewModel.allGroups.collectAsState()
    val totalStations by viewModel.stations.collectAsState()

    var showDeckDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var stationBeingEdited by remember { mutableStateOf<Station?>(null) }
    var showSleepDialog by remember { mutableStateOf(false) }

    val accentColor = parseHexColor(uiState.themeColorHex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentColor)
                                .shadow(8.dp, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Radio,
                                contentDescription = null,
                                tint = Color(0xFF090D16),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "AetherWave Radio",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.3).sp
                        )
                    }
                },
                actions = {
                    // Settings Button (Gear)
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF161F32))
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Studio Settings",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Single Primary Add Station Button
                    Button(
                        onClick = {
                            stationBeingEdited = null
                            showEditDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color(0xFF090D16)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Station", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground.copy(alpha = 0.95f)
                )
            )
        },
        bottomBar = {
            // Floating Docked Mini Player Bar
            uiState.currentStation?.let { current ->
                FloatingMiniPlayer(
                    station = current,
                    isPlaying = uiState.isPlaying,
                    isLoading = uiState.isLoading,
                    accentColor = accentColor,
                    onPlayPause = {
                        if (uiState.isPlaying) viewModel.pausePlayback() else viewModel.resumePlayback()
                    },
                    onNext = { viewModel.playNext() },
                    onPrevious = { viewModel.playPrevious() },
                    onFavorite = { viewModel.toggleFavorite(current.id) },
                    onOpenDeck = { showDeckDialog = true },
                    onOpenSleep = { showSleepDialog = true }
                )
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
        ) {
            // Quick Navigation Filter Tabs (All, Favorites, Recently Played)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isAll = uiState.selectedGroup == "All" && uiState.sortOption != SortOption.RECENTLY_PLAYED
                val isFav = uiState.selectedGroup == "Favorites"
                val isRecent = uiState.sortOption == SortOption.RECENTLY_PLAYED

                FilterTabButton(
                    text = "All Stations",
                    isSelected = isAll,
                    accentColor = accentColor,
                    onClick = {
                        viewModel.setSelectedGroup("All")
                        viewModel.setSortOption(SortOption.CUSTOM)
                    }
                )
                FilterTabButton(
                    text = "Favorites",
                    isSelected = isFav,
                    accentColor = accentColor,
                    icon = Icons.Default.Star,
                    onClick = { viewModel.setSelectedGroup("Favorites") }
                )
                FilterTabButton(
                    text = "Recently Played",
                    isSelected = isRecent,
                    accentColor = accentColor,
                    icon = Icons.Default.History,
                    onClick = {
                        viewModel.setSelectedGroup("All")
                        viewModel.setSortOption(SortOption.RECENTLY_PLAYED)
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Toolbar Row: Search Bar + View Toggle + Sort
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Input with Clear 'X'
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = {
                        Text("Search stations, genres, URLs...", color = Color.Gray, fontSize = 12.sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0x22FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                // Grid / List Segmented Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkSurface)
                        .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(20.dp))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (uiState.viewMode == ViewMode.GRID) accentColor else Color.Transparent)
                            .clickable { viewModel.setViewMode(ViewMode.GRID) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = "Grid",
                            tint = if (uiState.viewMode == ViewMode.GRID) Color(0xFF090D16) else Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (uiState.viewMode == ViewMode.LIST) accentColor else Color.Transparent)
                            .clickable { viewModel.setViewMode(ViewMode.LIST) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = "List",
                            tint = if (uiState.viewMode == ViewMode.LIST) Color(0xFF090D16) else Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Genre / Group Filter Chips (Horizontal Scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                allGroups.forEach { grp ->
                    val isSel = uiState.selectedGroup == grp
                    val isFav = grp == "Favorites"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSel) accentColor else DarkSurface.copy(alpha = 0.8f))
                            .border(1.dp, if (isSel) accentColor else Color(0x15FFFFFF), RoundedCornerShape(18.dp))
                            .clickable { viewModel.setSelectedGroup(grp) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isFav) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (isSel) Color(0xFF090D16) else Color(0xFFFBBF24),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                grp,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color(0xFF090D16) else Color.LightGray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${stations.size} of ${totalStations.size} stations",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Content Grid or List
            if (stations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Radio, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No stations found in this category", color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                stationBeingEdited = null
                                showEditDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF090D16)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("+ Add First Station")
                        }
                    }
                }
            } else if (uiState.viewMode == ViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    itemsIndexed(stations, key = { _, s -> s.id }) { index, station ->
                        val isCurrent = uiState.currentStation?.id == station.id
                        StationGridCard(
                            station = station,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && uiState.isPlaying,
                            accentColor = accentColor,
                            canMoveUp = index > 0 && uiState.sortOption == SortOption.CUSTOM,
                            canMoveDown = index < stations.size - 1 && uiState.sortOption == SortOption.CUSTOM,
                            onPlay = { viewModel.playStation(station) },
                            onToggleFavorite = { viewModel.toggleFavorite(station.id) },
                            onEdit = {
                                stationBeingEdited = station
                                showEditDialog = true
                            },
                            onMoveUp = { viewModel.moveStation(station.id, "up") },
                            onMoveDown = { viewModel.moveStation(station.id, "down") }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    itemsIndexed(stations, key = { _, s -> s.id }) { index, station ->
                        val isCurrent = uiState.currentStation?.id == station.id
                        StationListCard(
                            station = station,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && uiState.isPlaying,
                            accentColor = accentColor,
                            onPlay = { viewModel.playStation(station) },
                            onToggleFavorite = { viewModel.toggleFavorite(station.id) },
                            onEdit = {
                                stationBeingEdited = station
                                showEditDialog = true
                            },
                            onDelete = { viewModel.deleteStation(station.id) }
                        )
                    }
                }
            }
        }
    }

    // Expanded Master Studio Deck Modal
    if (showDeckDialog && uiState.currentStation != null) {
        ExpandedMasterDeckDialog(
            station = uiState.currentStation!!,
            isPlaying = uiState.isPlaying,
            isLoading = uiState.isLoading,
            volume = uiState.volume,
            isMuted = uiState.isMuted,
            eqPreset = uiState.eqPreset,
            accentColor = accentColor,
            onClose = { showDeckDialog = false },
            onPlayPause = {
                if (uiState.isPlaying) viewModel.pausePlayback() else viewModel.resumePlayback()
            },
            onNext = { viewModel.playNext() },
            onPrevious = { viewModel.playPrevious() },
            onVolumeChange = { viewModel.setVolume(it) },
            onToggleMute = { viewModel.toggleMute() },
            onSetEqPreset = { viewModel.setEqPreset(it) },
            onToggleFavorite = { viewModel.toggleFavorite(uiState.currentStation!!.id) },
            onOpenSleep = { showSleepDialog = true }
        )
    }

    // Add / Edit Station Dialog
    if (showEditDialog) {
        StationEditDialog(
            station = stationBeingEdited,
            allGroups = allGroups,
            accentColor = accentColor,
            onClose = { showEditDialog = false },
            onSave = { updated ->
                viewModel.saveStation(updated)
                showEditDialog = false
            },
            onDelete = { id ->
                viewModel.deleteStation(id)
                showEditDialog = false
            }
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            accentColor = accentColor,
            onClose = { showSettingsDialog = false }
        )
    }

    // Sleep Timer Dialog
    if (showSleepDialog) {
        SleepTimerDialog(
            viewModel = viewModel,
            accentColor = accentColor,
            onClose = { showSleepDialog = false }
        )
    }
}

@Composable
fun FilterTabButton(
    text: String,
    isSelected: Boolean,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) accentColor else Color(0xFF141C2D))
            .border(1.dp, if (isSelected) accentColor else Color(0x12FFFFFF), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF090D16) else Color.LightGray,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color(0xFF090D16) else Color.LightGray
            )
        }
    }
}

@Composable
fun StationGridCard(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) DarkSurfaceElevated else DarkSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isCurrent) accentColor.copy(alpha = 0.8f) else Color(0x12FFFFFF),
                RoundedCornerShape(20.dp)
            )
            .clickable { onPlay() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Top action bar on card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = station.group,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canMoveUp || canMoveDown) {
                        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }

                    IconButton(onClick = onEdit, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }

                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = if (station.isFavorite) Color(0xFFFBBF24) else Color(0xFF4B5563),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Artwork Squircle with Play Overlay & Live Equalizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF080C14))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!station.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = station.logoUrl,
                        contentDescription = station.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Radio, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(36.dp))
                        Text(
                            station.name.take(3).uppercase(),
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                // Play / Pause Circle Overlay
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent && isPlaying) accentColor else Color.Black.copy(alpha = 0.5f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isCurrent && isPlaying) Color(0xFF090D16) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Live Equalizer wave bars on bottom-left
                if (isCurrent && isPlaying) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        EqualizerBarAnimation(accentColor = accentColor, delayMs = 0)
                        EqualizerBarAnimation(accentColor = accentColor, delayMs = 150)
                        EqualizerBarAnimation(accentColor = accentColor, delayMs = 300)
                        EqualizerBarAnimation(accentColor = accentColor, delayMs = 100)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Station Name
            Text(
                text = station.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isCurrent) accentColor else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Codec & Bitrate row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E283C))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        station.codec,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    station.bitrate ?: "128 kbps",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun StationListCard(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) DarkSurfaceElevated else DarkSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isCurrent) accentColor.copy(alpha = 0.8f) else Color(0x10FFFFFF),
                RoundedCornerShape(16.dp)
            )
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play / Pause Circle
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent && isPlaying) accentColor else Color(0xFF1E283C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isCurrent && isPlaying) Color(0xFF090D16) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Thumbnail Squircle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF080C14)),
                contentAlignment = Alignment.Center
            ) {
                if (!station.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = station.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Radio, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = station.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isCurrent) accentColor else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = station.group,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                // Stream URL + Codec specs
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = station.streamUrl,
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "• ${station.codec} ${station.bitrate ?: ""}",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Quick actions
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = if (station.isFavorite) Color(0xFFFBBF24) else Color(0xFF4B5563),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun EqualizerBarAnimation(accentColor: Color, delayMs: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val heightFraction by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400 + delayMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar"
    )

    Box(
        modifier = Modifier
            .width(2.5.dp)
            .height((12 * heightFraction).dp)
            .clip(RoundedCornerShape(1.dp))
            .background(accentColor)
    )
}

@Composable
fun FloatingMiniPlayer(
    station: Station,
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onFavorite: () -> Unit,
    onOpenDeck: () -> Unit,
    onOpenSleep: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101522)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(26.dp))
            .shadow(16.dp, RoundedCornerShape(26.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Artwork + Title (Clickable to open Master Deck)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenDeck() }
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF090D16))
                        .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!station.logoUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = station.logoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Radio, contentDescription = null, tint = Color.Gray)
                    }

                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                                EqualizerBarAnimation(accentColor = accentColor, delayMs = 0)
                                EqualizerBarAnimation(accentColor = accentColor, delayMs = 150)
                                EqualizerBarAnimation(accentColor = accentColor, delayMs = 300)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            station.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onFavorite, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = if (station.isFavorite) Color(0xFFFBBF24) else Color(0xFF4B5563),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        "${station.group} • ${station.codec}",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            // Center: Playback Controls (Prev, Big Play FAB, Next)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.LightGray)
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color(0xFF090D16), strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF090D16),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.LightGray)
                }

                // Expand Deck button
                IconButton(onClick = onOpenDeck, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.OpenInFull, contentDescription = "Expand Deck", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun ExpandedMasterDeckDialog(
    station: Station,
    isPlaying: Boolean,
    isLoading: Boolean,
    volume: Float,
    isMuted: Boolean,
    eqPreset: String,
    accentColor: Color,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onSetEqPreset: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenSleep: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF0B101D),
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Dynamic ambient backlight
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = 40.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Radio, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("STUDIO MASTER DECK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onOpenSleep) {
                                Icon(Icons.Default.Bedtime, contentDescription = "Sleep Timer", tint = Color.LightGray)
                            }
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Hero Artwork Squircle
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.Black)
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!station.logoUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = station.logoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Radio, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        }

                        // Status pill "ON AIR" / "PAUSED"
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isPlaying) Color(0xFF10B981) else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    if (isPlaying) "ON AIR" else "PAUSED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Station Name & Details
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            station.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = if (station.isFavorite) Color(0xFFFBBF24) else Color(0xFF4B5563)
                            )
                        }
                    }

                    Text(
                        "${station.group} • ${station.codec} • ${station.bitrate ?: "128 kbps"}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Audio Visualizer Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF070B14))
                            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(16.dp))
                            .padding(8.dp)
                    ) {
                        NativeAudioVisualizerCanvas(isPlaying = isPlaying, accentColor = accentColor)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Tone Presets (Bass Boost, Vocal, Club, Treble, Balanced)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("bass" to "Bass", "vocal" to "Vocal", "club" to "Club", "treble" to "Treble", "flat" to "Flat").forEach { (key, label) ->
                            val isSel = eqPreset == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) accentColor else Color(0xFF131A2A))
                                    .clickable { onSetEqPreset(key) }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF090D16) else Color.LightGray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Playback Controls (Prev, Big 72.dp Play FAB, Next)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                                .shadow(14.dp, CircleShape)
                                .clickable { onPlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color(0xFF090D16), strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                            } else {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFF090D16),
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Volume Slider Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF111728))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onToggleMute) {
                            Icon(
                                if (isMuted || volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Mute",
                                tint = if (isMuted) Color(0xFFEF4444) else Color.Gray
                            )
                        }

                        Slider(
                            value = if (isMuted) 0f else volume,
                            onValueChange = onVolumeChange,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = Color(0xFF1E283C)
                            )
                        )

                        Text(
                            "${(if (isMuted) 0 else (volume * 100).toInt())}%",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NativeAudioVisualizerCanvas(isPlaying: Boolean, accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "viz")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing)
        ),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val barCount = 32
        val barWidth = (size.width / barCount) - 3f

        for (i in 0 until barCount) {
            val height = if (isPlaying) {
                val wave1 = (sin(phase + i * 0.35) * 0.5 + 0.5).toFloat()
                val wave2 = (cos(phase * 1.5 + i * 0.2) * 0.5 + 0.5).toFloat()
                val combined = (wave1 * 0.6f + wave2 * 0.4f)
                (combined * (size.height - 6f)).coerceAtLeast(4f)
            } else {
                3f
            }

            val x = i * (barWidth + 3f)
            val y = size.height - height

            drawRoundRect(
                color = accentColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }
}

@Composable
fun StationEditDialog(
    station: Station?,
    allGroups: List<String>,
    accentColor: Color,
    onClose: () -> Unit,
    onSave: (Station) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf(station?.name ?: "") }
    var streamUrl by remember { mutableStateOf(station?.streamUrl ?: "") }
    var logoUrl by remember { mutableStateOf(station?.logoUrl ?: "") }
    var group by remember { mutableStateOf(station?.group ?: "Lo-Fi & Chill") }
    var codec by remember { mutableStateOf(station?.codec ?: "MP3") }
    var bitrate by remember { mutableStateOf(station?.bitrate ?: "128 kbps") }

    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121929)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (station != null) "Edit Station" else "Add New Station",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Station Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("Audio Stream URL (MP3 / AAC / HLS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = logoUrl,
                    onValueChange = { logoUrl = it },
                    label = { Text("Logo Image URL (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text("Genre / Group") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = codec,
                        onValueChange = { codec = it },
                        label = { Text("Codec") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bitrate,
                        onValueChange = { bitrate = it },
                        label = { Text("Bitrate") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (station != null) {
                        TextButton(
                            onClick = { onDelete(station.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Text("Delete")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onClose) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                if (name.isNotBlank() && streamUrl.isNotBlank()) {
                                    val updated = Station(
                                        id = station?.id ?: "station-${System.currentTimeMillis()}",
                                        name = name.trim(),
                                        streamUrl = streamUrl.trim(),
                                        logoUrl = logoUrl.trim().ifEmpty { null },
                                        group = group.trim().ifEmpty { "General" },
                                        codec = codec.trim().ifEmpty { "MP3" },
                                        bitrate = bitrate.trim().ifEmpty { "128 kbps" },
                                        isFavorite = station?.isFavorite ?: false,
                                        lastPlayedAt = station?.lastPlayedAt,
                                        customOrder = station?.customOrder ?: 0
                                    )
                                    onSave(updated)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF090D16))
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    viewModel: RadioViewModel,
    accentColor: Color,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Themes", "Bluetooth", "Playlists")

    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111728)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Studio Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabs.forEachIndexed { idx, label ->
                        val isSel = selectedTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) accentColor else Color(0xFF182238))
                                .clickable { selectedTab = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color(0xFF090D16) else Color.LightGray
                            )
                        }
                    }
                }

                when (selectedTab) {
                    0 -> { // Themes
                        Text("10 Curated M3 Theme Presets", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(240.dp)
                        ) {
                            itemsIndexed(DefaultStations.THEMES) { _, theme ->
                                val color = parseHexColor(theme.colorHex)
                                val isCurrent = uiState.themeColorHex.equals(theme.colorHex, ignoreCase = true)
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isCurrent) Color(0xFF1E283C) else Color(0xFF0A0F1A))
                                        .border(1.dp, if (isCurrent) color else Color(0x10FFFFFF), RoundedCornerShape(12.dp))
                                        .clickable { viewModel.setThemeColor(theme.colorHex) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(theme.name, fontSize = 11.sp, color = Color.White, maxLines = 1)
                                }
                            }
                        }
                    }
                    1 -> { // Bluetooth
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Bluetooth Audio Outputs", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            val devices = viewModel.bluetoothHelper.getConnectedAudioDevices()
                            if (devices.isEmpty()) {
                                Text("No external Bluetooth audio devices detected. System default speaker is active.", fontSize = 12.sp, color = Color.Gray)
                            } else {
                                devices.forEach { dev ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF090D16))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Headphones, contentDescription = null, tint = accentColor)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(dev, color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    2 -> { // Playlists
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Export / Backup Stations", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Button(
                                onClick = { /* Export */ },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E283C)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Export Stations to JSON", color = Color.White)
                            }
                            Button(
                                onClick = { /* Export */ },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E283C)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Export M3U Playlist", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SleepTimerDialog(
    viewModel: RadioViewModel,
    accentColor: Color,
    onClose: () -> Unit
) {
    val timerState by viewModel.sleepTimerManager.timerState.collectAsState()
    var selectedMins by remember { mutableStateOf(30) }

    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111728)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Sleep Timer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)

                if (timerState.isActive) {
                    val m = timerState.remainingSeconds / 60
                    val s = timerState.remainingSeconds % 60
                    Text(
                        String.format("%02d:%02d", m, s),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("Audio is playing and will stop automatically", color = Color.Gray, fontSize = 12.sp)

                    Button(
                        onClick = {
                            viewModel.sleepTimerManager.cancelTimer()
                            onClose()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel Sleep Timer", color = Color.White)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(15, 30, 45, 60, 90).forEach { mins ->
                            val isSel = selectedMins == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) accentColor else Color(0xFF182238))
                                    .clickable { selectedMins = mins }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${mins}m",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF090D16) else Color.White
                                )
                            }
                        }
                    }

                    Text("Smooth volume fade during the last 60 seconds", color = Color.Gray, fontSize = 11.sp)

                    Button(
                        onClick = {
                            viewModel.sleepTimerManager.startTimer(selectedMins, fadeOut = true)
                            onClose()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF090D16)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Timer ($selectedMins min)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
