package com.korvus.voicechanger.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore("vc_settings")

class Settings(private val ctx: Context) {

    private val keySpace = stringPreferencesKey("hf_space")
    private val keySteps = intPreferencesKey("steps")
    private val keyLen = intPreferencesKey("len_pct")
    private val keyPitch = intPreferencesKey("pitch")
    private val keyActiveVoiceId = stringPreferencesKey("active_voice")

    val space: Flow<String> = ctx.dataStore.data.map { it[keySpace] ?: DEFAULT_SPACE }
    val steps: Flow<Int> = ctx.dataStore.data.map { it[keySteps] ?: 25 }
    val lenPct: Flow<Int> = ctx.dataStore.data.map { it[keyLen] ?: 100 }
    val pitch: Flow<Int> = ctx.dataStore.data.map { it[keyPitch] ?: 0 }
    val activeVoiceId: Flow<String?> = ctx.dataStore.data.map { it[keyActiveVoiceId] }

    suspend fun setSpace(v: String) = withContext(Dispatchers.IO) {
        ctx.dataStore.edit { it[keySpace] = v }
    }
    suspend fun setSteps(v: Int) = withContext(Dispatchers.IO) {
        ctx.dataStore.edit { it[keySteps] = v }
    }
    suspend fun setLenPct(v: Int) = withContext(Dispatchers.IO) {
        ctx.dataStore.edit { it[keyLen] = v }
    }
    suspend fun setPitch(v: Int) = withContext(Dispatchers.IO) {
        ctx.dataStore.edit { it[keyPitch] = v }
    }
    suspend fun setActiveVoice(id: String?) = withContext(Dispatchers.IO) {
        ctx.dataStore.edit {
            if (id == null) it.remove(keyActiveVoiceId) else it[keyActiveVoiceId] = id
        }
    }

    suspend fun snapshot() = withContext(Dispatchers.IO) {
        val s = ctx.dataStore.data.first()
        Snapshot(
            space = s[keySpace] ?: DEFAULT_SPACE,
            steps = s[keySteps] ?: 25,
            lenPct = s[keyLen] ?: 100,
            pitch = s[keyPitch] ?: 0,
            activeVoiceId = s[keyActiveVoiceId],
        )
    }

    data class Snapshot(
        val space: String,
        val steps: Int,
        val lenPct: Int,
        val pitch: Int,
        val activeVoiceId: String?,
    )

    companion object {
        const val DEFAULT_SPACE = "Plachta/Seed-VC"
    }
}
