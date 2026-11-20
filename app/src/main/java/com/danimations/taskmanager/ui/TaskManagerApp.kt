package com.danimations.taskmanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danimations.taskmanager.TaskManagerViewModel
import com.danimations.taskmanager.UpdateSpeed

private enum class Page(val label: String, val icon: ImageVector) {
    PROCESSES("Processes", Icons.Outlined.Apps),
    PERFORMANCE("Performance", Icons.Outlined.Speed),
    HISTORY("History", Icons.Outlined.History),
    STARTUP("Startup", Icons.Outlined.PowerSettingsNew),
    DETAILS("Details", Icons.Outlined.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskManagerApp(vm: TaskManagerViewModel) {
    var page by rememberSaveable { mutableStateOf(Page.PROCESSES) }
    var menuOpen by remember { mutableStateOf(false) }
    val speed by vm.speed.collectAsState()
    val showSystem by vm.showSystem.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (page == Page.PROCESSES) "Task Manager" else page.label,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { vm.refreshAll() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            Text(
                                "Real-time update speed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            UpdateSpeed.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    onClick = {
                                        vm.setSpeed(s)
                                        menuOpen = false
                                    },
                                    trailingIcon = {
                                        if (s == speed) Icon(Icons.Filled.Check, contentDescription = "Selected")
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Show system apps") },
                                onClick = {
                                    vm.setShowSystem(!showSystem)
                                    menuOpen = false
                                },
                                trailingIcon = {
                                    if (showSystem) Icon(Icons.Filled.Check, contentDescription = "On")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Usage access settings") },
                                onClick = {
                                    menuOpen = false
                                    openUsageAccessSettings(context)
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Page.entries.forEach { p ->
                    NavigationBarItem(
                        selected = page == p,
                        onClick = { page = p },
                        icon = { Icon(p.icon, contentDescription = null) },
                        label = { Text(p.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (page) {
                Page.PROCESSES -> ProcessesScreen(vm)
                Page.PERFORMANCE -> PerformanceScreen(vm)
                Page.HISTORY -> AppHistoryScreen(vm)
                Page.STARTUP -> StartupScreen(vm)
                Page.DETAILS -> DetailsScreen(vm)
            }
        }
    }
}
