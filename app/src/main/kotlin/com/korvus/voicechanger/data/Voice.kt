package com.korvus.voicechanger.data

import kotlinx.serialization.Serializable

@Serializable
data class Voice(
    val id: String,
    val name: String,
    val emoji: String = "🎙",
    val filename: String,    // путь в filesDir/voices/
    val sizeBytes: Long = 0,
    val builtin: Boolean = false,
)
