# Kamus Isyarat Online

Aplikasi kamus bahasa isyarat berbasis Cloudflare Workers dan Google Drive.

## Struktur folder Google Drive

Setiap kontributor menyediakan satu folder publik yang berisi:

- kamus.json
- File gambar
- File video

Folder dan file harus memiliki izin:
Anyone with the link – Viewer.

## Format kamus.json

{
  "version": 1,
  "nama": "Kamus Bahasa Isyarat",
  "kata": {
    "MAKAN": {
      "definisi": "Memasukkan makanan ke dalam mulut.",
      "gambar": "ID_FILE_GAMBAR",
      "video": "ID_FILE_VIDEO"
    },
    "MINUM": {
      "definisi": "Memasukkan air ke dalam mulut.",
      "gambar": "ID_FILE_GAMBAR",
      "video": "ID_FILE_VIDEO"
    }
  }
}

Nama kata menjadi kunci data. Tidak perlu nomor ID.

## Cloudflare

Buat KV namespace dengan nama kamus, lalu hubungkan sebagai binding:

KAMUS_KV

Tambahkan Worker secrets:

ADMIN_PASSWORD
SESSION_SECRET
GOOGLE_API_KEY

Jangan memasukkan secret ke file JavaScript atau HTML.

## API

Publik:
- GET /api/catalog
- GET /api/dictionary/:id

Admin:
- POST /api/admin/login
- GET /api/admin/me
- POST /api/admin/logout
- GET /api/admin/dictionaries
- POST /api/admin/dictionaries
- PATCH /api/admin/dictionaries/:id
- DELETE /api/admin/dictionaries/:id

## Catatan

Menghapus kamus dari aplikasi tidak menghapus file di Google Drive.

Video menggunakan Google Drive Preview. Keberhasilan pemutaran bergantung pada izin berbagi dan dukungan browser.
