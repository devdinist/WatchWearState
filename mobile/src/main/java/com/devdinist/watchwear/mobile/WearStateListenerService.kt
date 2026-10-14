package com.devdinist.watchwear.mobile

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.TimeUnit

/**
 * - onDataChanged: 워치 착용/미착용 → WATCH_ON / WATCH_OFF
 * - onCapabilityChanged: 워치 연결/해제 → WATCH_CONNECTED / WATCH_DISCONNECTED
 * 콜백은 백그라운드 스레드에서 실행되므로 Tasks.await 사용 가능.
 */
class WearStateListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearStateListener"

        /** 저장된 최신 착용 상태를 다시 읽어 진동 세기에 반영 (백그라운드 스레드에서 호출) */
        fun resyncCurrentState(context: Context) {
            val uri = Uri.Builder()
                .scheme(PutDataRequest.WEAR_URI_SCHEME)
                .path(Contract.PATH_WEAR_STATE)
                .build()
            val buffer = Tasks.await(
                Wearable.getDataClient(context).getDataItems(uri), 10, TimeUnit.SECONDS
            )
            try {
                val latest = buffer
                    .map { DataMapItem.fromDataItem(it).dataMap }
                    .maxByOrNull { it.getLong(Contract.KEY_TIMESTAMP) }
                    ?: return
                val onBody = latest.getBoolean(Contract.KEY_ON_BODY)
                VibrationControl.handle(
                    context,
                    if (onBody) Contract.ACTION_WATCH_ON else Contract.ACTION_WATCH_OFF,
                    latest.getLong(Contract.KEY_TIMESTAMP)
                )
            } finally {
                buffer.release()
            }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != Contract.PATH_WEAR_STATE) continue

            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val onBody = map.getBoolean(Contract.KEY_ON_BODY)
            val ts = map.getLong(Contract.KEY_TIMESTAMP)
            Log.i(TAG, "착용 상태 수신: onBody=$onBody")

            VibrationControl.handle(
                this,
                if (onBody) Contract.ACTION_WATCH_ON else Contract.ACTION_WATCH_OFF,
                ts
            )
        }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name != Contract.CAPABILITY) return

        // 삼성 원격 연결(서버 경유)은 isNearby=false → 연결 해제로 취급
        val connected = info.nodes.any { it.isNearby }
        Log.i(TAG, "연결 상태 변경: connected=$connected")

        if (connected) {
            VibrationControl.handle(this, Contract.ACTION_WATCH_CONNECTED)
            // 끊긴 동안 착용 상태가 그대로면 DATA_CHANGED가 안 오므로 직접 재동기화
            runCatching { resyncCurrentState(this) }
                .onFailure { Log.w(TAG, "재동기화 실패", it) }
        } else {
            VibrationControl.handle(this, Contract.ACTION_WATCH_DISCONNECTED)
        }
    }
}
