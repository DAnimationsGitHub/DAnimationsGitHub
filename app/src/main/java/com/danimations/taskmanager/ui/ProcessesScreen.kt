package com.danimations.taskmanager.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danimations.taskmanager.TaskManagerViewModel
import com.danimations.taskmanager.data.AppCategory
import com.danimations.taskmanager.data.AppEntry
import com.danimations.taskmanager.data.SystemSnapshot
import com.danimations.taskmanager.ui.theme.LocalResourceColors

private enum class ProcSort { NAME, STATUS, SCREEN, DATA, SIZE }

private val ColScreen = 58.dp
private val ColData = 64.dp
private val ColSize = 66.dp

@Composable
fun ProcessesScreen(vm: TaskManagerViewModel) {
    val apps by vm.apps.collectAsState()
    val loading by vm.appsLoading.collectAsState()
    val access by vm.hasUsageAccess.collectAsState()
    val showSystem by vm.showSystem.collectAsState()
    val snapshot by vm.snapshot.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var sortKey by rememberSaveable { mutableStateOf(ProcSort.STATUS) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var selectedPkg by rememberSaveable { mutableStateOf<String?>(null) }

    val groups = remember(apps, query, showSystem, sortKey, descending) {
        val q = query.trim().lowercase()
        val comparator: Comparator<AppEntry> = when (sortKey) {
            ProcSort.NAME -> compareBy<AppEntry> { it.label.lowercase() }
            ProcSort.STATUS -> compareBy<AppEntry> { it.status.ordinal }.thenBy { it.label.lowercase() }
            ProcSort.SCREEN -> compareBy<AppEntry> { it.foregroundMsToday }.thenBy { it.label.lowercase() }
            ProcSort.DATA -> compareBy<AppEntry> { it.dataTodayBytes }.thenBy { it.label.lowercase() }
            ProcSort.SIZE -> compareBy<AppEntry> { it.storage?.total ?: 0L }.thenBy { it.label.lowercase() }
        }
        val ordered = if (descending) comparator.reversed() else comparator
        apps.asSequence()
            .filter { showSystem || it.category != AppCategory.SYSTEM }
            .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.contains(q) }
            .sortedWith(ordered)
            .groupBy { it.category }
            .toSortedMap()
    }
    val visible = groups.values.flatten()
    val maxScreen = visible.maxOfOrNull { it.foregroundMsToday }?.coerceAtLeast(1L) ?: 1L
    val maxData = visible.maxOfOrNull { it.dataTodayBytes }?.coerceAtLeast(1L) ?: 1L
    val maxSize = visible.maxOfOrNull { it.storage?.total ?: 0L }?.coerceAtLeast(1L) ?: 1L

    fun sortBy(key: ProcSort) {
        if (sortKey == key) {
            descending = !descending
        } else {
            sortKey = key
            descending = key != ProcSort.NAME && key != ProcSort.STATUS
        }
    }

    Column(Modifier.fillMaxSize()) {
        snapshot?.let { SummaryStrip(it) }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = showSystem,
                onClick = { vm.setShowSystem(!showSystem) },
                label = { Text("System") },
            )
        }

        if (!access) {
            UsageAccessCard(
                message = "Android only shares which apps are running, screen time, data usage and app " +
                    "sizes with apps you allow. Without it you'll only see the installed app list.",
            )
        }

        // Column headers
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f)) {
                HeaderCell("Name", sortKey == ProcSort.NAME, descending, alignEnd = false) { sortBy(ProcSort.NAME) }
                Spacer(Modifier.width(12.dp))
                HeaderCell("Status", sortKey == ProcSort.STATUS, descending, alignEnd = false) { sortBy(ProcSort.STATUS) }
            }
            HeaderCell("Screen", sortKey == ProcSort.SCREEN, descending, Modifier.width(ColScreen)) { sortBy(ProcSort.SCREEN) }
            HeaderCell("Data", sortKey == ProcSort.DATA, descending, Modifier.width(ColData)) { sortBy(ProcSort.DATA) }
            HeaderCell("Size", sortKey == ProcSort.SIZE, descending, Modifier.width(ColSize)) { sortBy(ProcSort.SIZE) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No apps match", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                groups.forEach { (category, list) ->
                    item(key = "header-${category.name}") { GroupHeader(category.title, list.size) }
                    items(list, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            showValues = access,
                            screenFraction = app.foregroundMsToday.toFloat() / maxScreen,
                            dataFraction = app.dataTodayBytes.toFloat() / maxData,
                            sizeFraction = (app.storage?.total ?: 0L).toFloat() / maxSize,
                            onClick = { selectedPkg = app.packageName },
                        )
                    }
                }
            }
        }
    }

    val selected = selectedPkg?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
    if (selected != null) {
        AppDetailSheet(app = selected, vm = vm, onDismiss = { selectedPkg = null })
    }
}

@Composable
private fun SummaryStrip(s: SystemSnapshot) {
    val rc = LocalResourceColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SummaryCell(if (s.cpu.available) formatPercent(s.cpu.usage) else "—", "CPU", rc.cpu)
        SummaryCell(formatPercent(s.memory.usedPercent), "Memory", rc.memory)
        SummaryCell(formatPercent(s.storage.usedPercent), "Storage", rc.storage)
        SummaryCell(formatRate(s.network.rxPerSec + s.network.txPerSec), "Network", rc.network)
        s.battery?.let { SummaryCell("${it.level}%", "Battery", rc.battery) }
    }
}

@Composable
private fun SummaryCell(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    showValues: Boolean,
    screenFraction: Float,
    dataFraction: Float,
    sizeFraction: Float,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusLine(app.status)
        }
        if (showValues) {
            HeatCell(formatDuration(app.foregroundMsToday), screenFraction, ColScreen)
            HeatCell(if (app.dataTodayBytes > 0) formatBytes(app.dataTodayBytes) else "0 B", dataFraction, ColData)
            HeatCell(app.storage?.let { formatBytes(it.total) } ?: "—", sizeFraction, ColSize)
        } else {
            HeatCell("—", 0f, ColScreen)
            HeatCell("—", 0f, ColData)
            HeatCell("—", 0f, ColSize)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailSheet(app: AppEntry, vm: TaskManagerViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isSelf = app.packageName == context.packageName

    fun endTask() {
        when {
            isSelf -> (context as? Activity)?.finishAndRemoveTask()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                // Android 14+ only lets apps end their own processes; send the user to Force stop.
                toast(context, "Tap \"Force stop\" to end ${app.label}")
                openAppInfo(context, app.packageName)
            }
            else -> {
                vm.killBackground(app.packageName)
                toast(context, "Ended background processes of ${app.label}")
            }
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, 52.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusLine(app.status)
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { endTask() }, modifier = Modifier.weight(1f)) { Text("End task") }
                if (app.launchable && !isSelf) {
                    OutlinedButton(onClick = { launchApp(context, app.packageName); onDismiss() }, modifier = Modifier.weight(1f)) {
                        Text("Open")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { openAppInfo(context, app.packageName) }, modifier = Modifier.weight(1f)) {
                    Text("App info")
                }
                if (!app.isSystem && !isSelf) {
                    OutlinedButton(onClick = { uninstallApp(context, app.packageName); onDismiss() }, modifier = Modifier.weight(1f)) {
                        Text("Uninstall")
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !isSelf) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "On Android 14 and later, End task opens the system page where you can Force stop the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionCard("Activity today") {
                InfoRow("Screen time", formatDuration(app.foregroundMsToday))
                InfoRow("Data used", formatBytes(app.dataTodayBytes))
                InfoRow("Last used", formatRelative(app.lastUsed))
            }
            Spacer(Modifier.height(12.dp))
            SectionCard("Storage") {
                val st = app.storage
                if (st == null) {
                    InfoRow("Total", "—")
                } else {
                    InfoRow("App", formatBytes(st.appBytes))
                    InfoRow("User data", formatBytes(st.dataBytes))
                    InfoRow("Cache", formatBytes(st.cacheBytes))
                    InfoRow("Total", formatBytes(st.total))
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard("Details") {
                InfoRow("Version", app.versionName ?: "—")
                InfoRow("Type", if (app.isSystem) (if (app.isUpdatedSystem) "System (updated)" else "System") else "User-installed")
                InfoRow("Group", app.category.title)
                InfoRow("Target SDK", app.targetSdk.toString())
                InfoRow("UID", app.uid.toString())
                InfoRow("Installed", formatDate(app.installTime))
                InfoRow("Updated", formatDate(app.updateTime))
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}
