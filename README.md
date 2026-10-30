# Lumina TV Launcher

Compose-based Google TV / Android TV home launcher.

## Get the APK (no Android Studio needed)
1. Create a free GitHub repo and push this folder to it (branch `main`).
2. Optional: Settings > Secrets and variables > Actions > New secret `TMDB_KEY` (free key from themoviedb.org) for real artwork.
3. Open the Actions tab > "Build APK" > download the `lumina-apk` artifact (~5 min).

## Or build locally
Open in Android Studio (JDK 17) and run Build > Build APK, or `gradle assembleDebug`.

## Install
    adb connect <tv-ip>
    adb install -r app-debug.apk
Then press Home and choose Lumina > Always. Keep the stock launcher enabled until Home works.
