package com.devdinist.watchwear.wear

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

/** 센서 지원 여부 확인 + 서비스 시작/중지용 최소 화면 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.density * 16).toInt()

        statusView = TextView(this).apply { textSize = 13f; gravity = Gravity.CENTER }

        val startBtn = Button(this).apply {
            text = "감지 시작"
            setOnClickListener {
                ContextCompat.startForegroundService(
                    this@MainActivity, Intent(this@MainActivity, OffBodyService::class.java)
                )
                statusView.postDelayed({ refresh() }, 700)
            }
        }
        val stopBtn = Button(this).apply {
            text = "감지 중지"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OffBodyService::class.java))
                statusView.postDelayed({ refresh() }, 300)
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 3, pad, pad * 3)
            addView(statusView)
            addView(startBtn)
            addView(stopBtn)
        }
        setContentView(ScrollView(this).apply { addView(layout) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val sensor = OffBodySensor.find(getSystemService(SensorManager::class.java))
        statusView.text = buildString {
            if (sensor == null) {
                appendLine("착용감지 센서: 없음 ❌")
            } else {
                appendLine("착용감지 센서: 있음 ✅")
                appendLine(sensor.name)
                appendLine("wakeUp=${sensor.isWakeUpSensor}")
            }
            appendLine()
            appendLine("서비스: ${if (OffBodyService.isRunning) "실행 중" else "중지"}")
            append("마지막 상태: ")
            append(
                when (OffBodyService.lastOnBody) {
                    true -> "착용"; false -> "미착용"; null -> "-"
                }
            )
        }
    }
}
