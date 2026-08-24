# Draft API contract

Base URL during local development: `http://localhost:8000/api`

This contract is framework-neutral. Agree on it before the frontend and backend owners start separate work. All error responses use JSON and an appropriate HTTP status.

## Booth states

- `WAITING_A` — created, but Person A has not submitted all four photos.
- `WAITING_B` — Person A is finished; Person B may upload.
- `COMPLETED` — both participants have submitted four photos.

## Create a booth

```http
POST /api/booths
Content-Type: application/json
```

```json
{
  "name": "Kei"
}
```

`201 Created`

```json
{
  "code": "K7F2AD",
  "status": "WAITING_A",
  "shareUrl": "http://localhost:5173/booths/K7F2AD"
}
```

`name` is optional for the MVP. The code is uppercase, case-insensitive when entered, and safe to place in a URL.

## Get booth status

```http
GET /api/booths/{code}
```

`200 OK`

```json
{
  "code": "K7F2AD",
  "status": "WAITING_B",
  "photoCounts": {
    "a": 4,
    "b": 0
  },
  "resultUrl": null
}
```

When `status` is `COMPLETED`, `resultUrl` contains a URL for the combined photostrip. The frontend may poll this endpoint while waiting.

## Upload a participant's photos

```http
POST /api/booths/{code}/photos
Content-Type: multipart/form-data
```

Form fields:

- `participant`: `a` or `b`
- `photos`: exactly four image files, ordered from first to fourth

`200 OK`

```json
{
  "code": "K7F2AD",
  "status": "WAITING_B",
  "photoCounts": {
    "a": 4,
    "b": 0
  },
  "resultUrl": null
}
```

After Person B uploads successfully, the same response has `status: "COMPLETED"` and a non-null `resultUrl`.

## Download the completed photostrip

```http
GET /api/booths/{code}/result
```

`200 OK` returns an image with a download-friendly `Content-Disposition` header. Return `409 Conflict` until the booth is complete.

## Shared error shape

```json
{
  "error": {
    "code": "BOOTH_NOT_FOUND",
    "message": "Booth code was not found."
  }
}
```

Minimum cases to handle:

- `400 Bad Request` — malformed request;
- `404 Not Found` — unknown or expired booth code;
- `409 Conflict` — wrong participant turn, repeated submission, or result not ready;
- `413 Payload Too Large` — photo exceeds the configured limit; and
- `422 Unprocessable Entity` — wrong photo count, unsupported file type, or invalid field.

## Open decisions for kickoff

- whether the backend composes the final image or the frontend builds it with canvas;
- accepted photo formats and the maximum dimensions; and
- whether booth data expires automatically after the demo.

Choose the simplest option both owners can integrate within the sprint.
