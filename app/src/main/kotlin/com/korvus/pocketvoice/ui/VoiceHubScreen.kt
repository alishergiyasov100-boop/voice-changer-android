package com.korvus.pocketvoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import com.korvus.pocketvoice.PocketVoiceApp
import com.korvus.pocketvoice.api.HfHub
import com.korvus.pocketvoice.api.HubModel
import com.korvus.pocketvoice.api.RepoFile
import com.korvus.pocketvoice.ui.theme.VioletDeep
import com.korvus.pocketvoice.ui.theme.VioletPale
import com.korvus.pocketvoice.ui.theme.VioletPrimary
import kotlinx.coroutines.launch

private val PRESET_QUERIES = listOf(
    "gpt-sovits", "rvc", "voice-cloning", "openvoice", "xtts", "anime tts", "f5-tts", "cosyvoice"
)

@Composable
fun VoiceHubScreen() {
    val ctx = LocalContext.current
    val app = PocketVoiceApp.instance
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("gpt-sovits") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<HubModel>>(emptyList()) }
    var detail by remember { mutableStateOf<HubModel?>(null) }
    var detailFiles by remember { mutableStateOf<List<RepoFile>>(emptyList()) }
    var detailLoading by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    fun runSearch() {
        scope.launch {
            loading = true; error = null
            try { results = HfHub.search(query) }
            catch (t: Throwable) { error = t.message ?: t.toString() }
            finally { loading = false }
        }
    }

    LaunchedEffect(Unit) { runSearch() }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            Text(
                "VOICEHUB",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Голоса от community",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black, fontSize = 26.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Hugging Face Hub · TTS / voice-cloning модели",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск моделей (gpt-sovits, rvc…)") },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VioletPrimary,
                ),
            )
        }
        // Preset chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(PRESET_QUERIES) { _, q ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (query == q) VioletPrimary else VioletPale)
                        .clickable { query = q; runSearch() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        q,
                        color = if (query == q) Color.White else VioletDeep,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = VioletPrimary, strokeWidth = 3.dp) }
            error != null -> Text(
                "Ошибка: $error",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(24.dp),
            )
            results.isEmpty() -> Text(
                "Ничего не нашлось.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(results, key = { it.id }) { m ->
                    HubCard(m, onClick = {
                        detail = m
                        detailFiles = emptyList()
                        detailLoading = true
                        scope.launch {
                            try { detailFiles = HfHub.listAudioFiles(m.id) }
                            catch (t: Throwable) { toast = "Не загрузил файлы: ${t.message}" }
                            finally { detailLoading = false }
                        }
                    })
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    val det = detail
    if (det != null) {
        DetailDialog(
            m = det,
            files = detailFiles,
            loading = detailLoading,
            onDismiss = { detail = null; detailFiles = emptyList() },
            onInstall = { f ->
                scope.launch {
                    toast = "Скачиваю ${f.path}…"
                    try {
                        val name = "${det.name}/${f.path.substringAfterLast('/')}"
                        val v = HfHub.downloadAsVoice(ctx, app.voiceStore, det.id, f.path, name)
                        app.settings.setActiveVoice(v.id)
                        toast = "Установлено ✓"
                        detail = null
                    } catch (t: Throwable) {
                        toast = "Ошибка: ${t.message}"
                    }
                }
            },
        )
    }

    val t = toast
    if (t != null) {
        LaunchedEffect(t) {
            kotlinx.coroutines.delay(2400)
            toast = null
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .padding(bottom = 90.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onBackground)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(t, color = MaterialTheme.colorScheme.background, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HubCard(m: HubModel, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(VioletPale),
                contentAlignment = Alignment.Center,
            ) { Text("🎙", fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(m.name, color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                Text("by ${m.author}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("⬇ ${formatCount(m.downloads)}", color = VioletDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("♥ ${formatCount(m.likes)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        if (m.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(m.tags.take(5)) { t ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(t, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Тапни → выбрать аудио-семпл и установить",
            color = VioletPrimary, fontSize = 11.sp,
        )
    }
}

@Composable
private fun DetailDialog(
    m: HubModel,
    files: List<RepoFile>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onInstall: (RepoFile) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(m.name, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("by ${m.author}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VioletPrimary, strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp))
                    }
                } else if (files.isEmpty()) {
                    Text(
                        "Аудио-файлов не найдено. " +
                        "Открой репо на HF в Chrome чтобы вручную скачать ckpt.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    )
                } else {
                    Text(
                        "Аудио семплы (тапни чтобы установить):",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.heightIn(max = 360.dp),
                    ) {
                        items(files) { f ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VioletPale)
                                    .clickable { onInstall(f) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Column {
                                    Text(f.path, color = VioletDeep, fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("${f.size / 1024} KB",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = VioletPrimary, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1000 / 1000}M"
    n >= 1000 -> "${n / 1000}K"
    else -> n.toString()
}
