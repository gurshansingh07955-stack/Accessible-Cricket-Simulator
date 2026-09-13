package com.cricketsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CricketData

/**
 * Placeholder entry point. This repo is currently just a home for the
 * ported game-logic layer (see PORTING_NOTES.md) -- there is no real
 * gameplay UI yet. This screen exists only to prove the Gradle/Kotlin
 * wiring works end to end and that CricketData loads correctly.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LogicPortStatusScreen()
                }
            }
        }
    }
}

@Composable
fun LogicPortStatusScreen() {
    val teams = CricketData.getAllTeams()
    val totalPlayers = teams.sumOf { it.players.size }
    val summary = "${teams.size} teams loaded, ${totalPlayers} players"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Accessible Cricket Simulator", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Native Kotlin port \u2014 logic layer in progress")
        Spacer(modifier = Modifier.height(24.dp))
        Text(summary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("No gameplay UI yet \u2014 see PORTING_NOTES.md")
    }
}
