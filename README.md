# APK Builder

A real Android application that lets you:

1. Select an Android Studio project ZIP from your phone
2. Connect to your GitHub account (Personal Access Token)
3. Upload the project files to a GitHub repository
4. Automatically create a GitHub Actions workflow
5. Trigger a real `workflow_dispatch` build
6. Monitor build status
7. Download the generated APK artifact

**No fake/mock data.** All GitHub operations use the official REST API.

## Requirements

- Android Studio Ladybug (2024.2.1) or newer
- JDK 17
- A GitHub account
- A Personal Access Token with `repo` and `workflow` scopes

## How to open & build

1. Unzip / open this folder in Android Studio
2. Let Gradle sync
3. Build → Make Project (or Run on a device/emulator)

## First use on phone

1. Install the APK Builder app
2. Open **Settings / GitHub** screen
3. Create a classic Personal Access Token on GitHub:
   - Go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
   - Generate new token with scopes: `repo`, `workflow`
4. Paste the token, set username (`EUNOS058` by default), owner, repository name and branch
5. Tap **Save & Test Connection**
6. Go to Home → Select a `.zip` of an Android Gradle project
7. Tap **Upload Project**
8. Tap **Build APK**
9. Wait for GitHub Actions to finish (status auto-refreshes)
10. Download the APK artifact

## Security

- Token is stored using Android EncryptedSharedPreferences (AES256)
- Token is **never** written into the repository or workflow YAML
- Token is **never** printed in logs (Authorization header is redacted)
- Path traversal (`../`) is blocked when reading ZIP files
- Sensitive files (keystore, local.properties, secrets) are skipped

## Limitations

- Very large projects may hit GitHub API rate limits or file size limits when uploading file-by-file
- Only debug builds by default (no automatic release signing)
- Artifact download returns the zip that GitHub Actions uploaded; you may need to extract the `.apk` from it

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Authentication failed | Check token scopes and that it is not expired |
| Repository not found | Create the repo first or check owner/name |
| Workflow not found | Make sure upload succeeded (workflow file is created) |
| Build Failed | Open the run on GitHub to see Gradle logs |
| No artifact | Check that the project actually produced an APK under `build/outputs/apk` |

## Project structure

```
app/src/main/java/com/apkbuilder/app/
├── data/github/     # Real GitHub REST API client
├── data/storage/    # SecureStorage + ZIP FileManager
├── data/db/         # Room history
├── ui/screens/      # Compose screens
├── viewmodel/       # MainViewModel
└── MainActivity.kt
```

Built with Kotlin, Jetpack Compose, Material 3, Retrofit, Coroutines, Room, Security-Crypto.
