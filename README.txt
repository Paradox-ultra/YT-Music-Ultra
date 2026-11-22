YT MUSIC ULTRA DL32

NEW
- Fixed: the "View my" sheet (Library / Downloads / Device files) no longer pops up on its own when you come back to Library from Downloads with the bottom Library tab. DL31 fixed the back-button path; DL32 covers the bottom-tab path. When you leave Downloads, the app arms a short one-shot guard: the first "View my" sheet appearing right afterwards is dismissed automatically. Tapping the Library header yourself still opens the sheet normally, even straight after.
- Everything from DL31 is unchanged: return to Library by back button, Show voice recordings switch, copy or move songs between local playlists, cover art.

INSTALL
Package: app.rvx.android.apps.youtube.musiy
Version: 8.30.54-dl32 / 83054275
Certificate unchanged from DL25-DL31. Install over the current build, using the same CPU variant. Keep app data.
YT-Music-Ultra-DL32.apk: arm64
YT-Music-Ultra-DL32-armeabi-v7a.apk: v7a

VERIFICATION
169 automated tests passed with zero failures, errors or skips, including 6 new tests for the selector-sheet guard (auto sheet dismissed once after leaving Downloads, manual reopen stays open, sheet without a recent Downloads exit stays open, unrelated dialog does not spend the guard, expired guard leaves the sheet open, closing the panel arms the guard). Both APKs differ from DL31 only in AndroidManifest.xml and classes9.dex plus the re-signing, and pass v2/v3 signature verification with the same certificate as DL31.

LIMITS
Cause found by reading the code path and your screen recording: the system re-opens the Library content selector sheet shortly after the online Library page loads when you arrive from Downloads. The signed-in Library page cannot be reproduced on the signed-out cloud emulator, so the fix is verified by unit tests and static audits, not on a signed-in device or Android 13. If the sheet still appears, tell me exactly how you left Downloads.

SOURCE AND BUILD
Source ZIP includes Java module source, tests, audit and packaging scripts, Android API reference JAR and host reference JAR. Signing keys, emulator instrumentation and release APKs are excluded. Standard toolchain prerequisites: Java 17, Maven, Android Build Tools 35.0.0, Android API 33 android.jar. Extract classes.jar from androidx.test:monitor:1.7.2 and androidx.tracing:tracing:1.1.0 AARs and install them as local Maven JARs. Run colab-QA.py. Zip freshly compiled target/classes into module.jar, then D8 with min-api 26. package32.py requires MUSIC_BASE_ARM64 and MUSIC_BASE_V7A pointing to DL31, MUSIC_ZIPALIGN, MUSIC_APKSIGNER_JAR, MUSIC_SIGNING_KEY and MUSIC_SIGNING_CERT. Only manifest/version and classes9.dex change; resources, assets and native libraries remain unchanged.

Free GoFile hosting is temporary, not an archive.
