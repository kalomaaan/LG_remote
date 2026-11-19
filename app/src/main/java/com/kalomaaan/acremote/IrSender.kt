package com.kalomaaan.acremote

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build

class IrSender(context: Context) {

    private val ir: ConsumerIrManager? =
        context.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val available: Boolean
        get() = ir?.hasIrEmitter() == true

    /** Human-readable diagnostics about the phone's IR hardware. */
    fun info(): String {
        val mgr = ir ?: return "${Build.MANUFACTURER} ${Build.MODEL}: no IR service"
        val ranges = try {
            mgr.carrierFrequencies?.joinToString { "${it.minFrequency}-${it.maxFrequency} Hz" } ?: "none reported"
        } catch (e: Exception) {
            "error: ${e.message}"
        }
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n" +
            "IR emitter: ${if (mgr.hasIrEmitter()) "yes" else "NO"}\n" +
            "Carrier ranges: $ranges"
    }

    /** Returns null on success, or an error message. */
    fun send(frame: Int, variant: LgCodec.Variant): String? {
        val mgr = ir ?: return "This phone has no IR service"
        if (!mgr.hasIrEmitter()) return "No IR blaster found on this phone"
        return try {
            mgr.transmit(variant.freq, LgCodec.toPattern(frame, variant))
            null
        } catch (e: Exception) {
            "IR send failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
