# Backend

Spring Boot API for the LDR Photobooth MVP.

## Stack

- Java 17
- Spring Boot 3.5
- Maven Wrapper
- Spring Web and Bean Validation
- Spring Data JPA with an H2 file database
- Local image storage and Java 2D photostrip composition

## Run locally

From this directory:

```bash
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

The API starts at `http://localhost:8000/api`. The default configuration works without a `.env` file. Environment variables from `.env.example` can override it; Spring Boot does not load `.env` automatically, so export them in the shell or configure them in the IDE.

## Test

```bash
./mvnw test
```

Runtime data is written to `backend/data/` and `backend/storage/uploads/`. Both are ignored by Git.

## API

See [`../docs/API.md`](../docs/API.md). The main flow is:

1. `POST /api/booths`
2. `POST /api/booths/{code}/photos` with participant `a` and four JPEG/PNG files
3. `POST /api/booths/{code}/photos` with participant `b` and four JPEG/PNG files
4. `GET /api/booths/{code}/result`

The completed PNG is a two-column, four-row strip: Person A on the left and Person B on the right.
