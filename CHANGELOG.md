# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Phase 2: make plans from pasted or shared messages (share sheet "Make it a plan", "Add a plan" > "Paste a message", copied-message suggestion)
- On-device message parser: relative dates, weekdays, explicit dates, times, ranges, "in 2 hours", approximate words, plan types, people, places, importance, several plans per message, common Banglish
- Review sheet with highlighted understood words (tap to fix), AM/PM choice for ambiguous times, -15 / +15 nudges
- Update existing plans from a message: ranked matches with before/after, tick to update several, cancel support; or pick the plan first ("Update from a message")
- Per-plan history (database version 2, automatic migration)
- Phase 1: create, edit, rename, reschedule, duplicate, cancel and delete plans (with Undo)
- Home timeline: finished plans above the Now line (faded, green check), upcoming below, plain-language status sentence
- Importance levels with default reminder chains; reminder editor with a ruler, per-step style and on/off, add/remove
- Reminder engine: exact alarms, reschedule after reboot/time change/update, notifications with Done / Snooze / Turn off reminders
- Alarm screen: large Stop, separate Snooze with snoozes left, "I'm aware", volume button silences, ring limit, gentle start
- Discreet mode (vibrate only), silent/Do Not Disturb respected, optional ring-on-silent for Critical plans
- Settings: alarm behaviour, discreet mode, "Will my reminders arrive?" checklist
- Robolectric tests for the reminder engine and rendered screen tests with screenshots
- Android project (Gradle 9.8, AGP 9.5, Kotlin 2.4, Jetpack Compose), app id `com.mazidulhaque.kokhon`
- `core:model`: entries, entry types, importance levels, reminder chains, alarm behaviour, statuses, with unit tests
- `core:designsystem`: Kokhon colours, Atkinson Hyperlegible typography, light/dark theme, buttons, done badge
- Home screen with day header, "Now" line and empty state; adaptive launcher icon
- GitHub Actions CI (format, lint, tests, debug APK) and Dependabot
- Requirement analysis and development plan (`docs/`)
- All Rights Reserved license, NOTICE, and community health files
- Issue and pull-request templates
