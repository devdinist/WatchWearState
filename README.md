# WatchWearState

갤럭시워치 착용 → 폰 알림 진동 끄기 / 워치 벗음·연결 해제 → 폰 알림 진동 복원 (Tasker 불필요)

```
[워치 wear 모듈]                    [폰 mobile 모듈]
OffBodyService                      WearStateListenerService
 └ 착용감지 센서 ── DataClient ──▶   ├ onDataChanged ─────┐
                  (BT/Wi-Fi 자동)   └ onCapabilityChanged ┤
                                                          └ VibrationControl
                                                             └ SEM_VIBRATION_NOTIFICATION_INTENSITY 변경
```

| 이벤트 | 의미 | 동작 |
|---|---|---|
| `WATCH_ON` | 워치 착용 | 현재 세기를 기억하고 0으로 |
| `WATCH_OFF` | 워치 벗음 | 기억한 세기로 복원 (없으면 3) |
| `WATCH_DISCONNECTED` | 워치 연결 끊김 | 기억한 세기로 복원 |
| `WATCH_CONNECTED` | 워치 재연결 | 없음 (직후 현재 착용 상태가 재전송됨) |

- 착용 중에 직접 세기를 올려 두었다면, 벗을 때 그 값을 그대로 둡니다.
- 대상 키: `settings system SEM_VIBRATION_NOTIFICATION_INTENSITY` (설정 → 소리 및 진동 → 진동 세기 → 알림, 0~5). 다른 진동(전화 `VIB_RECVCALL_MAGNITUDE` 등)도 바꾸려면 `VibrationControl.kt` 수정.

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

1. 폰 앱의 [워치 착용] / [워치 벗음] 버튼 → "결과"와 "현재 알림 진동 세기"가 바뀌는지 (워치 없이 검증)
2. 워치 앱 [감지 시작] → 워치 벗기 → 폰 앱 "마지막 이벤트: WATCH_OFF" 확인
3. 폰 블루투스 끄기 → WATCH_DISCONNECTED 확인
4. 로그: `adb logcat -s OffBodyService WearStateListener VibrationControl`

## 3. 알려진 주의점

- 폰 앱은 실기기(Galaxy S25 Ultra, Android 16)에서 진동 세기 변경까지 확인했고, 워치 연동은 아직 미검증입니다.
- 워치 쪽 서비스가 가끔 죽으면 워치 설정에서 이 앱을 배터리 최적화에서 제외하세요. 폰 앱도 삼성 절전 예외(사용 안 함 앱 제외)에 넣으세요.
- 워치를 벗을 때 이미 연결이 끊긴 상태면 WATCH_DISCONNECTED가 먼저 처리되므로 진동은 정상적으로 켜집니다.
