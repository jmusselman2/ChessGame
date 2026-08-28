package com.jmussel.chessgame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.jmussel.chessgame.app.ChessApp
import com.jmussel.chessgame.app.ChessAppDependencies
import com.jmussel.chessgame.app.ChessAppViewModel
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * The only screen Android knows about; everything inside it is [ChessApp].
 *
 * The activity holds no state of its own. What is showing and what it is built from live
 * in [ChessAppViewModel], which outlives this activity, so a rotation redraws the same app
 * rather than restarting it.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: ChessAppViewModel by viewModels {
        ChessAppViewModel.factory { ChessAppDependencies.create(applicationContext) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Started here rather than from composition, so a recomposition cannot ask for a
        // second session; the model ignores the call when it already has one.
        viewModel.start()

        setContent {
            ChessGameTheme {
                // A back press the app has nowhere to go with is the system's: the app closes.
                BackHandler { if (!viewModel.back()) finish() }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChessApp(
                        navigation = viewModel.navigation,
                        modifier = Modifier.padding(innerPadding),
                        startup = viewModel.startup,
                        usernameClaim = viewModel.usernameClaim,
                        onOpen = viewModel::open,
                        onBack = { if (!viewModel.back()) finish() },
                        onRetryStartup = viewModel::start,
                        onClaimUsername = viewModel::claimUsername,
                    )
                }
            }
        }
    }
}
