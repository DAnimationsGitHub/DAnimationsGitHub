package com.danimations.taskmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danimations.taskmanager.TaskManagerViewModel
import java.util.Locale

@Composable
fun DetailsScreen(vm: TaskManagerViewModel) {
    val snap by vm.snapshot.collectAsState()
    val apps by vm.apps.collectAsState()
    val sections = vm.deviceInfo

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("Live") {
                val s = snap
                InfoRow("Up time", s?.let { formatUptime(it.uptimeMs) } ?: "—")
                InfoRow("Installed apps", apps.size.toString())
                InfoRow("Apps running now", apps.count { it.status == com.danimations.taskmanager.data.AppStatus.FOREGROUND || it.status == com.danimations.taskmanager.data.AppStatus.SERVICE }.toString())
                InfoRow("Memory in use", s?.let { "${formatBytes(it.memory.used)} (${formatPercent(it.memory.usedPercent)})" } ?: "—")
                InfoRow(
                    "Battery",
                    s?.battery?.let { String.format(Locale.US, "%d%% · %.1f °C", it.level, it.temperatureC) } ?: "—",
                )
            }
        }
        items(sections, key = { it.title }) { section ->
            SectionCard(section.title) {
                section.rows.forEach { (label, value) -> InfoRow(label, value.ifBlank { "—" }) }
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp)) }
    }
}
