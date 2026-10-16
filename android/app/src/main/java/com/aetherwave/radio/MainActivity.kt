package com.aetherwave.radio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aetherwave.radio.data.Station
import com.aetherwave.radio.data.ViewMode
import com.aetherwave.radio.ui.RadioViewModel
import com.aetherwave.radio.ui.theme.AetherWaveRadioTheme
import com.aetherwave.radio.ui.theme.AmberPrimary
import com.aetherwave.radio.ui.theme.DarkBackground
import com.aetherwave.radio.ui.theme.DarkSurface

class MainActivity : ComponentActivity() {

    private val viewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AetherWaveRadioTheme {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AmberPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Radio,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "AetherWave Radio",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    // Grid / List toggle
                    IconButton(onClick = {
                        val newMode = if (uiState.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                        viewModel.setViewMode(newMode)
                    }) {
                        Icon(
                            if (uiState.viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle view",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        bottomBar = {
            // Material 3 Expressive Floating Mini Player Bar
            uiState.currentStation?.let { current ->
                MiniPlayerBar(
                    station = current,
                    isPlaying = uiState.isPlaying,
                    onPlayPause = {
                        if (uiState.isPlaying) viewModel.pausePlayback() else viewModel.resumePlayback()
                    },
                    onFavorite = { viewModel.toggleFavorite(current.id) }
                )
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search stations, genres...", color = Color.Gray, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = AmberPrimary,
                    unfocusedBorderColor = Color(0x22FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            // Content Grid or List
            if (uiState.viewMode == ViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(stations, key = { it.id }) { station ->
                        StationGridCard(
                            station = station,
                            isCurrent = uiState.currentStation?.id == station.id,
                            isPlaying = uiState.currentStation?.id == station.id && uiState.isPlaying,
                            onPlay = { viewModel.playStation(station) },
                            onToggleFavorite = { viewModel.toggleFavorite(station.id) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(stations, key = { it.id }) { station ->
                        StationListRow(
                            station = station,
                            isCurrent = uiState.currentStation?.id == station.id,
                            isPlaying = uiState.currentStation?.id == station.id && uiState.isPlaying,
                            onPlay = { viewModel.playStation(station) },
                            onToggleFavorite = { viewModel.toggleFavorite(station.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StationGridCard(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) Color(0xFF1E283C) else DarkSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
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
                    Icon(Icons.Default.Radio, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                }

                // Play overlay
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isCurrent && isPlaying) AmberPrimary else Color(0x66000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isCurrent && isPlaying) Color.Black else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = station.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isCurrent) AmberPrimary else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = station.group,
                fontSize = 11.sp,
                color = Color.LightGray,
                maxLines = 1
            )
        }
    }
}

@Composable
fun StationListRow(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) Color(0xFF1E283C) else DarkSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
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
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isCurrent) AmberPrimary else Color.White,
                    maxLines = 1
                )
                Text(
                    text = "${station.group} • ${station.codec} • ${station.bitrate ?: "128 kbps"}",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    maxLines = 1
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = if (station.isFavorite) AmberPrimary else Color.Gray
                )
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    station: Station,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onFavorite: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121929)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black),
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
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    station.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    station.group,
                    fontSize = 11.sp,
                    color = AmberPrimary,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AmberPrimary)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black
                )
            }
        }
    }
}
