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
import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.app.TimePickerDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.aetherwave.radio.data.DefaultStations
import com.aetherwave.radio.data.SortOption
import com.aetherwave.radio.data.Station
import com.aetherwave.radio.data.ViewMode
import com.aetherwave.radio.ui.RadioViewModel
import com.aetherwave.radio.ui.SavedThemePreset
import com.aetherwave.radio.ui.ScrapedRadioStation
import com.aetherwave.radio.ui.SettingsSubPage
import com.aetherwave.radio.ui.theme.*
import com.aetherwave.radio.util.BluetoothDeviceInfo
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val viewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle alarm trigger launch if app was launched via AlarmReceiver
        if (intent?.action == "com.aetherwave.radio.ACTION_ALARM_FIRE") {
            val stationId = intent.getStringExtra("station_id")
            val station = viewModel.stations.value.firstOrNull { it.id == stationId }
                ?: viewModel.stations.value.firstOrNull()
            if (station != null) {
                viewModel.playStation(station)
                viewModel.openDeck()
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            AetherWaveRadioTheme(
                accentColorHex = uiState.themeColorHex,
                backgroundColorHex = uiState.backgroundColorHex,
                cardColorHex = uiState.cardColorHex,
                themeFontStyle = uiState.themeFontStyle,
                themeFontColorHex = uiState.themeFontColorHex,
                themeMode = uiState.themeMode,
                isMonet = uiState.isMonetEnabled
            ) {
                // System Back gesture support
                // If in settings: close settings (returns directly to deck if deck was open!)
                BackHandler(enabled = uiState.isSettingsPageOpen) {
                    viewModel.closeSettingsPage()
                }
                // If in big player deck and settings is closed: close deck
                BackHandler(enabled = uiState.isDeckOpen && !uiState.isSettingsPageOpen) {
                    viewModel.closeDeck()
                }
                // If in arrange mode on home: exit arrange mode
                BackHandler(enabled = uiState.isArrangeMode && !uiState.isDeckOpen && !uiState.isSettingsPageOpen) {
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
    val accentColor = parseHexColor(uiState.themeColorHex)

    var showEditDialog by remember { mutableStateOf(false) }
    var stationBeingEdited by remember { mutableStateOf<Station?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Dialog state
    var showSleepDialog by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    var showAlarmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "AetherWave Radio",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Live Broadcast & Studio Tuner",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                },
                actions = {
                    // + Add Station Button
                    FilledTonalButton(
                        onClick = {
                            stationBeingEdited = null
                            showEditDialog = true
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accentColor.copy(alpha = 0.20f),
                            contentColor = accentColor
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Dedicated Settings Button
                    IconButton(
                        onClick = { viewModel.openSettingsPage() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
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
            // Row 1: SEARCH BAR IS SOLO ON THIS ROW (44dp exact height, y-centered)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(24.dp))
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
                                "Search radio stations, genres, countries...",
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
                                    modifier = Modifier.size(12.dp)
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

            // Row 3: Grid / List Segmented Toggle + Arrange Pill Button (LEFT of Custom/Sort) + Sort Dropdown
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

                // Right cluster: Arrange Pill Medicine Button + Sort Dropdown
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ARRANGE BUTTON AS A MEDICINE PILL BESIDE LEFT OF CUSTOM/SORT (STABLE FIXED SIZE)
                    Box(
                        modifier = Modifier
                            .width(82.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (uiState.isArrangeMode) accentColor else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (uiState.isArrangeMode) accentColor else Color(0x18FFFFFF), RoundedCornerShape(18.dp))
                            .clickable { viewModel.toggleArrangeMode() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (uiState.isArrangeMode) Icons.Default.Check else Icons.Default.SwapVert,
                                contentDescription = "Arrange Mode",
                                tint = if (uiState.isArrangeMode) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (uiState.isArrangeMode) "Done" else "Arrange",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isArrangeMode) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface
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
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                when (uiState.sortOption) {
                                    SortOption.CUSTOM -> "Custom"
                                    SortOption.NAME_ASC -> "A - Z"
                                    SortOption.NAME_DESC -> "Z - A"
                                    SortOption.FAVORITES_FIRST -> "Favorites"
                                    SortOption.RECENTLY_PLAYED -> "Recent"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Custom Order") },
                                onClick = {
                                    viewModel.setSortOption(SortOption.CUSTOM)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Name (A to Z)") },
                                onClick = {
                                    viewModel.setSortOption(SortOption.NAME_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Name (Z to A)") },
                                onClick = {
                                    viewModel.setSortOption(SortOption.NAME_DESC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Favorites First") },
                                onClick = {
                                    viewModel.setSortOption(SortOption.FAVORITES_FIRST)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Recently Played") },
                                onClick = {
                                    viewModel.setSortOption(SortOption.RECENTLY_PLAYED)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // MAIN STATIONS CONTENT
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isArrangeMode) {
                    // ARRANGE MODE: Clean Reordering List
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        itemsIndexed(stations, key = { _, s -> s.id }) { idx, st ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
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
                                            Icon(Icons.Default.Radio, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(st.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                        Text(st.group, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                                    }

                                    var dragYOffset by remember { mutableFloatStateOf(0f) }
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(accentColor.copy(alpha = 0.15f))
                                            .pointerInput(st.id) {
                                                detectDragGestures(
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragYOffset += dragAmount.y
                                                        val currentIndex = stations.indexOfFirst { it.id == st.id }
                                                        if (dragYOffset > 42f && currentIndex in 0 until stations.size - 1) {
                                                            viewModel.reorderStations(currentIndex, currentIndex + 1)
                                                            dragYOffset -= 42f
                                                        } else if (dragYOffset < -42f && currentIndex > 0) {
                                                            viewModel.reorderStations(currentIndex, currentIndex - 1)
                                                            dragYOffset += 42f
                                                        }
                                                    },
                                                    onDragEnd = { dragYOffset = 0f },
                                                    onDragCancel = { dragYOffset = 0f }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            tint = accentColor,
                                            modifier = Modifier.size(24.dp)
                                        )
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
                            contentPadding = PaddingValues(bottom = 120.dp)
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
                            contentPadding = PaddingValues(bottom = 120.dp)
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

                // TRUE DETACHED FLOATING MINI PLAYER
                if (uiState.currentStation != null) {
                    TrueDetachedFloatingMiniPlayer(
                        station = uiState.currentStation!!,
                        isPlaying = uiState.isPlaying,
                        isLoading = uiState.isLoading,
                        accentColor = accentColor,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp),
                        onPlayPause = {
                            if (uiState.isPlaying) viewModel.pausePlayback() else viewModel.resumePlayback()
                        },
                        onOpenDeck = { viewModel.openDeck() }
                    )
                }
            }
        }
    }

    // MASTER STUDIO DECK DIALOG (Full Big Player Deck)
    if (uiState.isDeckOpen && uiState.currentStation != null) {
        ExpandedMasterDeckDialog(
            station = uiState.currentStation!!,
            isPlaying = uiState.isPlaying,
            isLoading = uiState.isLoading,
            accentColor = accentColor,
            viewModel = viewModel,
            onClose = { viewModel.closeDeck() },
            onPlayPause = {
                if (uiState.isPlaying) viewModel.pausePlayback() else viewModel.resumePlayback()
            },
            onNext = { viewModel.playNext() },
            onPrevious = { viewModel.playPrevious() },
            onToggleFavorite = { viewModel.toggleFavorite(uiState.currentStation!!.id) },
            onOpenSleep = { showSleepDialog = true },
            onOpenEqualizer = { showEqDialog = true },
            onOpenAlarm = { showAlarmDialog = true }
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

    // Equalizer & Audio Effects Dialog (Fixed, no nested scroll crash!)
    if (showEqDialog) {
        EqualizerDialog(
            viewModel = viewModel,
            accentColor = accentColor,
            onClose = { showEqDialog = false }
        )
    }

    // Wake-up Alarm Clock Dialog (Fixed, no nested scroll crash!)
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp)
            .border(
                width = if (isCurrent) 1.5.dp else 1.dp,
                color = if (isCurrent) accentColor else Color(0x14FFFFFF),
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onHoldEdit
            )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
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
                    Icon(
                        Icons.Default.Radio,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MiniLiveEqualizerBars(accentColor = accentColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                station.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x12FFFFFF))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    station.group,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Clean List Card: Logo on left, text in middle, play button on rightmost side!
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
            .height(68.dp)
            .border(
                width = if (isCurrent) 1.5.dp else 1.dp,
                color = if (isCurrent) accentColor else Color(0x14FFFFFF),
                shape = RoundedCornerShape(14.dp)
            )
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onHoldEdit
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Logo First
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
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
                    Icon(
                        Icons.Default.Radio,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MiniLiveEqualizerBars(accentColor = accentColor)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Station Name and Group
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    station.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        station.group,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!station.codec.isNullOrEmpty()) {
                        Text(" • ", fontSize = 10.sp, color = Color.DarkGray)
                        Text(station.codec, fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Play Button on rightmost side
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) accentColor else Color(0x16FFFFFF))
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isPlaying) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Mini Live Equalizer Bars indicator on playing card
@Composable
fun MiniLiveEqualizerBars(accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "mini_eq")
    val anim1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "b1"
    )
    val anim2 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(310, easing = LinearEasing), RepeatMode.Reverse), label = "b2"
    )
    val anim3 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "b3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        listOf(anim1, anim2, anim3).forEach { heightFraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(1.dp))
                    .background(accentColor)
            )
        }
    }
}

// True Detached Floating Mini Player (Elevated pill above bottom nav bounds)
@Composable
fun TrueDetachedFloatingMiniPlayer(
    station: Station,
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
    onOpenDeck: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp), clip = false)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF0D1424))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(32.dp))
            .clickable { onOpenDeck() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Station Logo
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black),
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
                    Icon(
                        Icons.Default.Radio,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Station Name & Group
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    station.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    station.group,
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play / Pause Squircle Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF090D16),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF090D16),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// Master Studio Deck Dialog (Big Player with fixed play & spectrum location, 3-part bottom pill, rounded squircle play button)
@Composable
fun ExpandedMasterDeckDialog(
    station: Station,
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    viewModel: RadioViewModel,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenAlarm: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF0B101D),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Ambient Radial Glow
                Box(
                    modifier = Modifier
                        .size(360.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = 10.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = 0.28f), Color.Transparent)
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // TOP BAR: Clean & uncluttered (Brand + Equalizer + Settings + Close)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Radio, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AETHERWAVE STUDIO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.openSettingsPage(SettingsSubPage.EQUALIZER, fromDeck = true) }) {
                                Icon(Icons.Default.Tune, contentDescription = "Equalizer", tint = accentColor, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { viewModel.openSettingsPage(SettingsSubPage.BLUETOOTH, fromDeck = true) }) {
                                Icon(Icons.Default.Bluetooth, contentDescription = "Bluetooth", tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { viewModel.openSettingsPage(null, fromDeck = true) }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                        }
                    }

                    // HERO ARTWORK SQUIRCLE (190dp)
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.Black)
                            .border(1.5.dp, Color(0x30FFFFFF), RoundedCornerShape(32.dp))
                            .shadow(20.dp, RoundedCornerShape(32.dp)),
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

                        // Live On Air badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.8f))
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

                    // FIXED-HEIGHT STATION INFO CONTAINER (Guarantees spectrum and play button NEVER shift!)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Centered Group Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF162138))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    station.group,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.LightGray,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Centered Station Title
                            Text(
                                station.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Centered Subtitle / Description / Specs
                            Text(
                                if (!station.description.isNullOrEmpty()) station.description!!
                                else listOfNotNull(station.codec, station.bitrate, station.country).joinToString(" • "),
                                fontSize = 11.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // LIVE AUDIO SPECTRUM VISUALIZER (Configurable height, style & sensitivity from Settings)
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
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Audio Spectrum", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Text(
                                    "${uiState.spectrumStyle.uppercase()} • ${(uiState.spectrumSensitivity * 100).toInt()}%",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.Gray
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(uiState.spectrumHeight.dp)
                            ) {
                                CustomizableAudioSpectrumCanvas(
                                    isPlaying = isPlaying,
                                    accentColor = accentColor,
                                    style = uiState.spectrumStyle,
                                    sensitivity = uiState.spectrumSensitivity,
                                    colorScheme = uiState.spectrumColorScheme,
                                    speed = uiState.spectrumSpeed
                                )
                            }
                        }
                    }

                    // TACTILE PLAYBACK CONTROLS (Prev, SQUIRCLE Play Button, Next)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(30.dp)
                    ) {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        // SQUIRCLE ROUNDED-CORNER SQUARE PLAY BUTTON (PixelPlayer M3 Expressive style!)
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(accentColor)
                                .shadow(18.dp, RoundedCornerShape(24.dp))
                                .clickable { onPlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color(0xFF090D16), strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
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

                    // BOTTOM 3-WAY SEGMENTED PILL BUTTON [ 🌙 Sleep | ⏰ Wake | ⭐ Favorite ]
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111726)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(28.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Section 1: Sleep Timer
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { onOpenSleep() },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bedtime, contentDescription = "Sleep", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sleep", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            // Vertical divider line
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(26.dp)
                                    .background(Color(0x22FFFFFF))
                            )

                            // Section 2: Wake-Up Alarm
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { viewModel.openSettingsPage(SettingsSubPage.ALARM_CLOCK, fromDeck = true) },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Alarm, contentDescription = "Wake", tint = Color(0xFFF97316), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wake", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            // Vertical divider line
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(26.dp)
                                    .background(Color(0x22FFFFFF))
                            )

                            // Section 3: Favorite Star Toggle
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { onToggleFavorite() },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "Favorite",
                                    tint = if (station.isFavorite) Color(0xFFFBBF24) else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Favorite",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (station.isFavorite) Color(0xFFFBBF24) else Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

// Customizable Dynamic Audio Spectrum Canvas
@Composable
fun CustomizableAudioSpectrumCanvas(
    isPlaying: Boolean,
    accentColor: Color,
    style: String,
    sensitivity: Float,
    colorScheme: String,
    speed: String
) {
    val speedDuration = when (speed) {
        "Smooth" -> 22000
        "Energetic" -> 9000
        else -> 15000
    }

    val infiniteTransition = rememberInfiniteTransition(label = "spec_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 62.83f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = speedDuration, easing = LinearEasing)
        ),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val barCount = when (style) {
            "Columns" -> 24
            "Mirrored" -> 48
            else -> 48
        }
        val spacing = 2.5f
        val barWidth = (size.width - (barCount - 1) * spacing) / barCount

        val primaryGradColor = when (colorScheme) {
            "Rainbow" -> Color(0xFFEC4899)
            "CyanAmber" -> Color(0xFF06B6D4)
            "NeonEmerald" -> Color(0xFF10B981)
            "PurpleGlow" -> Color(0xFFA855F7)
            else -> accentColor
        }
        val secondaryGradColor = when (colorScheme) {
            "Rainbow" -> Color(0xFF3B82F6)
            "CyanAmber" -> Color(0xFFF59E0B)
            "NeonEmerald" -> Color(0xFF34D399)
            "PurpleGlow" -> Color(0xFFEC4899)
            else -> accentColor.copy(alpha = 0.5f)
        }

        val gradient = Brush.verticalGradient(
            colors = listOf(primaryGradColor, secondaryGradColor, Color(0xFF090D16))
        )

        for (i in 0 until barCount) {
            val h = if (isPlaying) {
                val t = phase
                val harmonic1 = sin(t * 1.9 + i * 0.24) * 0.35 + 0.35
                val harmonic2 = cos(t * 2.8 - i * 0.42) * 0.25 + 0.25
                val harmonic3 = sin(t * 0.72 + i * 0.6) * 0.20 + 0.20
                val harmonic4 = cos(t * 3.5 + i * 0.15) * 0.10 + 0.10

                val acousticWeight = when {
                    i < 10 -> 1.0f + (sin(t * 2.2 + i) * 0.18f).toFloat() // Bass
                    i in 10..30 -> 0.85f + (cos(t * 1.5 + i) * 0.16f).toFloat() // Mids
                    else -> 0.68f + (sin(t * 3.1 + i) * 0.22f).toFloat() // Treble
                }

                val normalized = ((harmonic1 + harmonic2 + harmonic3 + harmonic4) * acousticWeight * sensitivity).toFloat()
                (normalized * (size.height - 4f)).coerceIn(4f, size.height)
            } else {
                3f
            }

            val x = i * (barWidth + spacing)

            when (style) {
                "Mirrored" -> {
                    // Center mirrored waves
                    val halfH = h / 2f
                    val centerY = size.height / 2f
                    drawRoundRect(
                        brush = gradient,
                        topLeft = Offset(x, centerY - halfH),
                        size = Size(barWidth, halfH * 2f),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
                "Columns" -> {
                    // Thick segmented columns
                    val y = size.height - h
                    drawRoundRect(
                        brush = gradient,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
                else -> {
                    // Standard bars
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
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val isScraping by viewModel.isScraping.collectAsState()

    var name by remember { mutableStateOf(station?.name ?: "") }
    var streamUrl by remember { mutableStateOf(station?.streamUrl ?: "") }
    var selectedGroup by remember { mutableStateOf(station?.group ?: allGenres.firstOrNull() ?: "Lofi") }
    var logoUrl by remember { mutableStateOf(station?.logoUrl ?: "") }
    var country by remember { mutableStateOf(station?.country ?: "") }
    var description by remember { mutableStateOf(station?.description ?: "") }

    var isAddingNewGenre by remember { mutableStateOf(false) }
    var newGenreInput by remember { mutableStateOf("") }
    var logoMode by remember { mutableStateOf("url") } // "url" or "local"

    val imagePickerLauncher = rememberLauncherForActivityResult(
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
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (station == null) "Add New Station" else "Edit Station",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                // Station Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Station Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Stream URL Input with Radio Browser live scraper
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = {
                        streamUrl = it
                        viewModel.queryRadioBrowserSuggestions(it)
                    },
                    label = { Text("Stream URL or Search Station Name") },
                    placeholder = { Text("e.g., love, jazz, bbc, or stream url") },
                    trailingIcon = {
                        if (isScraping) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Scraped Radio Suggestions Autocomplete Dropdown List
                if (searchSuggestions.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "Found from Radio Browser Database (Tap to auto-fill):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            searchSuggestions.take(4).forEach { sug ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            name = sug.name
                                            streamUrl = sug.url
                                            if (!sug.favicon.isNullOrEmpty()) logoUrl = sug.favicon
                                            if (!sug.country.isNullOrEmpty()) country = sug.country
                                            viewModel.clearSuggestions()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Radio, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sug.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                        Text("${sug.country ?: "Global"} • ${sug.codec ?: "MP3"} • ${sug.bitrate ?: 128}kbps", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }

                // Genre Group Selector (Existed List + Add New)
                Text("Genre / Group Category", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allGenres.forEach { grp ->
                        val isSel = selectedGroup == grp && !isAddingNewGenre
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) accentColor else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    selectedGroup = grp
                                    isAddingNewGenre = false
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                grp,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Add new genre chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isAddingNewGenre) accentColor else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { isAddingNewGenre = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "+ New Genre",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAddingNewGenre) Color(0xFF090D16) else accentColor
                        )
                    }
                }

                if (isAddingNewGenre) {
                    OutlinedTextField(
                        value = newGenreInput,
                        onValueChange = { newGenreInput = it },
                        label = { Text("New Genre Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Logo Source: Web URL vs Local Media Picker
                Text("Station Artwork / Logo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = logoMode == "url",
                        onClick = { logoMode = "url" },
                        label = { Text("Web URL") }
                    )
                    FilterChip(
                        selected = logoMode == "local",
                        onClick = {
                            logoMode = "local"
                            imagePickerLauncher.launch("image/*")
                        },
                        label = { Text("Pick Local Image") }
                    )
                }

                if (logoMode == "url") {
                    OutlinedTextField(
                        value = logoUrl,
                        onValueChange = { logoUrl = it },
                        label = { Text("Logo Image URL (HTTPS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (logoUrl.isNotEmpty()) "Local Image Selected" else "No image selected",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                        Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Text("Browse")
                        }
                    }
                }

                // Optional Info
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("Country / Region (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Slogan (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

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
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank() && streamUrl.isNotBlank()) {
                                val finalGroup = if (isAddingNewGenre && newGenreInput.isNotBlank()) {
                                    viewModel.addGenre(newGenreInput.trim())
                                    newGenreInput.trim()
                                } else {
                                    selectedGroup
                                }

                                val s = (station ?: Station(
                                    id = "station_${System.currentTimeMillis()}",
                                    name = name.trim(),
                                    streamUrl = streamUrl.trim(),
                                    group = finalGroup
                                )).copy(
                                    name = name.trim(),
                                    streamUrl = streamUrl.trim(),
                                    group = finalGroup,
                                    logoUrl = logoUrl.ifBlank { null },
                                    country = country.ifBlank { null },
                                    description = description.ifBlank { null }
                                )
                                onSave(s)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF090D16))
                    ) {
                        Text(if (station == null) "Create Station" else "Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Listed Settings Screen (Overhauled with individual subpages)
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
                            null -> "Studio Settings"
                            SettingsSubPage.PLAYER -> "Player & Audio Engine"
                            SettingsSubPage.APPEARANCE -> "Appearance & Themes"
                            SettingsSubPage.SPECTRUM_VISUALIZER -> "Audio Spectrum Visualizer"
                            SettingsSubPage.EQUALIZER -> "Master Equalizer & DSP"
                            SettingsSubPage.GENRE_MANAGER -> "Genre & Group Manager"
                            SettingsSubPage.CLOUD_BACKUP -> "Cloud Sync & Storage"
                            SettingsSubPage.ALARM_CLOCK -> "Wake-Up Radio Alarm"
                            SettingsSubPage.BLUETOOTH -> "Bluetooth & Cast Audio"
                            SettingsSubPage.ABOUT -> "About AetherWave Radio"
                        },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when (uiState.currentSettingsSubPage) {
                null -> SettingsRootList(viewModel = viewModel, accentColor = accentColor)
                SettingsSubPage.PLAYER -> PlayerSettingsSubPage(viewModel = viewModel, accentColor = accentColor)
                SettingsSubPage.APPEARANCE -> AppearanceSettingsSubPage(viewModel = viewModel, accentColor = accentColor)
                SettingsSubPage.SPECTRUM_VISUALIZER -> SpectrumSettingsSubPage(viewModel = viewModel, accentColor = accentColor)
                SettingsSubPage.EQUALIZER -> EqualizerSubPage(viewModel = viewModel, accentColor = accentColor)
                SettingsSubPage.GENRE_MANAGER -> GenreManagerSubPage(viewModel = viewModel, accentColor = accentColor)
                SettingsSubPage.CLOUD_BACKUP -> CloudBackupSubPage(viewModel = viewModel, accentColor = accentColor)
                SettingsSubPage.ALARM_CLOCK -> AlarmClockSubPage(viewModel = viewModel, accentColor = accentColor)
                SettingsSubPage.BLUETOOTH -> BluetoothSubPage(viewModel = viewModel, accentColor = accentColor)
                SettingsSubPage.ABOUT -> AboutSubPage(accentColor = accentColor)
            }
        }
    }
}

// Master Settings Options List
@Composable
fun SettingsRootList(viewModel: RadioViewModel, accentColor: Color) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SettingsOptionRow(
                icon = Icons.Default.Palette,
                title = "Appearance & Themes",
                subtitle = "Themes, Background Color, Material You Monet, Presets",
                accentColor = accentColor,
                onClick = { viewModel.navigateToSubPage(SettingsSubPage.APPEARANCE) }
            )
        }
        item {
            SettingsOptionRow(
                icon = Icons.Default.Bolt,
                title = "Audio Spectrum Visualizer",
                subtitle = "Custom styles, Height, Sensitivity, Color palettes",
                accentColor = accentColor,
                onClick = { viewModel.navigateToSubPage(SettingsSubPage.SPECTRUM_VISUALIZER) }
            )
        }
        item {
            SettingsOptionRow(
                icon = Icons.Default.Tune,
                title = "Master Equalizer & Audio Plugins",
                subtitle = "5-band vertical column EQ, Presets, Bass boost, Virtualizer",
                accentColor = accentColor,
                onClick = { viewModel.navigateToSubPage(SettingsSubPage.EQUALIZER) }
            )
        }
        item {
            SettingsOptionRow(
                icon = Icons.Default.Category,
                title = "Genre & Group Manager",
                subtitle = "Add, edit names, reorder, and batch delete genres",
                accentColor = accentColor,
                onClick = { viewModel.navigateToSubPage(SettingsSubPage.GENRE_MANAGER) }
            )
        }
        item {
            SettingsOptionRow(
                icon = Icons.Default.Alarm,
                title = "Wake-Up Radio Alarm",
                subtitle = "Exact 12-hr carousel clock, auto-play station, background service",
                accentColor = accentColor,
                onClick = { viewModel.navigateToSubPage(SettingsSubPage.ALARM_CLOCK) }
            )
        }
        item {
            SettingsOptionRow(
                icon = Icons.Default.Bluetooth,
                title = "Bluetooth & Audio Output",
                subtitle = "Paired audio devices, auto-resume on connect",
                accentColor = accentColor,
                onClick = { viewModel.navigateToSubPage(SettingsSubPage.BLUETOOTH) }
            )
        }
        item {
            SettingsOptionRow(
                icon = Icons.Default.Cloud,
                title = "Cloud Sync & Backup",
                subtitle = "Sync stations, backup JSON export and import",
                accentColor = accentColor,
                onClick = { viewModel.navigateToSubPage(SettingsSubPage.CLOUD_BACKUP) }
            )
        }
        item {
            SettingsOptionRow(
                icon = Icons.Default.PlayCircle,
                title = "Player & Audio Engine",
                subtitle = "Buffer size, stream quality, audio focus settings",
                accentColor = accentColor,
                onClick = { viewModel.navigateToSubPage(SettingsSubPage.PLAYER) }
            )
        }
        item {
            SettingsOptionRow(
                icon = Icons.Default.Info,
                title = "About AetherWave Radio",
                subtitle = "Version 1.0.0, creator @tehrzky, open-source repo",
                accentColor = accentColor,
                onClick = { viewModel.navigateToSubPage(SettingsSubPage.ABOUT) }
            )
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


// PHASE 3c — Compact tabbed Appearance screen
// Preview pinned on top · Tabs on bottom · Wheel-picker on tap
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val uiState by viewModel.uiState.collectAsState()

    // Local edit state — nothing applied until "Apply" is tapped
    var editingAccent by remember(uiState.themeColorHex) { mutableStateOf(uiState.themeColorHex) }
    var editingBg by remember(uiState.backgroundColorHex) { mutableStateOf(uiState.backgroundColorHex) }
    var editingCard by remember(uiState.cardColorHex) { mutableStateOf(uiState.cardColorHex) }
    var editingFontColor by remember(uiState.themeFontColorHex) { mutableStateOf(uiState.themeFontColorHex) }
    var editingFont by remember(uiState.themeFontStyle) { mutableStateOf(uiState.themeFontStyle) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Colors, 1 = Font, 2 = Presets
    var pickerTarget by remember { mutableStateOf<String?>(null) } // "accent" | "bg" | "card" | "font"
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    val activeFont = resolveFontFamily(editingFont)
    val previewAccent = parseHexColor(editingAccent)
    val previewBg = parseHexColor(editingBg)
    val previewCard = parseHexColor(editingCard)
    val previewFontColor = if (editingFontColor.equals("auto", ignoreCase = true))
        if (previewCard.luminance() > 0.5f) Color(0xFF0F172A) else Color(0xFFF1F5F9)
    else parseHexColor(editingFontColor, Color(0xFFF1F5F9))

    val changesMade = editingAccent != uiState.themeColorHex ||
            editingBg != uiState.backgroundColorHex ||
            editingCard != uiState.cardColorHex ||
            editingFontColor != uiState.themeFontColorHex ||
            editingFont != uiState.themeFontStyle

    fun applyAll() {
        viewModel.setThemeColor(editingAccent)
        viewModel.setBackgroundColor(editingBg)
        viewModel.setCardColor(editingCard)
        viewModel.setFontColor(editingFontColor)
        viewModel.setFontStyle(editingFont)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---------- PINNED PREVIEW ----------
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = previewBg),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "LIVE PREVIEW",
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                        color = previewAccent,
                        fontFamily = activeFont
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(previewAccent.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (editingFontColor.equals("auto", ignoreCase = true)) "AUTO" else "MANUAL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = previewAccent,
                            fontFamily = activeFont
                        )
                    }
                }
                Text(
                    "AetherWave Radio",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = activeFont,
                    color = previewFontColor
                )
                Text(
                    "This is how your app will look",
                    fontSize = 12.sp,
                    fontFamily = activeFont,
                    color = previewFontColor.copy(alpha = 0.75f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(previewAccent)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text("Play", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = activeFont, color = Color(0xFF090D16))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(previewCard)
                            .border(1.dp, previewFontColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text("Station", fontSize = 12.sp, fontFamily = activeFont, color = previewFontColor)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------- TAB ROW ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(18.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Colors", "Font", "Presets").forEachIndexed { idx, label ->
                val sel = selectedTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) previewAccent else Color.Transparent)
                        .clickable { selectedTab = idx }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = activeFont,
                        color = if (sel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---------- TAB CONTENT ----------
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                // ============= COLORS TAB =============
                0 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModernColorRow(
                        label = "Accent",
                        hex = editingAccent,
                        fontStyle = editingFont,
                        onTap = { pickerTarget = "accent" }
                    )
                    ModernColorRow(
                        label = "Background",
                        hex = editingBg,
                        fontStyle = editingFont,
                        onTap = { pickerTarget = "bg" }
                    )
                    ModernColorRow(
                        label = "Cards",
                        hex = editingCard,
                        fontStyle = editingFont,
                        onTap = { pickerTarget = "card" }
                    )
                    ModernColorRow(
                        label = "Font Color",
                        hex = editingFontColor,
                        fontStyle = editingFont,
                        isAuto = editingFontColor.equals("auto", ignoreCase = true),
                        onTap = { pickerTarget = "font" },
                        onAutoReset = { editingFontColor = "auto" }
                    )
                }

                // ============= FONT TAB =============
                1 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Font Family", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = activeFont, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("sans" to "Sans", "serif" to "Serif", "mono" to "Mono").forEach { (key, label) ->
                            val sel = editingFont == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (sel) previewAccent else MaterialTheme.colorScheme.surface)
                                    .border(1.dp, if (sel) previewAccent else Color(0x15FFFFFF), RoundedCornerShape(14.dp))
                                    .clickable { editingFont = key }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = resolveFontFamily(key),
                                    color = if (sel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Sample", fontSize = 10.sp, letterSpacing = 1.sp, color = Color.Gray, fontFamily = activeFont)
                            Text("The quick brown fox jumps over the lazy dog", fontSize = 14.sp, fontFamily = activeFont, color = MaterialTheme.colorScheme.onSurface)
                            Text("1234567890 · AetherWave Studio", fontSize = 12.sp, fontFamily = activeFont, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ============= PRESETS TAB =============
                2 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Quick Presets", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = activeFont, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactPresetChip("Studio Amber", "#F59E0B", "#090D16", "#111726", editingAccent, editingBg, editingCard, activeFont) {
                            editingAccent = "#F59E0B"; editingBg = "#090D16"; editingCard = "#111726"
                        }
                        CompactPresetChip("Cyber Cyan", "#06B6D4", "#000000", "#0F172A", editingAccent, editingBg, editingCard, activeFont) {
                            editingAccent = "#06B6D4"; editingBg = "#000000"; editingCard = "#0F172A"
                        }
                        CompactPresetChip("Emerald", "#10B981", "#0A1D1A", "#0E2420", editingAccent, editingBg, editingCard, activeFont) {
                            editingAccent = "#10B981"; editingBg = "#0A1D1A"; editingCard = "#0E2420"
                        }
                        CompactPresetChip("Magenta", "#EC4899", "#1A0A18", "#2A0F26", editingAccent, editingBg, editingCard, activeFont) {
                            editingAccent = "#EC4899"; editingBg = "#1A0A18"; editingCard = "#2A0F26"
                        }
                        CompactPresetChip("Pearl", "#F59E0B", "#F8FAFC", "#FFFFFF", editingAccent, editingBg, editingCard, activeFont) {
                            editingAccent = "#F59E0B"; editingBg = "#F8FAFC"; editingCard = "#FFFFFF"
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("My Saved Presets", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = activeFont, color = MaterialTheme.colorScheme.onSurface)
                        TextButton(onClick = { presetNameInput = ""; showSaveDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = previewAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Current", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = previewAccent, fontFamily = activeFont)
                        }
                    }

                    if (uiState.savedThemePresets.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("No saved presets yet", fontSize = 11.sp, color = Color.Gray, fontFamily = activeFont)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.savedThemePresets.forEach { preset ->
                                CompactSavedPreset(
                                    preset = preset,
                                    isActive = preset.id == uiState.activeThemePresetId,
                                    fontStyle = editingFont,
                                    onApply = {
                                        viewModel.applyPreset(preset.id)
                                        editingAccent = preset.accentHex
                                        editingBg = preset.backgroundHex
                                        editingCard = preset.cardHex
                                        editingFontColor = preset.fontColorHex
                                        editingFont = preset.fontStyle
                                    },
                                    onDelete = { viewModel.deletePreset(preset.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- PINNED ACTIONS ----------
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    editingAccent = uiState.themeColorHex
                    editingBg = uiState.backgroundColorHex
                    editingCard = uiState.cardColorHex
                    editingFontColor = uiState.themeFontColorHex
                    editingFont = uiState.themeFontStyle
                },
                enabled = changesMade,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Reset", fontSize = 13.sp, fontFamily = activeFont, color = MaterialTheme.colorScheme.onSurface)
            }
            Button(
                onClick = { applyAll() },
                enabled = changesMade,
                colors = ButtonDefaults.buttonColors(
                    containerColor = previewAccent,
                    contentColor = Color(0xFF090D16),
                    disabledContainerColor = Color(0x22FFFFFF),
                    disabledContentColor = Color(0x66FFFFFF)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1.5f).height(48.dp)
            ) {
                Text(
                    if (changesMade) "Apply Changes" else "No Changes",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = activeFont
                )
            }
        }
    }

    // ---------- COLOR PICKER DIALOG ----------
    pickerTarget?.let { target ->
        val currentHex = when (target) {
            "accent" -> editingAccent
            "bg" -> editingBg
            "card" -> editingCard
            "font" -> if (editingFontColor.equals("auto", ignoreCase = true)) editingFontColor.let { "auto" } else editingFontColor
            else -> "#000000"
        }
        val startHex = if (currentHex.equals("auto", ignoreCase = true)) "#F1F5F9" else currentHex
        val title = when (target) {
            "accent" -> "Pick Accent Color"
            "bg" -> "Pick Background Color"
            "card" -> "Pick Card Color"
            "font" -> "Pick Font Color"
            else -> "Pick Color"
        }
        ColorPickerDialog(
            title = title,
            initialHex = startHex,
            onDismiss = { pickerTarget = null },
            onPick = { newHex ->
                when (target) {
                    "accent" -> editingAccent = newHex
                    "bg" -> editingBg = newHex
                    "card" -> editingCard = newHex
                    "font" -> editingFontColor = newHex
                }
                pickerTarget = null
            }
        )
    }

    // ---------- SAVE PRESET DIALOG ----------
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Theme Preset", fontFamily = activeFont) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Give this theme a name:", fontSize = 12.sp, color = Color.Gray, fontFamily = activeFont)
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        placeholder = { Text("e.g. My Night Theme", fontFamily = activeFont) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Saves: accent, background, cards, font, font color.",
                        fontSize = 10.sp,
                        color = Color.DarkGray,
                        fontFamily = activeFont
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        applyAll()
                        viewModel.saveCurrentAsPreset(presetNameInput)
                        showSaveDialog = false
                    },
                    enabled = presetNameInput.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = previewAccent, contentColor = Color(0xFF090D16))
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, fontFamily = activeFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = Color.Gray, fontFamily = activeFont)
                }
            }
        )
    }
}

// ---------- Compact helpers ----------

@Composable
private fun ModernColorRow(
    label: String,
    hex: String,
    fontStyle: String,
    isAuto: Boolean = false,
    onTap: () -> Unit,
    onAutoReset: (() -> Unit)? = null
) {
    val isAutoMode = isAuto || hex.equals("auto", ignoreCase = true)
    val preview = if (isAutoMode) Color(0x66FFFFFF) else parseHexColor(hex)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(preview)
                    .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isAutoMode) {
                    Text("A", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A), fontFamily = resolveFontFamily(fontStyle))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = resolveFontFamily(fontStyle), color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (isAutoMode) "Auto (theme-based)" else hex.uppercase(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray
                )
            }
            if (isAutoMode && onAutoReset == null) {
                // nothing
            } else if (onAutoReset != null) {
                if (isAutoMode) {
                    Text("AUTO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                } else {
                    TextButton(onClick = onAutoReset, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("Auto", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CompactPresetChip(
    name: String,
    accent: String,
    bg: String,
    card: String,
    currentAccent: String,
    currentBg: String,
    currentCard: String,
    fontFamily: FontFamily,
    onClick: () -> Unit
) {
    val isActive = currentAccent == accent && currentBg == bg && currentCard == card
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) parseHexColor(accent).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (isActive) parseHexColor(accent) else Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(modifier = Modifier.size(18.dp, 12.dp).clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)).background(parseHexColor(accent)))
            Box(modifier = Modifier.size(18.dp, 12.dp).clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)).background(parseHexColor(bg)))
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(modifier = Modifier.size(38.dp, 10.dp).clip(RoundedCornerShape(4.dp)).background(parseHexColor(card)))
        Spacer(modifier = Modifier.height(5.dp))
        Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontFamily = fontFamily)
    }
}

@Composable
private fun CompactSavedPreset(
    preset: SavedThemePreset,
    isActive: Boolean,
    fontStyle: String,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) parseHexColor(preset.accentHex).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isActive) 1.5.dp else 1.dp, if (isActive) parseHexColor(preset.accentHex) else Color(0x15FFFFFF), RoundedCornerShape(14.dp))
            .clickable { onApply() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(modifier = Modifier.size(18.dp).clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)).background(parseHexColor(preset.accentHex)))
                Box(modifier = Modifier.size(18.dp).clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)).background(parseHexColor(preset.backgroundHex)))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = resolveFontFamily(preset.fontStyle), color = MaterialTheme.colorScheme.onSurface)
                    if (isActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(parseHexColor(preset.accentHex))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("ACTIVE", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFF090D16))
                        }
                    }
                }
                Text("${preset.fontStyle} · ${if (preset.fontColorHex == "auto") "auto text" else preset.fontColorHex}", fontSize = 10.sp, color = Color.Gray, fontFamily = resolveFontFamily(preset.fontStyle))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0x88EF4444), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// 2. Audio Spectrum Visualizer Settings Subpage
@Composable
fun SpectrumSettingsSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val uiState by viewModel.uiState.collectAsState()

    val styles = listOf("Bars", "Mirrored", "Columns")
    val colors = listOf("Accent", "Rainbow", "CyanAmber", "NeonEmerald", "PurpleGlow")
    val speeds = listOf("Smooth", "Normal", "Energetic")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Live Preview Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF070B14)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Live Preview", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(uiState.spectrumHeight.dp)
                ) {
                    CustomizableAudioSpectrumCanvas(
                        isPlaying = true,
                        accentColor = accentColor,
                        style = uiState.spectrumStyle,
                        sensitivity = uiState.spectrumSensitivity,
                        colorScheme = uiState.spectrumColorScheme,
                        speed = uiState.spectrumSpeed
                    )
                }
            }
        }

        // Spectrum Style
        Text("Spectrum Visualizer Style", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            styles.forEach { style ->
                val isSel = uiState.spectrumStyle == style
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) accentColor else MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.setSpectrumSettings(style = style) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(style, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Height Slider
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Height (${uiState.spectrumHeight} dp)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = uiState.spectrumHeight.toFloat(),
                onValueChange = { viewModel.setSpectrumSettings(height = it.toInt()) },
                valueRange = 50f..140f,
                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
            )
        }

        // Sensitivity Slider
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sensitivity (${String.format("%.1f", uiState.spectrumSensitivity)}x)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = uiState.spectrumSensitivity,
                onValueChange = { viewModel.setSpectrumSettings(sensitivity = it) },
                valueRange = 0.5f..2.5f,
                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
            )
        }

        // Color Palette
        Text("Color Scheme", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            colors.forEach { c ->
                val isSel = uiState.spectrumColorScheme == c
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) accentColor else MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.setSpectrumSettings(colorScheme = c) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(c, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Speed / Reactivity
        Text("Reactivity Speed", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            speeds.forEach { s ->
                val isSel = uiState.spectrumSpeed == s
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) accentColor else MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.setSpectrumSettings(speed = s) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(s, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// 3. Master Equalizer Subpage (FULLY INTERACTIVE 5-BAND DSP EQUALIZER)
@Composable
fun EqualizerSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val uiState by viewModel.uiState.collectAsState()
    val bandInfo = listOf(
        "60 Hz" to "Sub Bass",
        "230 Hz" to "Low Mids",
        "910 Hz" to "Midrange",
        "3.6 kHz" to "Presence",
        "14 kHz" to "Brilliance"
    )
    val presets = listOf("Flat", "Bass Boost", "Vocal Clarity", "Club/Dance", "Rock", "Classical", "Jazz")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Presets Chips & Reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Equalizer Presets", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            TextButton(
                onClick = { viewModel.setEqPreset("Flat") },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset Flat", fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
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
                    Text(
                        preset,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 5-BAND FLUID INTERACTIVE EQUALIZER
        Text("5-Band DSP Audio Equalizer (-12 dB to +12 dB)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                uiState.eqBands.forEachIndexed { index, gain ->
                    val (freq, desc) = bandInfo.getOrElse(index) { "$index Hz" to "Band" }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF090D16))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(freq, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("($desc)", fontSize = 11.sp, color = Color.Gray)
                            }

                            // Dynamic gain chip with fine-tune steppers
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.setEqBand(index, gain - 1f) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Step down", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (gain != 0f) accentColor.copy(alpha = 0.2f) else Color(0x18FFFFFF))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        "${if (gain > 0) "+" else ""}${String.format("%.1f", gain)} dB",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (gain != 0f) accentColor else Color.Gray,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.setEqBand(index, gain + 1f) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Step up", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        // Fluid, smooth continuous Slider (unlocked, full freedom of movement)
                        Slider(
                            value = gain,
                            onValueChange = { viewModel.setEqBand(index, it) },
                            valueRange = -12f..12f,
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = Color(0x30FFFFFF)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        )

                        // Quick Snap dB Markers
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(-12f, -6f, 0f, 6f, 12f).forEach { snapVal ->
                                val isCurrentVal = (gain - snapVal) in -0.5f..0.5f
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isCurrentVal) accentColor.copy(alpha = 0.25f) else Color(0x12FFFFFF))
                                        .clickable { viewModel.setEqBand(index, snapVal) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "${if (snapVal > 0) "+" else ""}${snapVal.toInt()}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentVal) accentColor else Color.Gray,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Audio Effects Plugins
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

// 4. Genre Group Manager Subpage (Overhauled: Add, Arrange, Select all on one row, no tall wrapping!)
@Composable
fun GenreManagerSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val uiState by viewModel.uiState.collectAsState()
    val genres by viewModel.genres.collectAsState()
    var newGenreText by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Description Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Genre & Category Manager", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Organize, reorder, or batch delete custom radio groups", fontSize = 11.sp, color = Color.Gray)
            }
        }

        // UNIFIED ACTION ROW: Input + Add + Arrange + Select (No Wrapping!)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = newGenreText,
                    onValueChange = { newGenreText = it },
                    placeholder = { Text("Genre...", fontSize = 12.sp) },
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
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF090D16)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.width(66.dp).height(38.dp)
                ) {
                    Text("+ Add", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Button(
                    onClick = { viewModel.toggleGenreArrangeMode() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isGenreArrangeMode) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (uiState.isGenreArrangeMode) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    modifier = Modifier.width(76.dp).height(38.dp)
                ) {
                    Icon(if (uiState.isGenreArrangeMode) Icons.Default.Check else Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(if (uiState.isGenreArrangeMode) "Done" else "Arrange", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { viewModel.toggleGenreSelectMode() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isGenreSelectMode) Color(0xFFEF4444) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (uiState.isGenreSelectMode) Color.White else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    modifier = Modifier.width(76.dp).height(38.dp)
                ) {
                    Icon(if (uiState.isGenreSelectMode) Icons.Default.Close else Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(if (uiState.isGenreSelectMode) "Done" else "Select", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Batch Delete Bar when in Select Mode
        if (uiState.isGenreSelectMode && uiState.selectedGenresForDeletion.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${uiState.selectedGenresForDeletion.size} genre(s) selected",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Delete Selected", fontWeight = FontWeight.Bold)
                    }
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isGenreSelectMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { viewModel.toggleGenreSelection(g) },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(g, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))

                    if (uiState.isGenreArrangeMode) {
                        var dragYOffset by remember { mutableFloatStateOf(0f) }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .pointerInput(g) {
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragYOffset += dragAmount.y
                                            val currentIndex = genres.indexOf(g)
                                            if (dragYOffset > 38f && currentIndex in 0 until genres.size - 1) {
                                                viewModel.reorderGenres(currentIndex, currentIndex + 1)
                                                dragYOffset -= 38f
                                            } else if (dragYOffset < -38f && currentIndex > 0) {
                                                viewModel.reorderGenres(currentIndex, currentIndex - 1)
                                                dragYOffset += 38f
                                            }
                                        },
                                        onDragEnd = { dragYOffset = 0f },
                                        onDragCancel = { dragYOffset = 0f }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder genre",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Selected Genres?") },
            text = { Text("Are you sure you want to delete ${uiState.selectedGenresForDeletion.size} genre categories? Stations belonging to these genres will be kept.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedGenres()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

// 5. Wake-Up Alarm Clock Subpage (Direct Time Selection, System Dial, Presets & Background Scheduling)
@Composable
fun AlarmClockSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val stations by viewModel.stations.collectAsState()

    var enabled by remember { mutableStateOf(uiState.alarmEnabled) }
    var hour24 by remember { mutableStateOf(uiState.alarmHour) }
    var minute by remember { mutableStateOf(uiState.alarmMinute) }
    var selectedStationId by remember { mutableStateOf(uiState.alarmStationId ?: stations.firstOrNull()?.id) }

    var isAm by remember { mutableStateOf(hour24 < 12) }
    var hour12 by remember {
        val h = hour24 % 12
        mutableStateOf(if (h == 0) 12 else h)
    }

    fun updateAlarmTime(h12: Int, min: Int, am: Boolean) {
        val h24 = when {
            am && h12 == 12 -> 0
            am -> h12
            !am && h12 == 12 -> 12
            else -> h12 + 12
        }
        hour24 = h24
        minute = min
        hour12 = h12
        isAm = am
        viewModel.setAlarm(enabled, h24, min, selectedStationId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
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
                        Text("Radio Alarm Clock", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Survives background & screen-off via AlarmManager", fontSize = 11.sp, color = Color.Gray)
                    }

                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            viewModel.setAlarm(it, hour24, minute, selectedStationId)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.3f))
                    )
                }

                // Digital Clock Display with Direct Time Indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF090D16))
                        .padding(vertical = 18.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                String.format("%02d:%02d", hour12, minute),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (enabled) accentColor else Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            // AM / PM Toggle Pills
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isAm) accentColor else Color(0x20FFFFFF))
                                        .clickable { updateAlarmTime(hour12, minute, true) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text("AM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isAm) Color(0xFF090D16) else Color.White)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (!isAm) accentColor else Color(0x20FFFFFF))
                                        .clickable { updateAlarmTime(hour12, minute, false) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text("PM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (!isAm) Color(0xFF090D16) else Color.White)
                                }
                            }
                        }

                        // Open System Android Clock Dial Button
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, selectedH24, selectedM ->
                                        val am = selectedH24 < 12
                                        val h = selectedH24 % 12
                                        val h12 = if (h == 0) 12 else h
                                        updateAlarmTime(h12, selectedM, am)
                                    },
                                    hour24,
                                    minute,
                                    false
                                ).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Clock Dial", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick Wake-Up Presets
                Text("Quick Wake Presets", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple(6, 0, true) to "6:00 AM",
                        Triple(6, 30, true) to "6:30 AM",
                        Triple(7, 0, true) to "7:00 AM",
                        Triple(7, 30, true) to "7:30 AM",
                        Triple(8, 0, true) to "8:00 AM",
                        Triple(8, 30, true) to "8:30 AM"
                    ).forEach { (timeTuple, label) ->
                        val (h, m, am) = timeTuple
                        val isSel = hour12 == h && minute == m && isAm == am
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) accentColor else Color(0x15FFFFFF))
                                .clickable { updateAlarmTime(h, m, am) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // Direct Hour Selector (1-12)
                Text("Select Hour", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..12).forEach { h ->
                        val isSel = hour12 == h
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSel) accentColor else Color(0x18FFFFFF))
                                .clickable { updateAlarmTime(h, minute, isAm) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$h", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // Direct Minute Selector (:00, :05, :10, :15, :20, :25, :30, :35, :40, :45, :50, :55)
                Text("Select Minute", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55).forEach { m ->
                        val isSel = minute == m
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) accentColor else Color(0x18FFFFFF))
                                .clickable { updateAlarmTime(hour12, m, isAm) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(String.format(":%02d", m), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // Station Selector Card
                Text("Wake-Up Radio Broadcast Station", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)

                val currentSelectedSt = stations.firstOrNull { it.id == selectedStationId } ?: stations.firstOrNull()
                var showStationDropdown by remember { mutableStateOf(false) }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStationDropdown = true }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!currentSelectedSt?.logoUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = currentSelectedSt?.logoUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.Radio, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentSelectedSt?.name ?: "Select Station", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(currentSelectedSt?.group ?: "All", fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                        }

                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = accentColor)
                    }

                    DropdownMenu(
                        expanded = showStationDropdown,
                        onDismissRequest = { showStationDropdown = false }
                    ) {
                        stations.forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st.name, fontSize = 13.sp) },
                                onClick = {
                                    selectedStationId = st.id
                                    viewModel.setAlarm(enabled, hour24, minute, st.id)
                                    showStationDropdown = false
                                }
                            )
                        }
                    }
                }

                // Test Alarm in 5 Seconds Button
                Button(
                    onClick = {
                        viewModel.testAlarmInSeconds(5)
                        Toast.makeText(context, "Alarm scheduled in 5s! You can turn off the screen now to test.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF090D16)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Alarm Now (5 Seconds)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// 6. Bluetooth Subpage
@Composable
fun BluetoothSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val isAvailable: Boolean by viewModel.bluetoothHelper.isAvailable.collectAsState()
    val isEnabled: Boolean by viewModel.bluetoothHelper.isEnabled.collectAsState()
    val pairedDevices: List<BluetoothDeviceInfo> by viewModel.bluetoothHelper.pairedDevices.collectAsState()
    val connectedDevice: BluetoothDeviceInfo? by viewModel.bluetoothHelper.connectedDevice.collectAsState()

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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bluetooth Audio Output", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (!isAvailable) "Bluetooth adapter not detected."
                    else if (!isEnabled) "Bluetooth is turned off on device."
                    else "Active Audio Output: ${connectedDevice?.name ?: "Phone Internal Speaker"}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        Text("Paired Bluetooth Audio Devices", fontSize = 14.sp, fontWeight = FontWeight.Bold)

        if (pairedDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No paired Bluetooth headsets or speakers found.", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            pairedDevices.forEach { dev: BluetoothDeviceInfo ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dev.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(dev.address, fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// 7. Cloud Backup Subpage
@Composable
fun CloudBackupSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val stations by viewModel.stations.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Station Library Stats Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Station Library Stats", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Total radio stations saved: ${stations.size}", fontSize = 12.sp, color = Color.LightGray)
                Text("Favorited broadcasts: ${stations.count { it.isFavorite }}", fontSize = 12.sp, color = Color.LightGray)
            }
        }

        // Backup Export & Import Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Backup & Restore Stations (JSON)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Export all radio presets and custom added streams, or import back anytime.", fontSize = 11.sp, color = Color.Gray)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val json = viewModel.exportStationsJson()
                            clipboardManager.setText(AnnotatedString(json))
                            Toast.makeText(context, "Copied backup JSON (${stations.size} stations) to clipboard!", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF090D16)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Backup", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Button(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Backup", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                // Restore Defaults
                OutlinedButton(
                    onClick = { showRestoreConfirmDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore Default Stations Library", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Cloud Room Sync Code Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cloud Room Sync Code", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Sync your curated presets across devices using this 6-character room token.", fontSize = 11.sp, color = Color.Gray)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF090D16))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(uiState.syncRoomCode, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, color = accentColor)
                    }

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(uiState.syncRoomCode))
                            Toast.makeText(context, "Sync Room Code copied!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF090D16)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.width(80.dp).height(40.dp)
                    ) {
                        Text("Copy", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    IconButton(
                        onClick = { viewModel.generateSyncRoomCode() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = Color.Gray)
                    }
                }
            }
        }

        // Import Dialog
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("Import Station Backup") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Paste your exported JSON stations list below:", fontSize = 12.sp, color = Color.Gray)
                        OutlinedTextField(
                            value = importText,
                            onValueChange = { importText = it },
                            placeholder = { Text("[{\"id\":\"1\", \"name\":\"...\"}]", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (importText.isNotBlank()) {
                                val success = viewModel.importStationsJson(importText.trim())
                                if (success) {
                                    Toast.makeText(context, "Successfully restored stations!", Toast.LENGTH_LONG).show()
                                    showImportDialog = false
                                    importText = ""
                                } else {
                                    Toast.makeText(context, "Invalid JSON backup format", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF090D16))
                    ) {
                        Text("Import & Merge")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }

        // Restore Confirm Dialog
        if (showRestoreConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false },
                title = { Text("Restore Default Stations?") },
                text = { Text("This will reset your station list back to the default curated broadcasts.", fontSize = 13.sp) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.restoreDefaultStations()
                            Toast.makeText(context, "Restored default stations", Toast.LENGTH_SHORT).show()
                            showRestoreConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Restore Defaults")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirmDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }
}

// 8. Player Engine Subpage
@Composable
fun PlayerSettingsSubPage(viewModel: RadioViewModel, accentColor: Color) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Stream Engine Architecture
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Streaming Engine", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("ExoPlayer 3", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accentColor)
                    }
                }
                Text("Hardware accelerated MPEG, AAC, Ogg, and HLS .m3u8 live stream decoding with adaptive audio buffering.", fontSize = 11.sp, color = Color.Gray)
            }
        }

        // Buffer Profile Setting
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Audio Buffer Profile", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Configure stream pre-fetch buffer to balance switching speed vs cellular stability.", fontSize = 11.sp, color = Color.Gray)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Low", "Medium", "High").forEach { size ->
                        val isSel = uiState.bufferSize.equals(size, ignoreCase = true)
                        val label = when (size) {
                            "Low" -> "Fast (5s)"
                            "High" -> "Stable (30s)"
                            else -> "Balanced (15s)"
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) accentColor else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewModel.setBufferSize(size) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // Audio Focus & Protection Toggles
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Audio Focus & Hardware Handling", fontSize = 14.sp, fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pause on Headset Disconnect", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Immediately halts audio when wired or Bluetooth earphones unplug", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = uiState.stopOnHeadsetDisconnect,
                        onCheckedChange = { viewModel.setStopOnHeadsetDisconnect(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.3f))
                    )
                }

                HorizontalDivider(color = Color(0x15FFFFFF))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Resume on Bluetooth", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Automatically resume last station when trusted car/headset reconnects", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = uiState.autoResumeOnBluetooth,
                        onCheckedChange = { viewModel.setAutoResumeOnBluetooth(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.3f))
                    )
                }

                HorizontalDivider(color = Color(0x15FFFFFF))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Prefer High-Quality Audio (320k)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Request highest bitrate audio chunks when multi-bitrate HLS streams exist", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = uiState.highQualityAudio,
                        onCheckedChange = { viewModel.setHighQualityAudio(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

// 9. About Subpage
@Composable
fun AboutSubPage(accentColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(accentColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Radio, contentDescription = null, tint = Color(0xFF090D16), modifier = Modifier.size(40.dp))
        }

        Text("AetherWave Radio", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text("Version 1.0.0 (Native Android Edition)", fontSize = 12.sp, color = Color.Gray)

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Developer & Maintainer", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("tehrzky", fontSize = 14.sp, color = accentColor, fontWeight = FontWeight.Bold)
                Text("GitHub: https://github.com/tehrzky/AetherWaveRadio", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

// Equalizer Modal Dialog (Fixed: No nested scroll crash!)
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Equalizer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Box(modifier = Modifier.weight(1f, fill = false)) {
                    EqualizerSubPage(viewModel, accentColor)
                }
            }
        }
    }
}

// Wake-up Alarm Clock Modal Dialog (Fixed: Bounded height prevents scroll measurement crash!)
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Wake-Up Radio Alarm", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Box(modifier = Modifier.weight(1f, fill = false)) {
                    AlarmClockSubPage(viewModel, accentColor)
                }
            }
        }
    }
}

// Sleep Timer Dialog (Fixed: No nested scroll crash!)
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
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sleep Timer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                if (timerState.isActive) {
                    val minutesLeft = (timerState.remainingSeconds / 60)
                    val secondsLeft = (timerState.remainingSeconds % 60)

                    Text(
                        String.format("%02d:%02d", minutesLeft, secondsLeft),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { viewModel.sleepTimerManager.cancelTimer() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stop Sleep Timer")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 45, 60).forEach { mins ->
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

                    Button(
                        onClick = {
                            viewModel.sleepTimerManager.startTimer(selectedMins, fadeOut = true)
                            onClose()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF090D16)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start ${selectedMins} Min Timer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
