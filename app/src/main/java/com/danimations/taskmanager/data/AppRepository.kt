package com.danimations.taskmanager.data

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import java.util.Calendar
import java.util.UUID

enum class AppStatus(val label: String) {
    FOREGROUND("Running"),
    SERVICE("Running in background"),
    RECENT("Recently used"),
    IDLE("Idle"),
    STOPPED("Stopped"),
    DISABLED("Disabled"),
}

enum class AppCategory(val title: String) {
    APPS("Apps"),
    BACKGROUND("Background processes"),
    SYSTEM("System processes"),
}

data class StorageInfo(val appBytes: Long, val dataBytes: Long, val cacheBytes: Long) {
    val total: Long get() = appBytes + dataBytes + cacheBytes
}

data class AppEntry(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean,
    val launchable: Boolean,
    val status: AppStatus,
    val versionName: String?,
    val targetSdk: Int,
    val installTime: Long,
    val updateTime: Long,
    val foregroundMsToday: Long,
    val lastUsed: Long,
    val dataTodayBytes: Long,
    val storageUuid: UUID,
    val storage: StorageInfo? = null,
) {
    val category: AppCategory
        get() = when {
            launchable -> AppCategory.APPS
            !isSystem || isUpdatedSystem -> AppCategory.BACKGROUND
            else -> AppCategory.SYSTEM
        }
}

enum class HistoryRange(val label: String, val days: Int) {
    TODAY("Today", 0),
    WEEK("7 days", 7),
    MONTH("30 days", 30),
}

data class HistoryEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val foregroundMs: Long,
    val mobileBytes: Long,
    val wifiBytes: Long,
    val lastUsed: Long,
)

enum class StartupStatus(val label: String) {
    ENABLED("Enabled"),
    STOPPED("Won't start (stopped)"),
    DISABLED("Disabled"),
}

data class StartupEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val receivers: Int,
    val status: StartupStatus,
    val lastUsed: Long,
)

/** Reads app information. Anything needing usage access degrades to empty values without it. */
class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private val nsm = context.getSystemService(NetworkStatsManager::class.java)
    private val ssm = context.getSystemService(StorageStatsManager::class.java)
    private val am = context.getSystemService(ActivityManager::class.java)
    private val labelCache = HashMap<String, String>()

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = checkUsageOp(appOps)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    @Suppress("DEPRECATION")
    private fun checkUsageOp(appOps: AppOpsManager): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }

    @Suppress("DEPRECATION")
    private fun installedPackages(): List<PackageInfo> = try {
        pm.getInstalledPackages(0)
    } catch (e: Exception) {
        emptyList()
    }

    private fun label(info: PackageInfo, ai: ApplicationInfo): String {
        val key = "${info.packageName}@${info.lastUpdateTime}"
        return labelCache.getOrPut(key) {
            try {
                ai.loadLabel(pm).toString()
            } catch (e: Exception) {
                info.packageName
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun launcherPackages(): Set<String> = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0).mapTo(HashSet()) { it.activityInfo.packageName }
    } catch (e: Exception) {
        emptySet()
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun usageStats(start: Long, end: Long): Map<String, UsageStats> = try {
        usm.queryAndAggregateUsageStats(start, end) ?: emptyMap()
    } catch (e: Exception) {
        emptyMap()
    }

    @Suppress("DEPRECATION")
    private fun dataByUid(networkType: Int, start: Long, end: Long): Map<Int, Long> {
        val out = HashMap<Int, Long>()
        try {
            val stats = nsm.querySummary(networkType, null, start, end)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                out[bucket.uid] = (out[bucket.uid] ?: 0L) + bucket.rxBytes + bucket.txBytes
            }
            stats.close()
        } catch (e: Exception) {
            // Not permitted or not supported on this device / network type.
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun wifiData(start: Long, end: Long) = dataByUid(ConnectivityManager.TYPE_WIFI, start, end)

    @Suppress("DEPRECATION")
    private fun mobileData(start: Long, end: Long) = dataByUid(ConnectivityManager.TYPE_MOBILE, start, end)

    private class LiveState(val foreground: Set<String>, val services: Set<String>)

    /** Replays the last day of usage events to work out which apps are on screen or running a foreground service. */
    private fun liveState(now: Long): LiveState {
        val resumed = HashMap<String, MutableSet<String>>()
        val services = HashMap<String, MutableSet<String>>()
        try {
            val events = usm.queryEvents(now - DAY_MS, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val pkg = e.packageName ?: continue
                val cls = e.className ?: ""
                when (e.eventType) {
                    EVENT_RESUMED -> resumed.getOrPut(pkg) { HashSet() }.add(cls)
                    EVENT_PAUSED, EVENT_STOPPED -> resumed[pkg]?.remove(cls)
                    EVENT_FGS_START -> services.getOrPut(pkg) { HashSet() }.add(cls)
                    EVENT_FGS_STOP -> services[pkg]?.remove(cls)
                }
            }
        } catch (e: Exception) {
            // Usage access missing.
        }
        return LiveState(
            foreground = resumed.filterValues { it.isNotEmpty() }.keys,
            services = services.filterValues { it.isNotEmpty() }.keys,
        )
    }

    fun loadApps(access: Boolean): List<AppEntry> {
        val now = System.currentTimeMillis()
        val dayStart = startOfToday()
        val launchable = launcherPackages()
        val usage = if (access) usageStats(dayStart, now) else emptyMap()
        val live = if (access) liveState(now) else LiveState(emptySet(), emptySet())
        val data = if (access) {
            val wifi = wifiData(dayStart, now)
            val mobile = mobileData(dayStart, now)
            (wifi.keys + mobile.keys).associateWith { (wifi[it] ?: 0L) + (mobile[it] ?: 0L) }
        } else {
            emptyMap()
        }

        return installedPackages().mapNotNull { info ->
            val ai = info.applicationInfo ?: return@mapNotNull null
            val pkg = info.packageName
            val u = usage[pkg]
            val lastUsed = u?.lastTimeUsed ?: 0L
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val status = when {
                !ai.enabled -> AppStatus.DISABLED
                pkg in live.foreground -> AppStatus.FOREGROUND
                pkg in live.services -> AppStatus.SERVICE
                (ai.flags and ApplicationInfo.FLAG_STOPPED) != 0 -> AppStatus.STOPPED
                lastUsed > now - RECENT_MS -> AppStatus.RECENT
                else -> AppStatus.IDLE
            }
            AppEntry(
                packageName = pkg,
                label = label(info, ai),
                uid = ai.uid,
                isSystem = isSystem,
                isUpdatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                launchable = pkg in launchable,
                status = status,
                versionName = info.versionName,
                targetSdk = ai.targetSdkVersion,
                installTime = info.firstInstallTime,
                updateTime = info.lastUpdateTime,
                foregroundMsToday = u?.totalTimeInForeground ?: 0L,
                lastUsed = lastUsed,
                dataTodayBytes = data[ai.uid] ?: 0L,
                storageUuid = ai.storageUuid ?: StorageManager.UUID_DEFAULT,
            )
        }
    }

    fun loadSizes(apps: List<AppEntry>): Map<String, StorageInfo> {
        val user = Process.myUserHandle()
        val out = HashMap<String, StorageInfo>()
        for (app in apps) {
            try {
                val s = ssm.queryStatsForPackage(app.storageUuid, app.packageName, user)
                out[app.packageName] = StorageInfo(s.appBytes, s.dataBytes, s.cacheBytes)
            } catch (e: Exception) {
                // Skip apps we can't measure.
            }
        }
        return out
    }

    fun loadHistory(range: HistoryRange): List<HistoryEntry> {
        val now = System.currentTimeMillis()
        val start = if (range == HistoryRange.TODAY) startOfToday() else now - range.days * DAY_MS
        val usage = usageStats(start, now)
        val wifi = wifiData(start, now)
        val mobile = mobileData(start, now)
        return installedPackages().mapNotNull { info ->
            val ai = info.applicationInfo ?: return@mapNotNull null
            val u = usage[info.packageName]
            val entry = HistoryEntry(
                packageName = info.packageName,
                label = label(info, ai),
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                foregroundMs = u?.totalTimeInForeground ?: 0L,
                mobileBytes = mobile[ai.uid] ?: 0L,
                wifiBytes = wifi[ai.uid] ?: 0L,
                lastUsed = u?.lastTimeUsed ?: 0L,
            )
            entry.takeIf { it.foregroundMs > 0 || it.mobileBytes > 0 || it.wifiBytes > 0 }
        }
    }

    @Suppress("DEPRECATION")
    fun loadStartup(access: Boolean): List<StartupEntry> {
        // package -> (receiver class -> enabled)
        val receivers = HashMap<String, HashMap<String, Boolean>>()
        for (action in BOOT_ACTIONS) {
            val found = try {
                pm.queryBroadcastReceivers(Intent(action), PackageManager.MATCH_DISABLED_COMPONENTS)
            } catch (e: Exception) {
                emptyList()
            }
            for (ri in found) {
                val info = ri.activityInfo ?: continue
                val enabled = try {
                    when (pm.getComponentEnabledSetting(ComponentName(info.packageName, info.name))) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> info.enabled
                        else -> false
                    }
                } catch (e: Exception) {
                    info.enabled
                }
                receivers.getOrPut(info.packageName) { HashMap() }[info.name] = enabled
            }
        }

        val now = System.currentTimeMillis()
        val usage = if (access) usageStats(now - 30 * DAY_MS, now) else emptyMap()
        val packages = installedPackages().associateBy { it.packageName }
        return receivers.mapNotNull { (pkg, classes) ->
            val info = packages[pkg] ?: return@mapNotNull null
            val ai = info.applicationInfo ?: return@mapNotNull null
            val status = when {
                !ai.enabled || classes.values.none { it } -> StartupStatus.DISABLED
                (ai.flags and ApplicationInfo.FLAG_STOPPED) != 0 -> StartupStatus.STOPPED
                else -> StartupStatus.ENABLED
            }
            StartupEntry(
                packageName = pkg,
                label = label(info, ai),
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                receivers = classes.size,
                status = status,
                lastUsed = usage[pkg]?.lastTimeUsed ?: 0L,
            )
        }
    }

    /** Only effective on Android 13 and older; Android 14 limits this to the caller's own processes. */
    fun killBackground(packageName: String) {
        try {
            am.killBackgroundProcesses(packageName)
        } catch (e: Exception) {
            // Ignore: permission revoked or package gone.
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val RECENT_MS = 60L * 60 * 1000
        const val EVENT_RESUMED = 1
        const val EVENT_PAUSED = 2
        const val EVENT_FGS_START = 19
        const val EVENT_FGS_STOP = 20
        const val EVENT_STOPPED = 23
        val BOOT_ACTIONS = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
