# AI Recycle Guide

사진을 촬영하면 서버가 Gemini API로 사진과 관악구 분리배출 데이터를 함께 분석하고, 안내 결과를 Android 앱에 보여주는 프로젝트입니다.

## Local Demo

Windows 로컬 환경에서 Android 앱과 FastAPI 서버를 함께 실행하는 방법입니다.

### Requirements

- Windows PowerShell
- Android Studio와 Android SDK
- Android 14(API 34) 이상 기기 또는 Android Virtual Device(AVD)
- uv 설치 및 PATH 등록
- Google AI Studio에서 발급한 Gemini API 키

실물 기기를 사용할 경우 Android Studio의 무선 디버깅 등으로 먼저 연결하고, adb devices -l에서 상태가 device로 표시되는지 확인합니다. 에뮬레이터를 사용할 경우 Android Studio Device Manager에서 AVD를 만들어 둡니다.

### Configure Gemini

프로젝트 루트에서 예시 환경 파일을 복사하고 편집합니다.

    Copy-Item server/.env.example server/.env
    notepad server/.env

GEMINI_API_KEY에 발급받은 키를 입력합니다. GEMINI_MODEL에는 사용할 수 있는 Gemini API 모델 ID를 지정할 수 있습니다. 예시 파일은 gemini-3.1-flash-lite를 사용합니다. 모델별 이용 가능 여부와 가격은 [Gemini 모델 안내](https://ai.google.dev/gemini-api/docs/models)에서 확인하세요. API 키가 든 server/.env는 저장소에 커밋하지 마세요.

Gemini API에는 모델과 프로젝트 사용 등급별 요청·토큰·일일 한도가 있어, 한도를 넘으면 요청이 거부될 수 있습니다. 현재 프로젝트의 제한은 [Google AI Studio의 사용량 제한 안내](https://ai.google.dev/gemini-api/docs/rate-limits)에서 확인하세요. 무료 사용 조건과 모델별 한도·가격은 계정과 시점에 따라 달라질 수 있습니다.

환경 변수를 수정한 뒤에는 서버를 다시 시작해야 적용됩니다.

### Start the Demo

프로젝트 루트에서 PowerShell 터미널로 실행합니다.

    .\scripts\run-local-demo.ps1

스크립트는 연결된 Android 기기가 하나면 해당 기기를 사용하고, 연결된 기기가 없으면 설정된 AVD를 실행합니다. 기기나 AVD가 여러 개라 자동으로 선택할 수 없는 경우 대상을 지정합니다.

    .\scripts\run-local-demo.ps1 -DeviceSerial "adb-device-serial"
    .\scripts\run-local-demo.ps1 -AvdName "Pixel_8_API_35"

스크립트는 FastAPI 서버를 시작하거나 이미 실행 중인 서버를 재사용하고, Android 앱을 빌드·설치·실행합니다. 빌드한 APK를 다시 사용하려면 -SkipBuild 옵션을 추가할 수 있습니다. 서버 API 문서는 실행 후 http://127.0.0.1:8000/docs 에서 확인합니다. 분석에 실패하면 FastAPI 서버 터미널의 Gemini 오류 로그를 확인하세요.

## Project Structure

### Android Client

Android 클라이언트는 android/에 있으며 Gradle 기반 Kotlin 앱입니다. UI는 Jetpack Compose로 구성합니다.

- android/app/src/main/java/.../ui/screens/: 카메라, 분석 중, 분석 결과 화면
- android/app/src/main/java/.../ui/viewmodel/: 화면 상태와 분석 요청 흐름
- android/app/src/main/java/.../data/: 사진 준비, API 모델, Retrofit 통신 및 저장소
- android/app/build.gradle.kts: 앱 빌드 설정과 기본 API 주소

앱은 CameraX로 사진을 촬영하고, 이미지를 multipart 요청으로 서버에 전송합니다.

### FastAPI Server

FastAPI 서버는 server/에 있으며 uv로 Python 의존성을 관리합니다.

- server/app/main.py: FastAPI 앱, 헬스 체크와 예외 처리
- server/app/routers/analysis.py: POST /api/v1/analyze 분석 API
- server/app/services/gemini.py: 이미지와 규칙 데이터를 Gemini API에 전달하고 결과 처리
- server/app/services/image_input.py: 업로드 이미지 검증 및 정규화
- server/app/services/recycling_rules.py: 지역 규칙 데이터 로드와 규칙 ID 검증
- server/app/schemas/: 분석 요청·응답 및 오류 데이터 모델
- server/app/rules/data/gwanak.json: 현재 데모에서 사용하는 관악구 분리배출 데이터
- server/docs/api-contract.md: 클라이언트와 서버 사이 API 계약

현재 분석 요청은 관악구 규칙을 사용합니다. 서버는 Gemini 응답을 검증하고 데이터에 없는 규칙을 근거로 안내하지 않도록 처리합니다.

### Local Demo Script

scripts/run-local-demo.ps1은 Windows PowerShell 스크립트입니다. ADB 기기 또는 AVD를 선택하고 FastAPI 서버 상태를 확인한 뒤 앱을 빌드·설치·실행합니다. 실물 기기에서는 ADB reverse를 설정해 기기가 호스트 컴퓨터의 로컬 서버에 접속하도록 합니다.

## Remaining Work

- 관악구 외 지역의 분리배출 데이터와 지역별 규칙 DB 마련
- 클라이언트 UI에 지역 선택 기능 추가 및 선택 지역 규칙 적용
- 사용자가 매일 배출할 품목을 확인할 수 있는 데일리 분리배출 루틴 마련
- 현재 서버 개발과 로컬 데모는 Windows 환경을 기준으로 합니다. 추후 운영 서버를 Linux 환경으로 이전하고, 실행·배포 설정도 Linux에 맞게 정리해야 합니다.
