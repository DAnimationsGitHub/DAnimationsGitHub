package com.danimations.taskmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danimations.taskmanager.TaskManagerViewModel
import com.danimations.taskmanager.data.HistoryEntry
import com.danimations.taskmanager.data.HistoryRange

private enum class HistorySort { NAME, SCREEN, MOBILE, WIFI }

private val ColH = 70.dp

@Composable
fun AppHistoryScreen(vm: TaskManagerViewModel) {
    LaunchedEffect(Unit) { vm.ensureHistoryLoaded() }
    val access by vm.hasUsageAccess.collectAsState()
    val range by vm.historyRange.collectAsState()
    val entries by vm.appHistory.collectAsState()
    val loading by vm.historyLoading.collectAsState()
    val showSystem by vm.showSystem.collectAsState()
    val context = LocalContext.current

    var sortKey by rememberSaveable { mutableStateOf(HistorySort.SCREEN) }
    var descending by rememberSaveable { mutableStateOf(true) }

    val rows = remember(entries, sortKey, descending, showSystem) {
        val c: Comparator<HistoryEntry> = when (sortKey) {
            HistorySort.NAME -> compareBy<HistoryEntry> { it.label.lowercase() }
            HistorySort.SCREEN -> compareBy<HistoryEntry> { it.foregroundMs }
            HistorySort.MOBILE -> compareBy<HistoryEntry> { it.mobileBytes }
            HistorySort.WIFI -> compareBy<HistoryEntry> { it.wifiBytes }
        }
        entries.filter { showSystem || !it.isSystem }.sortedWith(if (descending) c.reversed() else c)
    }
    val maxScreen = (rows.maxOfOrNull { it.foregroundMs } ?: 0L).coerceAtLeast(1L)
    val maxMobile = (rows.maxOfOrNull { it.mobileBytes } ?: 0L).coerceAtLeast(1L)
    val maxWifi = (rows.maxOfOrNull { it.wifiBytes } ?: 0L).coerceAtLeast(1L)

    fun sortBy(key: HistorySort) {
        if (sortKey == key) descending = !descending else {
            sortKey = key
            descending = key != HistorySort.NAME
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryRange.entries.forEach { r ->
                FilterChip(selected = r == range, onClick = { vm.setHistoryRange(r) }, label = { Text(r.label) })
            }
        }
        if (!access) {
            UsageAccessCard(message = "App history shows each app's screen time and mobile / Wi-Fi data. Android requires usage access for this.")
        } else {
            HistoryTable(rows, loading && entries.isEmpty(), maxScreen, maxMobile, maxWifi, sortKey, descending) { sortBy(it) }
        }
    }
}

@Composable
private fun HistoryTable(
    rows: List<HistoryEntry>,
    loading: Boolean,
    maxScreen: Long,
    maxMobile: Long,
    maxWifi: Long,
    sortKey: HistorySort,
    descending: Boolean,
    sortBy: (HistorySort) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        // Totals
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Total("Screen time", formatDuration(rows.sumOf { it.foregroundMs }))
            Total("Mobile data", formatBytes(rows.sumOf { it.mobileBytes }))
            Total("Wi-Fi data", formatBytes(rows.sumOf { it.wifiBytes }))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("Name", sortKey == HistorySort.NAME, descending, Modifier.weight(1f), alignEnd = false) { sortBy(HistorySort.NAME) }
            HeaderCell("Screen", sortKey == HistorySort.SCREEN, descending, Modifier.width(ColH)) { sortBy(HistorySort.SCREEN) }
            HeaderCell("Mobile", sortKey == HistorySort.MOBILE, descending, Modifier.width(ColH)) { sortBy(HistorySort.MOBILE) }
            HeaderCell("Wi-Fi", sortKey == HistorySort.WIFI, descending, Modifier.width(ColH)) { sortBy(HistorySort.WIFI) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No activity in this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.packageName }) { e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { openAppInfo(context, e.packageName) }
                            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(e.packageName, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Last used ${formatRelative(e.lastUsed).lowercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        HeatCell(formatDuration(e.foregroundMs), e.foregroundMs.toFloat() / maxScreen, ColH)
                        HeatCell(formatBytes(e.mobileBytes), e.mobileBytes.toFloat() / maxMobile, ColH)
                        HeatCell(formatBytes(e.wifiBytes), e.wifiBytes.toFloat() / maxWifi, ColH)
                    }
                }
            }
        }
    }
}

@Composable
private fun Total(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
