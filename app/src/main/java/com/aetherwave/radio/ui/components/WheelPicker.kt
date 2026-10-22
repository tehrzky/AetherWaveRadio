package com.aetherwave.radio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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


@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex - visibleCount / 2).coerceAtLeast(0)
    )
    val itemHeight = 44.dp
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .map { (it + visibleCount / 2).coerceIn(0, items.lastIndex) }
            .distinctUntilChanged()
            .collect { if (it != selectedIndex) onItemSelected(it) }
    }

    LaunchedEffect(selectedIndex) {
        val target = (selectedIndex - visibleCount / 2).coerceAtLeast(0)
        if (listState.firstVisibleItemIndex != target) listState.animateScrollToItem(target)
    }

    Box(modifier = modifier.height(itemHeight * visibleCount), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.15f))
        )
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items.size) { i ->
                val center = listState.firstVisibleItemIndex + visibleCount / 2
                val dist = kotlin.math.abs(i - center)
                Box(Modifier.fillMaxWidth().height(itemHeight), contentAlignment = Alignment.Center) {
                    Text(
                        items[i],
                        fontSize = if (dist == 0) 20.sp else 15.sp,
                        fontWeight = if (dist == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (dist == 0) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(if (dist <= visibleCount / 2) 1f - dist * 0.25f else 0f)
                    )
                }
            }
        }
    }
}
