package com.kamusisyarat.speechpractice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var recognizer: SpeechRecognizer? = null
    private var state by mutableStateOf(UiState())
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) listen()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Screen(
                        state,
                        { state = state.copy(mode = it, result = "", confidence = null, status = "Siap") },
                        { state = state.copy(target = it) },
                        ::requestAudio
                    )
                }
            }
        }
    }

    private fun requestAudio() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listen()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun listen() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {
                    state = state.copy(status = "Mendengarkan…", listening = true)
                }

                override fun onBeginningOfSpeech() {
                    state = state.copy(status = "Mendeteksi suara…")
                }

                override fun onEndOfSpeech() {
                    state = state.copy(status = "Memproses…")
                }

                override fun onError(e: Int) {
                    state = state.copy(status = err(e), listening = false, confidence = null)
                }

                override fun onResults(b: Bundle?) {
                    val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    val raw = b?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        ?.firstOrNull()
                    val confidence = raw?.takeIf { it in 0f..1f }?.times(100f)?.roundToInt()

                    state = state.copy(
                        result = t,
                        confidence = confidence,
                        status = if (t.isBlank()) "Suara tidak membentuk kata" else "Selesai",
                        listening = false
                    )
                }

                override fun onPartialResults(b: Bundle?) {
                    val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (t.isNotBlank()) state = state.copy(result = t)
                }

                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEvent(t: Int, b: Bundle?) {}
            })
        }

        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        state = state.copy(listening = true, result = "", confidence = null, status = "Memulai…")
        recognizer?.startListening(i)
    }

    private fun err(e: Int) = when (e) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Suara tidak membentuk kata"
        SpeechRecognizer.ERROR_AUDIO -> "Kesalahan audio"
        SpeechRecognizer.ERROR_NETWORK -> "Jaringan bermasalah"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada ucapan"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon diperlukan"
        else -> "Pengenalan suara gagal"
    }

    override fun onDestroy() {
        recognizer?.destroy()
        super.onDestroy()
    }
}

private enum class Mode { PRACTICE, FREE }

private data class UiState(
    val mode: Mode = Mode.PRACTICE,
    val target: String = "ma",
    val result: String = "",
    val confidence: Int? = null,
    val status: String = "Siap",
    val listening: Boolean = false
)

@Composable
private fun Screen(
    s: UiState,
    onMode: (Mode) -> Unit,
    onTarget: (String) -> Unit,
    onListen: () -> Unit
) {
    val confidenceColor = when {
        s.confidence == null -> MaterialTheme.colorScheme.onSurfaceVariant
        s.confidence >= 80 -> Color(0xFF2E7D32)
        s.confidence >= 50 -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Latihan Bicara", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = s.mode == Mode.PRACTICE,
                onClick = { onMode(Mode.PRACTICE) },
                label = { Text("Latihan") }
            )
            FilterChip(
                selected = s.mode == Mode.FREE,
                onClick = { onMode(Mode.FREE) },
                label = { Text("Bebas") }
            )
        }

        Spacer(Modifier.height(18.dp))

        if (s.mode == Mode.PRACTICE) {
            OutlinedTextField(
                value = s.target,
                onValueChange = onTarget,
                label = { Text("Kata / kalimat target") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (s.mode == Mode.PRACTICE) "Target ucapan" else "Ucapan bebas",
                    style = MaterialTheme.typography.titleMedium
                )

                if (s.mode == Mode.PRACTICE) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        s.target.ifBlank { "—" },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    "Hasil yang didengar",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (s.result.isBlank()) "—" else "“" + s.result + "”",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    if (s.confidence == null) "—" else s.confidence.toString() + "%",
                    style = MaterialTheme.typography.displaySmall,
                    color = confidenceColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Keyakinan mesin mengenali ucapan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onListen,
            enabled = !s.listening,
            modifier = Modifier.size(width = 190.dp, height = 58.dp),
            shape = RoundedCornerShape(30.dp)
        ) {
            Text(
                if (s.listening) "Mendengarkan…" else "🎤  Mulai",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(s.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
