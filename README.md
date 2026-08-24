# LDR Photobooth

An asynchronous photobooth for two people in different places. Person A starts a booth and takes four photos, then shares a code or link. Person B joins and takes four photos. The app combines both sets into one downloadable photostrip.

This repository is intentionally a lightweight project skeleton for a focused three-hour, two-person sprint.

## MVP flow

1. Person A creates a booth.
2. Person A takes four photos.
3. Person A shares the booth link or six-character code.
4. Person B opens the booth and takes four photos.
5. The app reveals a combined photostrip.
6. Either participant downloads the result.

The MVP is asynchronous. Person A and Person B do not need to be online at the same time.

## Repository structure

```text
ldr-photobooth/
├── frontend/          # React + Vite client (not scaffolded yet)
├── backend/           # Laravel or Spring Boot API (decision pending)
├── docs/
│   ├── API.md         # Draft API contract
│   └── BRANCHING.md   # Two-person Git workflow
├── .gitignore
└── README.md
```

## Stack decision

- **Frontend:** React + Vite
- **Backend:** choose exactly one before implementation starts:
  - **Laravel** — recommended for this three-hour sprint when speed and lower setup friction matter most.
  - **Spring Boot** — choose when practicing Java is an explicit goal and the backend owner is already comfortable with its setup.
- **Storage for the sprint:** local file storage and a lightweight database are acceptable. Keep the API contract independent of the framework.

Do not initialize both backend frameworks. Record the final choice in `backend/README.md` before scaffolding it.

## Ownership

- **Frontend owner:** React/Vite setup, camera permission, countdown and capture flow, UI states, photostrip rendering, and download action.
- **Backend owner:** booth/session lifecycle, code generation, photo upload and storage, validation, status transitions, and API responses.
- **Shared:** API contract, end-to-end integration, manual testing, and final demo.

The owners can work independently after agreeing on the contract in [`docs/API.md`](docs/API.md).

## Three-hour plan

- **00:00–00:20 — Align:** confirm the flow, backend choice, response shapes, ownership, and local ports.
- **00:20–01:20 — Build separately:** frontend implements the complete flow against mock responses; backend implements booth creation, lookup, and photo upload.
- **01:20–02:15 — Integrate:** connect the client to the API and complete status transitions.
- **02:15–02:45 — Finish the strip:** combine the photos, enable download, and handle the main error states.
- **02:45–03:00 — Test and demo:** run the flow in two browser sessions, fix blockers only, and update setup notes.

## Definition of done

The sprint is done when:

- two browser sessions can complete one booth without editing data manually;
- Person A can create a booth, take exactly four photos, and share a working link or code;
- Person B can join that booth and take exactly four photos;
- the booth moves through `WAITING_A`, `WAITING_B`, and `COMPLETED` correctly;
- the result displays photos from both participants as one photostrip;
- the photostrip can be downloaded;
- invalid codes and incomplete photo sets show a clear message; and
- a fresh clone can be started using the documented environment examples.

## Non-goals

- accounts, login, profiles, or an admin panel;
- realtime video, chat, WebSockets, or presence indicators;
- payments, public galleries, social feeds, or analytics;
- AI filters, face recognition, or advanced image editing;
- cloud-scale storage, production deployment, or exhaustive automated tests;
- supporting more than two participants per booth.

## Getting started

1. Read [`docs/API.md`](docs/API.md) together and settle any contract changes before coding.
2. Pick Laravel or Spring Boot and update `backend/README.md`.
3. Fetch and check out the shared branch for your role as described in [`docs/BRANCHING.md`](docs/BRANCHING.md).
4. Copy each `.env.example` to `.env` only after the corresponding app has been scaffolded.
5. Scaffold React/Vite in `frontend/` and the chosen backend in `backend/` on their respective branches.

No install or run commands are included yet because the application frameworks have deliberately not been initialized.
