package com.korvus.pocketvoice

import android.app.Application
import com.korvus.pocketvoice.data.Settings
import com.korvus.pocketvoice.data.VoiceStore
import com.korvus.pocketvoice.onnx.LocalConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PocketVoiceApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var voiceStore: VoiceStore
        private set
    lateinit var converter: LocalConverter
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings(this)
        voiceStore = VoiceStore(this)
        converter = LocalConverter(this)
        appScope.launch {
            voiceStore.load()
            voiceStore.seedFromAssetsIfEmpty(this@PocketVoiceApp)
        }
    }

    companion object {
        lateinit var instance: PocketVoiceApp
            private set
    }
}
