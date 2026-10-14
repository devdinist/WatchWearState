package com.devdinist.watchwear.mobile

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 상태 확인 + 워치 없이 진동 세기 변경을 테스트하는 화면 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.density * 16).toInt()

        statusView = TextView(this).apply { textSize = 15f }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(statusView)
            addView(button("워치 연결 확인 + 상태 재동기화") { checkWatch() })
            addView(TextView(context).apply {
                text = "\n[워치 없이 테스트]"
            })
            addView(button("워치 착용 (알림 진동 끄기)") { test(Contract.ACTION_WATCH_ON) })
            addView(button("워치 벗음 (알림 진동 복원)") { test(Contract.ACTION_WATCH_OFF) })
        }
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { onClick() } }

    private fun test(action: String) {
        VibrationControl.handle(this, action)
        refresh()
    }

    private fun checkWatch() {
        Wearable.getCapabilityClient(this)
            .getCapability(Contract.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { info ->
                val nearby = info.nodes.filter { it.isNearby }
                refresh(
                    if (nearby.isEmpty()) "워치: 연결 안 됨 (워치앱 설치 확인)"
                    else "워치: ${nearby.joinToString { it.displayName }} 연결됨"
                )
                if (nearby.isNotEmpty()) {
                    Thread {
                        runCatching { WearStateListenerService.resyncCurrentState(this) }
                        runOnUiThread { refresh() }
                    }.start()
                }
            }
            .addOnFailureListener { refresh("워치 확인 실패: ${it.message}") }
    }

    private fun refresh(extra: String? = null) {
        val (action, time) = StateStore.load(this)
        val timeText = if (time == 0L) "-" else
            SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA).format(Date(time))
        statusView.text = buildString {
            appendLine("마지막 이벤트: ${action ?: "-"}")
            appendLine("시각: $timeText")
            appendLine("결과: ${StateStore.loadResult(this@MainActivity) ?: "-"}")
            appendLine()
            appendLine("현재 알림 진동 세기: ${VibrationControl.currentIntensity(this@MainActivity) ?: "?"}")
            appendLine("복원할 세기: ${StateStore.loadRestoreValue(this@MainActivity) ?: "-"}")
            if (!VibrationControl.hasPermission(this@MainActivity)) {
                appendLine()
                appendLine("⚠️ 권한 없음. PC에서 1회 실행:")
                appendLine("adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
            }
            if (extra != null) appendLine(extra)
        }
    }
}
