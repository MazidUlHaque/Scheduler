# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2026-11-06

### Added
- Appearance setting: Follow phone, Light or Dark
- After saving, a summary shows the plan, its date and time and every reminder, with Change details and Change reminders; OK opens the timeline with the new plan highlighted
- Every choice has a "Custom..." option: discreet mode (for a time or until a time), ring limit, snooze length, number of snoozes, and reminder times (any minutes, hours or days before, or an exact date and time)
- A warning before saving a time that has already passed, with "Move to tomorrow"
- Screenshot errors show what went wrong, with a "Copy details" button

### Changed
- Clearer status words: Coming up, Past, Done, Cancelled, Missed
- The battery check only warns when the phone actually restricts Kokhon in the background
- Screenshot reading: images are opened more reliably, small message times (like 8:18 PM) are ignored, a question and its reply are read together, the plan type nearest the date and time wins, and messages from the day before are dated correctly

### Fixed
- Dark text on a dark background in dark mode

## [1.0.0] - 2026-11-06

### Added
- Phase 5: signed release builds and a release workflow; privacy policy and Google Play listing
- Calendar files: export and import `.ics` (time zones, all-day events, alarms); JSON backup and restore
- Add a plan to the phone's calendar app, or share it as a calendar invite
- Bangla: the whole app translated, per-app language on Android 13+, Bangla digits in dates and times
- The parser understands Bangla script (আজ, কাল, পরশু, রাতে, সকালে, ৯টায়, সাড়ে ৯টা, মিটিং, ডিনার, পরীক্ষা, বাতিল ...)
- Phase 4: reminders per plan type (get ready, time to leave, pack, check in, prepare, gift), saved presets, smart snooze
- Daily summary (morning on, evening opt-in); clash and duplicate warnings
- Month calendar, search, Next up widget, Discreet Quick Settings tile, auto-start help for common phone makers
- Opt-in: offer plans from new screenshots (read on the phone; only notifies when a plan is found)
- Phase 3: make or update plans from screenshots and photos (Add a plan > From a screenshot / Take a photo, share an image to Kokhon, Pick a screenshot when updating)
- On-device text recognition (ML Kit bundled model): nothing is uploaded
- Chat screenshot cleanup: status bar, read receipts, timestamps, date headers and typing box removed; wrapped lines joined
- Dates are read from when the message was sent ("tomorrow" in yesterday's message means today)
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
