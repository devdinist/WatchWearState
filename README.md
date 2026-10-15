# WatchWearState

갤럭시워치를 차면 폰 진동을 줄이고, 벗거나 연결이 끊기면 다시 원래 세기로 돌려놓는 앱입니다.
알림·전화·시스템·미디어 진동 세기를 각각 따로 정할 수 있고, Tasker 같은 다른 앱은 필요 없습니다.

## 어떻게 동작하나

워치 앱이 착용감지 센서를 계속 지켜보다가, 상태가 바뀌면 폰으로 알려 줍니다.
폰 앱은 그 신호를 받아 진동 세기를 바꿉니다.

```
[워치 앱]                           [폰 앱]
OffBodyService                      WearStateListenerService
 └ 착용감지 센서 ── DataClient ──▶   ├ 착용 상태 변경 ─┐
                  (BT/Wi-Fi)        └ 연결 상태 변경 ─┤
                                                      └ VibrationControl
                                                         └ 진동 세기 변경
```

| 상황 | 폰 진동 |
|---|---|
| 워치를 찼을 때 | "착용 시" 세기로 |
| 워치를 벗었을 때 | "벗었을 때" 세기로 |
| 워치 연결이 끊겼을 때 | "벗었을 때" 세기로 |
| 워치가 다시 연결됐을 때 | 바로 이어서 현재 착용 상태를 받아 그에 맞춤 |

## 진동 세기 정하기

폰의 "워치 착용 브릿지" 앱에서 항목마다 다음을 정합니다.

- 조절할지 여부 (체크박스)
- 착용 시 세기, 벗었을 때 세기 (0~5)

처음에는 알림만 켜져 있고, 착용 시 0, 벗었을 때는 그때 폰에 설정돼 있던 값으로 시작합니다.

앱과 폰 설정은 서로 맞춰집니다.

- 앱에서 슬라이더를 움직이면, 지금 워치 상태에 해당하는 값은 폰 설정에 바로 적용됩니다.
- 폰 설정(소리 및 진동 → 진동 세기)에서 직접 바꾼 값은 앱에도 저장됩니다. 워치를 벗은 상태에서 바꿨다면 "벗었을 때" 값이 바뀌는 식입니다.

## 설치

두 앱은 **같은 PC에서 빌드해야** 서로 연결됩니다. 서명 키가 다르면 폰과 워치가 통신하지 못합니다.

### 1. 빌드

Android Studio에서 실행하거나 명령줄에서 빌드합니다.

```
gradlew.bat assembleDebug
```

결과물은 아래 두 파일입니다.

- 폰: `mobile/build/outputs/apk/debug/mobile-debug.apk`
- 워치: `wear/build/outputs/apk/debug/wear-debug.apk`

### 2. 폰에 설치

개발자 옵션에서 USB 디버깅을 켜고 PC에 연결한 뒤 실행합니다.

```
adb install -r --bypass-low-target-sdk-block mobile/build/outputs/apk/debug/mobile-debug.apk
adb shell pm grant com.devdinist.watchwear android.permission.WRITE_SECURE_SETTINGS
```

- 앱을 처음 열 때 "이전 버전용 앱" 경고가 뜨면 [확인]을 누르면 됩니다. 이유는 아래 [참고](#참고)에 있습니다.
- 권한 부여(`pm grant`)는 한 번만 하면 됩니다. 업데이트(`install -r`)해도 유지되고, 앱을 지웠다가 다시 설치할 때만 다시 해 주세요.

### 3. 워치에 설치

1. 워치에서 설정 → 디바이스 정보 → 소프트웨어 정보 → 소프트웨어 버전을 7번 눌러 개발자 옵션을 켭니다.
2. 개발자 옵션에서 ADB 디버깅과 무선 디버깅을 켭니다. PC와 같은 Wi-Fi에 연결돼 있어야 합니다.
3. 아래 명령으로 연결하고 설치합니다.

```
adb pair <워치IP>:<페어링포트>      # 무선 디버깅 → 새 기기 페어링 화면에 나온 코드 입력
adb connect <워치IP>:<포트>         # 무선 디버깅 첫 화면에 나온 IP:포트
adb -s <워치IP>:<포트> install -r wear/build/outputs/apk/debug/wear-debug.apk
```

4. 워치에서 "착용 감지" 앱을 열고 **착용감지 센서: 있음 ✅** 이 보이면 [감지 시작]을 누릅니다. 알림 권한도 허용해 주세요.

설치가 끝나면 폰과 워치 모두 디버깅을 꺼도 됩니다. 워치를 재부팅해도 감지는 자동으로 다시 시작됩니다(부팅 후 시작까지 조금 걸릴 수 있습니다).

### 4. 꺼지지 않게 설정

- 워치: 설정 → 애플리케이션 → 착용 감지 → 배터리를 **제한 없음**으로
- 폰: 설정 → 배터리 → 백그라운드 사용 제한 → **사용 안 함 앱**에 "워치 착용 브릿지" 추가

## 동작 확인

1. 폰 앱의 [워치 착용] / [워치 벗음] 버튼을 눌러 "현재 폰 설정"이 정한 값으로 바뀌는지 봅니다. 워치 없이도 확인할 수 있습니다.
   테스트 후에는 [워치 연결 확인 + 상태 재동기화]를 눌러 실제 워치 상태로 되돌려 주세요.
2. 워치를 벗었다가 다시 차 보고, 폰 앱의 "워치 상태"와 진동 세기가 따라 바뀌는지 봅니다.
3. 폰 블루투스를 꺼서 "마지막 이벤트"가 `WATCH_DISCONNECTED`로 바뀌는지 봅니다.

문제가 있으면 로그를 확인해 보세요.

```
adb logcat -s OffBodyService WearStateListener VibrationControl
```

## 참고

### 폰 앱이 targetSdk 22인 이유

진동 세기는 삼성이 따로 쓰는 설정이라 Android가 공개한 설정 목록에 없습니다.
Android 6(API 23) 이상을 대상으로 만든 앱은 이런 설정을 바꿀 수 없고, `WRITE_SECURE_SETTINGS` 권한이 있어도 `You cannot keep your settings in the secure settings` 오류로 막힙니다.
그래서 폰 앱만 일부러 targetSdk 22로 빌드합니다. 설치할 때 `--bypass-low-target-sdk-block`이 필요하고, 실행할 때 경고가 뜨는 것도 이 때문입니다.

### 진동 항목별 설정 키

설정 → 소리 및 진동 → 진동 세기의 각 항목은 아래 `settings system` 키에 저장됩니다. 값은 0~5입니다.

| 항목 | 키 |
|---|---|
| 알림 | `SEM_VIBRATION_NOTIFICATION_INTENSITY` |
| 전화 | `VIB_RECVCALL_MAGNITUDE` |
| 시스템 | `VIB_FEEDBACK_MAGNITUDE` |
| 미디어 | `media_vibration_intensity` |

Galaxy S25 Ultra(Android 16)에서 확인한 키입니다. 다른 기종에서 다르다면 `VibrationControl.kt`의 `VibrationChannel`을 고치면 됩니다.

### 확인된 환경

- 폰: Galaxy S25 Ultra (Android 16)
- 워치: Galaxy Watch4 (SM-R870)
- 워치 착용·벗음에 따른 진동 변경, 워치 재부팅 후 자동 시작까지 확인했습니다.
- 블루투스 연결이 끊겼을 때의 동작은 아직 직접 확인하지 않았습니다.

## 라이선스

[MIT](LICENSE)
