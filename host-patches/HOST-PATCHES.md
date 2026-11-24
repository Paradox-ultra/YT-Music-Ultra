# Host patches for YT Music Ultra (stock RVX YT Music 8.30.54)

Smali for the three host classes added to classes7 (UltraNav, UltraTouch, UltraBack). The module (src/) is built into classes9.dex.

Other host edits made in the same builds (applied to the stock dex files, not stored here as full trees):
- classes2: kzv FEmusic_ultra_home filled/outline icon branch; ijj.b backAtRoot hook before finish().
- classes6: MusicActivity.onBackPressed backAtRoot gate; dispatchTouchEvent calls UltraNav.touch (DL53+); onCreate registers UltraBack.
- classes7: lac/kzy hooks that start UltraNav (cold-start Home enforcer), SBRequester (music) skips ids that are not 11 chars or start with "ultra_" (DL54).
- Download system (paradox.dl) is untouched.

Contract with the module: NativeIntegration.fromTab(Object,String)Z, backAtRoot()Z, backAtRoot(Object)Z, back()Z, homeShowing()Z (DL54).
Rules: install -r only, never clear data, do not rename the app.
