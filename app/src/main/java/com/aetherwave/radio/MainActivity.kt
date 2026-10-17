package com.aetherwave.radio

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
                if (uiState.isSettingsPageOpen) {
                    SettingsScreen(
                        viewModel = viewModel,
                        accentColor = parseHexColor(uiState.themeColorHex),
                        onBack = { viewModel.closeSettingsPage() }
                    )
                } else {
                    RadioAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioAppScreen(viewModel: RadioViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val stations by viewModel.filteredAndSortedStations.collectAsState()
    val allFilterGroups by viewModel.allFilterGroups.collectAsState()
    val totalStations by viewModel.stations.collectAsState()

    var showDeckDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var stationBeingEdited by remember { mutableStateOf<Station?>(null) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val accentColor = parseHexColor(uiState.themeColorHex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Radio,
                                contentDescription = null,
                                tint = Color(0xFF090D16),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "AetherWave Radio",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    // Settings Page button
                    IconButton(
                        onClick = { viewModel.openSettingsPage() },
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

                    // Single primary Add Station Button
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
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
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
            // Material 3 True Floating Capsule Pill Mini Player
            uiState.currentStation?.let { current ->
                TrueFloatingMiniPlayerPill(
                    station = current,
                    isPlaying = uiState.isPlaying,
                    isLoading = uiState.isLoading,
                    accentColor = accentColor,
                    onPlayPause = {
                        if (uiState.isPlaying) viewModel.pausePlayback() else viewModel.resumePlayback()
                    },
                    onOpenDeck = { showDeckDialog = true }
                )
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            // Row 1: Compact Search bar + Edit Mode Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = {
                        Text("Search stations, genres, URLs...", color = Color.Gray, fontSize = 12.sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(17.dp))
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0x18FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                // Edit Mode Toggle Switch
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (uiState.isEditMode) accentColor.copy(alpha = 0.18f) else DarkSurface)
                        .border(1.dp, if (uiState.isEditMode) accentColor else Color(0x15FFFFFF), RoundedCornerShape(20.dp))
                        .clickable { viewModel.toggleEditMode() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Edit Mode",
                        tint = if (uiState.isEditMode) accentColor else Color.Gray,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        if (uiState.isEditMode) "Done" else "Edit",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isEditMode) accentColor else Color.LightGray
                    )
                }
            }

            // Row 2: Genre Filter Row (Horizontal Scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                allFilterGroups.forEach { grp ->
                    val isSel = uiState.selectedGroup.equals(grp, ignoreCase = true)
                    val isFav = grp.equals("Favorites", ignoreCase = true)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSel) accentColor else DarkSurface)
                            .border(1.dp, if (isSel) accentColor else Color(0x12FFFFFF), RoundedCornerShape(16.dp))
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
            }

            // Row 3: View Mode (Grid / List) + Sort Dropdown + Live Count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = "Grid",
                            tint = if (uiState.viewMode == ViewMode.GRID) Color(0xFF090D16) else Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (uiState.viewMode == ViewMode.LIST) accentColor else Color.Transparent)
                            .clickable { viewModel.setViewMode(ViewMode.LIST) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = "List",
                            tint = if (uiState.viewMode == ViewMode.LIST) Color(0xFF090D16) else Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Sort Dropdown Button
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurface)
                            .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(16.dp))
                            .clickable { showSortMenu = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            when (uiState.sortOption) {
                                SortOption.CUSTOM -> "Custom Order"
                                SortOption.NAME_ASC -> "Name A→Z"
                                SortOption.NAME_DESC -> "Name Z→A"
                                SortOption.RECENTLY_PLAYED -> "Recent"
                                SortOption.FAVORITES_FIRST -> "Favorites First"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(DarkSurfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Custom Order", fontSize = 12.sp, color = Color.White) },
                            onClick = {
                                viewModel.setSortOption(SortOption.CUSTOM)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Name (A → Z)", fontSize = 12.sp, color = Color.White) },
                            onClick = {
                                viewModel.setSortOption(SortOption.NAME_ASC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Name (Z → A)", fontSize = 12.sp, color = Color.White) },
                            onClick = {
                                viewModel.setSortOption(SortOption.NAME_DESC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Recently Played", fontSize = 12.sp, color = Color.White) },
                            onClick = {
                                viewModel.setSortOption(SortOption.RECENTLY_PLAYED)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Favorites First", fontSize = 12.sp, color = Color.White) },
                            onClick = {
                                viewModel.setSortOption(SortOption.FAVORITES_FIRST)
                                showSortMenu = false
                            }
                        )
                    }
                }

                // Station Counter
                Text(
                    "${stations.size} / ${totalStations.size}",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Stations List or Grid
            if (stations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Radio, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(52.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No radio stations found", color = Color.Gray, fontSize = 14.sp)
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
                        StationGridCardClean(
                            station = station,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && uiState.isPlaying,
                            isEditMode = uiState.isEditMode,
                            accentColor = accentColor,
                            canMoveUp = index > 0,
                            canMoveDown = index < stations.size - 1,
                            onPlay = { viewModel.playStation(station) },
                            onEdit = {
                                stationBeingEdited = station
                                showEditDialog = true
                            },
                            onDelete = { viewModel.deleteStation(station.id) },
                            onMoveUp = { viewModel.moveStation(station.id, "up") },
                            onMoveDown = { viewModel.moveStation(station.id, "down") }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    itemsIndexed(stations, key = { _, s -> s.id }) { index, station ->
                        val isCurrent = uiState.currentStation?.id == station.id
                        StationListCardTwoRow(
                            station = station,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && uiState.isPlaying,
                            isEditMode = uiState.isEditMode,
                            accentColor = accentColor,
                            canMoveUp = index > 0,
                            canMoveDown = index < stations.size - 1,
                            onPlay = { viewModel.playStation(station) },
                            onEdit = {
                                stationBeingEdited = station
                                showEditDialog = true
                            },
                            onDelete = { viewModel.deleteStation(station.id) },
                            onMoveUp = { viewModel.moveStation(station.id, "up") },
                            onMoveDown = { viewModel.moveStation(station.id, "down") }
                        )
                    }
                }
            }
        }
    }

    // Master Studio Deck Dialog
    if (showDeckDialog && uiState.currentStation != null) {
        ExpandedMasterDeckDialog(
            station = uiState.currentStation!!,
            isPlaying = uiState.isPlaying,
            isLoading = uiState.isLoading,
            eqPreset = uiState.eqPreset,
            accentColor = accentColor,
            onClose = { showDeckDialog = false },
            onPlayPause = {
                if (uiState.isPlaying) viewModel.pausePlayback() else viewModel.resumePlayback()
            },
            onNext = { viewModel.playNext() },
            onPrevious = { viewModel.playPrevious() },
            onSetEqPreset = { viewModel.setEqPreset(it) },
            onToggleFavorite = { viewModel.toggleFavorite(uiState.currentStation!!.id) },
            onOpenSleep = { showSleepDialog = true },
            onOpenBluetooth = { viewModel.openSettingsPage(tabIndex = 3) }
        )
    }

    // Add / Edit Station Dialog (Auto-detect codec & local media picker)
    if (showEditDialog) {
        StationAddEditDialog(
            station = stationBeingEdited,
            allGenres = allFilterGroups.filter { it != "All" && it != "Favorites" },
            accentColor = accentColor,
            viewModel = viewModel,
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

    // Sleep Timer Dialog
    if (showSleepDialog) {
        SleepTimerDialog(
            viewModel = viewModel,
            accentColor = accentColor,
            onClose = { showSleepDialog = false }
        )
    }
}

// Clean Grid Card: In normal mode shows ZERO arrows or edit pencil!
@Composable
fun StationGridCardClean(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isEditMode: Boolean,
    accentColor: Color,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
                if (isCurrent) accentColor.copy(alpha = 0.85f) else Color(0x12FFFFFF),
                RoundedCornerShape(20.dp)
            )
            .clickable { onPlay() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Artwork Box with Play Overlay & EQ Wave Bars
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF080C14))
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!station.logoUrl.isNullOrEmpty()) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(station.logoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = station.name,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low,
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
                        .size(44.dp)
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
                            .background(Color.Black.copy(alpha = 0.75f))
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

            // Station Name (Single line, truncated)
            Text(
                text = station.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isCurrent) accentColor else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Genre & Codec Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = station.group,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E283C))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        station.codec,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Edit Phase Controls: Only shown when isEditMode is true!
            if (isEditMode) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141C2D))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = if (canMoveUp) Color.White else Color.DarkGray, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = if (canMoveDown) Color.White else Color.DarkGray, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = accentColor, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// Clean Two-Row List Card (No word-wrapping overflow, compact height!)
@Composable
fun StationListCardTwoRow(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isEditMode: Boolean,
    accentColor: Color,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) DarkSurfaceElevated else DarkSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isCurrent) accentColor.copy(alpha = 0.85f) else Color(0x10FFFFFF),
                RoundedCornerShape(14.dp)
            )
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent && isPlaying) accentColor else Color(0xFF1E283C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isCurrent && isPlaying) Color(0xFF090D16) else Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Thumbnail Squircle
            Box(
                modifier = Modifier
                    .size(40.dp)
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
                    Icon(Icons.Default.Radio, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Two-Row Details: Row 1 = Name, Row 2 = Genre + Codec
            Column(modifier = Modifier.weight(1f)) {
                // Row 1: Station Name alone (No wrapping!)
                Text(
                    text = station.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isCurrent) accentColor else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Row 2: Genre/Group name on its own row below!
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = station.group,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                    Text("•", fontSize = 10.sp, color = Color.DarkGray)
                    Text(
                        text = "${station.codec} ${station.bitrate ?: ""}",
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Edit Mode Action Buttons (only visible when Edit Mode switch is ON)
            if (isEditMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = if (canMoveUp) Color.White else Color.DarkGray, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = if (canMoveDown) Color.White else Color.DarkGray, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = accentColor, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// True Floating Capsule Pill Mini Player
@Composable
fun TrueFloatingMiniPlayerPill(
    station: Station,
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onOpenDeck: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101522)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(32.dp))
                .shadow(20.dp, RoundedCornerShape(32.dp))
                .clickable { onOpenDeck() }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo with animated live equalizer overlay
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF090D16))
                        .border(1.dp, Color(0x18FFFFFF), CircleShape),
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

                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp), verticalAlignment = Alignment.Bottom) {
                                EqualizerBarAnimation(accentColor = accentColor, delayMs = 0)
                                EqualizerBarAnimation(accentColor = accentColor, delayMs = 150)
                                EqualizerBarAnimation(accentColor = accentColor, delayMs = 300)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Station Title & Genre
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        station.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${station.group} • ${station.codec}",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }

                // ONLY ONE BUTTON: Big tactile Play / Pause Circle
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
            }
        }
    }
}

// Master Studio Player Deck replicating the exact web preview
@Composable
fun ExpandedMasterDeckDialog(
    station: Station,
    isPlaying: Boolean,
    isLoading: Boolean,
    eqPreset: String,
    accentColor: Color,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSetEqPreset: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenBluetooth: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF0B101D),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Ambient Radial Gradient Backlight in theme color
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = 20.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Row: Brand label + Bluetooth + Sleep + Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Radio, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("STUDIO MASTER DECK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onOpenBluetooth) {
                                Icon(Icons.Default.Bluetooth, contentDescription = "Bluetooth", tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onOpenSleep) {
                                Icon(Icons.Default.Bedtime, contentDescription = "Sleep Timer", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Hero Artwork Squircle with ON AIR badge
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.Black)
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(28.dp))
                            .shadow(16.dp, RoundedCornerShape(28.dp)),
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Station Group Chip + Favorite Star Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF161F32))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(station.group, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Favorite star button inside the player deck!
                        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Favorite",
                                tint = if (station.isFavorite) Color(0xFFFBBF24) else Color(0xFF4B5563),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Station Title
                    Text(
                        station.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Subtitle / Description
                    if (!station.description.isNullOrEmpty()) {
                        Text(
                            station.description!!,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Tech specs pills: Codec, Bitrate, Country
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(station.codec, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                        Text("•", color = Color.Gray, fontSize = 10.sp)
                        Text(station.bitrate ?: "128 kbps", fontSize = 10.sp, color = Color.LightGray, fontFamily = FontFamily.Monospace)
                        if (!station.country.isNullOrEmpty()) {
                            Text("•", color = Color.Gray, fontSize = 10.sp)
                            Text(station.country!!, fontSize = 10.sp, color = Color.LightGray)
                        }
                    }

                    // Stream link row
                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1524))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            station.streamUrl,
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Audio Spectrum Card Layer with title and 64-band subtext!
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF070B14)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Live Audio Spectrum", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Text("64-BAND FFT ANALYSER", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Organic reactive FFT Canvas
                            Box(modifier = Modifier.fillMaxWidth().height(62.dp)) {
                                WebGradeFFTAudioSpectrumCanvas(isPlaying = isPlaying, accentColor = accentColor)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Audio Tone Presets
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

                    // Tactile Playback Controls (Prev, Big 76.dp Play FAB, Next) - NO volume slider!
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                                .shadow(18.dp, CircleShape)
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
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

// Organic 48-bar FFT Audio Spectrum with vertical gradient & soft glow
@Composable
fun WebGradeFFTAudioSpectrumCanvas(isPlaying: Boolean, accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "fft")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing)
        ),
        label = "fftTime"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val barCount = 48
        val spacing = 2.5f
        val barWidth = (size.width - (barCount - 1) * spacing) / barCount

        val gradient = Brush.verticalGradient(
            colors = listOf(
                accentColor,
                accentColor.copy(alpha = 0.5f),
                Color(0xFF090D16)
            )
        )

        for (i in 0 until barCount) {
            val h = if (isPlaying) {
                val t = time * 0.8f
                val n1 = sin(t * 1.5 + i * 0.28) * 0.35 + 0.35
                val n2 = cos(t * 2.2 + i * 0.45) * 0.25 + 0.25
                val n3 = sin(t * 0.7 + i * 0.12) * 0.2 + 0.2
                // Bass curve weight on left
                val bassWeight = (1.0f - (i.toFloat() / barCount) * 0.4f)
                val raw = (n1 + n2 + n3).toFloat() * bassWeight
                (raw * (size.height - 4f)).coerceIn(4f, size.height)
            } else {
                3f
            }

            val x = i * (barWidth + spacing)
            val y = size.height - h

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(2.5f, 2.5f)
            )
        }
    }
}

// Station Add / Edit Dialog with auto-detect codec & local media picker
@Composable
fun StationAddEditDialog(
    station: Station?,
    allGenres: List<String>,
    accentColor: Color,
    viewModel: RadioViewModel,
    onClose: () -> Unit,
    onSave: (Station) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf(station?.name ?: "") }
    var streamUrl by remember { mutableStateOf(station?.streamUrl ?: "") }
    var logoUrl by remember { mutableStateOf(station?.logoUrl ?: "") }
    var selectedGenre by remember { mutableStateOf(station?.group ?: (allGenres.firstOrNull() ?: "Lo-Fi & Chill")) }
    var newGenreInput by remember { mutableStateOf("") }
    var isEnteringNewGenre by remember { mutableStateOf(false) }

    // Android Local Media Picker for station logo
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            logoUrl = uri.toString()
        }
    }

    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111728)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
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
                    label = { Text("Stream URL (MP3 / AAC / HLS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Logo Input: URL + "Pick from Device" button
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = logoUrl,
                        onValueChange = { logoUrl = it },
                        label = { Text("Logo Image URL or Local") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { launcher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2438)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Logo from Device Gallery", fontSize = 12.sp, color = Color.White)
                    }
                }

                // Genre selection: list of existing genres + Add new
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Genre Group", fontSize = 12.sp, color = Color.Gray)

                    if (!isEnteringNewGenre) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allGenres.forEach { g ->
                                val isSel = selectedGenre.equals(g, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSel) accentColor else Color(0xFF182238))
                                        .clickable { selectedGenre = g }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        g,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color(0xFF090D16) else Color.White
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1B263E))
                                    .clickable { isEnteringNewGenre = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("+ New Genre", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = newGenreInput,
                                onValueChange = { newGenreInput = it },
                                placeholder = { Text("Enter new genre name", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (newGenreInput.isNotBlank()) {
                                        selectedGenre = newGenreInput.trim()
                                        isEnteringNewGenre = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF090D16))
                            ) {
                                Text("Add")
                            }
                        }
                    }
                }

                // Auto-detected Codec & Bitrate summary info
                val autoCodec = viewModel.autoDetectCodec(streamUrl)
                val autoBitrate = viewModel.autoDetectBitrate(streamUrl)
                Text(
                    "Auto-detected Stream Format: $autoCodec • $autoBitrate",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace
                )

                // Actions
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
                                        group = selectedGenre.trim().ifEmpty { "General" },
                                        codec = autoCodec,
                                        bitrate = autoBitrate,
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

// Settings Dedicated Page (Full Screen with Genre Manager, Themes, Cloud Sync, and Bluetooth)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RadioViewModel,
    accentColor: Color,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val genres by viewModel.genres.collectAsState()
    var newGenreText by remember { mutableStateOf("") }

    val tabs = listOf("Genres", "Themes", "Cloud Sync", "Bluetooth")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Studio Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Settings Navigation Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabs.forEachIndexed { idx, label ->
                    val isSel = uiState.activeSettingsTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSel) accentColor else Color(0xFF141C2D))
                            .clickable { viewModel.setSettingsTab(idx) }
                            .padding(vertical = 8.dp),
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

            Spacer(modifier = Modifier.height(10.dp))

            when (uiState.activeSettingsTab) {
                0 -> { // GENRE MANAGER
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Manage Genres & Categories", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Add, reorder, or delete station groups. Used everywhere in the app.", fontSize = 11.sp, color = Color.Gray)

                        // Add Genre Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newGenreText,
                                onValueChange = { newGenreText = it },
                                placeholder = { Text("New genre name...", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (newGenreText.isNotBlank()) {
                                        viewModel.addGenre(newGenreText.trim())
                                        newGenreText = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF090D16))
                            ) {
                                Text("+ Add")
                            }
                        }

                        // Genres List
                        genres.forEachIndexed { index, g ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(g, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.moveGenre(g, "up") }, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = if (index > 0) Color.White else Color.DarkGray, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { viewModel.moveGenre(g, "down") }, enabled = index < genres.size - 1, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = if (index < genres.size - 1) Color.White else Color.DarkGray, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { viewModel.deleteGenre(g) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> { // THEMES & COLOR PICKER
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("10 Curated M3 Theme Presets", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Changes the glowing studio accents and controls dynamically.", fontSize = 11.sp, color = Color.Gray)

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(280.dp)
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
                                        .padding(10.dp),
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
                }
                2 -> { // CLOUD SYNC & BACKUP
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Cloud Presets & Backup", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Sync Room Key", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(uiState.syncRoomCode, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = accentColor, fontFamily = FontFamily.Monospace)
                                Text("Enter this room code on another phone or computer to sync all stations and custom genres.", fontSize = 11.sp, color = Color.Gray)
                            }
                        }

                        Button(
                            onClick = { /* Export */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E283C)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Stations to JSON", color = Color.White)
                        }

                        Button(
                            onClick = { /* Export */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E283C)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export M3U Playlist", color = Color.White)
                        }
                    }
                }
                3 -> { // BLUETOOTH
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Bluetooth & Audio Outputs", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        val devices = viewModel.bluetoothHelper.getConnectedAudioDevices()
                        if (devices.isEmpty()) {
                            Text("No external Bluetooth headphones or speakers currently connected. Audio is playing via phone speaker.", fontSize = 12.sp, color = Color.Gray)
                        } else {
                            devices.forEach { dev ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Headphones, contentDescription = null, tint = accentColor)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(dev, color = Color.White, fontSize = 13.sp)
                                    }
                                }
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
                    Text("Audio will stop automatically with smooth fade out", color = Color.Gray, fontSize = 12.sp)

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

@Composable
fun EqualizerBarAnimation(accentColor: Color, delayMs: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "eqBar")
    val heightFraction by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350 + delayMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqFraction"
    )

    Box(
        modifier = Modifier
            .width(2.5.dp)
            .height((13 * heightFraction).dp)
            .clip(RoundedCornerShape(1.dp))
            .background(accentColor)
    )
}

