package com.korvus.pocketvoice.hook

import android.media.AudioRecord
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

class AudioRecordHook : IXposedHookLoadPackage {

    private val totalBytes = AtomicLong(0)
    private val callCount = AtomicLong(0)

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in TARGET_PACKAGES) return

        XposedBridge.log("$TAG loaded in ${lpparam.packageName}")

        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val n = (param.result as? Int) ?: return
                if (n <= 0) return
                val total = totalBytes.addAndGet(n.toLong())
                val calls = callCount.incrementAndGet()
                if (calls % 50L == 0L) {
                    XposedBridge.log("$TAG ${lpparam.packageName} read #$calls, +$n, total=${total / 1024}KB")
                }
            }
        }

        val ar = AudioRecord::class.java
        val int = Int::class.javaPrimitiveType!!

        hookQuiet { XposedHelpers.findAndHookMethod(ar, "read", ByteArray::class.java, int, int, hook) }
        hookQuiet { XposedHelpers.findAndHookMethod(ar, "read", ByteArray::class.java, int, int, int, hook) }
        hookQuiet { XposedHelpers.findAndHookMethod(ar, "read", ShortArray::class.java, int, int, hook) }
        hookQuiet { XposedHelpers.findAndHookMethod(ar, "read", ShortArray::class.java, int, int, int, hook) }
        hookQuiet { XposedHelpers.findAndHookMethod(ar, "read", FloatArray::class.java, int, int, int, hook) }
        hookQuiet { XposedHelpers.findAndHookMethod(ar, "read", ByteBuffer::class.java, int, hook) }
        hookQuiet { XposedHelpers.findAndHookMethod(ar, "read", ByteBuffer::class.java, int, int, hook) }
    }

    private inline fun hookQuiet(block: () -> Unit) {
        try { block() } catch (t: Throwable) { XposedBridge.log("$TAG hook skip: ${t.message}") }
    }

    private companion object {
        const val TAG = "[PocketVoiceHook]"
        val TARGET_PACKAGES = setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "com.discord",
            "com.whatsapp"
        )
    }
}
