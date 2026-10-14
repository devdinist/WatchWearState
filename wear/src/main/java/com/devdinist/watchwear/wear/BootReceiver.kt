package com.devdinist.watchwear.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** 워치 재부팅 후 감지 서비스 자동 재시작 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ContextCompat.startForegroundService(
                context, Intent(context, OffBodyService::class.java)
            )
        }
    }
}
