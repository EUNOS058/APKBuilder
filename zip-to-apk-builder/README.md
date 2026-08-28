# 📱 ZIP to APK Builder — Phone-Only Real Android Compilation System

A complete solution to build **REAL Android APK packages** from `.zip` project archives using **ONLY an Android Phone**. No PC, laptop, or local server required.

---

## 📱 Phone-Only Workflow Summary

```
Android project ZIP on phone
  → Upload to Cloud / Remote Worker
  → Remote Build Worker / GitHub Actions compiles (gradle :app:assembleDebug)
  → Real app-debug.apk generated
  → Direct download link provided in app
  → Install APK directly on Android phone
```

---

## 🔧 Phone Setup Quickstart

### Method A: GitHub Actions Remote Runner (No Backend Hosting Required)
1. Fork or push this repository to your GitHub account using your phone browser or GitHub Mobile.
2. In GitHub settings on phone browser, generate a **Personal Access Token (PAT)** with `workflow` scope.
3. Open the Android App -> Tap Settings -> Choose **GitHub Actions Cloud**.
4. Enter `owner/repo` and your token -> Tap **Save & Test Connection**.

### Method B: Free Cloud Backend (Render / Koyeb / Cloud Run)
1. Deploy `zip-to-apk-builder/backend` to Render.com or Koyeb.com from your phone browser.
2. Open the Android App -> Tap Settings -> Choose **Cloud Host API**.
3. Enter your deployed server URL (`https://your-app.onrender.com/`) -> Tap **Save & Test Connection**.

---

## 🔨 Real APK Compilation Verification

When a build is triggered:
- The worker executes: `gradle :app:assembleDebug --stacktrace --no-daemon`
- The worker verifies the artifact at `app/build/outputs/apk/debug/app-debug.apk`.
- Real terminal logs and compilation steps are streamed to the phone app.
- Upon completion, the app presents the APK file size, filename, and direct download link.
