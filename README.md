# 📱 Phone-Only ZIP to APK Builder — Beginner Setup & Usage Guide

Build **REAL Android APKs** from source `.zip` archives directly using **ONLY your Android Phone** — **NO PC, NO Laptop, NO local Node.js, and NO Docker required!**

---

## 🏗️ Real Remote ZIP Handoff Architecture

Unlike mock systems, this system actually transmits the binary `.zip` file from your phone to the remote compilation environment:

```
[Phone App] 
    │ 1. Encodes selected ZIP to Base64
    │ 2. PUT https://api.github.com/repos/{owner}/{repo}/contents/.build-inputs/{buildId}.zip
    ▼
[GitHub Repository] Stores .build-inputs/{buildId}.zip
    │
    │ 3. Dispatch workflow_dispatch with zip_path: ".build-inputs/{buildId}.zip"
    ▼
[GitHub Actions Runner]
    │ 4. Check out repository with .build-inputs/{buildId}.zip
    │ 5. Extract ZIP & locate Android project root (settings.gradle / build.gradle)
    │ 6. Run real Gradle build: gradle :app:assembleDebug
    │ 7. Verify output: app/build/outputs/apk/debug/app-debug.apk
    │ 8. Publish APK to GitHub Release assets
    │ 9. Delete temporary .build-inputs/{buildId}.zip from repository
    ▼
[Phone App] Polls run status, receives release asset URL, and downloads real APK directly!
```

---

## 🛠️ Option 1: Phone-Only GitHub Actions Setup (Recommended)

You can set up GitHub Actions using only the Chrome/Brave/Firefox browser on your Android phone or the GitHub Mobile app.

### Step 1: Create a GitHub Repository on Phone
1. Open [github.com](https://github.com) on your phone browser.
2. Log in and tap **New Repository** (`+` button).
3. Name your repository (e.g., `my-apk-builder`).
4. Set visibility to **Public** or **Private**.
5. Ensure `.github/workflows/build-apk.yml` exists in your repository.

### Step 2: Generate a GitHub Personal Access Token (PAT)
1. On GitHub Mobile browser, tap your profile picture -> **Settings** -> **Developer Settings**.
2. Tap **Personal Access Tokens** -> **Tokens (classic)** -> **Generate new token**.
3. Name it `APK Builder App Token`.
4. Select scope: `workflow` (and `repo` if private).
5. Tap **Generate token** and **copy** the token string (`ghp_...`).

### Step 3: Configure the App on your Phone
1. Launch the **ZIP to APK Builder** app on your phone.
2. Tap the **Settings (Gear/Cloud Icon)** in the top header.
3. Select **GitHub Actions Cloud** mode.
4. Enter your Repository: `your-username/my-apk-builder`.
5. Paste your PAT in the token field (*Stored safely ONLY on your local device storage, never exposed or hardcoded*).
6. Tap **Save & Test Connection**. You will see green confirmation **"Remote Build Runner Active"**.

---

## ☁️ Option 2: Free 1-Click Cloud Host Setup (Render / Koyeb)

If you prefer using our hosted Node.js Express backend service:

1. Open [render.com](https://render.com) or [koyeb.com](https://koyeb.com) on your phone browser.
2. Tap **New Web Service** and select `zip-to-apk-builder` repository.
3. Render automatically installs dependencies (`npm install`) and starts `npm start`.
4. Copy your live service URL (e.g., `https://my-cloud-apk-builder.onrender.com/`).
5. Open the app -> Settings -> Select **Cloud Host API** -> paste your URL -> Tap **Save & Test Connection**.

---

## 📦 How to Build and Download your Real APK from Phone

1. **Select ZIP File**:
   Tap **Tap to Select ZIP File**. Choose any Android Gradle project `.zip` from your phone storage (e.g. Downloads or Google Drive).

2. **ZIP Verification**:
   The app automatically inspects the ZIP contents on-device to verify valid Gradle structure (`build.gradle`, `settings.gradle`, `gradlew`, or subfolders).

3. **Start Remote Build**:
   Select your build variant (`assembleDebug`) and tap **Build APK**.

4. **Monitor Progress & Live Logs**:
   Watch real compilation progress and live terminal logs streamed directly to your phone screen as the remote cloud worker compiles the project.

5. **Download Real APK**:
   When complete, you will see **"APK Build Successful"** showing the APK file size. Tap **Download APK** to download the compiled `.apk` directly to your phone's Download folder and install it!
