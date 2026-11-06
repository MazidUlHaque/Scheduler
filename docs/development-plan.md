# Development Plan & Architecture

> Status: Draft v1 · Author: Md. Mazid-Ul-Haque · Last updated: 2026-11-06

## 1. Tech stack

| Concern | Choice | Why |
|---------|--------|-----|
| Language | Kotlin 2.x | Modern, the Android default |
| UI | Jetpack Compose + Material 3 (dynamic colour) | The look Android users expect today; fast to iterate |
| Architecture | MVVM / unidirectional data flow, Clean-ish layers, multi-module | Easy to test and to scale |
| Dependency injection | Hilt | The standard on Android |
| Persistence | Room (SQLite) + DataStore (preferences) | Offline-first |
| Async | Coroutines + Flow | |
| Scheduling | AlarmManager (exact) + WorkManager (digests, maintenance) | Reminders arrive on time while using little battery |
| OCR | ML Kit Text Recognition v2 (bundled model) | Runs on the device, free, works offline |
| Date/time spotting | Our own pure-Kotlin rule parser + ML Kit Entity Extraction as a second signal | Predictable, unit-testable, offline |
| Widgets | Jetpack Glance | |
| Navigation | Navigation-Compose (type-safe routes) | |
| Testing | JUnit5, Turbine, MockK, Compose UI tests, Roborazzi screenshot tests | |
| Code quality | ktlint (Spotless), detekt, Android Lint | |
| CI/CD | GitHub Actions: build, lint, test on every PR; signed release on tag | |

## 2. Module layout

```
Scheduler/
├── app/                      # Application, navigation host, DI wiring
├── core/
│   ├── model/                # Pure Kotlin domain models (Entry, Reminder, EntryType…)
│   ├── parser/               # Pure Kotlin NL parser — JVM-testable, no Android deps
│   ├── data/                 # Room DB, DAOs, repositories
│   ├── alarm/                # AlarmScheduler, receivers, boot/time-change handling
│   ├── notification/         # Channels, builders, full-screen alarm activity
│   ├── ocr/                  # ML Kit wrapper + chat-screenshot cleanup
│   ├── designsystem/         # Theme, typography, colours, reusable components
│   └── common/               # Dispatchers, Result types, utils
├── feature/
│   ├── timeline/             # Home screen
│   ├── calendar/             # Month view
│   ├── editor/               # Create/edit entry
│   ├── capture/              # Paste/share/screenshot → parse preview
│   ├── settings/             # Settings + Reliability Doctor
│   └── widget/               # Glance widgets
├── build-logic/              # Gradle convention plugins
└── docs/
```

## 3. Smart-capture pipeline

```
Input (text | image)
  │
  ├─ image ─► OCR (ML Kit) ─► ChatCleanup (drop status bar, timestamps, UI chrome)
  │
  ▼
Normalizer (lowercase copy, unify 9.30→9:30, Banglish dictionary later)
  ▼
Tokenizer + Span detectors
  ├─ DateDetector      (today/tomorrow/weekday/next X/explicit dates)
  ├─ TimeDetector      (9:30, half past nine, noon, ranges)
  ├─ ApproxDetector    (around, maybe, let's try)
  ├─ TypeClassifier    (keyword lexicon → Meeting/Dinner/Match…)
  ├─ LocationDetector  (at/@/in + proper nouns)
  └─ PeopleDetector    (with X, my team)
  ▼
Resolver (reference time + time zone → absolute ZonedDateTime; am/pm inference)
  ▼
ParseResult { drafts[], spans[], confidence, ambiguities[] }
  ▼
Parse Preview UI (highlighted chips, quick fixes) ─► Save ─► AlarmScheduler
```

## 4. Phased roadmap

Each phase is delivered as its own pull request (or a few small ones) into `main`, with CI passing.

### Phase 0 — Foundation
- Gradle project, version catalog, convention plugins, all the modules above as empty shells
- Design system: theme, colours per entry type, typography, core components
- CI: build, unit tests, lint, detekt, ktlint
- **Exit:** the app launches and shows a themed empty state; CI passes

### Phase 1 — MVP: manual scheduling + reliable, controllable reminders
- Domain model and Room schema (with migrations): Entry, ImportanceLevel, ReminderChain, ReminderStep, ChainPreset
- Timeline home screen with status styling (completed = faded + green check, in a collapsible "Done" section); editor with full control (edit, rename, reschedule, change type/importance, duplicate, cancel, delete, undo)
- Importance levels with default reminder chains; per-entry chain editor; saved presets
- AlarmScheduler with exact alarms, boot and time-change rescheduling; moving an entry moves its chain
- Notification channels and actions (Done / Snooze / Turn off reminders)
- **Alarm screen:** big, clearly labelled Stop and Snooze buttons; "I'm aware — turn off remaining reminders"; volume button silences; ring limit; snooze limit; volume ramp
- Discreet mode toggle; respect for Do Not Disturb and ringer mode
- Permissions requested at the moment they're needed
- **Exit:** an Important entry fires 30 → 15 → 5 min reminders on time (also after a reboot); the alarm can be stopped by anyone on the first try; turning reminders off for an entry silences its whole chain

### Phase 2 — Text capture (paste + share)
- `core:parser` with 300+ test phrases (including your own examples)
- Share target for text, paste from the clipboard, quick-add bar
- Parse preview screen with highlighted chips and an ambiguity picker
- Several plans in one message; approximate flag
- Importance suggestion from text and type, shown with its reminder chain in the preview
- **Update from new text:** targeted flow (select entry → paste/screenshot) and central flow (paste → ranked list of all matched entries), before/after diff, Update / Update selected / Create new / Mark cancelled
- **Exit:** "let's try to sit for a meeting tomorrow around 9:30 pm" → Meeting, tomorrow, 21:30, approximate. Three taps from WhatsApp. Sharing "let's make it 10 pm instead" then offers to update that same meeting.

### Phase 3 — Screenshot capture
- ML Kit OCR, chat cleanup, reference-date detection
- Share target for images, Photo Picker, camera
- Optional screenshot auto-detect
- **Exit:** a WhatsApp or Messenger screenshot produces the same result as Phase 2

### Phase 4 — Smart reminders & UX polish
- Extra reminder profiles per event type (get-ready, leave-by, packing checklist)
- Smart snooze ("until 10 min before")
- Reliability Doctor
- Morning and evening digests
- Glance widgets, Quick Settings tile
- Calendar month view, search, conflict and duplicate detection
- **Exit:** a feature-complete v1.0 beta

### Phase 5 — Release
- `.ics` export/import, JSON backup
- Optional sync with the device calendar
- Bangla/Banglish parsing
- Play Store listing, privacy policy, signed release workflow
- **Exit:** v1.0.0 on the Play Store

## 5. Git & GitHub workflow

- `main` is always releasable and protected (PR plus green CI required; configured in repo settings).
- Branches: `feature/<name>`, `fix/<name>`, `chore/<name>`.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `chore:`…).
- Versions follow Semantic Versioning, tracked in [CHANGELOG.md](../CHANGELOG.md).
- GitHub Issues track work, with labels: `type:feature`, `type:bug`, `area:parser`, `area:alarm`, `area:ui`, `phase:1`…
- A GitHub Project board (Backlog → In progress → Review → Done) and one Milestone per phase.

## 6. Visual identity (draft)

Warm and fresh. No deep blues.

| Token | Hex | Use |
|-------|-----|-----|
| Ink | `#1E2420` | Text, the "Next up" hero card |
| Ground | `#F4F5F0` | App background |
| Tangerine | `#C4461C` | Primary actions (Save, Update) |
| Tangerine soft | `#FCE6DC` | Capture bar, highlights |
| Done green | `#2E8B57` | Completed check badge, toggles |
| Meeting | `#0F7C72` | Teal |
| Dinner date | `#C23B6B` | Rose |
| Match | `#B7770B` | Amber |
| Task | `#7A4BB0` | Plum |
| Tour | `#5E7A22` | Olive |

Font: Manrope. Completed entries are shown at 70% opacity with a green check badge, in a collapsible "Done" section.
