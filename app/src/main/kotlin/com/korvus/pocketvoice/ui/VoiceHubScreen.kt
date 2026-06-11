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
import com.korvus.pocketvoice.api.HfHub
import com.korvus.pocketvoice.api.HubModel
import com.korvus.pocketvoice.ui.theme.VioletDeep
import com.korvus.pocketvoice.ui.theme.VioletPale
import com.korvus.pocketvoice.ui.theme.VioletPrimary
import kotlinx.coroutines.launch

private val PRESET_QUERIES = listOf(
    "gpt-sovits", "rvc", "voice-cloning", "openvoice", "xtts", "anime tts", "f5-tts", "cosyvoice"
)

@Composable
fun VoiceHubScreen() {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("gpt-sovits") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<HubModel>>(emptyList()) }

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
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results, key = { it.id }) { m ->
                    HubCard(m)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun HubCard(m: HubModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
            "Открыть на HF: huggingface.co/${m.id}",
            color = VioletPrimary, fontSize = 11.sp,
        )
    }
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1000 / 1000}M"
    n >= 1000 -> "${n / 1000}K"
    else -> n.toString()
}
