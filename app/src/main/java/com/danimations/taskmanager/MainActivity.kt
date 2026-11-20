package com.danimations.taskmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.danimations.taskmanager.ui.TaskManagerApp
import com.danimations.taskmanager.ui.theme.TaskManagerTheme

class MainActivity : ComponentActivity() {
    private val vm: TaskManagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TaskManagerTheme {
                TaskManagerApp(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.setMonitoring(true)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onStop() {
        vm.setMonitoring(false)
        super.onStop()
    }
}
