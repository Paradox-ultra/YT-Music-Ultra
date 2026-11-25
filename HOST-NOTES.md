# Host notes

This repo holds the Java module (injected as classes9.dex) and its build and test tooling, under GPL-3.0.

The module expects a patched YouTube Music host that provides: NativeIntegration.fromTab(Object,String)Z, LUltraPivots.order, backAtRoot()Z and a public homeShowing()Z. The host smali edits are changes to Google's app and are not redistributed here. Build the host patches yourself from your own copy of the stock APK.

Host nav note: the host's TabLayout is obfuscated. The selected-tab position getter is a()I, not getSelectedTabPosition(). Calling the latter throws NoSuchMethodError (this crashed DL58b to DL61, fixed in DL62).

Latest tested build: DL62 (module dex md5 f7afc4481f401ac7abd8cf07ed167dbf).
