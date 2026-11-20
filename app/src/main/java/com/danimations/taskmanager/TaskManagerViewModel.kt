package com.danimations.taskmanager

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danimations.taskmanager.data.AppEntry
import com.danimations.taskmanager.data.AppRepository
import com.danimations.taskmanager.data.DeviceInfo
import com.danimations.taskmanager.data.HistoryEntry
import com.danimations.taskmanager.data.HistoryRange
import com.danimations.taskmanager.data.InfoSection
import com.danimations.taskmanager.data.StartupEntry
import com.danimations.taskmanager.data.StorageInfo
import com.danimations.taskmanager.data.SystemMonitor
import com.danimations.taskmanager.data.SystemSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UpdateSpeed(val label: String, val intervalMs: Long) {
    HIGH("High", 500),
    NORMAL("Normal", 1000),
    LOW("Low", 4000),
    PAUSED("Paused", 0),
}

/** Rolling 60-sample history for the Performance graphs. */
data class PerfHistory(
    val cpu: List<Float> = emptyList(),
    val cores: List<List<Float>> = emptyList(),
    val memory: List<Float> = emptyList(),
    val rx: List<Float> = emptyList(),
    val tx: List<Float> = emptyList(),
    val batteryLevel: List<Float> = emptyList(),
    val batteryCurrent: List<Float> = emptyList(),
) {
    fun push(s: SystemSnapshot): PerfHistory {
        val coreHistory = s.cpu.coreUsage.mapIndexed { i, v -> cores.getOrElse(i) { emptyList() }.add(v) }
        return PerfHistory(
            cpu = cpu.add(s.cpu.usage),
            cores = coreHistory,
            memory = memory.add(s.memory.usedPercent),
            rx = rx.add(s.network.rxPerSec.toFloat()),
            tx = tx.add(s.network.txPerSec.toFloat()),
            batteryLevel = s.battery?.let { batteryLevel.add(it.level.toFloat()) } ?: batteryLevel,
            batteryCurrent = s.battery?.currentMa?.let { batteryCurrent.add(kotlin.math.abs(it).toFloat()) }
                ?: batteryCurrent,
        )
    }

    private fun List<Float>.add(v: Float): List<Float> = (this + v).takeLast(CAPACITY)

    companion object {
        const val CAPACITY = 60
    }
}

class TaskManagerViewModel(app: Application) : AndroidViewModel(app) {
    private val monitor = SystemMonitor(app)
    private val repo = AppRepository(app)

    private val _snapshot = MutableStateFlow<SystemSnapshot?>(null)
    val snapshot: StateFlow<SystemSnapshot?> = _snapshot

    private val _history = MutableStateFlow(PerfHistory())
    val history: StateFlow<PerfHistory> = _history

    private val _speed = MutableStateFlow(UpdateSpeed.NORMAL)
    val speed: StateFlow<UpdateSpeed> = _speed

    private val _hasUsageAccess = MutableStateFlow(repo.hasUsageAccess())
    val hasUsageAccess: StateFlow<Boolean> = _hasUsageAccess

    private val _showSystem = MutableStateFlow(false)
    val showSystem: StateFlow<Boolean> = _showSystem

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps
    private val _appsLoading = MutableStateFlow(true)
    val appsLoading: StateFlow<Boolean> = _appsLoading

    private val _startup = MutableStateFlow<List<StartupEntry>>(emptyList())
    val startup: StateFlow<List<StartupEntry>> = _startup
    private val _startupLoading = MutableStateFlow(false)
    val startupLoading: StateFlow<Boolean> = _startupLoading

    private val _historyRange = MutableStateFlow(HistoryRange.TODAY)
    val historyRange: StateFlow<HistoryRange> = _historyRange
    private val _appHistory = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val appHistory: StateFlow<List<HistoryEntry>> = _appHistory
    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading

    val deviceInfo: List<InfoSection> by lazy { DeviceInfo.collect(app) }

    private val sizeCache = HashMap<String, StorageInfo>()
    private var sizesLoaded = false
    private var monitorJob: Job? = null
    private var appsJob: Job? = null
    private var startupLoaded = false
    private var historyLoaded = false

    init {
        refreshApps(computeSizes = true)
    }

    fun setMonitoring(on: Boolean) {
        if (!on) {
            monitorJob?.cancel()
            monitorJob = null
            return
        }
        if (monitorJob?.isActive == true) return
        monitorJob = viewModelScope.launch {
            var lastAppRefresh = SystemClock.elapsedRealtime()
            while (isActive) {
                val speed = _speed.value
                if (speed != UpdateSpeed.PAUSED) {
                    val s = withContext(Dispatchers.IO) { monitor.sample() }
                    _snapshot.value = s
                    _history.value = _history.value.push(s)
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastAppRefresh > APP_REFRESH_MS) {
                        lastAppRefresh = now
                        refreshApps(computeSizes = false)
                    }
                }
                delay(if (speed == UpdateSpeed.PAUSED) 500 else speed.intervalMs)
            }
        }
    }

    /** Called when the app comes back to the foreground, e.g. after granting usage access. */
    fun onResume() {
        val access = repo.hasUsageAccess()
        val changed = access != _hasUsageAccess.value
        _hasUsageAccess.value = access
        refreshApps(computeSizes = changed)
        if (changed) {
            if (startupLoaded) loadStartup()
            if (historyLoaded) loadHistory()
        }
    }

    fun setSpeed(speed: UpdateSpeed) {
        _speed.value = speed
    }

    fun setShowSystem(show: Boolean) {
        _showSystem.value = show
    }

    fun refreshAll() {
        refreshApps(computeSizes = true)
        if (startupLoaded) loadStartup()
        if (historyLoaded) loadHistory()
    }

    fun refreshApps(computeSizes: Boolean) {
        if (appsJob?.isActive == true) return
        appsJob = viewModelScope.launch {
            val access = repo.hasUsageAccess()
            _hasUsageAccess.value = access
            val list = withContext(Dispatchers.IO) { repo.loadApps(access) }
            _apps.value = list.map { it.copy(storage = sizeCache[it.packageName]) }
            _appsLoading.value = false
            if (access && (computeSizes || !sizesLoaded)) {
                val sizes = withContext(Dispatchers.IO) { repo.loadSizes(list) }
                sizeCache.putAll(sizes)
                sizesLoaded = true
                _apps.value = _apps.value.map { it.copy(storage = sizeCache[it.packageName]) }
            }
        }
    }

    fun ensureStartupLoaded() {
        if (!startupLoaded) loadStartup()
    }

    private fun loadStartup() {
        startupLoaded = true
        viewModelScope.launch {
            _startupLoading.value = true
            _startup.value = withContext(Dispatchers.IO) { repo.loadStartup(repo.hasUsageAccess()) }
            _startupLoading.value = false
        }
    }

    fun ensureHistoryLoaded() {
        if (!historyLoaded) loadHistory()
    }

    fun setHistoryRange(range: HistoryRange) {
        if (range == _historyRange.value) return
        _historyRange.value = range
        loadHistory()
    }

    private fun loadHistory() {
        historyLoaded = true
        val range = _historyRange.value
        viewModelScope.launch {
            _historyLoading.value = true
            val result = withContext(Dispatchers.IO) { repo.loadHistory(range) }
            if (range == _historyRange.value) _appHistory.value = result
            _historyLoading.value = false
        }
    }

    fun killBackground(packageName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.killBackground(packageName) }
            delay(500)
            refreshApps(computeSizes = false)
        }
    }

    private companion object {
        const val APP_REFRESH_MS = 15_000L
    }
}
