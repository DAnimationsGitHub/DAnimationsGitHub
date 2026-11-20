package com.danimations.taskmanager.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.File
import kotlin.math.abs

data class CpuSnapshot(
    /** Overall utilization, 0..100. */
    val usage: Float,
    /** True when usage is estimated from clock speed because /proc/stat is not readable. */
    val estimated: Boolean,
    /** False when the device exposes neither /proc/stat nor CPU frequencies. */
    val available: Boolean,
    val coreUsage: List<Float>,
    val coreCurMhz: List<Int>,
    val coreMaxMhz: List<Int>,
    val minMhz: Int,
    val maxMhz: Int,
) {
    val coreCount: Int get() = coreUsage.size
    val onlineCores: Int get() = coreCurMhz.count { it > 0 }
    val avgMhz: Int
        get() = coreCurMhz.filter { it > 0 }.let { if (it.isEmpty()) 0 else it.average().toInt() }
}

data class MemorySnapshot(
    val total: Long,
    val available: Long,
    val threshold: Long,
    val lowMemory: Boolean,
    val cached: Long?,
    val swapTotal: Long?,
    val swapFree: Long?,
) {
    val used: Long get() = (total - available).coerceAtLeast(0)
    val usedPercent: Float get() = if (total > 0) used * 100f / total else 0f
}

data class NetworkSnapshot(
    val rxPerSec: Long,
    val txPerSec: Long,
    val totalRx: Long,
    val totalTx: Long,
    val mobileRx: Long,
    val mobileTx: Long,
)

data class StorageSnapshot(val total: Long, val free: Long) {
    val used: Long get() = (total - free).coerceAtLeast(0)
    val usedPercent: Float get() = if (total > 0) used * 100f / total else 0f
}

data class BatterySnapshot(
    val level: Int,
    val status: String,
    val charging: Boolean,
    val source: String,
    val health: String,
    val temperatureC: Float,
    val voltageMv: Int,
    val currentMa: Int?,
    val technology: String,
    val chargeCounterMah: Int?,
)

data class SystemSnapshot(
    val time: Long,
    val cpu: CpuSnapshot,
    val memory: MemorySnapshot,
    val network: NetworkSnapshot,
    val storage: StorageSnapshot,
    val battery: BatterySnapshot?,
    val uptimeMs: Long,
)

/**
 * Samples system-wide resource usage. Android restricts what ordinary apps can read, so every
 * source degrades gracefully: /proc/stat (real CPU load, readable on some devices and emulators)
 * falls back to per-core clock speed, /proc/meminfo extras are optional, and so on.
 */
class SystemMonitor(private val context: Context) {
    private val am = context.getSystemService(ActivityManager::class.java)
    private val bm = context.getSystemService(BatteryManager::class.java)

    private val cores: List<Int> = File("/sys/devices/system/cpu").listFiles()
        ?.mapNotNull { f -> CORE_DIR.matchEntire(f.name)?.groupValues?.get(1)?.toIntOrNull() }
        ?.sorted()
        ?.takeIf { it.isNotEmpty() }
        ?: (0 until Runtime.getRuntime().availableProcessors()).toList()

    private val coreMax: List<Int> = cores.map { readMhz(it, "cpuinfo_max_freq") }
    private val coreMin: List<Int> = cores.map { readMhz(it, "cpuinfo_min_freq") }

    private var prevStat: Map<String, LongArray>? = null
    private var statReadable = true
    private var meminfoReadable = true
    private var prevRx = -1L
    private var prevTx = -1L
    private var prevNetTime = 0L

    fun sample(): SystemSnapshot = SystemSnapshot(
        time = System.currentTimeMillis(),
        cpu = sampleCpu(),
        memory = sampleMemory(),
        network = sampleNetwork(),
        storage = sampleStorage(),
        battery = sampleBattery(),
        uptimeMs = SystemClock.elapsedRealtime(),
    )

    // ---- CPU ----

    private fun readLong(path: String): Long? = try {
        File(path).readText().trim().toLongOrNull()
    } catch (e: Exception) {
        null
    }

    private fun readMhz(core: Int, name: String): Int =
        readLong("/sys/devices/system/cpu/cpu$core/cpufreq/$name")?.let { (it / 1000).toInt() } ?: 0

    private fun readProcStat(): Map<String, LongArray>? {
        if (!statReadable) return null
        return try {
            File("/proc/stat").readLines()
                .filter { it.startsWith("cpu") }
                .associate { line ->
                    val parts = line.trim().split(WHITESPACE)
                    parts[0] to parts.drop(1).mapNotNull { it.toLongOrNull() }.toLongArray()
                }
                .takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            statReadable = false
            null
        }
    }

    private fun usageBetween(a: LongArray?, b: LongArray?): Float? {
        if (a == null || b == null || a.size < 4 || b.size < 4) return null
        fun idle(x: LongArray) = x[3] + (if (x.size > 4) x[4] else 0L)
        fun total(x: LongArray) = x.take(8).sum()
        val dt = total(b) - total(a)
        val di = idle(b) - idle(a)
        if (dt <= 0) return null
        return ((dt - di) * 100f / dt).coerceIn(0f, 100f)
    }

    private fun sampleCpu(): CpuSnapshot {
        val cur = cores.map { readMhz(it, "scaling_cur_freq") }
        val maxAll = coreMax.maxOrNull() ?: 0
        val minAll = coreMin.filter { it > 0 }.minOrNull() ?: 0

        val stat = readProcStat()
        val prev = prevStat
        prevStat = stat
        if (stat != null && prev != null) {
            val total = usageBetween(prev["cpu"], stat["cpu"])
            if (total != null) {
                val perCore = cores.map { usageBetween(prev["cpu$it"], stat["cpu$it"]) ?: 0f }
                return CpuSnapshot(total, false, true, perCore, cur, coreMax, minAll, maxAll)
            }
        }

        val perCore = cur.mapIndexed { i, f ->
            val max = coreMax.getOrElse(i) { 0 }
            if (f > 0 && max > 0) (f * 100f / max).coerceIn(0f, 100f) else 0f
        }
        val usage = if (perCore.isEmpty()) 0f else perCore.average().toFloat()
        return CpuSnapshot(
            usage = usage,
            estimated = true,
            available = cur.any { it > 0 } || stat != null,
            coreUsage = perCore,
            coreCurMhz = cur,
            coreMaxMhz = coreMax,
            minMhz = minAll,
            maxMhz = maxAll,
        )
    }

    // ---- Memory ----

    private fun readMeminfo(): Map<String, Long> {
        if (!meminfoReadable) return emptyMap()
        return try {
            File("/proc/meminfo").readLines().mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx < 0) return@mapNotNull null
                val kb = line.substring(idx + 1).trim().split(WHITESPACE).firstOrNull()?.toLongOrNull()
                    ?: return@mapNotNull null
                line.substring(0, idx).trim() to kb * 1024
            }.toMap()
        } catch (e: Exception) {
            meminfoReadable = false
            emptyMap()
        }
    }

    private fun sampleMemory(): MemorySnapshot {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val extra = readMeminfo()
        return MemorySnapshot(
            total = mi.totalMem,
            available = mi.availMem,
            threshold = mi.threshold,
            lowMemory = mi.lowMemory,
            cached = extra["Cached"],
            swapTotal = extra["SwapTotal"],
            swapFree = extra["SwapFree"],
        )
    }

    // ---- Network ----

    private fun sampleNetwork(): NetworkSnapshot {
        val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
        val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
        val now = SystemClock.elapsedRealtime()
        var rxRate = 0L
        var txRate = 0L
        if (prevRx >= 0 && now > prevNetTime) {
            val seconds = (now - prevNetTime) / 1000.0
            rxRate = ((rx - prevRx).coerceAtLeast(0) / seconds).toLong()
            txRate = ((tx - prevTx).coerceAtLeast(0) / seconds).toLong()
        }
        prevRx = rx
        prevTx = tx
        prevNetTime = now
        return NetworkSnapshot(
            rxPerSec = rxRate,
            txPerSec = txRate,
            totalRx = rx,
            totalTx = tx,
            mobileRx = TrafficStats.getMobileRxBytes().coerceAtLeast(0),
            mobileTx = TrafficStats.getMobileTxBytes().coerceAtLeast(0),
        )
    }

    // ---- Storage ----

    private fun sampleStorage(): StorageSnapshot = try {
        val fs = StatFs(Environment.getDataDirectory().path)
        StorageSnapshot(fs.totalBytes, fs.availableBytes)
    } catch (e: Exception) {
        StorageSnapshot(0, 0)
    }

    // ---- Battery ----

    private fun sampleBattery(): BatterySnapshot? {
        val i = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            null
        } ?: return null

        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val statusInt = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val status = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        val source = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            8 -> "Dock"
            else -> "Battery"
        }
        val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }

        val rawCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // Most devices report microamps, a few report milliamps.
        val currentMa = when {
            rawCurrent == Int.MIN_VALUE || rawCurrent == 0 -> null
            abs(rawCurrent) > 10_000 -> rawCurrent / 1000
            else -> rawCurrent
        }
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val counterMah = if (counter == Int.MIN_VALUE || counter <= 0) null else counter / 1000

        return BatterySnapshot(
            level = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            status = status,
            charging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
                statusInt == BatteryManager.BATTERY_STATUS_FULL,
            source = source,
            health = health,
            temperatureC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            currentMa = currentMa,
            technology = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown",
            chargeCounterMah = counterMah,
        )
    }

    private companion object {
        val CORE_DIR = Regex("cpu(\\d+)")
        val WHITESPACE = Regex("\\s+")
    }
}
