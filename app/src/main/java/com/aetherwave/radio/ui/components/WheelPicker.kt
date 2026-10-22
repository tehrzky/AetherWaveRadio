package com.aetherwave.radio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.abs

@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    isCarousel: Boolean = true
) {
    if (items.isEmpty()) return

    val n = items.size
    val visibleCount = 3 // 3 is the cleanest math for centering (1 above, 1 center, 1 below)
    val itemHeight = 44.dp

    val listState = rememberLazyListState()

    // If not carousel, we just use the original items.
    val listItems = if (isCarousel) remember(items) { List(3) { items }.flatten() } else items
    val baseOffset = if (isCarousel) n else 0

    // Initial scroll
    LaunchedEffect(Unit) {
        val initialIndex = baseOffset + selectedIndex
        listState.scrollToItem(initialIndex)
    }

    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    // Handle scroll state changes
    LaunchedEffect(listState, n, isCarousel) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .map { visibleIndex -> visibleIndex + 1 } // Center item is +1 because visibleCount=3
            .distinctUntilChanged()
            .collect { centerIndex ->
                val realIndex = if (isCarousel) {
                    val mod = centerIndex % n
                    if (mod < 0) mod + n else mod
                } else {
                    centerIndex.coerceIn(0, n - 1)
                }
                onItemSelected(realIndex)
            }
    }

    // Handle external selectedIndex changes (e.g. from AM/PM toggle)
    LaunchedEffect(selectedIndex) {
        if (isCarousel) {
            val currentCenter = listState.firstVisibleItemIndex + 1
            val currentReal = (currentCenter % n + n) % n
            if (currentReal != selectedIndex) {
                // Find the closest tripled index
                val targetBase = (currentCenter / n) * n
                listState.animateScrollToItem(targetBase + selectedIndex)
            }
        } else {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center
    ) {
        // Background highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.15f))
        )

        LazyColumn(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight), // 1 item height padding top & bottom
            modifier = Modifier.fillMaxSize()
        ) {
            items(listItems.size) { i ->
                val center = listState.firstVisibleItemIndex + 1
                val dist = abs(i - center)
                val isSelected = dist == 0

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        listItems[i],
                        fontSize = if (isSelected) 20.sp else 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(if (dist <= 1) 1f else 0f)
                    )
                }
            }
        }
    }
}
