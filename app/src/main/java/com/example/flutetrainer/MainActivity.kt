package com.example.flutetrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.flutetrainer.ui.theme.FluteTrainerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluteTrainerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var isQuizVisible by remember { mutableStateOf(false) }
                    SimpleMenuScreen(
                        onStartClick = { isQuizVisible = isQuizVisible xor true },
                        onSettingsClick = { /* Do nothing for preview */ },
                        onExitClick = { finishAffinity() },
                        modifier = Modifier.padding(innerPadding)
                    )
                    Greeting("Android", Modifier.padding(innerPadding), isQuizVisible)
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier, visible: Boolean) {
    if (visible) {
        Text(
            text = "Hello $name! huh?",
            modifier = modifier
        )
    }
}

@Composable
fun SimpleMenuScreen(
    onStartClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onExitClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to Flute Trainer!",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onStartClick) {
            Text("Start Quiz")
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onSettingsClick) {
            Text("Settings")
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onExitClick) {
            Text("Exit")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SimpleMenuScreenPreview() {
    SimpleMenuScreen(
        onStartClick = { /* Do nothing for preview */ },
        onSettingsClick = { /* Do nothing for preview */ },
        onExitClick = { /* Do nothing for preview */ }
    )
}