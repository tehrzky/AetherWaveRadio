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

/**
 * Infinite-loop carousel picker. Used for alarm hour/minute/AM-PM and
 * sleep timer hour/minute wheels.
 *
 * Internally the items list is tripled so scrolling past the ends wraps
 * to the start without a hitch. Reports back the ORIGINAL index (0..items.size-1).
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    if (items.isEmpty()) return

    val n = items.size
    // Odd visible count keeps the center math clean.
    val visibleCount = 5
    val halfVisible = (visibleCount - 1) / 2 // 2
    val itemHeight = 44.dp

    // Tripled items so scroll can loop indefinitely.
    val tripled: List<String> = remember(items) {
        buildList { repeat(3) { addAll(items) } }
    }
    // Start in the middle third, at the selected item.
    val startOffset = remember { n * 1 } // one full copy in

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (startOffset + selectedIndex).coerceAtLeast(0)
    )
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    // Track last-known real index so we only fire onItemSelected when it changes.
    var lastReal by remember { mutableIntStateOf(selectedIndex) }

    // Listen to scroll: compute which real index is centered.
    LaunchedEffect(listState, n) {
        snapshotFlow { listState.firstVisibleItemIndex + halfVisible }
            .map { globalIndex ->
                // global index in the tripled list → real 0..n-1
                ((globalIndex % n) + n) % n
            }
            .distinctUntilChanged()
            .collect { realIdx ->
                if (realIdx != lastReal) {
                    lastReal = realIdx
                    onItemSelected(realIdx)
                }
            }
    }

    // When external selectedIndex changes, gently scroll to it.
    LaunchedEffect(selectedIndex, n) {
        if (selectedIndex == lastReal) return@LaunchedEffect
        lastReal = selectedIndex
        val target = startOffset + selectedIndex - halfVisible
        val current = listState.firstVisibleItemIndex
        // Pick the closest tripled occurrence so the jump feels natural.
        val candidates = listOf(
            selectedIndex - halfVisible,
            n + selectedIndex - halfVisible,
            2 * n + selectedIndex - halfVisible
        )
        val best = candidates.minByOrNull { kotlin.math.abs(it - current) } ?: target
        listState.animateScrollToItem(best.coerceAtLeast(0))
    }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center
    ) {
        // Center band highlight
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
            contentPadding = PaddingValues(vertical = itemHeight * halfVisible),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tripled.size) { i ->
                val center = listState.firstVisibleItemIndex + halfVisible
                val dist = kotlin.math.abs(i - center)
                val realIdx = ((i % n) + n) % n
                val isSelected = dist == 0 && realIdx == lastReal
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tripled[i],
                        fontSize = if (isSelected) 20.sp else 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(
                            if (dist <= halfVisible) 1f - dist * 0.25f else 0f
                        )
                    )
                }
            }
        }
    }
}
