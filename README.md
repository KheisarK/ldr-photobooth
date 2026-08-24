# LDR Photobooth

Photobooth dua orang untuk pasangan atau teman yang sedang berada di tempat berbeda. Pembuat room berfoto lebih dulu, lalu tamu merespons dari tempat lain. Delapan foto mereka disusun menjadi satu photostrip yang dapat dipilih frame-nya dan diunduh.

## Demo

- Frontend: https://ldr-photobooth.up.railway.app
- Backend API: https://ldr-photobooth-production-b840.up.railway.app/api

## Teknologi

- Frontend: React 19, TypeScript, dan Vite
- Backend: Spring Boot 3 dan Java 17
- Database: PostgreSQL di Railway, H2 untuk pengembangan lokal
- Penyimpanan foto: filesystem lokal atau volume Railway

## Struktur project

```text
ldr-photobooth/
|-- frontend/   # Antarmuka, kamera, dan alur pengguna
|-- backend/    # API, database, penyimpanan foto, dan photostrip
|-- docs/       # Kontrak API
`-- README.md
```

## Menjalankan di komputer lokal

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Salin `frontend/.env.example` menjadi `frontend/.env.local` jika URL API perlu diubah.

Backend:

```bash
cd backend
mvn spring-boot:run
```

Backend lokal berjalan di `http://localhost:8000` dan menggunakan database H2 secara default. Konfigurasi production tersedia melalui environment variables pada `backend/.env.example`.

## Alur MVP

1. Pembuat memilih `Reference Mode` atau `Surprise Mode`, lalu membuat room.
2. Pembuat mengambil empat foto dan membagikan link atau kode.
3. Tamu mengambil empat foto; dalam Reference Mode, foto pembuat tampil sebagai panduan pose.
4. Setelah reveal, pembuat memilih salah satu dari tiga frame: Classic, Polaroid, atau Midnight.
5. Keduanya dapat mengunduh photostrip. Pembuat juga dapat menghapus room secara manual.
6. Demi privasi, room selesai otomatis terhapus setelah 15 menit; room yang tidak selesai dibersihkan setelah 24 jam.

## Cara kerja tim

- `main` hanya berisi versi yang sudah berhasil dibangun dan siap dideploy.
- Buat branch `feat/nama-fitur` atau `fix/nama-bug` untuk pekerjaan baru.
- Frontend dikerjakan di `frontend/`, backend di `backend/`.
- Gabungkan perubahan ke `main` melalui pull request agar pekerjaan dua orang tidak saling tertimpa.

Railway memantau branch `main`. Service frontend menggunakan root directory `/frontend`, sedangkan service backend menggunakan `/backend`.

## Kontrak API

Lihat [docs/API.md](docs/API.md) untuk endpoint, status ruang, format upload foto, dan bentuk error.

## Batasan MVP

Belum ada akun, chat, filter AI, editor bebas, pembayaran, atau dashboard admin. Fokus MVP adalah satu pengalaman dua orang yang ringkas, privat, dan terasa dilakukan bersama.
