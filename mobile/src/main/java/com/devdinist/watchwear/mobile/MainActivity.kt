package com.devdinist.watchwear.mobile

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 상태 확인 + 항목별 진동 세기 지정 + 워치 없이 테스트하는 화면 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private val currentViews = mutableMapOf<VibrationChannel, TextView>()

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
                text = "\n[진동 세기 지정] 0~${VibrationControl.MAX_INTENSITY}"
            })
            VibrationChannel.entries.forEach { addView(channelSection(it, pad)) }
            addView(TextView(context).apply {
                text = "\n[워치 없이 테스트]"
            })
            addView(button("워치 착용 (착용 시 세기 적용)") { test(Contract.ACTION_WATCH_ON) })
            addView(button("워치 벗음 (벗었을 때 세기 적용)") { test(Contract.ACTION_WATCH_OFF) })
        }
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** 항목 하나: [✓ 알림 (현재 n)] + 착용 시 / 벗었을 때 슬라이더 */
    private fun channelSection(channel: VibrationChannel, pad: Int): LinearLayout {
        val header = CheckBox(this).apply {
            isChecked = ChannelConfig.isEnabled(this@MainActivity, channel)
            text = channel.label
            textSize = 16f
        }
        val current = TextView(this).also { currentViews[channel] = it }
        val sliders = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad * 2, 0, 0, 0)
            addView(current)
            addView(slider(channel, onBody = true))
            addView(slider(channel, onBody = false))
            visibility = if (header.isChecked) LinearLayout.VISIBLE else LinearLayout.GONE
        }
        header.setOnCheckedChangeListener { _, checked ->
            ChannelConfig.setEnabled(this, channel, checked)
            sliders.visibility = if (checked) LinearLayout.VISIBLE else LinearLayout.GONE
            if (checked) {
                VibrationControl.applyIfCurrent(this, channel)
                refresh()
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(sliders)
        }
    }

    private fun slider(channel: VibrationChannel, onBody: Boolean): LinearLayout {
        val name = if (onBody) "착용 시" else "벗었을 때"
        val label = TextView(this)
        val seekBar = SeekBar(this).apply {
            max = VibrationControl.MAX_INTENSITY
            progress = ChannelConfig.value(this@MainActivity, channel, onBody)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    label.text = "$name: $value"
                    if (!fromUser) return
                    ChannelConfig.setValue(this@MainActivity, channel, onBody, value)
                    // 지금 워치 상태에 해당하는 슬라이더면 폰 설정에 바로 반영
                    VibrationControl.applyIfCurrent(this@MainActivity, channel, onBody)
                    refresh()
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        label.text = "$name: ${seekBar.progress}"
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(seekBar)
        }
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
            appendLine(
                "워치 상태: " + when (StateStore.loadOnBody(this@MainActivity)) {
                    true -> "착용"; false -> "벗음"; null -> "-"
                }
            )
            appendLine("마지막 이벤트: ${action ?: "-"}")
            appendLine("시각: $timeText")
            appendLine("결과: ${StateStore.loadResult(this@MainActivity) ?: "-"}")
            if (!VibrationControl.hasPermission(this@MainActivity)) {
                appendLine()
                appendLine("⚠️ 권한 없음. PC에서 1회 실행:")
                appendLine("adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
            }
            if (extra != null) appendLine(extra)
        }
        currentViews.forEach { (channel, view) ->
            view.text = "현재 폰 설정: ${VibrationControl.currentIntensity(this, channel) ?: "?"}"
        }
    }
}
