# Kamus Isyarat Offline

APK WebView offline terpisah dari proyek WebView online.

- UI utama tetap HTML/CSS/JS di WebView.
- Katalog, manifest, gambar, dan video disimpan di penyimpanan internal aplikasi.
- Halaman Download menggantikan Admin.
- Sinkronisasi mengunduh media yang belum ada atau ukuran file yang berubah.
- Kamus yang tersimpan muncul di dropdown dan dapat digunakan tanpa internet.

Build: gradle --no-daemon assembleDebug
APK: app/build/outputs/apk/debug/app-debug.apk
