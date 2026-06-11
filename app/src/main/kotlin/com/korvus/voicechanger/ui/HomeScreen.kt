package com.korvus.voicechanger.ui

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.korvus.voicechanger.VoiceChangerApp
import com.korvus.voicechanger.api.SeedVCClient
import com.korvus.voicechanger.audio.Recorder
import com.korvus.voicechanger.data.Settings
import com.korvus.voicechanger.data.Voice
import com.korvus.voicechanger.ui.theme.Accent
import com.korvus.voicechanger.ui.theme.Bg
import com.korvus.voicechanger.ui.theme.BgCard
import com.korvus.voicechanger.ui.theme.BgElev
import com.korvus.voicechanger.ui.theme.Ink
import com.korvus.voicechanger.ui.theme.InkSoft
import com.korvus.voicechanger.ui.theme.Line
import com.korvus.voicechanger.ui.theme.Warn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Phase { Idle, Recording, Processing, Done, Error }

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val app = VoiceChangerApp.instance
    val scope = rememberCoroutineScope()

    val voices by app.voiceStore.items.collectAsState()
    val activeVoiceId by app.settings.activeVoiceId.collectAsState(initial = null)
    val activeVoice = voices.firstOrNull { it.id == activeVoiceId }

    var phase by remember { mutableStateOf(Phase.Idle) }
    var srcFile by remember { mutableStateOf<File?>(null) }
    var outUrl by remember { mutableStateOf<String?>(null) }
    var errMsg by remember { mutableStateOf<String?>(null) }
    var elapsedSec by remember { mutableStateOf(0f) }
    var settingsOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val recorder = remember { Recorder(ctx) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* user will tap again */ }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            addOpen = true
        }
    }

    suspend fun runConvert(src: File, voice: Voice) {
        phase = Phase.Processing
        elapsedSec = 0f
        val snap = app.settings.snapshot()
        val refFile = app.voiceStore.fileOf(voice)
        try {
            val client = SeedVCClient(
                space = snap.space,
                steps = snap.steps,
                lengthAdjust = snap.lenPct / 100f,
                pitchShift = snap.pitch,
            )
            val url = client.convert(src, refFile)
            outUrl = url
            phase = Phase.Done
        } catch (t: Throwable) {
            errMsg = t.message ?: t.toString()
            phase = Phase.Error
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState()),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp).clip(RoundedCornerShape(50)).background(Accent),
            )
            Spacer(Modifier.width(10.dp))
            Text("VOICE CHANGER", color = Ink, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.4.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { settingsOpen = true }) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = InkSoft)
            }
        }

        // Voice picker
        Text(
            "ГОЛОС",
            color = InkSoft,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(voices, key = { _, v -> v.id }) { _, v ->
                VoiceCard(
                    voice = v,
                    active = v.id == activeVoiceId,
                    onClick = { scope.launch { app.settings.setActiveVoice(v.id) } },
                    onDelete = { scope.launch {
                        app.voiceStore.delete(v.id)
                        if (activeVoiceId == v.id) app.settings.setActiveVoice(null)
                    } },
                )
            }
            itemsIndexed(listOf("add")) { _, _ ->
                AddVoiceCard { picker.launch("audio/*") }
            }
        }
        Spacer(Modifier.height(28.dp))

        // Status / Hint
        val hint = when {
            voices.isEmpty() -> "Импортируй MP3 голоса ↑"
            activeVoice == null -> "Выбери голос ↑"
            phase == Phase.Idle -> "Зажми, говори, отпусти."
            phase == Phase.Recording -> "Запись… отпусти когда готов."
            phase == Phase.Processing -> "Конвертирую через Seed-VC…"
            phase == Phase.Done -> "Готово ✓"
            phase == Phase.Error -> "Ошибка — попробуй ещё."
            else -> ""
        }
        Text(
            hint,
            color = InkSoft,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(20.dp))

        // Record button
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RecordButton(
                phase = phase,
                onPressStart = {
                    if (activeVoice == null) return@RecordButton
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                        permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@RecordButton
                    }
                    try {
                        srcFile = recorder.start()
                        phase = Phase.Recording
                    } catch (t: Throwable) {
                        errMsg = t.message; phase = Phase.Error
                    }
                },
                onPressEnd = {
                    if (phase != Phase.Recording) return@RecordButton
                    val f = recorder.stop()
                    val voice = activeVoice
                    if (f != null && voice != null) {
                        srcFile = f
                        scope.launch { runConvert(f, voice) }
                    } else {
                        phase = Phase.Idle
                    }
                },
            )
        }

        Spacer(Modifier.height(28.dp))

        // Result card
        AnimatedVisibility(visible = phase == Phase.Done && outUrl != null) {
            ResultCard(srcPath = srcFile?.absolutePath, outUrl = outUrl)
        }
        AnimatedVisibility(visible = phase == Phase.Error) {
            ErrorCard(msg = errMsg ?: "?") { phase = Phase.Idle; errMsg = null }
        }
        Spacer(Modifier.height(40.dp))
    }

    if (settingsOpen) {
        SettingsSheet(onDismiss = { settingsOpen = false })
    }
    if (addOpen && pendingUri != null) {
        AddVoiceDialog(
            onCancel = { addOpen = false; pendingUri = null },
            onSave = { name ->
                val uri = pendingUri ?: return@AddVoiceDialog
                addOpen = false; pendingUri = null
                scope.launch {
                    try {
                        val v = withContext(Dispatchers.IO) {
                            app.voiceStore.addFromUri(ctx, uri, name)
                        }
                        app.settings.setActiveVoice(v.id)
                    } catch (t: Throwable) {
                        errMsg = "import: ${t.message}"; phase = Phase.Error
                    }
                }
            }
        )
    }
}

@Composable
private fun VoiceCard(voice: Voice, active: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Box(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Accent.copy(alpha = 0.10f) else BgCard)
            .border(
                1.5.dp,
                if (active) Accent else Line,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Column {
            Text(voice.emoji, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                voice.name,
                color = Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
            )
            Text(
                "${voice.sizeBytes / 1024} KB" + if (voice.builtin) " · встроен" else "",
                color = InkSoft, fontSize = 10.sp,
            )
        }
        if (!voice.builtin) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp),
            ) {
                Text("✕", color = InkSoft, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AddVoiceCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(110.dp).height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+ MP3", color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
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
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                )
            )
        } else {
            pulseScale.snapTo(1f)
        }
    }
    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(pulseScale.value)
            .clip(RoundedCornerShape(50))
            .background(if (recording) Warn else if (processing) BgCard else Accent)
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
            CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Mic, contentDescription = null,
                    tint = if (recording) Color.White else Bg,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (recording) "REC" else "HOLD",
                    color = if (recording) Color.White else Bg,
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
    var playingOut by remember { mutableStateOf(false) }
    var playingSrc by remember { mutableStateOf(false) }

    LaunchedEffect(outUrl) {
        if (outUrl != null) {
            try {
                player.reset()
                player.setDataSource(outUrl)
                player.setOnPreparedListener {
                    it.start(); playingOut = true
                }
                player.setOnCompletionListener { playingOut = false }
                player.prepareAsync()
            } catch (_: Throwable) {}
        }
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .padding(18.dp),
    ) {
        Text(
            "РЕЗУЛЬТАТ", color = InkSoft, fontSize = 10.sp,
            fontWeight = FontWeight.Black, letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Чужой голос", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                player.reset()
                outUrl?.let { player.setDataSource(it); player.prepare(); player.start(); playingOut = true }
            }) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "play", tint = Accent)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Оригинал", color = InkSoft, fontSize = 12.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                player.reset()
                srcPath?.let { player.setDataSource(it); player.prepare(); player.start() }
            }) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "play orig", tint = InkSoft)
            }
        }
    }
}

@Composable
private fun ErrorCard(msg: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.dp, Warn.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Text("⚠ ОШИБКА", color = Warn, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Text(msg, color = Ink, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onDismiss) {
            Text("OK", color = Accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingsSheet(onDismiss: () -> Unit) {
    val app = VoiceChangerApp.instance
    val scope = rememberCoroutineScope()
    val space by app.settings.space.collectAsState(initial = Settings.DEFAULT_SPACE)
    val steps by app.settings.steps.collectAsState(initial = 25)
    val lenPct by app.settings.lenPct.collectAsState(initial = 100)
    val pitch by app.settings.pitch.collectAsState(initial = 0)

    var spaceVal by remember(space) { mutableStateOf(space) }
    var stepsVal by remember(steps) { mutableStateOf(steps.toFloat()) }
    var lenVal by remember(lenPct) { mutableStateOf(lenPct.toFloat()) }
    var pitchVal by remember(pitch) { mutableStateOf(pitch.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElev,
        title = { Text("Настройки", color = Ink, fontWeight = FontWeight.Black) },
        text = {
            Column {
                OutlinedTextField(
                    value = spaceVal,
                    onValueChange = { spaceVal = it },
                    label = { Text("HF Space (username/repo)", color = InkSoft) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Diffusion Steps: ${stepsVal.toInt()}", color = InkSoft, fontSize = 12.sp)
                Slider(value = stepsVal, onValueChange = { stepsVal = it }, valueRange = 10f..60f,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
                Text("Length Adjust: ${(lenVal / 100f).format(2)}", color = InkSoft, fontSize = 12.sp)
                Slider(value = lenVal, onValueChange = { lenVal = it }, valueRange = 50f..200f,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
                Text("Pitch Shift: ${pitchVal.toInt()}", color = InkSoft, fontSize = 12.sp)
                Slider(value = pitchVal, onValueChange = { pitchVal = it }, valueRange = -12f..12f,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        app.settings.setSpace(spaceVal.trim())
                        app.settings.setSteps(stepsVal.toInt())
                        app.settings.setLenPct(lenVal.toInt())
                        app.settings.setPitch(pitchVal.toInt())
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
            ) { Text("Сохранить", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = InkSoft) }
        },
    )
}

@Composable
private fun AddVoiceDialog(onCancel: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = BgElev,
        title = { Text("Имя голоса", color = Ink) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                placeholder = { Text("Например: Морган Фримен", color = InkSoft) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
            ) { Text("Добавить", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Отмена", color = InkSoft) }
        },
    )
}

private fun Float.format(digits: Int) = "%.${digits}f".format(this / 100f * 100f)
