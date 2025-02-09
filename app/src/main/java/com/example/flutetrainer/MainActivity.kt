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
import androidx.core.content.ContextCompat
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import be.tarsos.dsp.pitch.Yin
import com.example.flutetrainer.ui.theme.FluteTrainerTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import kotlin.math.sqrt
import com.google.accompanist.permissions.rememberPermissionState
import kotlin.math.abs

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
        val analyzer = AudioAnalyzer(context)
        { freq ->
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
                    "Listening… (no frequency detected)${"%.2f".format(frequency)} Hz"
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

    // Minimum volume (RMS) threshold.
    private val minVolumeThreshold = 400

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

        // Check that AudioRecord is properly initialized.
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("AudioAnalyzer", "AudioRecord initialization failed")
            return
        }

        audioRecord?.startRecording()
        isRecording = true

        Thread {
            val buffer = ShortArray(bufferSize)
            // Variables for consistency checking.
            var stableFrequency = 0f
            var stableCount = 0
            val requiredStableCount = 2   // Require 3 consistent readings.
            val tolerance = 2.0f           // Allowed Hz difference between readings.

            // Reset delay (in milliseconds) for clearing the frequency if nothing changes.
            val resetDelay = 2000L // 2 seconds.
            var lastDetectionTime = System.currentTimeMillis()

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // Compute RMS for volume check.
                    var sumSquares = 0.0
                    for (i in 0 until read) {
                        sumSquares += (buffer[i] * buffer[i]).toDouble()
                    }
                    val rms = sqrt(sumSquares / read)

                    // Only process if the volume is high enough.
                    if (rms < minVolumeThreshold) {
                        // If we've been quiet for longer than the resetDelay, reset.
                        if (System.currentTimeMillis() - lastDetectionTime > resetDelay) {
                            if (stableFrequency != 0f) {
                                stableFrequency = 0f
                                stableCount = 0
                                onFrequencyDetected(0f)
                                lastDetectionTime = System.currentTimeMillis()
                            }
                        }
                        continue
                    }

                    // Detect the frequency from the current buffer.
                    val freq = detectPitch(buffer, read, sampleRate)
                    if (freq == 0f || freq > 2500f) {
                        stableCount = 0
                        continue
                    }

                    // Update stability: if the new frequency is close enough, increase the counter.
                    if (stableFrequency == 0f) {
                        stableFrequency = freq
                        stableCount = 1
                    } else if (abs(freq - stableFrequency) <= tolerance) {
                        stableCount++
                        stableFrequency = (stableFrequency * (stableCount - 1) + freq) / stableCount
                    } else {
                        // Reset if the new reading is too far off.
                        stableFrequency = freq
                        stableCount = 1
                    }

                    // Once we've reached a stable reading, report it and update the detection time.
                    if (stableCount >= requiredStableCount) {
                        onFrequencyDetected(stableFrequency)
                        lastDetectionTime = System.currentTimeMillis()
                    }
                }

                // Outside of reading a new buffer, check if too much time has passed without updates.
                if (System.currentTimeMillis() - lastDetectionTime > resetDelay) {
                    if (stableFrequency != 0f) {
                        stableFrequency = 0f
                        stableCount = 0
                        lastDetectionTime = System.currentTimeMillis()
                        onFrequencyDetected(0f)
                    }
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
     *
     * @param buffer Audio sample buffer.
     * @param read   Number of samples read.
     * @param sampleRate The audio sample rate.
     * @return Detected frequency in Hertz, or 0 if no pitch is detected.
     */
    private fun detectPitch(buffer: ShortArray, read: Int, sampleRate: Int): Float {
        // First, perform a quick RMS check to see if volume is high enough:
        var sumSquares = 0.0
        for (i in 0 until read) {
            sumSquares += buffer[i] * buffer[i]
        }
        val rms = sqrt(sumSquares / read)
        if (rms < minVolumeThreshold) {
            // If volume is below threshold, return 0.
            return 0f
        }

        // Convert from ShortArray (-32768..32767) to FloatArray (-1..1).
        val floatBuffer = FloatArray(read)
        for (i in 0 until read) {
            floatBuffer[i] = buffer[i] / 32768f
        }

        // Create a YIN detector with TarsosDSP
        // The second parameter is typically the size of the buffer or 'frame size'.
        val yin = Yin(sampleRate.toFloat(), read)

        // Run pitch detection
        val result = yin.getPitch(floatBuffer)

        // If Tarsos says there's a pitched result, return it. Otherwise return 0.
        return if (result.isPitched) result.pitch else 0f
    }
}