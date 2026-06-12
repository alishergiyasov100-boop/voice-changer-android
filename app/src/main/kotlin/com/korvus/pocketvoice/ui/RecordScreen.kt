package com.korvus.pocketvoice.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.korvus.pocketvoice.PocketVoiceApp
import com.korvus.pocketvoice.api.RemoteVoiceServer
import com.korvus.pocketvoice.audio.Pcm
import com.korvus.pocketvoice.audio.Recorder
import com.korvus.pocketvoice.data.Voice
import com.korvus.pocketvoice.onnx.DownloadState
import com.korvus.pocketvoice.ui.theme.CrimsonError
import com.korvus.pocketvoice.ui.theme.VioletDeep
import com.korvus.pocketvoice.ui.theme.VioletLight
import com.korvus.pocketvoice.ui.theme.VioletPale
import com.korvus.pocketvoice.ui.theme.VioletPrimary
import kotlinx.coroutines.launch
import java.io.File

private enum class Phase { Idle, Recording, Processing, Done, Error }

@Composable
fun RecordScreen() {
    val ctx = LocalContext.current
    val app = PocketVoiceApp.instance
    val scope = rememberCoroutineScope()

    val voices by app.voiceStore.items.collectAsState()
    val activeVoiceId by app.settings.activeVoiceId.collectAsState(initial = null)
    val activeVoice = voices.firstOrNull { it.id == activeVoiceId }
    val downloadState by app.converter.downloadState.collectAsState()
    var modelReady by remember { mutableStateOf(app.converter.isReady()) }

    var phase by remember { mutableStateOf(Phase.Idle) }
    var srcFile by remember { mutableStateOf<File?>(null) }
    var outUrl by remember { mutableStateOf<String?>(null) }
    var errMsg by remember { mutableStateOf<String?>(null) }
    val recorder = remember { Recorder(ctx) }

    LaunchedEffect(Unit) {
        if (!app.converter.isReady()) app.converter.ensureDownloaded()
        modelReady = app.converter.isReady()
    }
    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Ready) modelReady = true
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    suspend fun runConvert(src: File, voice: Voice) {
        phase = Phase.Processing
        val refFile = app.voiceStore.fileOf(voice)
        try {
            val snap = app.settings.snapshot()
            val outFile = File(ctx.cacheDir, "out_${System.currentTimeMillis()}.wav")
            if (snap.serverOn && snap.serverUrl.isNotBlank()) {
                // Серверный режим — Colab/Kaggle GPU
                val server = RemoteVoiceServer(
                    baseUrl = snap.serverUrl,
                    pitchShift = snap.pitch,
                )
                val bytes = server.convert(src)
                outFile.writeBytes(bytes)
            } else {
                // Локальный OpenVoice ONNX
                val audio = app.converter.convert(src, refFile, tau = 0.8f)
                Pcm.writeWav(audio, outFile)
            }
            outUrl = "file://${outFile.absolutePath}"
            phase = Phase.Done
        } catch (t: Throwable) {
            val trace = java.io.StringWriter().also { t.printStackTrace(java.io.PrintWriter(it)) }.toString()
            errMsg = (t.message ?: t.toString()) + "\n\n" + trace.take(700)
            phase = Phase.Error
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Hero violet
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(colors = listOf(VioletPrimary, VioletLight)),
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                )
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "POCKETVOICE",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (activeVoice != null) activeVoice.name else "Выбери голос",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (activeVoice != null) "${activeVoice.emoji}  готов перевоплотиться"
                    else "вкладка Голоса → импорт MP3",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Download progress
        val dl = downloadState
        if (!modelReady && dl is DownloadState.Downloading) {
            val pct = (dl.overallDone * 100 / dl.overallTotal.coerceAtLeast(1)).toInt()
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    "ПОДГОТОВКА · $pct%",
                    color = VioletDeep, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(VioletPale)) {
                    Box(modifier = Modifier.fillMaxWidth(pct / 100f).height(4.dp).background(VioletPrimary))
                }
            }
            Spacer(Modifier.height(20.dp))
        } else if (!modelReady && dl is DownloadState.Error) {
            Text(
                "Не подготовилось: ${dl.msg}",
                color = CrimsonError, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        // Hint
        Text(
            when {
                !modelReady -> "Готовлю OpenVoice (~160 МБ из APK)…"
                voices.isEmpty() -> "Импортируй голос на вкладке Голоса"
                activeVoice == null -> "Выбери голос на вкладке Голоса"
                phase == Phase.Recording -> "Запись… отпусти когда готов"
                phase == Phase.Processing -> "Конвертирую локально…"
                phase == Phase.Done -> "Готово ✓"
                phase == Phase.Error -> "Ошибка — попробуй снова"
                else -> "Зажми кнопку, говори, отпусти"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(28.dp))

        // Record button
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RecordButton(
                phase = phase,
                onPressStart = {
                    if (!modelReady) return@RecordButton
                    if (activeVoice == null) return@RecordButton
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                        permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@RecordButton
                    }
                    try {
                        srcFile = recorder.start()
                        phase = Phase.Recording
                    } catch (t: Throwable) { errMsg = t.message; phase = Phase.Error }
                },
                onPressEnd = {
                    if (phase != Phase.Recording) return@RecordButton
                    val f = recorder.stop()
                    val v = activeVoice
                    if (f != null && v != null) {
                        srcFile = f
                        scope.launch { runConvert(f, v) }
                    } else { phase = Phase.Idle }
                },
            )
        }

        Spacer(Modifier.height(28.dp))

        AnimatedVisibility(visible = phase == Phase.Done && outUrl != null) {
            ResultCard(srcPath = srcFile?.absolutePath, outUrl = outUrl)
        }
        AnimatedVisibility(visible = phase == Phase.Error) {
            ErrorCard(msg = errMsg ?: "?") { phase = Phase.Idle; errMsg = null }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun RecordButton(
    phase: Phase,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
) {
    val recording = phase == Phase.Recording
    val processing = phase == Phase.Processing
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(recording) {
        if (recording) {
            pulseScale.animateTo(
                1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                )
            )
        } else { pulseScale.snapTo(1f) }
    }
    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(pulseScale.value)
            .clip(RoundedCornerShape(50))
            .background(
                brush = Brush.linearGradient(
                    colors = if (recording) listOf(CrimsonError, Color(0xFFFF8585))
                             else if (processing) listOf(VioletPale, VioletPale)
                             else listOf(VioletPrimary, VioletLight),
                )
            )
            .pointerInput(processing) {
                if (processing) return@pointerInput
                detectTapGestures(
                    onPress = {
                        onPressStart()
                        try { tryAwaitRelease() } finally { onPressEnd() }
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (processing) {
            CircularProgressIndicator(color = VioletPrimary, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Mic, contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (recording) "REC" else "HOLD",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(srcPath: String?, outUrl: String?) {
    val player = remember { MediaPlayer() }
    LaunchedEffect(outUrl) {
        if (outUrl != null) {
            try {
                player.reset(); player.setDataSource(outUrl)
                player.setOnPreparedListener { it.start() }
                player.prepareAsync()
            } catch (_: Throwable) {}
        }
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VioletPale)
            .padding(18.dp),
    ) {
        Text("РЕЗУЛЬТАТ", color = VioletDeep.copy(alpha = 0.7f), fontSize = 10.sp,
            fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Чужой голос", color = VioletDeep, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                player.reset()
                outUrl?.let { player.setDataSource(it); player.prepare(); player.start() }
            }) { Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = VioletPrimary) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Оригинал", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                player.reset()
                srcPath?.let { player.setDataSource(it); player.prepare(); player.start() }
            }) { Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ErrorCard(msg: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFFFEDED))
            .padding(18.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
    ) {
        Text("⚠ ОШИБКА", color = CrimsonError, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Text(msg, color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Text("тап чтобы скрыть", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}
