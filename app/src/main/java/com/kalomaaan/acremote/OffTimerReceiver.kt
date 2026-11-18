package com.kalomaaan.acremote

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Sends the OFF code when the off-timer fires (works even if the app is closed). */
class OffTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = Store.load(context)
        IrSender(context).send(LgCodec.CMD_OFF, state.timing)
        Store.save(context, state.copy(power = false, offTimerAt = 0L))
    }

    companion object {
        private fun pending(ctx: Context): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, 1001, Intent(ctx, OffTimerReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun schedule(ctx: Context, atMillis: Long) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pending(ctx)
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        }

        fun cancel(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending(ctx))
        }
    }
}
