package com.danimations.taskmanager.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return if (value >= 100) String.format(Locale.US, "%.0f %s", value, units[i])
    else String.format(Locale.US, "%.1f %s", value, units[i])
}

/** Bytes per second rendered as bits per second, like a desktop task manager. */
fun formatRate(bytesPerSec: Long): String {
    val bits = bytesPerSec * 8.0
    return when {
        bits < 1_000 -> String.format(Locale.US, "%.0f bps", bits)
        bits < 1_000_000 -> String.format(Locale.US, "%.1f Kbps", bits / 1_000)
        bits < 1_000_000_000 -> String.format(Locale.US, "%.1f Mbps", bits / 1_000_000)
        else -> String.format(Locale.US, "%.2f Gbps", bits / 1_000_000_000)
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    if (ms < 60_000) return "<1m"
    val hours = ms / 3_600_000
    val minutes = (ms / 60_000) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/** d:hh:mm:ss */
fun formatUptime(ms: Long): String {
    val total = ms / 1000
    val d = total / 86_400
    val h = (total % 86_400) / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return String.format(Locale.US, "%d:%02d:%02d:%02d", d, h, m, s)
}

fun formatPercent(value: Float): String = "${value.roundToInt()}%"

fun formatMhz(mhz: Int): String =
    if (mhz <= 0) "—" else if (mhz >= 1000) String.format(Locale.US, "%.2f GHz", mhz / 1000.0) else "$mhz MHz"

fun formatDate(time: Long): String =
    if (time <= 0) "—" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

fun formatRelative(time: Long): String {
    if (time <= 0) return "Never"
    val diff = System.currentTimeMillis() - time
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} h ago"
        else -> "${diff / 86_400_000} days ago"
    }
}
