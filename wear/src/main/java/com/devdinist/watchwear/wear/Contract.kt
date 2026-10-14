package com.devdinist.watchwear.wear

/** 워치 ↔ 폰 공통 규약. mobile 모듈의 Contract.kt와 값이 같아야 합니다. */
object Contract {
    const val PATH_WEAR_STATE = "/wear_state"
    const val KEY_ON_BODY = "on_body"
    const val KEY_TIMESTAMP = "timestamp"
    const val CAPABILITY = "wear_state_sender"
}
