# BabyLog

A private-by-design Android baby care and growth tracker with optional self-hosted family sync.

## Principles

- **Offline by default:** no account, server, or internet connection is required for local records.
- **Optional self-hosted sync:** users can configure their own HTTPS NAS endpoint.
- **Family sharing:** parents use separate accounts and join one family.
- **Local-first:** Room remains the Android source of truth; background sync is optional.

## Features

- Formula feeding volume and time (ml)
- Urine and stool observations
- Sleep records and editable timeline
- Growth measurements and charts
- Local baby photo album
- NAS sync server scaffold (Android client integration pending)

## Technology

- Android: Kotlin, Jetpack Compose, Room, WorkManager
- Server: FastAPI, SQLite WAL, NAS filesystem
- Deployment: Docker Compose / Dockge
- Default NAS host port: **18765**, configurable with `BABYLOG_PORT`

See [docs/FAMILY_SYNC.md](docs/FAMILY_SYNC.md) for deployment and implementation status.

Keep real baby records, passwords, tokens, and secrets out of source control. Do not expose the SQLite database or NAS management ports to the internet.
