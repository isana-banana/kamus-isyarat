package com.kamusisyarat.speechpractice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordingFile: java.io.File? = null
    private lateinit var referenceStore: AudioReferenceStore
    private var state by mutableStateOf(UiState())
    private var pendingReferenceRecording = false

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) {
            if (pendingReferenceRecording) {
                pendingReferenceRecording = false
                startReferenceRecording()
            } else {
                listen()
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        referenceStore = AudioReferenceStore(this)
        state = state.copy(references = referenceStore.list(state.target))
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Screen(
                        state,
                        { mode -> state = state.copy(mode = mode, result = "", confidence = null, score = null, status = "Siap") },
                        { target ->
                            state = state.copy(
                                target = target,
                                references = referenceStore.list(target),
                                result = "",
                                confidence = null,
                                score = null
                            )
                        },
                        ::requestAudio,
                        ::toggleReferenceRecording,
                        ::playReference,
                        ::deleteReference
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

    private fun toggleReferenceRecording() {
        if (state.recordingReference) stopReferenceRecording() else startReferenceRecording()
    }

    private fun startReferenceRecording() {
        if (state.target.isBlank()) {
            state = state.copy(status = "Isi target terlebih dahulu")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
            state = state.copy(status = "Izinkan mikrofon, lalu tekan Tambah contoh lagi")
            return
        }

        try {
            recognizer?.destroy()
            recognizer = null
            val file = referenceStore.createFile()
            val recorder = MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            recordingFile = file
            state = state.copy(
                recordingReference = true,
                listening = false,
                status = "Merekam contoh: ${state.target}"
            )
        } catch (e: Exception) {
            recordingFile?.delete()
            recordingFile = null
            mediaRecorder?.release()
            mediaRecorder = null
            state = state.copy(status = "Gagal memulai rekaman")
        }
    }

    private fun stopReferenceRecording() {
        val file = recordingFile
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
            file?.delete()
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
            recordingFile = null
        }

        if (file != null && file.exists() && file.length() > 1000) {
            referenceStore.add(state.target, file.name)
            state = state.copy(
                references = referenceStore.list(state.target),
                recordingReference = false,
                status = "Contoh suara tersimpan"
            )
        } else {
            file?.delete()
            state = state.copy(recordingReference = false, status = "Rekaman terlalu pendek")
        }
    }

    private fun playReference(ref: AudioReference) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(referenceStore.file(ref).absolutePath)
                prepare()
                setOnCompletionListener { release(); mediaPlayer = null }
                start()
            }
            state = state.copy(status = "Memutar ${ref.label}")
        } catch (_: Exception) {
            state = state.copy(status = "Audio tidak dapat diputar")
        }
    }

    private fun deleteReference(ref: AudioReference) {
        mediaPlayer?.release()
        mediaPlayer = null
        referenceStore.file(ref).delete()
        referenceStore.delete(state.target, ref.id)
        state = state.copy(
            references = referenceStore.list(state.target),
            status = "${ref.label} dihapus"
        )
    }

    private fun listen() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {
                    state = state.copy(status = "Mendengarkan…", listening = true)
                }
                override fun onBeginningOfSpeech() { state = state.copy(status = "Mendeteksi suara…") }
                override fun onEndOfSpeech() { state = state.copy(status = "Memproses…") }
                override fun onError(e: Int) {
                    state = state.copy(status = err(e), listening = false, confidence = null,
                        score = if (state.mode == Mode.PRACTICE) 0 else null)
                }
                override fun onResults(b: Bundle?) {
                    val results = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val t = results.firstOrNull().orEmpty()
                    val raw = b?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
                    val confidence = raw?.takeIf { it in 0f..1f }?.times(100f)?.roundToInt()
                    val score = if (state.mode == Mode.PRACTICE) {
                        results.maxOfOrNull { Similarity.score(state.target, it) } ?: 0
                    } else null
                    state = state.copy(result = t, confidence = confidence, score = score,
                        status = if (t.isBlank()) {
                            if (state.mode == Mode.PRACTICE) "Tidak dikenali — nilai 0%" else "Suara tidak membentuk kata"
                        } else if (state.mode == Mode.PRACTICE) scoreLabel(score ?: 0) else "Selesai",
                        listening = false)
                }
                override fun onPartialResults(b: Bundle?) {
                    val results = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val t = results.firstOrNull().orEmpty()
                    if (t.isNotBlank()) {
                        val score = if (state.mode == Mode.PRACTICE)
                            results.maxOfOrNull { Similarity.score(state.target, it) } ?: 0 else null
                        state = state.copy(result = t, score = score,
                            status = if (state.mode == Mode.PRACTICE) scoreLabel(score ?: 0) else "Mendengar…")
                    }
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

    private fun scoreLabel(score: Int) = when {
        score >= 80 -> "Hijau — hampir/sangat sesuai ($score%)"
        score >= 50 -> "Kuning — agak mirip ($score%)"
        else -> "Merah — belum sesuai ($score%)"
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
        mediaRecorder?.release()
        mediaPlayer?.release()
        super.onDestroy()
    }
}

private enum class Mode { PRACTICE, FREE }

private data class UiState(
    val mode: Mode = Mode.PRACTICE,
    val target: String = "ma",
    val result: String = "",
    val confidence: Int? = null,
    val score: Int? = null,
    val status: String = "Siap",
    val listening: Boolean = false,
    val recordingReference: Boolean = false,
    val references: List<AudioReference> = emptyList()
)

@Composable
private fun Screen(
    s: UiState,
    onMode: (Mode) -> Unit,
    onTarget: (String) -> Unit,
    onListen: () -> Unit,
    onRecordReference: () -> Unit,
    onPlayReference: (AudioReference) -> Unit,
    onDeleteReference: (AudioReference) -> Unit
) {
    val confidenceColor = when {
        s.confidence == null -> MaterialTheme.colorScheme.onSurfaceVariant
        s.confidence >= 80 -> Color(0xFF2E7D32)
        s.confidence >= 50 -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Latihan Bicara", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(s.mode == Mode.PRACTICE, { onMode(Mode.PRACTICE) }, label = { Text("Latihan") })
            FilterChip(s.mode == Mode.FREE, { onMode(Mode.FREE) }, label = { Text("Bebas") })
        }
        Spacer(Modifier.height(14.dp))

        if (s.mode == Mode.PRACTICE) {
            OutlinedTextField(
                value = s.target,
                onValueChange = onTarget,
                label = { Text("Kata / kalimat target") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Contoh suara", fontWeight = FontWeight.Bold)
                    Text(
                        "Guru/orang tua dapat merekam beberapa contoh. Anak menirukan suara ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRecordReference, modifier = Modifier.fillMaxWidth()) {
                        Text(if (s.recordingReference) "⏹ Selesai & simpan contoh" else "🎙 Tambah contoh audio")
                    }

                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 176.dp).verticalScroll(rememberScrollState())) {
                    s.references.forEach { ref ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { onPlayReference(ref) }, modifier = Modifier.weight(1f)) {
                                Text("▶ ${ref.label}")
                            }
                            OutlinedButton(onClick = { onDeleteReference(ref) }) {
                                Text("Hapus")
                            }
                        }
                    }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (s.mode == Mode.PRACTICE) "Target ucapan" else "Ucapan bebas", style = MaterialTheme.typography.titleMedium)
                if (s.mode == Mode.PRACTICE) {
                    Spacer(Modifier.height(5.dp))
                    Text(s.target.ifBlank { "—" }, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
                Text("Hasil yang didengar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(if (s.result.isBlank()) "—" else "“${s.result}”", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                if (s.mode == Mode.PRACTICE) {
                    val scoreColor = when {
                        s.score == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        s.score >= 80 -> Color(0xFF2E7D32)
                        s.score >= 50 -> Color(0xFFF9A825)
                        else -> Color(0xFFC62828)
                    }
                    Text(
                        if (s.score == null) "—" else "${s.score}%",
                        style = MaterialTheme.typography.displaySmall,
                        color = scoreColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Nilai kesesuaian ucapan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                Text(if (s.confidence == null) "—" else "${s.confidence}%", style = MaterialTheme.typography.titleLarge, color = confidenceColor, fontWeight = FontWeight.Bold)
                Text("Keyakinan mesin mengenali ucapan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onListen,
            enabled = !s.listening && !s.recordingReference,
            modifier = Modifier.size(width = 190.dp, height = 58.dp),
            shape = RoundedCornerShape(30.dp)
        ) {
            Text(if (s.listening) "Mendengarkan…" else "🎤  Mulai", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        Text(s.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
