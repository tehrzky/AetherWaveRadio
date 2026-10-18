package com.aetherwave.radio

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
                // System gesture back handler: safely closes settings or arrange mode
                BackHandler(enabled = uiState.isSettingsPageOpen) {
                    viewModel.closeSettingsPage()
                }
                BackHandler(enabled = uiState.isArrangeMode && !uiState.isSettingsPageOpen) {
                    viewModel.toggleArrangeMode()
                }

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

    override fun onResume() {
        super.onResume()
        // Re-sync playback state whenever app resumes so mini player is never lost
        viewModel.syncPlayerState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioAppScreen(viewModel: RadioViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val stations by viewModel.filteredAndSortedStations.collectAsState()
    val allFilterGroups by viewModel.allFilterGroups.collectAsState()

    var showDeckDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var stationBeingEdited by remember { mutableStateOf<Station?>(null) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val accentColor = parseHexColor(uiState.themeColorHex)

    Scaffold(
        topBar = {
            // Minimalist Single-Row Top App Bar with uniform circular icon buttons
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Radio,
                                contentDescription = null,
                                tint = Color(0xFF090D16),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "AetherWave Radio",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    // Uniform Circular Add Station Button
                    IconButton(
                        onClick = {
                            stationBeingEdited = null
                            showEditDialog = true
                        },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141C2D))
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Station",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Uniform Circular Settings Button
                    IconButton(
                        onClick = { viewModel.openSettingsPage() },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141C2D))
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Studio Settings",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        bottomBar = {
            // True Detached Floating Capsule Pill (zero full-width container background)
            uiState.currentStation?.let { current ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
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
            // Row 1: Search Bar (Y-Centered, 44dp height) + Arrange Mode Pill (44dp height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sleek Y-Centered Search Pill (44dp exact height)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(DarkSurface)
                        .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(28.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (uiState.searchQuery.isEmpty()) {
                                Text(
                                    "Search stations, genres...",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(accentColor),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.setSearchQuery("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }

                // Arrange Mode Pill (44dp exact height)
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (uiState.isArrangeMode) accentColor.copy(alpha = 0.2f) else DarkSurface)
                        .border(1.dp, if (uiState.isArrangeMode) accentColor else Color(0x18FFFFFF), RoundedCornerShape(28.dp))
                        .clickable { viewModel.toggleArrangeMode() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (uiState.isArrangeMode) Icons.Default.Check else Icons.Default.SwapVert,
                            contentDescription = "Arrange Mode",
                            tint = if (uiState.isArrangeMode) accentColor else Color.LightGray,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (uiState.isArrangeMode) "Done" else "Arrange",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isArrangeMode) accentColor else Color.LightGray
                        )
                    }
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

            // Row 3: Grid / List Segmented Toggle + Sort Dropdown
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
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ARRANGE MODE: Centered Reorder Frame
            if (uiState.isArrangeMode) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 96.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Arrange Stations: ${uiState.selectedGroup}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Tap arrows to reorder",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(stations, key = { _, s -> s.id }) { idx, st ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141C2D)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Station Logo
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!st.logoUrl.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = st.logoUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(Icons.Default.Radio, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(st.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White, maxLines = 1)
                                            Text(st.group, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                                        }

                                        // Drag / Reorder handle on right side
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { viewModel.moveStation(st.id, "up") },
                                                enabled = idx > 0,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = if (idx > 0) Color.White else Color.DarkGray, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = { viewModel.moveStation(st.id, "down") },
                                                enabled = idx < stations.size - 1,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = if (idx < stations.size - 1) Color.White else Color.DarkGray, modifier = Modifier.size(16.dp))
                                            }
                                            Icon(
                                                Icons.Default.DragHandle,
                                                contentDescription = "Reorder",
                                                tint = accentColor,
                                                modifier = Modifier
                                                    .padding(start = 4.dp)
                                                    .size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // NORMAL VIEW: Grid or List
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
                        itemsIndexed(stations, key = { _, s -> s.id }) { _, station ->
                            val isCurrent = uiState.currentStation?.id == station.id
                            StationGridCardClean(
                                station = station,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && uiState.isPlaying,
                                accentColor = accentColor,
                                onPlay = { viewModel.playStation(station) },
                                onHoldEdit = {
                                    stationBeingEdited = station
                                    showEditDialog = true
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        itemsIndexed(stations, key = { _, s -> s.id }) { _, station ->
                            val isCurrent = uiState.currentStation?.id == station.id
                            StationListCardRightPlay(
                                station = station,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && uiState.isPlaying,
                                accentColor = accentColor,
                                onPlay = { viewModel.playStation(station) },
                                onHoldEdit = {
                                    stationBeingEdited = station
                                    showEditDialog = true
                                }
                            )
                        }
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

// Clean Grid Card: Hold to Edit, no ugly buttons
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationGridCardClean(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    onPlay: () -> Unit,
    onHoldEdit: () -> Unit
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
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onHoldEdit
            )
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
        }
    }
}

// List Card: Logo on left, Name/Genre in center, PLAY BUTTON ON THE RIGHT! Hold to edit.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationListCardRightPlay(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    onPlay: () -> Unit,
    onHoldEdit: () -> Unit
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
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onHoldEdit
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo first on the left
            Box(
                modifier = Modifier
                    .size(42.dp)
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
                Text(
                    text = station.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isCurrent) accentColor else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

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

            Spacer(modifier = Modifier.width(8.dp))

            // Play Button on the MOST RIGHT SIDE!
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent && isPlaying) accentColor else Color(0xFF1E283C))
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isCurrent && isPlaying) Color(0xFF090D16) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// True Detached Floating Capsule Pill Mini Player
@Composable
fun TrueFloatingMiniPlayerPill(
    station: Station,
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onOpenDeck: () -> Unit
) {
    Card(
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101522)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x28FFFFFF), CircleShape)
            .shadow(20.dp, CircleShape)
            .clickable { onOpenDeck() }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Logo with live equalizer overlay
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
                    station.group,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            }

            // ONLY ONE BUTTON: Big tactile Play / Pause Circle on right
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
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                            onClick = { showDeleteConfirm = true },
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

    // Delete Confirmation Safeguard Dialog
    if (showDeleteConfirm && station != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Station?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Are you sure you want to delete \"${station.name}\"? This action cannot be undone.", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(station.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF141C2D)
        )
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
    val allStations by viewModel.stations.collectAsState()
    var newGenreText by remember { mutableStateOf("") }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

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
        Box(modifier = Modifier.fillMaxSize()) {
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
                    0 -> { // GENRE MANAGER: Arrange & Select modes
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Manage Genres", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Add, reorder, or batch delete categories", fontSize = 11.sp, color = Color.Gray)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Arrange Button
                                    Button(
                                        onClick = { viewModel.toggleGenreArrangeMode() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (uiState.isGenreArrangeMode) accentColor else Color(0xFF182238),
                                            contentColor = if (uiState.isGenreArrangeMode) Color(0xFF090D16) else Color.White
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (uiState.isGenreArrangeMode) "Done" else "Arrange", fontSize = 11.sp)
                                    }

                                    // Select / Batch Delete Button
                                    Button(
                                        onClick = { viewModel.toggleGenreSelectMode() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (uiState.isGenreSelectMode) Color(0xFFEF4444) else Color(0xFF182238),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            if (uiState.isGenreSelectMode) Icons.Default.Close else Icons.Default.Checklist,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (uiState.isGenreSelectMode) "Cancel" else "Select", fontSize = 11.sp)
                                    }
                                }
                            }

                            // Add Genre Input
                            if (!uiState.isGenreSelectMode && !uiState.isGenreArrangeMode) {
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
                            }

                            // Genres List
                            genres.forEachIndexed { index, g ->
                                val isSelected = uiState.selectedGenresForDeletion.contains(g)

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = uiState.isGenreSelectMode) {
                                            viewModel.toggleGenreSelection(g)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(g, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)

                                        if (uiState.isGenreArrangeMode) {
                                            // Reorder handle on right side
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(onClick = { viewModel.moveGenre(g, "up") }, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = if (index > 0) Color.White else Color.DarkGray, modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(onClick = { viewModel.moveGenre(g, "down") }, enabled = index < genres.size - 1, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = if (index < genres.size - 1) Color.White else Color.DarkGray, modifier = Modifier.size(16.dp))
                                                }
                                                Icon(Icons.Default.DragHandle, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                                            }
                                        } else if (uiState.isGenreSelectMode) {
                                            // Selection Circle on right side
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color(0xFFEF4444) else Color.Transparent)
                                                    .border(2.dp, if (isSelected) Color(0xFFEF4444) else Color.Gray, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(80.dp))
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
                    2 -> { // CLOUD SYNC & BACKUP + LIBRARY STATS
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Cloud Presets & Library Info", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)

                            // Library Count Info (Moved here as requested!)
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Total Radio Library", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Active in app storage", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Text(
                                        "${allStations.size} stations • ${genres.size} genres",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

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

            // Floating Batch Delete Action Bar in Select Mode
            if (uiState.isGenreSelectMode && uiState.selectedGenresForDeletion.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2438)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(24.dp))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${uiState.selectedGenresForDeletion.size} selected",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Button(
                            onClick = { showBatchDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Selected", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Batch Delete Confirmation Dialog
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("Delete Genres?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Are you sure you want to delete ${uiState.selectedGenresForDeletion.size} selected genres? Stations in these groups will be moved to General.", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchDeleteConfirm = false
                        viewModel.deleteSelectedGenres()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF141C2D)
        )
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
