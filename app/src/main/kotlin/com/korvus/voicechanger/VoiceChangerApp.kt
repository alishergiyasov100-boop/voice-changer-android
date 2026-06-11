package com.korvus.voicechanger

import android.app.Application
import com.korvus.voicechanger.data.Settings
import com.korvus.voicechanger.data.VoiceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VoiceChangerApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var voiceStore: VoiceStore
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings(this)
        voiceStore = VoiceStore(this)
        appScope.launch {
            voiceStore.load()
            voiceStore.seedFromAssetsIfEmpty(this@VoiceChangerApp)
        }
    }

    companion object {
        lateinit var instance: VoiceChangerApp
            private set
    }
}
