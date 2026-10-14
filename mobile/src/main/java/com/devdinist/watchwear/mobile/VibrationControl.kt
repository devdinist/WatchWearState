package com.devdinist.watchwear.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 워치 상태 이벤트를 받아 폰의 알림 진동 세기를 직접 변경.
 * - WATCH_ON: 현재 세기를 기억하고 0으로
 * - WATCH_OFF / WATCH_DISCONNECTED: 기억한 세기로 복원
 *
 * 삼성 비공개 키라 일반 WRITE_SETTINGS로는 못 쓰고, 최초 1회 adb 권한 부여가 필요:
 *   adb shell pm grant com.devdinist.watchwear android.permission.WRITE_SECURE_SETTINGS
 */
object VibrationControl {
    private const val TAG = "VibrationControl"

    /** 설정 → 소리 및 진동 → 진동 세기 → 알림 (0~5) */
    const val KEY_NOTIFICATION_INTENSITY = "SEM_VIBRATION_NOTIFICATION_INTENSITY"

    /** 기억한 값이 없을 때 복원할 기본 세기 */
    private const val DEFAULT_INTENSITY = 3

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun currentIntensity(context: Context): Int? =
        runCatching {
            Settings.System.getInt(context.contentResolver, KEY_NOTIFICATION_INTENSITY)
        }.getOrNull()

    fun handle(context: Context, action: String, eventTime: Long = System.currentTimeMillis()) {
        StateStore.save(context, action, eventTime)
        Log.i(TAG, "event: $action")
        val result = runCatching {
            when (action) {
                Contract.ACTION_WATCH_ON -> mute(context)
                Contract.ACTION_WATCH_OFF, Contract.ACTION_WATCH_DISCONNECTED -> restore(context)
                else -> null // CONNECTED: 직후 ON/OFF가 따로 옴
            }
        }
        result.onFailure { Log.e(TAG, "진동 세기 변경 실패", it) }
        StateStore.saveResult(
            context,
            result.fold({ it }, { "실패: ${it.javaClass.simpleName} (권한 확인)" })
        )
    }

    private fun mute(context: Context): String {
        val current = currentIntensity(context) ?: DEFAULT_INTENSITY
        // ON이 중복으로 와도(재연결 재동기화 등) 0을 기억하지 않도록
        if (current > 0) StateStore.saveRestoreValue(context, current)
        put(context, 0)
        return "알림 진동 $current → 0"
    }

    private fun restore(context: Context): String {
        val current = currentIntensity(context) ?: 0
        // 착용 중에 직접 세기를 올렸다면 그 값을 존중
        if (current > 0) return "알림 진동 $current 유지"
        val target = StateStore.loadRestoreValue(context) ?: DEFAULT_INTENSITY
        put(context, target)
        return "알림 진동 0 → $target"
    }

    private fun put(context: Context, value: Int) {
        Settings.System.putInt(context.contentResolver, KEY_NOTIFICATION_INTENSITY, value)
        Log.i(TAG, "$KEY_NOTIFICATION_INTENSITY=$value")
    }
}

object StateStore {
    private const val PREFS = "state"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, action: String, time: Long) {
        prefs(context).edit()
            .putString("last_action", action.substringAfterLast('.'))
            .putLong("last_time", time)
            .apply()
    }

    fun load(context: Context): Pair<String?, Long> {
        val p = prefs(context)
        return p.getString("last_action", null) to p.getLong("last_time", 0L)
    }

    fun saveResult(context: Context, result: String?) {
        prefs(context).edit().putString("last_result", result).apply()
    }

    fun loadResult(context: Context): String? = prefs(context).getString("last_result", null)

    fun saveRestoreValue(context: Context, value: Int) {
        prefs(context).edit().putInt("restore_value", value).apply()
    }

    fun loadRestoreValue(context: Context): Int? =
        prefs(context).getInt("restore_value", -1).takeIf { it > 0 }
}
