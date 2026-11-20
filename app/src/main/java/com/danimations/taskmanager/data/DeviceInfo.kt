package com.danimations.taskmanager.data

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import java.io.File
import java.util.Locale

data class InfoSection(val title: String, val rows: List<Pair<String, String>>)

object DeviceInfo {
    fun collect(context: Context): List<InfoSection> {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val cores = Runtime.getRuntime().availableProcessors()
        val cpuHardware = try {
            File("/proc/cpuinfo").readLines()
                .firstOrNull { it.startsWith("Hardware") || it.startsWith("model name") }
                ?.substringAfter(':')?.trim()
        } catch (e: Exception) {
            null
        }
        val maxFreq = (0 until cores).mapNotNull {
            try {
                File("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
            } catch (e: Exception) {
                null
            }
        }.maxOrNull()

        val device = mutableListOf(
            "Model" to Build.MODEL,
            "Manufacturer" to Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) },
            "Brand" to Build.BRAND,
            "Device" to Build.DEVICE,
            "Board" to Build.BOARD,
            "Hardware" to Build.HARDWARE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            device += "SoC" to "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim()
        }

        val software = listOf(
            "Android version" to Build.VERSION.RELEASE,
            "API level" to Build.VERSION.SDK_INT.toString(),
            "Security patch" to Build.VERSION.SECURITY_PATCH,
            "Build" to Build.DISPLAY,
            "Kernel" to (System.getProperty("os.version") ?: "Unknown"),
            "Runtime" to "ART ${System.getProperty("java.vm.version") ?: ""}".trim(),
            "Baseband" to (Build.getRadioVersion()?.takeIf { it.isNotBlank() } ?: "Unknown"),
            "Bootloader" to Build.BOOTLOADER,
        )

        val cpu = buildList {
            add("Cores" to cores.toString())
            maxFreq?.let { add("Max speed" to String.format(Locale.US, "%.2f GHz", it / 1_000_000.0)) }
            cpuHardware?.let { add("Processor" to it) }
            add("Instruction sets" to Build.SUPPORTED_ABIS.joinToString(", "))
        }

        val memory = listOf(
            "Installed RAM" to String.format(Locale.US, "%.1f GB", mem.totalMem / 1024.0 / 1024.0 / 1024.0),
            "Per-app heap limit" to "${am.memoryClass} MB (large: ${am.largeMemoryClass} MB)",
            "Low-RAM device" to if (am.isLowRamDevice) "Yes" else "No",
        )

        val display = try {
            val dm = context.getSystemService(DisplayManager::class.java)
            val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            d.getRealMetrics(metrics)
            listOf(
                "Resolution" to "${metrics.widthPixels} × ${metrics.heightPixels}",
                "Density" to "${metrics.densityDpi} dpi",
                "Refresh rate" to String.format(Locale.US, "%.0f Hz", d.refreshRate),
            )
        } catch (e: Exception) {
            emptyList()
        }

        return listOf(
            InfoSection("Device", device),
            InfoSection("Software", software),
            InfoSection("Processor", cpu),
            InfoSection("Memory", memory),
            InfoSection("Display", display),
        ).filter { it.rows.isNotEmpty() }
    }
}
