# LDR Photobooth

Photobooth dua orang untuk pasangan atau teman yang sedang berada di tempat berbeda. Orang A membuat ruang dan mengambil empat foto, lalu membagikan tautan atau kode kepada Orang B. Setelah Orang B mengambil empat foto, aplikasi menyusun keduanya menjadi satu photostrip yang dapat diunduh.

## Demo

- Frontend: https://trustworthy-reprieve-production-b657.up.railway.app
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

1. Orang A membuat ruang.
2. Orang A membagikan tautan atau kode ruang kepada Orang B.
3. Orang A mengambil dan mengirim empat foto.
4. Orang B membuka ruang, lalu mengambil dan mengirim empat foto.
5. Backend menyusun delapan foto menjadi satu photostrip.
6. Kedua orang dapat mengunduh hasilnya.

## Cara kerja tim

- `main` hanya berisi versi yang sudah berhasil dibangun dan siap dideploy.
- Buat branch `feat/nama-fitur` atau `fix/nama-bug` untuk pekerjaan baru.
- Frontend dikerjakan di `frontend/`, backend di `backend/`.
- Gabungkan perubahan ke `main` melalui pull request agar pekerjaan dua orang tidak saling tertimpa.

Railway memantau branch `main`. Service frontend menggunakan root directory `/frontend`, sedangkan service backend menggunakan `/backend`.

## Kontrak API

Lihat [docs/API.md](docs/API.md) untuk endpoint, status ruang, format upload foto, dan bentuk error.

## Batasan MVP

Belum ada akun, chat, filter AI, pembayaran, atau dashboard admin. Fokus MVP adalah menyelesaikan satu alur dua orang dari pembuatan ruang sampai photostrip dapat diunduh.
