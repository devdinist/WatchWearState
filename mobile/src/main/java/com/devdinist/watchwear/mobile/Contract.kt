package com.devdinist.watchwear.mobile

/** 워치 ↔ 폰 공통 규약. wear 모듈의 Contract.kt와 값이 같아야 합니다. */
object Contract {
    const val PATH_WEAR_STATE = "/wear_state"
    const val KEY_ON_BODY = "on_body"
    const val KEY_TIMESTAMP = "timestamp"
    const val CAPABILITY = "wear_state_sender"

    // 폰 내부 이벤트 이름 (VibrationControl.handle 입력)
    const val ACTION_WATCH_ON = "com.devdinist.watchwear.WATCH_ON"
    const val ACTION_WATCH_OFF = "com.devdinist.watchwear.WATCH_OFF"
    const val ACTION_WATCH_CONNECTED = "com.devdinist.watchwear.WATCH_CONNECTED"
    const val ACTION_WATCH_DISCONNECTED = "com.devdinist.watchwear.WATCH_DISCONNECTED"
}
