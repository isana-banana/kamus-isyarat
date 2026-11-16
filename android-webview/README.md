# Kamus Isyarat Android WebView

APK Android sederhana yang membuka website Kamus Isyarat di dalam WebView.

URL:
https://kamus-isyarat.bravewarrior011.workers.dev/

## Build APK

1. Buka folder `android-webview` dengan Android Studio.
2. Tunggu Gradle Sync selesai.
3. Pilih **Build > Build APK(s)** untuk APK debug.
4. APK hasil build berada di:
   `app/build/outputs/apk/debug/app-debug.apk`

Untuk release:
- buat signing key di Android Studio;
- pilih **Build > Generate Signed Bundle / APK**;
- pilih APK;
- pilih keystore yang dibuat;
- build release.

## Catatan

Aplikasi membutuhkan internet karena data kamus, gambar, dan video berasal dari website/Worker online.

Video HTML5 tetap menggunakan WebView. Dukungan fullscreen ditangani oleh `WebChromeClient`.

Website dan Worker tidak disalin ke APK, sehingga perubahan website dapat langsung terlihat pada APK.
