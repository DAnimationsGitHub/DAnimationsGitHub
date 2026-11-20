package com.danimations.taskmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danimations.taskmanager.PerfHistory
import com.danimations.taskmanager.TaskManagerViewModel
import com.danimations.taskmanager.data.SystemSnapshot
import com.danimations.taskmanager.ui.theme.LocalResourceColors
import java.util.Locale

private enum class Resource(val title: String) {
    CPU("CPU"),
    MEMORY("Memory"),
    STORAGE("Storage"),
    NETWORK("Network"),
    BATTERY("Battery"),
}

@Composable
fun PerformanceScreen(vm: TaskManagerViewModel) {
    val snap by vm.snapshot.collectAsState()
    val hist by vm.history.collectAsState()
    val apps by vm.apps.collectAsState()
    var selected by rememberSaveable { mutableStateOf(Resource.CPU) }

    val s = snap
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Resource.entries.forEach { r ->
                if (r == Resource.BATTERY && s.battery == null) return@forEach
                ResourceTile(r, selected == r, s, hist) { selected = r }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            when (selected) {
                Resource.CPU -> CpuPanel(s, hist)
                Resource.MEMORY -> MemoryPanel(s, hist)
                Resource.STORAGE -> StoragePanel(s, apps.sumOf { it.storage?.total ?: 0L }, apps.size)
                Resource.NETWORK -> NetworkPanel(s, hist)
                Resource.BATTERY -> BatteryPanel(s, hist)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun colorFor(r: Resource): Color {
    val rc = LocalResourceColors.current
    return when (r) {
        Resource.CPU -> rc.cpu
        Resource.MEMORY -> rc.memory
        Resource.STORAGE -> rc.storage
        Resource.NETWORK -> rc.network
        Resource.BATTERY -> rc.battery
    }
}

private fun networkMax(vararg lists: List<Float>): Float =
    (lists.maxOfOrNull { l -> l.maxOrNull() ?: 0f } ?: 0f).coerceAtLeast(1_250f) * 1.25f

@Composable
private fun ResourceTile(r: Resource, selected: Boolean, s: SystemSnapshot, h: PerfHistory, onClick: () -> Unit) {
    val color = colorFor(r)
    val value = when (r) {
        Resource.CPU -> if (s.cpu.available) "${formatPercent(s.cpu.usage)}  ${formatMhz(s.cpu.avgMhz)}" else "Not available"
        Resource.MEMORY -> String.format(
            Locale.US, "%.1f/%.1f GB (%d%%)",
            s.memory.used / GB, s.memory.total / GB, s.memory.usedPercent.toInt(),
        )
        Resource.STORAGE -> "${formatPercent(s.storage.usedPercent)} used"
        Resource.NETWORK -> "↓ ${formatRate(s.network.rxPerSec)}"
        Resource.BATTERY -> s.battery?.let { "${it.level}%  ${it.status}" } ?: ""
    }
    Card(
        onClick = onClick,
        modifier = Modifier.width(156.dp),
        border = if (selected) BorderStroke(2.dp, color) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(r.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            val mod = Modifier
                .fillMaxWidth()
                .height(36.dp)
            when (r) {
                Resource.CPU -> PerfGraph(listOf(GraphSeries(h.cpu, color)), 100f, color, mod, grid = false, strokeWidth = 1.dp)
                Resource.MEMORY -> PerfGraph(listOf(GraphSeries(h.memory, color)), 100f, color, mod, grid = false, strokeWidth = 1.dp)
                Resource.STORAGE -> Box(mod, contentAlignment = Alignment.Center) {
                    UsageBar(listOf(s.storage.usedPercent / 100f to color), Modifier.fillMaxWidth().height(14.dp))
                }
                Resource.NETWORK -> PerfGraph(
                    listOf(GraphSeries(h.rx, color), GraphSeries(h.tx, color, fill = false, dashed = true)),
                    networkMax(h.rx, h.tx), color, mod, grid = false, strokeWidth = 1.dp,
                )
                Resource.BATTERY -> PerfGraph(listOf(GraphSeries(h.batteryLevel, color)), 100f, color, mod, grid = false, strokeWidth = 1.dp)
            }
        }
    }
}

@Composable
private fun PanelHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun BigGraph(topLeft: String, topRight: String, series: List<GraphSeries>, max: Float, color: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(topLeft, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(topRight, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    PerfGraph(
        series,
        max,
        color,
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(color.copy(alpha = 0.03f)),
    )
    Row(Modifier.fillMaxWidth()) {
        Text("60 seconds", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun StatGrid(stats: List<Pair<String, String>>, columns: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.titleMedium)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Horizontal stacked bar; each segment is (fraction of total, color). */
@Composable
private fun UsageBar(segments: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        segments.forEach { (fraction, color) ->
            val f = fraction.coerceIn(0f, 1f)
            if (f > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(f)
                        .background(color),
                )
            }
        }
        val rest = 1f - segments.sumOf { it.first.coerceIn(0f, 1f).toDouble() }.toFloat()
        if (rest > 0.001f) Spacer(Modifier.weight(rest))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ---------- Panels ----------

@Composable
private fun CpuPanel(s: SystemSnapshot, h: PerfHistory) {
    val color = colorFor(Resource.CPU)
    val cpu = s.cpu
    PanelHeader("CPU", android.os.Build.HARDWARE)
    BigGraph(
        if (cpu.estimated) "% Load (estimated from clock speed)" else "% Utilization",
        "100%",
        listOf(GraphSeries(h.cpu, color)),
        100f,
        color,
    )
    StatGrid(
        listOf(
            (if (cpu.estimated) "Load" else "Utilization") to (if (cpu.available) formatPercent(cpu.usage) else "—"),
            "Speed" to formatMhz(cpu.avgMhz),
            "Base speed" to formatMhz(cpu.minMhz),
            "Max speed" to formatMhz(cpu.maxMhz),
            "Cores" to cpu.coreCount.toString(),
            "Online cores" to cpu.onlineCores.toString(),
            "Up time" to formatUptime(s.uptimeMs),
            "Battery temp" to (s.battery?.let { String.format(Locale.US, "%.1f °C", it.temperatureC) } ?: "—"),
        ),
    )
    if (cpu.estimated) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Android doesn't let apps read real CPU utilization, so load is estimated from how fast each core is clocked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(20.dp))
    Text("Cores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    val perRow = 4
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        h.cores.indices.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { i ->
                    Column(Modifier.weight(1f)) {
                        val mhz = cpu.coreCurMhz.getOrElse(i) { 0 }
                        Text(
                            "CPU $i · ${if (mhz > 0) formatMhz(mhz) else "off"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        PerfGraph(
                            listOf(GraphSeries(h.cores[i], color)),
                            100f,
                            color,
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            grid = false,
                            strokeWidth = 1.dp,
                        )
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MemoryPanel(s: SystemSnapshot, h: PerfHistory) {
    val color = colorFor(Resource.MEMORY)
    val m = s.memory
    PanelHeader("Memory", String.format(Locale.US, "%.1f GB", m.total / GB))
    BigGraph("Memory usage", String.format(Locale.US, "%.1f GB", m.total / GB), listOf(GraphSeries(h.memory, color)), 100f, color)

    Text("Memory composition", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    val total = m.total.coerceAtLeast(1).toFloat()
    val cached = (m.cached ?: 0L).coerceAtMost(m.available)
    UsageBar(
        listOf(m.used / total to color, cached / total to color.copy(alpha = 0.45f)),
        Modifier
            .fillMaxWidth()
            .height(28.dp),
    )
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(color, "In use")
        if (m.cached != null) LegendDot(color.copy(alpha = 0.45f), "Cached")
        LegendDot(MaterialTheme.colorScheme.surfaceVariant, "Free")
    }
    Spacer(Modifier.height(16.dp))

    val stats = mutableListOf(
        "In use" to formatBytes(m.used),
        "Available" to formatBytes(m.available),
        "Total" to formatBytes(m.total),
        "Used" to formatPercent(m.usedPercent),
    )
    m.cached?.let { stats += "Cached" to formatBytes(it) }
    if (m.swapTotal != null && m.swapTotal > 0) {
        stats += "Swap (zRAM)" to "${formatBytes(m.swapTotal - (m.swapFree ?: 0L))} / ${formatBytes(m.swapTotal)}"
    }
    stats += "Low-memory limit" to formatBytes(m.threshold)
    stats += "Status" to if (m.lowMemory) "Low memory" else "Normal"
    StatGrid(stats)
}

@Composable
private fun StoragePanel(s: SystemSnapshot, appsBytes: Long, appCount: Int) {
    val color = colorFor(Resource.STORAGE)
    val st = s.storage
    PanelHeader("Storage", "Internal shared storage")
    Text("Capacity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    val total = st.total.coerceAtLeast(1).toFloat()
    val apps = appsBytes.coerceAtMost(st.used)
    UsageBar(
        listOf(apps / total to color, (st.used - apps) / total to color.copy(alpha = 0.45f)),
        Modifier
            .fillMaxWidth()
            .height(36.dp),
    )
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (appsBytes > 0) LegendDot(color, "Apps")
        LegendDot(color.copy(alpha = if (appsBytes > 0) 0.45f else 1f), if (appsBytes > 0) "Other" else "Used")
        LegendDot(MaterialTheme.colorScheme.surfaceVariant, "Free")
    }
    Spacer(Modifier.height(16.dp))
    StatGrid(
        listOf(
            "Used" to formatBytes(st.used),
            "Free" to formatBytes(st.free),
            "Capacity" to formatBytes(st.total),
            "Used %" to formatPercent(st.usedPercent),
            "Installed apps" to appCount.toString(),
            "Apps & data" to if (appsBytes > 0) formatBytes(appsBytes) else "Needs usage access",
        ),
    )
}

@Composable
private fun NetworkPanel(s: SystemSnapshot, h: PerfHistory) {
    val color = colorFor(Resource.NETWORK)
    val n = s.network
    val max = networkMax(h.rx, h.tx)
    PanelHeader("Network", "All interfaces")
    BigGraph(
        "Throughput",
        formatRate(max.toLong()),
        listOf(GraphSeries(h.rx, color), GraphSeries(h.tx, color, fill = false, dashed = true)),
        max,
        color,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(color, "Receive")
        LegendDot(color.copy(alpha = 0.6f), "Send (dashed)")
    }
    Spacer(Modifier.height(16.dp))
    val otherRx = (n.totalRx - n.mobileRx).coerceAtLeast(0)
    val otherTx = (n.totalTx - n.mobileTx).coerceAtLeast(0)
    StatGrid(
        listOf(
            "Receive" to formatRate(n.rxPerSec),
            "Send" to formatRate(n.txPerSec),
            "Received since boot" to formatBytes(n.totalRx),
            "Sent since boot" to formatBytes(n.totalTx),
            "Mobile received" to formatBytes(n.mobileRx),
            "Mobile sent" to formatBytes(n.mobileTx),
            "Wi-Fi & other received" to formatBytes(otherRx),
            "Wi-Fi & other sent" to formatBytes(otherTx),
        ),
    )
}

@Composable
private fun BatteryPanel(s: SystemSnapshot, h: PerfHistory) {
    val color = colorFor(Resource.BATTERY)
    val b = s.battery ?: return
    PanelHeader("Battery", b.technology)
    if (h.batteryCurrent.isNotEmpty()) {
        val max = (h.batteryCurrent.maxOrNull() ?: 0f).coerceAtLeast(100f) * 1.25f
        BigGraph(
            if (b.charging) "Charging current (mA)" else "Power draw (mA)",
            "${max.toInt()} mA",
            listOf(GraphSeries(h.batteryCurrent, color)),
            max,
            color,
        )
    } else {
        BigGraph("Charge level", "100%", listOf(GraphSeries(h.batteryLevel, color)), 100f, color)
    }
    StatGrid(
        listOf(
            "Level" to "${b.level}%",
            "Status" to b.status,
            "Power source" to b.source,
            "Health" to b.health,
            "Temperature" to String.format(Locale.US, "%.1f °C", b.temperatureC),
            "Voltage" to String.format(Locale.US, "%.2f V", b.voltageMv / 1000.0),
            "Current" to (b.currentMa?.let { "${kotlin.math.abs(it)} mA" } ?: "—"),
            "Remaining charge" to (b.chargeCounterMah?.let { "$it mAh" } ?: "—"),
        ),
    )
}

private const val GB = 1024.0 * 1024.0 * 1024.0
