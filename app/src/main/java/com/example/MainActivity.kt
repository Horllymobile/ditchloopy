package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.InvitationRepository
import com.example.ui.DitchLoopyApp
import com.example.ui.DitchLoopyViewModel
import com.example.ui.DitchLoopyViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SQLite local database and repositories
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = InvitationRepository(database.invitationDao(), database.reflectionDao())

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
