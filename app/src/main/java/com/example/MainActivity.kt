package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.CompanionDatabase
import com.example.data.CompanionRepository
import com.example.receiver.CompanionScheduler
import com.example.ui.DashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CompanionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup simple Room construction injection
        val database = CompanionDatabase.getDatabase(applicationContext)
        val repository = CompanionRepository(database.dao())
        
        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(CompanionViewModel::class.java)) {
                    return CompanionViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
        
        val viewModel = ViewModelProvider(this, viewModelFactory)[CompanionViewModel::class.java]
        
        // Schedule native Android system tray alarms
        CompanionScheduler.scheduleAlarms(applicationContext)

        // Runtime permission request for Android 13+ (Post Notifications)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                // System notification permission status completed
            }
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
