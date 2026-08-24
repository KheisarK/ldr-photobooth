# Backend

This directory is reserved for one backend API. It has deliberately not been scaffolded yet.

## Choose before coding

- **Laravel (recommended for the three-hour sprint):** use it when delivery speed is the priority.
- **Spring Boot:** use it when Java practice is a project goal and setup is already familiar.

Delete this decision section and record the chosen stack here before initialization. Do not install both frameworks.

The backend owner is responsible for:

- creating booths and generating unique share codes;
- accepting exactly four photos per participant;
- persisting booth state and uploaded images;
- enforcing `WAITING_A` → `WAITING_B` → `COMPLETED` transitions;
- validating codes, participant values, indexes, file types, and file sizes; and
- returning responses that match `../docs/API.md`.

For the sprint, local storage and SQLite are sufficient. Production hosting and cloud storage are outside the MVP.
