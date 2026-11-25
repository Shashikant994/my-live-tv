# LiveTV — Android TV Starter (Compose for TV + Media3 + GitHub OTA)

## Step-by-Step Setup

1. **Open in Android Studio**
   - Unzip this folder and open it in Android Studio (JDK 17).
   - Let Gradle sync dependencies (`androidx.tv:tv-material`, `media3-exoplayer-hls`, `okhttp`).

2. **Connect GitHub OTA Auto-Update**
   - Create a public GitHub repo and push this project.
   - Open `app/build.gradle.kts` and update:
     ```kotlin
     buildConfigField("String", "GITHUB_OWNER", "\"your-username\"")
     buildConfigField("String", "GITHUB_REPO", "\"your-repo-name\"")
     ```

3. **First Install on Android TV**
   - Build APK (`Build -> Build APK(s)` or `./gradlew assembleDebug`).
   - Sideload `app-debug.apk` on your Android TV via USB pendrive or `adb install app-debug.apk`.
   - On TV: `Settings -> Apps -> Security & Restrictions -> Unknown Sources` -> Allow for this app (required once so OTA updates can install).

4. **Test Auto-Update**
   - Bump `versionCode = 2` and `versionName = "1.0.1"` in `app/build.gradle.kts`.
   - Commit & push a tag:
     ```bash
     git tag v1.0.1
     git push origin v1.0.1
     ```
   - GitHub Actions (`.github/workflows/release.yml`) will auto-build and publish `app-debug.apk`.
   - Open the app on your TV — the green **Update to v1.0.1** button will appear in the sidebar!
