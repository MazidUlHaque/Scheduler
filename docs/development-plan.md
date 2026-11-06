# Development Plan & Architecture

> Status: Draft v1 · Author: Md. Mazid-Ul-Haque · Last updated: 2026-11-06

## 1. Tech stack

| Concern | Choice | Why |
|---------|--------|-----|
| Language | Kotlin 2.x | Modern, the Android default |
| UI | Jetpack Compose + Material 3 (dynamic colour) | The look Android users expect today; fast to iterate |
| Architecture | MVVM / unidirectional data flow, Clean-ish layers, multi-module | Easy to test and to scale |
| Dependency injection | Manual (`AppContainer`) | The object graph is small; no annotation processing keeps builds fast. Hilt can be added later if needed. |
| Persistence | Room (SQLite) + DataStore (preferences) | Offline-first |
| Async | Coroutines + Flow | |
| Scheduling | AlarmManager (exact) + WorkManager (digests, maintenance) | Reminders arrive on time while using little battery |
| OCR | ML Kit Text Recognition v2 (bundled model) | Runs on the device, free, works offline |
| Date/time spotting | Our own pure-Kotlin rule parser + ML Kit Entity Extraction as a second signal | Predictable, unit-testable, offline |
| Widgets | Jetpack Glance | |
| Navigation | Navigation-Compose (type-safe routes) | |
| Testing | JUnit 5 for pure Kotlin modules, JUnit 4 + Compose UI tests for Android modules | |
| Code quality | ktlint (via Spotless), Android Lint | |
| CI/CD | GitHub Actions: build, lint, test on every PR; signed release on tag | |

## 2. Module layout

```
Kokhon (repo: Scheduler)/
├── app/                      # Application, navigation host, DI wiring
├── core/
│   ├── model/                # Pure Kotlin domain models (Entry, Reminder, EntryType…)
│   ├── parser/               # Pure Kotlin NL parser - JVM-testable, no Android deps
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

### Phase 0 - Foundation ✅
- Gradle project (Gradle 9.8, AGP 9.5, Kotlin 2.4) with a version catalog; modules `app`, `core:model`, `core:designsystem`. The other modules are added in the phase that needs them.
- Domain model: entries, importance levels, reminder chains, alarm behaviour, statuses (unit-tested)
- Design system: colours, Atkinson Hyperlegible font, light/dark theme, buttons, done badge
- CI: formatting (ktlint), Android Lint, unit tests, debug APK artifact
- **Exit:** the app launches and shows a themed empty state; CI passes

### Phase 1 - MVP: manual scheduling + reliable, controllable reminders ✅
- Domain model and Room schema (with migrations): Entry, ImportanceLevel, ReminderChain, ReminderStep, ChainPreset
- Timeline home screen with status styling (completed = faded + green check, in a collapsible "Done" section); editor with full control (edit, rename, reschedule, change type/importance, duplicate, cancel, delete, undo)
- Importance levels with default reminder chains; per-entry chain editor; saved presets
- AlarmScheduler with exact alarms, boot and time-change rescheduling; moving an entry moves its chain
- Notification channels and actions (Done / Snooze / Turn off reminders)
- **Alarm screen:** big, clearly labelled Stop and Snooze buttons; "I'm aware - turn off remaining reminders"; volume button silences; ring limit; snooze limit; volume ramp
- Discreet mode toggle; respect for Do Not Disturb and ringer mode
- Permissions requested at the moment they're needed
- Delivered: everything above except saved reminder presets (moved to Phase 4) and per-entry edit history (moved to Phase 2, where updates from messages need it). Feature screens live in the `app` module for now and move to `feature:*` modules when they grow.
- **Exit:** an Important entry fires 30 → 15 → 5 min reminders on time (also after a reboot); the alarm can be stopped by anyone on the first try; turning reminders off for an entry silences its whole chain

### Phase 2 - Text capture (paste + share) ✅
- `core:parser` with 300+ test phrases (including your own examples)
- Share target for text, paste from the clipboard, quick-add bar
- Parse preview screen with highlighted chips and an ambiguity picker
- Several plans in one message; approximate flag
- Importance suggestion from text and type, shown with its reminder chain in the preview
- **Update from new text:** targeted flow (select entry → paste/screenshot) and central flow (paste → ranked list of all matched entries), before/after diff, Update / Update selected / Create new / Mark cancelled
- Delivered: `core:parser` (English and common Banglish: kal, aj, porshu, rat, sokal, bikal, sondha, "9.30e", "5tay"), update matcher, share target, paste with a copied-message suggestion, highlighted review sheet, AM/PM choice, several plans per message, targeted and central updates, per-plan history. The test corpus has 61 parser/matcher tests covering ~70 phrases; it grows with every reported parsing issue towards the 300+ target.
- **Exit:** "let's try to sit for a meeting tomorrow around 9:30 pm" → Meeting, tomorrow, 21:30, approximate. Three taps from WhatsApp. Sharing "let's make it 10 pm instead" then offers to update that same meeting.

### Phase 3 - Screenshot capture ✅
- ML Kit OCR, chat cleanup, reference-date detection
- Share target for images, Photo Picker, camera
- Optional screenshot auto-detect
- Delivered: `core:ocr` (ML Kit bundled model, offline), screenshot cleanup in `core:parser` (status bar, receipts, timestamps, typing box, date headers, wrapped lines), "message was sent yesterday" reference dates, Photo Picker, camera via the system camera app, sharing images to Kokhon, and screenshots in "Update from a message". Automatic screenshot detection moves to Phase 4 (it needs a media permission and should stay opt-in).
- **Exit:** a WhatsApp or Messenger screenshot produces the same result as Phase 2

### Phase 4 - Smart reminders & UX polish
- Extra reminder profiles per event type (get-ready, leave-by, packing checklist)
- Smart snooze ("until 10 min before")
- Reliability Doctor
- Morning and evening digests
- Glance widgets, Quick Settings tile
- Calendar month view, search, conflict and duplicate detection
- **Exit:** a feature-complete v1.0 beta

### Phase 5 - Release
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

## 6. Visual identity (draft v2)

A calm, legible look built on HCI principles. No deep blues, and no orange-on-cream.

| Token | Hex | Use |
|-------|-----|-----|
| Ink | `#16181D` | Text and primary buttons (highest contrast) |
| Paper | `#FFFFFF` | Background |
| Surface | `#F3F4F2` | Grouped content, secondary buttons |
| Highlighter | `#FFE066` | Understood words, the "Now" line, focus |
| Done green | `#1E8E4E` | Completed check badge |
| Stop red | `#C8102E` | Alarm Stop button, alarm-style reminders |
| Meeting | `#0F7C72` | Teal |
| Dinner date | `#C23B6B` | Rose |
| Match | `#B7770B` | Amber |
| Task | `#7A4BB0` | Plum |
| Tour | `#5E7A22` | Olive |

Font: **Atkinson Hyperlegible**, designed by the Braille Institute so that easily confused letters and numbers (like 1/l/I or 0/O) are easy to tell apart.
Times use tabular numbers, and are shown in 12-hour format by default.
