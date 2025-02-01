package com.example.flutetrainer

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.flutetrainer.ui.theme.FluteTrainerTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import kotlin.math.sqrt
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluteTrainerTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "menu") {
                    composable("menu") {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            var isQuizVisible by remember { mutableStateOf(false) }
                            SimpleMenuScreen(
                                onStartClick = { navController.navigate("quiz") },
                                onSettingsClick = { isQuizVisible = isQuizVisible xor true },
                                onExitClick = { finishAffinity() },
                                modifier = Modifier.padding(innerPadding)
                            )
                            Greeting("Android", Modifier.padding(innerPadding), isQuizVisible)
                        }
                    }
                    composable("quiz") {
                        QuizScreen()
                    }
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QuizScreen() {
    // Get the current context.
    val context = LocalContext.current

    // Check for the RECORD_AUDIO permission using Accompanist Permissions.
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    if (!audioPermissionState.status.isGranted) {
        LaunchedEffect(Unit) {
            audioPermissionState.launchPermissionRequest()
        }
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Please grant microphone access to detect frequency.")
            }
        }
        return
    }

    var frequency by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        // Pass the context to the AudioAnalyzer.
        val analyzer = AudioAnalyzer(context) { freq ->
            frequency = freq
        }
        analyzer.start()

        onDispose {
            analyzer.stop()
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (frequency > 0f)
                    "Detected frequency: ${"%.2f".format(frequency)} Hz"
                else
                    "Listening… (no frequency detected)"
            )
        }
    }
}

class AudioAnalyzer(
    private val context: Context,
    private val sampleRate: Int = 44100,
    private val onFrequencyDetected: (Float) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false

    fun start() {
        // Check for RECORD_AUDIO permission.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("AudioAnalyzer", "RECORD_AUDIO permission not granted")
            return
        }
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )


        audioRecord?.startRecording()
        isRecording = true

        Thread {
            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val frequency = detectPitch(buffer, read, sampleRate)
                    // Report the frequency on the main thread if needed.
                    onFrequencyDetected(frequency)
                }
            }
        }.start()
    }

    fun stop() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    /**
     * A simple autocorrelation-based pitch detection.
     * @param buffer Audio sample buffer.
     * @param read   Number of samples read.
     * @param sampleRate The audio sample rate.
     * @return Detected frequency in Hertz, or 0 if no pitch is detected.
     */
    private fun detectPitch(buffer: ShortArray, read: Int, sampleRate: Int): Float {
        // Compute RMS to check for sufficient signal
        var sumSquares = 0.0
        for (i in 0 until read) {
            sumSquares += (buffer[i] * buffer[i]).toDouble()
        }
        val rms = sqrt(sumSquares / read)
        if (rms < 10) { // low amplitude: consider it silence
            return 0f
        }

        var bestOffset = 0
        var bestCorrelation = 0.0
        var lastCorrelation = 1.0
        var foundGoodCorrelation = false

        // Loop over possible offsets (lags)
        for (offset in 1 until read) {
            var correlation = 0.0
            for (i in 0 until read - offset) {
                correlation += kotlin.math.abs(buffer[i].toDouble() - buffer[i + offset].toDouble())
            }
            // Normalize: 0 means perfect correlation
            correlation = 1 - (correlation / (read - offset)) / 32768.0

            if (correlation > 0.9 && correlation > lastCorrelation) {
                foundGoodCorrelation = true
                bestCorrelation = correlation
                bestOffset = offset
            } else if (foundGoodCorrelation) {
                // Assume the first drop after the peak is our best estimate.
                break
            }
            lastCorrelation = correlation
        }
        return if (bestOffset != 0) sampleRate.toFloat() / bestOffset.toFloat() else 0f
    }
}