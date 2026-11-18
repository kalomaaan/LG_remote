package com.kalomaaan.acremote

import android.content.Context
import android.hardware.ConsumerIrManager

class IrSender(context: Context) {

    private val ir: ConsumerIrManager? =
        context.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val available: Boolean
        get() = ir?.hasIrEmitter() == true

    /** Returns null on success, or an error message. */
    fun send(frame: Int, timing: LgCodec.Timing): String? {
        val mgr = ir ?: return "This phone has no IR service"
        if (!mgr.hasIrEmitter()) return "No IR blaster found on this phone"
        return try {
            mgr.transmit(LgCodec.CARRIER_HZ, LgCodec.toPattern(frame, timing))
            null
        } catch (e: Exception) {
            "IR send failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
