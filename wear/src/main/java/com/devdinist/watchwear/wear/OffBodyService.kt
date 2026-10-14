package com.devdinist.watchwear.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object OffBodySensor {
    /** wake-up 버전을 우선 사용하고, 없으면 일반 버전으로 fallback */
    fun find(sm: SensorManager): Sensor? =
        sm.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true)
            ?: sm.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
}

/**
 * 착용 감지 센서를 상시 구독하다가 상태가 바뀌면 DataClient로 폰에 동기화.
 * DataItem을 쓰므로 연결이 끊긴 동안 바뀐 상태도 재연결 시 자동 전달됩니다.
 */
class OffBodyService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "OffBodyService"
        private const val CHANNEL_ID = "offbody"
        private const val NOTIFICATION_ID = 1

        @Volatile var isRunning = false; private set
        @Volatile var lastOnBody: Boolean? = null; private set
    }

    private lateinit var sensorManager: SensorManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        sensorManager = getSystemService(SensorManager::class.java)
        val sensor = OffBodySensor.find(sensorManager)
        if (sensor == null) {
            Log.e(TAG, "TYPE_LOW_LATENCY_OFFBODY_DETECT 센서가 없습니다.")
            stopSelf()
            return
        }
        // on-change 센서라 등록 직후 현재 상태가 한 번 전달됨
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        isRunning = true
        Log.i(TAG, "센서 등록: ${sensor.name} (wakeUp=${sensor.isWakeUpSensor})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onSensorChanged(event: SensorEvent) {
        // 스펙: 1.0 = 착용, 0.0 = 미착용
        val onBody = event.values[0] >= 0.5f
        if (onBody == lastOnBody) return
        lastOnBody = onBody
        Log.i(TAG, "착용 상태 변경: onBody=$onBody")
        sendState(onBody)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun sendState(onBody: Boolean) {
        // 전송이 끝날 때까지 CPU가 잠들지 않도록 짧게 WakeLock 유지
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "watchwear:send")
            .apply { acquire(10_000L) }

        scope.launch {
            try {
                val request = PutDataMapRequest.create(Contract.PATH_WEAR_STATE).apply {
                    dataMap.putBoolean(Contract.KEY_ON_BODY, onBody)
                    // 같은 값이어도 변경으로 인식되도록 타임스탬프 포함
                    dataMap.putLong(Contract.KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()

                Wearable.getDataClient(this@OffBodyService).putDataItem(request).await()
                Log.i(TAG, "폰으로 전송 완료: onBody=$onBody")
            } catch (e: Exception) {
                Log.w(TAG, "전송 실패(연결 복구 시 자동 동기화됨)", e)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "착용 감지", NotificationManager.IMPORTANCE_LOW)
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("착용 감지 중")
            .setOngoing(true)
            .build()

        // specialUse 타입은 API 34(Wear OS 5)부터 존재 → 그 이전엔 타입 없이 시작
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    override fun onDestroy() {
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
        scope.cancel()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
