package com.devdinist.watchwear.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/** 설정 → 소리 및 진동 → 진동 세기의 각 항목 (삼성 비공개 system 키, 0~5) */
enum class VibrationChannel(val label: String, val key: String, val enabledByDefault: Boolean) {
    NOTIFICATION("알림", "SEM_VIBRATION_NOTIFICATION_INTENSITY", true),
    CALL("전화", "VIB_RECVCALL_MAGNITUDE", false),
    SYSTEM("시스템", "VIB_FEEDBACK_MAGNITUDE", false),
    MEDIA("미디어", "media_vibration_intensity", false),
}

/**
 * 워치 상태 이벤트를 받아 폰의 진동 세기를 사용자가 지정한 값으로 변경.
 * - WATCH_ON: 각 항목을 "착용 시" 값으로
 * - WATCH_OFF / WATCH_DISCONNECTED: 각 항목을 "벗었을 때" 값으로
 *
 * 비공개 키라 일반 WRITE_SETTINGS로는 못 쓰고, 최초 1회 adb 권한 부여가 필요:
 *   adb shell pm grant com.devdinist.watchwear android.permission.WRITE_SECURE_SETTINGS
 */
object VibrationControl {
    private const val TAG = "VibrationControl"

    const val MAX_INTENSITY = 5

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun currentIntensity(context: Context, channel: VibrationChannel): Int? =
        runCatching {
            Settings.System.getInt(context.contentResolver, channel.key)
        }.getOrNull()

    fun handle(context: Context, action: String, eventTime: Long = System.currentTimeMillis()) {
        StateStore.save(context, action, eventTime)
        Log.i(TAG, "event: $action")
        val onBody = when (action) {
            Contract.ACTION_WATCH_ON -> true
            Contract.ACTION_WATCH_OFF, Contract.ACTION_WATCH_DISCONNECTED -> false
            else -> { // CONNECTED: 직후 ON/OFF가 따로 옴
                StateStore.saveResult(context, null)
                return
            }
        }
        StateStore.saveOnBody(context, onBody)
        val results = VibrationChannel.entries
            .filter { ChannelConfig.isEnabled(context, it) }
            .map { apply(context, it, onBody) }
        StateStore.saveResult(context, results.joinToString().ifEmpty { "조절할 항목 없음" })
    }

    /**
     * 앱에서 세기/조절 여부를 바꿨을 때, 지금 워치 상태에 해당하는 값이면 바로 폰 설정에 반영.
     * 아직 받은 착용 상태가 없으면 다음 이벤트 때 반영된다.
     */
    fun applyIfCurrent(context: Context, channel: VibrationChannel, onBody: Boolean? = null) {
        val current = StateStore.loadOnBody(context) ?: return
        if (onBody != null && onBody != current) return
        if (!ChannelConfig.isEnabled(context, channel)) return
        StateStore.saveResult(context, apply(context, channel, current))
    }

    private fun apply(context: Context, channel: VibrationChannel, onBody: Boolean): String {
        val target = ChannelConfig.value(context, channel, onBody)
        return runCatching { put(context, channel, target) }
            .fold(
                { "${channel.label} $target" },
                {
                    Log.e(TAG, "${channel.label} 진동 세기 변경 실패", it)
                    "${channel.label} 실패(${it.javaClass.simpleName})"
                }
            )
    }

    private fun put(context: Context, channel: VibrationChannel, value: Int) {
        Settings.System.putInt(context.contentResolver, channel.key, value)
        Log.i(TAG, "${channel.key}=$value")
    }
}

/** 항목별 조절 여부와 착용 시 / 벗었을 때 세기 (폰 앱 화면에서 지정) */
object ChannelConfig {
    private const val PREFS = "channels"
    private const val DEFAULT_OFF_BODY = 3

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, channel: VibrationChannel) =
        prefs(context).getBoolean("${channel.name}_enabled", channel.enabledByDefault)

    fun setEnabled(context: Context, channel: VibrationChannel, enabled: Boolean) {
        prefs(context).edit().putBoolean("${channel.name}_enabled", enabled).apply()
    }

    fun value(context: Context, channel: VibrationChannel, onBody: Boolean): Int {
        val key = valueKey(channel, onBody)
        val p = prefs(context)
        if (p.contains(key)) return p.getInt(key, 0)
        // 처음엔 착용 시 0, 벗었을 때는 지금 폰에 설정된 세기(0이면 기본값)로 시작
        val initial = if (onBody) 0 else
            VibrationControl.currentIntensity(context, channel)?.takeIf { it > 0 } ?: DEFAULT_OFF_BODY
        p.edit().putInt(key, initial).apply()
        return initial
    }

    fun setValue(context: Context, channel: VibrationChannel, onBody: Boolean, value: Int) {
        prefs(context).edit().putInt(valueKey(channel, onBody), value).apply()
    }

    private fun valueKey(channel: VibrationChannel, onBody: Boolean) =
        "${channel.name}_${if (onBody) "on" else "off"}"
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

    /** 마지막으로 받은 착용 상태 (CONNECTED 이벤트에는 갱신되지 않음) */
    fun saveOnBody(context: Context, onBody: Boolean) {
        prefs(context).edit().putBoolean("on_body", onBody).apply()
    }

    fun loadOnBody(context: Context): Boolean? {
        val p = prefs(context)
        if (p.contains("on_body")) return p.getBoolean("on_body", false)
        // 이전 버전에서 업데이트한 경우: 마지막 이벤트 이름으로 추정
        return when (p.getString("last_action", null)) {
            "WATCH_ON" -> true
            "WATCH_OFF", "WATCH_DISCONNECTED" -> false
            else -> null
        }
    }
}
