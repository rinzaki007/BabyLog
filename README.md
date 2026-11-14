# BabyLog

A private-by-design family baby care and growth tracker for Android.

## Project status

🚧 Initial project scaffold — implementation is being built in small, verifiable stages.

## Goals

- Record feeding (breastfeeding, expressed breast milk, formula, mixed feeding)
- Track diaper changes, urine and stool observations
- Track sleep sessions
- Keep a chronological, editable care timeline
- Record growth measurements and health notes
- Share one baby's records between parents
- Work offline first, then synchronize safely when a connection is available

## Technology

- Kotlin, Jetpack Compose
- Room for local-first storage
- WorkManager for background synchronization
- Supabase Auth + PostgreSQL with strict Row Level Security (RLS)
- GitHub Actions for Android build verification

## Privacy

BabyLog may contain sensitive family and child-related information. Keep real records out of source control, logs, screenshots, and issue reports. Never commit Supabase service-role keys or other secrets. The Android client must use only a publishable/anon key, protected by database RLS.

> Repository visibility is currently controlled by the GitHub repository settings. Private visibility is strongly recommended before storing any real family data.
