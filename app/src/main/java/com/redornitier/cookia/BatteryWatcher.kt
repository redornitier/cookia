package com.redornitier.cookia

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class BatteryWatcher(
    private val context: Context,
    private val onPct: (Int?) -> Unit
) {
    private val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent) {
            onPct(extractPct(intent))
        }
    }

    fun start() {
        // Valor inicial (intent sticky)
        context.registerReceiver(null, filter)?.let { onPct(extractPct(it)) }
        // Escuchar cambios
        context.registerReceiver(receiver, filter)
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    private fun extractPct(intent: Intent): Int? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100 / scale) else null
    }
}