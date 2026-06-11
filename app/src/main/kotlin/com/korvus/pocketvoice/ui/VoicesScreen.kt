package com.korvus.pocketvoice.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korvus.pocketvoice.PocketVoiceApp
import com.korvus.pocketvoice.data.Voice
import com.korvus.pocketvoice.ui.theme.VioletDeep
import com.korvus.pocketvoice.ui.theme.VioletPale
import com.korvus.pocketvoice.ui.theme.VioletPrimary
import kotlinx.coroutines.launch

@Composable
fun VoicesScreen() {
    val ctx = LocalContext.current
    val app = PocketVoiceApp.instance
    val scope = rememberCoroutineScope()
    val voices by app.voiceStore.items.collectAsState()
    val activeId by app.settings.activeVoiceId.collectAsState(initial = null)

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var addOpen by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) { pendingUri = uri; addOpen = true } }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 28.dp, bottom = 100.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        "ГОЛОСА",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Твоя библиотека",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Black, fontSize = 28.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${voices.size} ${plural(voices.size, "голос", "голоса", "голосов")}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
            if (voices.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🎙", fontSize = 64.sp)
                        Spacer(Modifier.height(14.dp))
                        Text("Пусто", color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Тапни + чтобы импортировать MP3,\nили зайди в VoiceHub за community-голосами.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                items(voices, key = { it.id }) { v ->
                    VoiceRow(
                        v = v,
                        active = v.id == activeId,
                        onPick = { scope.launch { app.settings.setActiveVoice(v.id) } },
                        onDelete = {
                            scope.launch {
                                app.voiceStore.delete(v.id)
                                if (activeId == v.id) app.settings.setActiveVoice(null)
                            }
                        },
                    )
                }
            }
        }
        // FAB
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(60.dp)
                .clip(RoundedCornerShape(50))
                .background(VioletPrimary)
                .clickable { picker.launch("audio/*") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "+", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }

    if (addOpen && pendingUri != null) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addOpen = false; pendingUri = null },
            title = { Text("Имя голоса", fontWeight = FontWeight.Black) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("Например: Морган Фримен") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val u = pendingUri ?: return@Button
                        val n = name.trim().ifBlank { "Без имени" }
                        addOpen = false; pendingUri = null; name = ""
                        scope.launch {
                            try {
                                val v = app.voiceStore.addFromUri(ctx, u, n)
                                app.settings.setActiveVoice(v.id)
                            } catch (_: Throwable) {}
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                ) { Text("Добавить", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { addOpen = false; pendingUri = null }) {
                    Text("Отмена", color = VioletPrimary)
                }
            },
        )
    }
}

@Composable
private fun VoiceRow(v: Voice, active: Boolean, onPick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) VioletPale else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (active) 1.5.dp else 0.dp,
                color = if (active) VioletPrimary else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(50))
                .background(if (active) VioletPrimary else Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text(v.emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(v.name, color = if (active) VioletDeep else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            Text(
                "${v.sizeBytes / 1024} KB" + if (v.builtin) " · встроен" else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
            )
        }
        if (!v.builtin) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun plural(n: Int, one: String, few: String, many: String): String {
    val a = n % 10; val b = n % 100
    if (a == 1 && b != 11) return one
    if (a in 2..4 && (b < 12 || b > 14)) return few
    return many
}
