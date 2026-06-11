package com.korvus.pocketvoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korvus.pocketvoice.PocketVoiceApp
import com.korvus.pocketvoice.data.Settings
import com.korvus.pocketvoice.ui.theme.VioletPrimary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val app = PocketVoiceApp.instance
    val scope = rememberCoroutineScope()

    val space by app.settings.space.collectAsState(initial = Settings.DEFAULT_SPACE)
    val steps by app.settings.steps.collectAsState(initial = 25)
    val lenPct by app.settings.lenPct.collectAsState(initial = 100)
    val pitch by app.settings.pitch.collectAsState(initial = 0)
    val serverUrl by app.settings.serverUrl.collectAsState(initial = "")
    val serverOn by app.settings.serverOn.collectAsState(initial = false)

    var spaceVal by remember(space) { mutableStateOf(space) }
    var stepsVal by remember(steps) { mutableFloatStateOf(steps.toFloat()) }
    var lenVal by remember(lenPct) { mutableFloatStateOf(lenPct.toFloat()) }
    var pitchVal by remember(pitch) { mutableFloatStateOf(pitch.toFloat()) }
    var serverUrlVal by remember(serverUrl) { mutableStateOf(serverUrl) }

    LaunchedEffect(spaceVal) { app.settings.setSpace(spaceVal.trim()) }
    LaunchedEffect(stepsVal) { app.settings.setSteps(stepsVal.toInt()) }
    LaunchedEffect(lenVal)   { app.settings.setLenPct(lenVal.toInt()) }
    LaunchedEffect(pitchVal) { app.settings.setPitch(pitchVal.toInt()) }
    LaunchedEffect(serverUrlVal) { app.settings.setServerUrl(serverUrlVal.trim()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "НАСТРОЙКИ", color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
        )
        Text(
            "Параметры", color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Black, fontSize = 26.sp,
        )

        Spacer(Modifier.height(6.dp))

        SectionCard(
            title = "Server Mode (Colab / Kaggle GPU)",
            hint = "Подними сервер по инструкции server/colab_seedvc_server.py и вставь cloudflared URL ниже. Когда включён — конверсия идёт на GPU, качество в разы выше.",
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "Использовать сервер",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = serverOn,
                    onCheckedChange = { scope.launch { app.settings.setServerOn(it) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = VioletPrimary,
                    ),
                )
            }
            OutlinedTextField(
                value = serverUrlVal,
                onValueChange = { serverUrlVal = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://xxx.trycloudflare.com") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VioletPrimary),
            )
        }

        SectionCard(
            title = "HF Space (резерв)",
            hint = "Резервный путь через публичный HF Space, не реализовано.",
        ) {
            OutlinedTextField(
                value = spaceVal,
                onValueChange = { spaceVal = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("username/Seed-VC") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VioletPrimary),
            )
        }

        SectionCard(
            title = "OpenVoice (локальная модель)",
            hint = "Подкручивают качество и тембр конверсии.",
        ) {
            SliderLabel("Diffusion Steps", stepsVal.toInt().toString())
            Slider(value = stepsVal, onValueChange = { stepsVal = it }, valueRange = 10f..60f,
                colors = SliderDefaults.colors(thumbColor = VioletPrimary, activeTrackColor = VioletPrimary))
            Spacer(Modifier.height(8.dp))
            SliderLabel("Длительность", "%.2f".format(lenVal / 100f))
            Slider(value = lenVal, onValueChange = { lenVal = it }, valueRange = 50f..200f,
                colors = SliderDefaults.colors(thumbColor = VioletPrimary, activeTrackColor = VioletPrimary))
            Spacer(Modifier.height(8.dp))
            SliderLabel("Pitch Shift", "${pitchVal.toInt()}")
            Slider(value = pitchVal, onValueChange = { pitchVal = it }, valueRange = -12f..12f,
                colors = SliderDefaults.colors(thumbColor = VioletPrimary, activeTrackColor = VioletPrimary))
        }

        SectionCard(title = "О приложении", hint = "PocketVoice v0.4.0") {
            Text(
                "Локальный voice converter на OpenVoice v2 ONNX. " +
                "VoiceHub браузит community-голоса с Hugging Face. " +
                "В планах: GPT-SoVITS server-mode для топ-качества.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp, lineHeight = 17.sp,
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SliderLabel(label: String, value: String) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = VioletPrimary, fontSize = 12.sp,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionCard(title: String, hint: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}
