# JARVIS VIP — Personal Bilingual AI Assistant (Android)

**JARVIS VIP** is a futuristic dark-navy & electric-blue Android personal AI assistant supporting **Hindi + English** voice & text conversations, **OpenAI ChatGPT API** reasoning, real Android phone control, smart assistant utilities, and confirmation-gated social media drafting.

## Architecture & Modules
- **AI Brain (`OpenAiChatClient.kt`)**: Connects to OpenAI ChatGPT (`gpt-4o-mini`) via either:
  1. **Production Secure Backend Proxy (`/backend/server.js`)** authenticated with Firebase Auth ID Tokens (zero OpenAI API keys inside the APK), or
  2. **Prototype Secrets Injection** via `OPENAI_API_KEY` in AI Studio Secrets (`.env` -> `BuildConfig.OPENAI_API_KEY`).
- **Voice Engine (`VoiceEngineManager.kt` & `JarvisForegroundService.kt`)**: Bilingual Hindi/English speech recognition, female AI Text-to-Speech calibration, and opt-in foreground `"Hey JARVIS"` wake-word listener (`foregroundServiceType="microphone"`).
- **Phone Control (`PhoneControlExecutor.kt`)**: Contacts lookup, confirmation-gated Call/SMS/WhatsApp, app launcher, volume control, alarms, calendar events, Wi-Fi/Bluetooth/Settings shortcuts, Camera capture, Android Photo Picker, Battery & Storage telemetry, and opt-in Notification Reader (`JarvisNotificationListenerService.kt`).
- **Smart Services (`SmartServicesClient.kt` & `MathEvaluator.kt`)**: Live Open-Meteo weather, live Google News RSS headlines (English & Hindi), web search, Google Maps navigation, and offline math evaluation.
- **Cloud Persistence (`JarvisRepository.kt` & `firestore.rules`)**: Google Sign-In via Jetpack Credential Manager + per-user isolated Firestore collections (`users`, `memories`, `tasks`, `messages`, `socialDrafts`).

## GitHub Actions Automatic APK Build & Release
Workflow file: `.github/workflows/android-apk.yml`

1. Push your exported repository to `https://github.com/nasim2n2n-code/JARVIS-VIP` on branch `main`.
2. GitHub Actions automatically runs:
   - `./gradlew testDebugUnitTest --no-daemon`
   - `./gradlew assembleDebug --no-daemon`
3. Verifies `app/build/outputs/apk/debug/app-debug.apk` exists, uploads build artifacts, creates a versioned GitHub Release (`v1.0.1-build.<run_number>`), attaches both `JARVIS-VIP.apk` and `app-debug.apk`, and prints the direct download URL in the **Actions Build Summary**.

### Direct Release URL Format
Once a `main` push succeeds in GitHub Actions, your APK will be available at:
- `https://github.com/nasim2n2n-code/JARVIS-VIP/releases/latest`
- `https://github.com/nasim2n2n-code/JARVIS-VIP/releases/download/<TAG>/JARVIS-VIP.apk`

## Samsung Galaxy A51 Installation & Setup
1. Download `JARVIS-VIP.apk` from your GitHub Release.
2. Allow **Install unknown apps** for your browser/Files app on Galaxy A51.
3. Sign in with Google, then open **Settings -> Open Battery Optimization Settings** inside JARVIS VIP and set **Jarvis VIP** to **Unrestricted** so Samsung One UI does not sleep the optional `"Hey JARVIS"` voice guard.
