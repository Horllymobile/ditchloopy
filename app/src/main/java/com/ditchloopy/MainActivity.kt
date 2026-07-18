package com.ditchloopy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.ditchloopy.data.AppDatabase
import com.ditchloopy.data.InvitationRepository
import com.ditchloopy.ui.DitchLoopyApp
import com.ditchloopy.ui.DitchLoopyViewModel
import com.ditchloopy.ui.DitchLoopyViewModelFactory
import com.ditchloopy.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SQLite local database and repositories
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = InvitationRepository(
            database.invitationDao(),
            database.reflectionDao(),
            database.dailyCheckInDao()
        )

        // Initialize state manager with ViewModel Factory
        val viewModel: DitchLoopyViewModel by viewModels {
            DitchLoopyViewModelFactory(application, repository)
        }

        setContent {
            MyApplicationTheme {
                DitchLoopyApp(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
