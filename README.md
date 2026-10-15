# WatchWearState

갤럭시워치 착용 / 벗음·연결 해제 → 폰 진동 세기(알림·전화·시스템·미디어)를 지정한 값으로 변경 (Tasker 불필요)

```
[워치 wear 모듈]                    [폰 mobile 모듈]
OffBodyService                      WearStateListenerService
 └ 착용감지 센서 ── DataClient ──▶   ├ onDataChanged ─────┐
                  (BT/Wi-Fi 자동)   └ onCapabilityChanged ┤
                                                          └ VibrationControl
                                                             └ 항목별 진동 세기 변경
```

| 이벤트 | 의미 | 동작 |
|---|---|---|
| `WATCH_ON` | 워치 착용 | 켜 둔 항목을 "착용 시" 값으로 |
| `WATCH_OFF` | 워치 벗음 | 켜 둔 항목을 "벗었을 때" 값으로 |
| `WATCH_DISCONNECTED` | 워치 연결 끊김 | 켜 둔 항목을 "벗었을 때" 값으로 |
| `WATCH_CONNECTED` | 워치 재연결 | 없음 (직후 현재 착용 상태가 재전송됨) |

폰 앱 화면에서 항목별로 조절 여부(체크)와 착용 시 / 벗었을 때 세기(0~5)를 지정합니다. 처음 값은 착용 시 0, 벗었을 때는 그 시점의 폰 설정값입니다. 기본으로는 알림만 켜져 있습니다.

양방향 동기화: 앱에서 지금 워치 상태에 해당하는 슬라이더를 바꾸면 폰 설정에 바로 적용되고, 반대로 설정 → 진동 세기에서 직접 바꾼 값은 지금 워치 상태의 지정 값으로 저장됩니다(앱을 열 때, 앱을 보는 중 실시간, 그리고 워치 상태가 바뀌기 직전에 가져옴).

| 항목 (설정 → 소리 및 진동 → 진동 세기) | `settings system` 키 |
|---|---|
| 알림 | `SEM_VIBRATION_NOTIFICATION_INTENSITY` |
| 전화 | `VIB_RECVCALL_MAGNITUDE` |
| 시스템 | `VIB_FEEDBACK_MAGNITUDE` |
| 미디어 | `media_vibration_intensity` |

키는 Galaxy S25 Ultra(Android 16)에서 슬라이더를 움직여 확인했습니다. 다른 기종에서 다르면 `VibrationChannel`(`VibrationControl.kt`)을 수정하세요.

## 1. 빌드 & 설치

1. 빌드: Android Studio로 열어 실행하거나, CLI로
   ```
   gradlew.bat assembleDebug
   ```
   → `mobile/build/outputs/apk/debug/mobile-debug.apk`, `wear/build/outputs/apk/debug/wear-debug.apk`
2. **두 앱은 같은 PC에서 빌드해야 합니다.** applicationId와 서명 키(debug keystore)가 같아야 Data Layer가 연결됩니다.
3. 폰: 개발자 옵션 → USB 디버깅 켜고 USB 연결
   ```
   adb install -r --bypass-low-target-sdk-block mobile/build/outputs/apk/debug/mobile-debug.apk
   adb shell pm grant com.devdinist.watchwear android.permission.WRITE_SECURE_SETTINGS
   ```
   - 폰 앱은 의도적으로 `targetSdk 22`입니다. API 23 이상을 타깃하면 삼성 비공개 키를 앱에서 쓸 수 없습니다(`You cannot keep your settings in the secure settings`). 그래서 설치 시 `--bypass-low-target-sdk-block`가 필요하고, 실행할 때 "이전 버전용 앱" 경고가 뜨면 [확인]을 누르면 됩니다.
   - 권한 부여(`pm grant`)는 앱을 **삭제 후 재설치**했을 때만 다시 하면 됩니다(`install -r` 업데이트는 유지).
4. 워치: 설정 → 디바이스 정보 → 소프트웨어 정보 → 소프트웨어 버전 7번 탭(개발자 옵션) → ADB 디버깅 + 무선 디버깅 켜기 (PC와 같은 Wi-Fi)
   ```
   adb pair <워치IP>:<페어링포트>      # 무선 디버깅 → 새 기기 페어링 화면의 코드 입력
   adb connect <워치IP>:<포트>         # 무선 디버깅 메인 화면의 IP:포트
   adb -s <워치IP>:<포트> install -r wear/build/outputs/apk/debug/wear-debug.apk
   ```
5. 워치 앱에서 **착용감지 센서: 있음 ✅** 확인 → [감지 시작] (알림 권한 허용)

## 2. 테스트 순서

1. 폰 앱의 [워치 착용] / [워치 벗음] 버튼 → "결과"와 항목별 "현재 폰 설정"이 지정한 값으로 바뀌는지 (워치 없이 검증)
2. 워치 앱 [감지 시작] → 워치 벗기 → 폰 앱 "마지막 이벤트: WATCH_OFF" 확인
3. 폰 블루투스 끄기 → WATCH_DISCONNECTED 확인
4. 로그: `adb logcat -s OffBodyService WearStateListener VibrationControl`

## 3. 알려진 주의점

- 폰 앱은 실기기(Galaxy S25 Ultra, Android 16)에서 진동 세기 변경까지 확인했고, 워치 연동은 아직 미검증입니다.
- 워치 쪽 서비스가 가끔 죽으면 워치 설정에서 이 앱을 배터리 최적화에서 제외하세요. 폰 앱도 삼성 절전 예외(사용 안 함 앱 제외)에 넣으세요.
- 워치를 벗을 때 이미 연결이 끊긴 상태면 WATCH_DISCONNECTED가 먼저 처리되므로 진동은 정상적으로 켜집니다.
