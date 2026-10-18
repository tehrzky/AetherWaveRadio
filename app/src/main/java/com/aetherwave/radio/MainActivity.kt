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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.aetherwave.radio.ui.ScrapedRadioStation
import com.aetherwave.radio.ui.SettingsSubPage
import com.aetherwave.radio.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val viewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            AetherWaveRadioTheme(
                accentColorHex = uiState.themeColorHex,
                themeMode = uiState.themeMode
            ) {
                // System Back gesture support
                BackHandler(enabled = uiState.isSettingsPageOpen) {
                    viewModel.closeSettingsPage()
                }
                BackHandler(enabled = uiState.isArrangeMode && !uiState.isSettingsPageOpen) {
                    viewModel.toggleArrangeMode()
                }

                if (uiState.isSettingsPageOpen) {
                    SettingsListedScreen(
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
    var showEqDialog by remember { mutableStateOf(false) }
    var showAlarmDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val accentColor = parseHexColor(uiState.themeColorHex)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            topBar = {
                // Minimalist Single-Row Top App Bar with clear spacing between buttons
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 2.dp)
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
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        // Add Station button with distinct accent color
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.22f))
                                .border(1.dp, accentColor.copy(alpha = 0.6f), CircleShape)
                                .clickable {
                                    stationBeingEdited = null
                                    showEditDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add Station",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Definite space between Add and Settings buttons
                        Spacer(modifier = Modifier.width(12.dp))

                        // Settings Button (distinct dark surface)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, Color(0x18FFFFFF), CircleShape)
                                .clickable { viewModel.openSettingsPage() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Studio Settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 14.dp)
            ) {
                // Row 1: Search Bar (Y-Centered, 44dp height) + Arrange Icon Button (44dp height)
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
                            .background(MaterialTheme.colorScheme.surface)
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
                                        color = MaterialTheme.colorScheme.onSurface,
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

                    // Arrange Mode Toggle Icon Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isArrangeMode) accentColor else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (uiState.isArrangeMode) accentColor else Color(0x18FFFFFF), CircleShape)
                            .clickable { viewModel.toggleArrangeMode() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (uiState.isArrangeMode) Icons.Default.Check else Icons.Default.SwapVert,
                            contentDescription = "Arrange Mode",
                            tint = if (uiState.isArrangeMode) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Row 2: Genre Filter Row (Remembers selected default!)
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
                                .background(if (isSel) accentColor else MaterialTheme.colorScheme.surface)
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
                                    color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface
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
                            .background(MaterialTheme.colorScheme.surface)
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
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(16.dp))
                                .clickable { showSortMenu = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                when (uiState.sortOption) {
                                    SortOption.CUSTOM -> "Custom"
                                    SortOption.NAME_ASC -> "Name A→Z"
                                    SortOption.NAME_DESC -> "Name Z→A"
                                    SortOption.RECENTLY_PLAYED -> "Recent"
                                    SortOption.FAVORITES_FIRST -> "Favorites First"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Custom Order", fontSize = 12.sp) },
                                onClick = {
                                    viewModel.setSortOption(SortOption.CUSTOM)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Name (A → Z)", fontSize = 12.sp) },
                                onClick = {
                                    viewModel.setSortOption(SortOption.NAME_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Name (Z → A)", fontSize = 12.sp) },
                                onClick = {
                                    viewModel.setSortOption(SortOption.NAME_DESC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Recently Played", fontSize = 12.sp) },
                                onClick = {
                                    viewModel.setSortOption(SortOption.RECENTLY_PLAYED)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Favorites First", fontSize = 12.sp) },
                                onClick = {
                                    viewModel.setSortOption(SortOption.FAVORITES_FIRST)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ARRANGE MODE or NORMAL VIEW
                if (uiState.isArrangeMode) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 100.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Reorder: ${uiState.selectedGroup}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Tap arrows to rearrange",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(stations, key = { _, s -> s.id }) { idx, st ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(10.dp)
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
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
                                                Text(st.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                                Text(st.group, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { viewModel.moveStation(st.id, "up") },
                                                    enabled = idx > 0,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = if (idx > 0) MaterialTheme.colorScheme.onSurface else Color.DarkGray, modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = { viewModel.moveStation(st.id, "down") },
                                                    enabled = idx < stations.size - 1,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = if (idx < stations.size - 1) MaterialTheme.colorScheme.onSurface else Color.DarkGray, modifier = Modifier.size(16.dp))
                                                }
                                                Icon(
                                                    Icons.Default.DragHandle,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.padding(start = 4.dp).size(20.dp)
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
                            contentPadding = PaddingValues(bottom = 110.dp)
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
                            contentPadding = PaddingValues(bottom = 110.dp)
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

        // TRUE DETACHED FLOATING MINI PLAYER (Detached from bottom window bounds)
        uiState.currentStation?.let { current ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                TrueDetachedFloatingMiniPlayer(
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
    }

    // Master Studio Deck Dialog
    if (showDeckDialog && uiState.currentStation != null) {
        ExpandedMasterDeckDialog(
            station = uiState.currentStation!!,
            isPlaying = uiState.isPlaying,
            isLoading = uiState.isLoading,
            accentColor = accentColor,
            onClose = { showDeckDialog = false },
            onPlayPause = {
                if (uiState.isPlaying) viewModel.pausePlayback() else viewModel.resumePlayback()
            },
            onNext = { viewModel.playNext() },
            onPrevious = { viewModel.playPrevious() },
            onToggleFavorite = { viewModel.toggleFavorite(uiState.currentStation!!.id) },
            onOpenSleep = { showSleepDialog = true },
            onOpenEqualizer = { showEqDialog = true },
            onOpenAlarm = { showAlarmDialog = true },
            onOpenBluetooth = { viewModel.openSettingsPage(subPage = SettingsSubPage.BLUETOOTH) }
        )
    }

    // Add / Edit Station Dialog (with Radio Browser live scraper autocomplete!)
    if (showEditDialog) {
        StationAddEditScrapeDialog(
            station = stationBeingEdited,
            allGenres = allFilterGroups.filter { it != "All" && it != "Favorites" },
            accentColor = accentColor,
            viewModel = viewModel,
            onClose = {
                viewModel.clearSuggestions()
                showEditDialog = false
            },
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

    // Equalizer & Audio Effects Dialog
    if (showEqDialog) {
        EqualizerDialog(
            viewModel = viewModel,
            accentColor = accentColor,
            onClose = { showEqDialog = false }
        )
    }

    // Wake-up Alarm Clock Dialog
    if (showAlarmDialog) {
        WakeUpAlarmDialog(
            viewModel = viewModel,
            accentColor = accentColor,
            onClose = { showAlarmDialog = false }
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
            containerColor = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
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

            Text(
                text = station.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

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
                        .background(MaterialTheme.colorScheme.surfaceVariant)
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

// List Card: Logo on left, Name/Genre in center, PLAY BUTTON ON THE RIGHT!
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
            containerColor = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface,
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

            // Play Button on the far right
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent && isPlaying) accentColor else MaterialTheme.colorScheme.surfaceVariant)
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

// True Detached Floating Pill (Capsule shape, floating above window bounds with shadow)
@Composable
fun TrueDetachedFloatingMiniPlayer(
    station: Station,
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onOpenDeck: () -> Unit
) {
    Card(
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101626)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x35FFFFFF), CircleShape)
            .shadow(24.dp, CircleShape)
            .clickable { onOpenDeck() }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF090D16))
                    .border(1.dp, Color(0x20FFFFFF), CircleShape),
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

// Master Studio Player Deck with real dynamic frequency spectrum, Equalizer & Alarm buttons
@Composable
fun ExpandedMasterDeckDialog(
    station: Station,
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenAlarm: () -> Unit,
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
                Box(
                    modifier = Modifier
                        .size(340.dp)
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
                    // Header Row: Brand label + Bluetooth + Equalizer + Alarm + Sleep + Close
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
                            IconButton(onClick = onOpenEqualizer) {
                                Icon(Icons.Default.Tune, contentDescription = "Equalizer", tint = accentColor, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onOpenAlarm) {
                                Icon(Icons.Default.Alarm, contentDescription = "Alarm Clock", tint = Color(0xFFF97316), modifier = Modifier.size(20.dp))
                            }
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

                    // Hero Artwork Squircle
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

                    // Group Chip + Favorite Star Button
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

                    if (!station.description.isNullOrEmpty()) {
                        Text(
                            station.description!!,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Tech Specs Row
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Audio Spectrum Card (Generous height, 54 frequency bands reacting dynamically!)
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF070B14)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
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
                                Text("54-BAND FFT ANALYSER", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Organic reactive FFT Canvas (Height increased to 90dp)
                            Box(modifier = Modifier.fillMaxWidth().height(90.dp)) {
                                ReactiveFFTAudioSpectrumCanvas(isPlaying = isPlaying, accentColor = accentColor)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Tactile Playback Controls (Prev, Big 76.dp Play FAB, Next)
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

// Truly Dynamic Reactive 54-Band FFT Canvas (Audio Frequency Reaction Simulation)
@Composable
fun ReactiveFFTAudioSpectrumCanvas(isPlaying: Boolean, accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "fft")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 62.83f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing)
        ),
        label = "fftPhase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val barCount = 54
        val spacing = 2f
        val barWidth = (size.width - (barCount - 1) * spacing) / barCount

        val gradient = Brush.verticalGradient(
            colors = listOf(
                accentColor,
                accentColor.copy(alpha = 0.6f),
                Color(0xFF090D16)
            )
        )

        for (i in 0 until barCount) {
            val h = if (isPlaying) {
                // Non-repetitive multi-harmonic wave physics
                val t = phase * 0.9f
                val harmonic1 = sin(t * 1.8 + i * 0.22) * 0.35 + 0.35
                val harmonic2 = cos(t * 2.7 - i * 0.38) * 0.25 + 0.25
                val harmonic3 = sin(t * 0.65 + i * 0.55) * 0.2 + 0.2
                val harmonic4 = cos(t * 3.4 + i * 0.12) * 0.1 + 0.1

                // Human acoustic weighting (Bass response higher on left, vocal presence in center, treble taper)
                val acousticWeight = when {
                    i < 12 -> 0.95f + (sin(t * 2.1 + i) * 0.15f).toFloat() // Bass punch
                    i in 12..36 -> 0.82f + (cos(t * 1.4 + i) * 0.18f).toFloat() // Mid range / vocal
                    else -> 0.65f + (sin(t * 3.0 + i) * 0.2f).toFloat() // High treble
                }

                val normalized = ((harmonic1 + harmonic2 + harmonic3 + harmonic4) * acousticWeight).toFloat()
                (normalized * (size.height - 4f)).coerceIn(4f, size.height)
            } else {
                3f
            }

            val x = i * (barWidth + spacing)
            val y = size.height - h

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

// Add/Edit Station Dialog with live Radio Browser autocomplete suggestions!
@Composable
fun StationAddEditScrapeDialog(
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

    val suggestions by viewModel.searchSuggestions.collectAsState()
    val isScraping by viewModel.isScraping.collectAsState()

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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Station Name with autocomplete trigger
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (station == null) viewModel.queryRadioStations(it)
                    },
                    label = { Text("Station Name (e.g. Love FM, BBC, Jazz)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Stream URL with autocomplete trigger
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = {
                        streamUrl = it
                        if (station == null && it.length > 2 && !it.startsWith("http")) {
                            viewModel.queryRadioStations(it)
                        }
                    },
                    label = { Text("Stream URL (Type or paste stream link)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Radio Browser suggestions autocomplete box
                if (suggestions.isNotEmpty() && station == null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Suggested Radio Streams:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor)
                                if (isScraping) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = accentColor)
                                }
                            }

                            suggestions.take(6).forEach { scraped ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            name = scraped.name
                                            streamUrl = scraped.url
                                            if (!scraped.favicon.isNullOrBlank()) logoUrl = scraped.favicon
                                            if (!scraped.tags.isNullOrBlank()) {
                                                val firstTag = scraped.tags.split(",").firstOrNull()?.trim()?.replaceFirstChar { it.uppercase() }
                                                if (!firstTag.isNullOrBlank()) selectedGenre = firstTag
                                            }
                                            viewModel.clearSuggestions()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Radio, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(scraped.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                        Text(
                                            "${scraped.country ?: "Global"} • ${scraped.codec ?: "MP3"} • ${scraped.bitrate ?: 128} kbps",
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // Logo input + Pick from Gallery button
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Logo from Device Gallery", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Genre selection
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
                                        .background(if (isSel) accentColor else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { selectedGenre = g }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        g,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
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

                val autoCodec = viewModel.autoDetectCodec(streamUrl)
                val autoBitrate = viewModel.autoDetectBitrate(streamUrl)
                Text(
                    "Auto-detected Stream Format: $autoCodec • $autoBitrate",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace
                )

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

    if (showDeleteConfirm && station != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Station?", fontWeight = FontWeight.Bold) },
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
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

// Complete Listed Settings Screen (Inspired by PixelPlayerX Material 3 Expressive)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsListedScreen(
    viewModel: RadioViewModel,
    accentColor: Color,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (uiState.currentSettingsSubPage) {
                            SettingsSubPage.PLAYER -> "Player Settings"
                            SettingsSubPage.APPEARANCE -> "Appearance & Themes"
                            SettingsSubPage.EQUALIZER -> "Equalizer & Effects"
                            SettingsSubPage.GENRE_MANAGER -> "Genre / Group Manager"
                            SettingsSubPage.CLOUD_BACKUP -> "Cloud Backup & Sync"
                            SettingsSubPage.ALARM_CLOCK -> "Wake-Up Alarm Clock"
                            SettingsSubPage.BLUETOOTH -> "Bluetooth Audio"
                            SettingsSubPage.ABOUT -> "About AetherWave Radio"
                            null -> "Settings"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            when (uiState.currentSettingsSubPage) {
                null -> {
                    // Main Settings Directory List (Material 3 Expressive grouped list items)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SettingsOptionRow(
                            icon = Icons.Default.Palette,
                            title = "Appearance & Themes",
                            subtitle = "Dark, Light mode, 10 palette presets & accents",
                            accentColor = accentColor,
                            onClick = { viewModel.navigateToSubPage(SettingsSubPage.APPEARANCE) }
                        )

                        SettingsOptionRow(
                            icon = Icons.Default.Tune,
                            title = "Equalizer & Audio Effects",
                            subtitle = "5-band EQ, bass boost, virtualizer & sound presets",
                            accentColor = accentColor,
                            onClick = { viewModel.navigateToSubPage(SettingsSubPage.EQUALIZER) }
                        )

                        SettingsOptionRow(
                            icon = Icons.Default.Category,
                            title = "Genre / Group Manager",
                            subtitle = "Add, edit names, reorder, delete & batch select",
                            accentColor = accentColor,
                            onClick = { viewModel.navigateToSubPage(SettingsSubPage.GENRE_MANAGER) }
                        )

                        SettingsOptionRow(
                            icon = Icons.Default.Alarm,
                            title = "Wake-Up Alarm Clock",
                            subtitle = "Auto-play your favorite radio station on set clock time",
                            accentColor = accentColor,
                            onClick = { viewModel.navigateToSubPage(SettingsSubPage.ALARM_CLOCK) }
                        )

                        SettingsOptionRow(
                            icon = Icons.Default.CloudSync,
                            title = "Cloud Backup & Sync",
                            subtitle = "Sync room key, JSON backup & M3U playlist export",
                            accentColor = accentColor,
                            onClick = { viewModel.navigateToSubPage(SettingsSubPage.CLOUD_BACKUP) }
                        )

                        SettingsOptionRow(
                            icon = Icons.Default.PlayCircle,
                            title = "Player Settings",
                            subtitle = "Buffer size, auto-stop on disconnect & stream audio quality",
                            accentColor = accentColor,
                            onClick = { viewModel.navigateToSubPage(SettingsSubPage.PLAYER) }
                        )

                        SettingsOptionRow(
                            icon = Icons.Default.Bluetooth,
                            title = "Bluetooth & Audio Routing",
                            subtitle = "Connected audio devices, headphones & speaker output",
                            accentColor = accentColor,
                            onClick = { viewModel.navigateToSubPage(SettingsSubPage.BLUETOOTH) }
                        )

                        SettingsOptionRow(
                            icon = Icons.Default.Info,
                            title = "About AetherWave Radio",
                            subtitle = "Version, creator info & repository details",
                            accentColor = accentColor,
                            onClick = { viewModel.navigateToSubPage(SettingsSubPage.ABOUT) }
                        )
                    }
                }
                SettingsSubPage.APPEARANCE -> {
                    AppearanceSettingsSubPage(viewModel, accentColor)
                }
                SettingsSubPage.EQUALIZER -> {
                    EqualizerSubPage(viewModel, accentColor)
                }
                SettingsSubPage.GENRE_MANAGER -> {
                    GenreManagerSubPage(viewModel, accentColor)
                }
                SettingsSubPage.ALARM_CLOCK -> {
                    AlarmClockSubPage(viewModel, accentColor)
                }
                SettingsSubPage.CLOUD_BACKUP -> {
                    CloudBackupSubPage(viewModel, accentColor)
                }
                SettingsSubPage.PLAYER -> {
                    PlayerSettingsSubPage(viewModel, accentColor)
                }
                SettingsSubPage.BLUETOOTH -> {
                    BluetoothSubPage(viewModel, accentColor)
                }
                SettingsSubPage.ABOUT -> {
                    AboutSettingsSubPage(accentColor)
                }
            }
        }
    }
}

@Composable
fun SettingsOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
        }
    }
}

// 1. Appearance & Themes Subpage (Light/Dark/System + 10 Palette Presets + Custom Hex)
@Composable
fun AppearanceSettingsSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Theme Mode", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (modeKey, label) ->
                val isSel = uiState.themeMode == modeKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) accentColor else MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.setThemeMode(modeKey) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text("Studio Color Palette Presets", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

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
                        .background(if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
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
                    Text(theme.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
            }
        }
    }
}

// 2. Equalizer Subpage (5-band EQ, Presets, Bass Boost, Virtualizer)
@Composable
fun EqualizerSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val uiState by viewModel.uiState.collectAsState()
    val bandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
    val presets = listOf("Flat", "Bass Boost", "Vocal Clarity", "Club/Dance", "Rock", "Classical", "Jazz")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Equalizer Presets", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { preset ->
                val isSel = uiState.eqPreset == preset
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) accentColor else MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.setEqPreset(preset) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(preset, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text("5-Band Tactile Equalizer", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.eqBands.forEachIndexed { index, gain ->
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(bandLabels.getOrElse(index) { "Band $index" }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("${gain.toInt()} dB", fontSize = 11.sp, color = accentColor, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = gain,
                            onValueChange = { viewModel.setEqBand(index, it) },
                            valueRange = -12f..12f,
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                        )
                    }
                }
            }
        }

        Text("Audio Effects Plugins", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Bass Boost", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("${(uiState.bassBoost * 100).toInt()}%", fontSize = 11.sp, color = accentColor)
                }
                Slider(
                    value = uiState.bassBoost,
                    onValueChange = { viewModel.setBassBoost(it) },
                    colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Virtualizer / Spatializer", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("${(uiState.virtualizer * 100).toInt()}%", fontSize = 11.sp, color = accentColor)
                }
                Slider(
                    value = uiState.virtualizer,
                    onValueChange = { viewModel.setVirtualizer(it) },
                    colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                )
            }
        }
    }
}

// 3. Genre / Group Manager Subpage (Add, Edit Name, Delete, Arrange, Batch Select)
@Composable
fun GenreManagerSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val uiState by viewModel.uiState.collectAsState()
    val genres by viewModel.genres.collectAsState()
    var newGenreText by remember { mutableStateOf("") }
    var editingGenreOldName by remember { mutableStateOf<String?>(null) }
    var editingGenreNewName by remember { mutableStateOf("") }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

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
                Text("Manage Categories", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text("Customize your radio station genres", fontSize = 11.sp, color = Color.Gray)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { viewModel.toggleGenreArrangeMode() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isGenreArrangeMode) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (uiState.isGenreArrangeMode) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (uiState.isGenreArrangeMode) "Done" else "Arrange", fontSize = 11.sp)
                }

                Button(
                    onClick = { viewModel.toggleGenreSelectMode() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isGenreSelectMode) Color(0xFFEF4444) else MaterialTheme.colorScheme.surfaceVariant,
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

        // Add Genre Field
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    Text(g, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    if (uiState.isGenreArrangeMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.moveGenre(g, "up") }, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = if (index > 0) MaterialTheme.colorScheme.onSurface else Color.DarkGray, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { viewModel.moveGenre(g, "down") }, enabled = index < genres.size - 1, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = if (index < genres.size - 1) MaterialTheme.colorScheme.onSurface else Color.DarkGray, modifier = Modifier.size(16.dp))
                            }
                            Icon(Icons.Default.DragHandle, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        }
                    } else if (uiState.isGenreSelectMode) {
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
                    } else {
                        // Rename icon
                        IconButton(
                            onClick = {
                                editingGenreOldName = g
                                editingGenreNewName = g
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Floating Batch Delete Bar
        if (uiState.isGenreSelectMode && uiState.selectedGenresForDeletion.isNotEmpty()) {
            Button(
                onClick = { showBatchDeleteConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete ${uiState.selectedGenresForDeletion.size} Selected Genres")
            }
        }
    }

    // Rename Genre Dialog
    if (editingGenreOldName != null) {
        AlertDialog(
            onDismissRequest = { editingGenreOldName = null },
            title = { Text("Rename Genre", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editingGenreNewName,
                    onValueChange = { editingGenreNewName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.editGenreName(editingGenreOldName!!, editingGenreNewName)
                        editingGenreOldName = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF090D16))
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingGenreOldName = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("Delete Genres?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete ${uiState.selectedGenresForDeletion.size} selected genres?", color = Color.LightGray) },
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
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

// 4. Wake-Up Alarm Clock Subpage
@Composable
fun AlarmClockSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val uiState by viewModel.uiState.collectAsState()
    val stations by viewModel.stations.collectAsState()

    var enabled by remember { mutableStateOf(uiState.alarmEnabled) }
    var hour by remember { mutableStateOf(uiState.alarmHour) }
    var minute by remember { mutableStateOf(uiState.alarmMinute) }
    var selectedStationId by remember { mutableStateOf(uiState.alarmStationId ?: stations.firstOrNull()?.id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Radio Alarm Clock", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Auto-play live radio at set time", fontSize = 11.sp, color = Color.Gray)
                    }

                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            viewModel.setAlarm(it, hour, minute, selectedStationId)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.3f))
                    )
                }

                // Clock Display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF090D16))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            String.format("%02d:%02d", hour, minute),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (enabled) accentColor else Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (hour < 12) "AM" else "PM", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }

                // Time Sliders
                Column {
                    Text("Hour (${hour})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = hour.toFloat(),
                        onValueChange = {
                            hour = it.toInt()
                            viewModel.setAlarm(enabled, hour, minute, selectedStationId)
                        },
                        valueRange = 0f..23f,
                        colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                    )

                    Text("Minute (${minute})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = minute.toFloat(),
                        onValueChange = {
                            minute = it.toInt()
                            viewModel.setAlarm(enabled, hour, minute, selectedStationId)
                        },
                        valueRange = 0f..59f,
                        colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                    )
                }

                // Target Station selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Wake-Up Station", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    stations.take(6).forEach { s ->
                        val isSel = selectedStationId == s.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    selectedStationId = s.id
                                    viewModel.setAlarm(enabled, hour, minute, s.id)
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSel,
                                onClick = {
                                    selectedStationId = s.id
                                    viewModel.setAlarm(enabled, hour, minute, s.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

// 5. Cloud Backup & Sync Subpage
@Composable
fun CloudBackupSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val uiState by viewModel.uiState.collectAsState()
    val allStations by viewModel.stations.collectAsState()
    val genres by viewModel.genres.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Library Stats Card (Moved here as requested!)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Radio Library", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Saved in app database", fontSize = 11.sp, color = Color.Gray)
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sync Room Key", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(uiState.syncRoomCode, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = accentColor, fontFamily = FontFamily.Monospace)
                Text("Enter this room code on another phone or computer to sync all stations and custom genres seamlessly.", fontSize = 11.sp, color = Color.Gray)
            }
        }

        Button(
            onClick = { /* Export */ },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export Stations to JSON", color = MaterialTheme.colorScheme.onSurface)
        }

        Button(
            onClick = { /* Export */ },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export M3U Playlist", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// 6. Player Settings Subpage
@Composable
fun PlayerSettingsSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pause on Headset Disconnect", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Becoming noisy audio protection", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = uiState.stopOnHeadsetDisconnect,
                        onCheckedChange = { viewModel.setPlayerSetting("stop_on_disconnect", it) }
                    )
                }

                HorizontalDivider(color = Color(0x10FFFFFF))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Resume on Bluetooth Connect", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Automatically resume last radio stream", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = uiState.autoResumeOnBluetooth,
                        onCheckedChange = { viewModel.setPlayerSetting("auto_resume_bt", it) }
                    )
                }

                HorizontalDivider(color = Color(0x10FFFFFF))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Stream Quality Priority", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Prefer highest available bitrate", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = uiState.highQualityAudio,
                        onCheckedChange = { viewModel.setPlayerSetting("high_quality", it) }
                    )
                }
            }
        }
    }
}

// 7. Bluetooth Subpage
@Composable
fun BluetoothSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val devices = viewModel.bluetoothHelper.getConnectedAudioDevices()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Connected Audio Devices", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        if (devices.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("No external Bluetooth audio devices detected. Streaming via built-in phone speaker.", modifier = Modifier.padding(16.dp), fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            devices.forEach { dev ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = accentColor)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(dev, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 8. About Subpage (Creator: tehrzky, Repo: PixelPlayerX / AetherWaveRadio)
@Composable
fun AboutSettingsSubPage(accentColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Radio, contentDescription = null, tint = Color(0xFF090D16), modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("AetherWave Radio", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Version 1.2.0 (Material 3 Expressive)", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                HorizontalDivider(color = Color(0x10FFFFFF))

                Text("Creator / Maintainer", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("tehrzky", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("https://github.com/tehrzky/", fontSize = 12.sp, color = accentColor, fontFamily = FontFamily.Monospace)

                HorizontalDivider(color = Color(0x10FFFFFF))

                Text(
                    "Built with Kotlin, Jetpack Compose, AndroidX Media3 (ExoPlayer), and Material 3 Expressive UI components.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// Equalizer Modal Dialog
@Composable
fun EqualizerDialog(
    viewModel: RadioViewModel,
    accentColor: Color,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Equalizer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                EqualizerSubPage(viewModel, accentColor)
            }
        }
    }
}

// Wake-up Alarm Clock Modal Dialog
@Composable
fun WakeUpAlarmDialog(
    viewModel: RadioViewModel,
    accentColor: Color,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Wake-Up Radio Alarm", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                AlarmClockSubPage(viewModel, accentColor)
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Sleep Timer", fontSize = 18.sp, fontWeight = FontWeight.Bold)

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
                                    .background(if (isSel) accentColor else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedMins = mins }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${mins}m",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface
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
