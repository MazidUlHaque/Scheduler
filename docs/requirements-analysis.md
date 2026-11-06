# Requirement Analysis — Scheduler (working name)

> Status: Draft v1 · Author: Mazidul Haque · Last updated: 2026-11-06

## 1. Problem statement

Plans are usually made in chat, not in a calendar app. A typical case:

> "let's try to sit for a meeting tomorrow around 9:30 pm"

To turn that into a reminder today, you have to open a calendar, work out what
"tomorrow" means, pick the time, type a title and set an alert. It's slow, so people
skip it and then forget the plan.

**Goal:** an Android app that turns any plan, whether typed, pasted, shared or
screenshotted, into a scheduled entry with dependable reminders and alarms. It
should take seconds and look good doing it.

## 2. Target users & core scenarios

| # | Scenario | Input | Expected result |
|---|----------|-------|-----------------|
| S1 | Manual entry | Form / quick-add bar | Entry with reminders |
| S2 | Pasted chat text | "meeting tomorrow around 9:30 pm" | Meeting, tomorrow's date, 21:30, flagged *approximate* |
| S3 | Screenshot of a chat | PNG from gallery or share sheet | On-device OCR → same as S2 |
| S4 | Shared from another app | Android share sheet (WhatsApp, Messenger, Gmail, SMS…) | Opens the parse preview directly |
| S5 | Event types | meeting, event, task, dinner, tour, match, dinner date, birthday… | Each type gets a sensible default reminder profile |
| S6 | Must-not-miss | e.g. early flight, match kickoff | Full-screen alarm with sound, plus escalation |

## 3. Market scan — what regular schedulers do

Apps looked at: Google Calendar, Samsung Calendar, Todoist, TickTick, Any.do,
Microsoft To Do, Structured, Alarmy, plus Fantastical on iOS as the benchmark for
natural-language input.

### 3.1 What they have (table stakes we must match)
- Manual create, edit and delete with title, date, time, location and notes
- Recurring events (daily, weekly, custom RRULE)
- Several reminders per event, plus snooze
- Day, week, month and agenda views
- Categories, colours and tags
- Home-screen widgets
- Basic natural-language quick-add (Todoist, TickTick, Fantastical; Google Calendar only partially)
- Search
- Dark mode
- Cloud sync and backup

### 3.2 What they don't have, or do badly (our opportunity)
| Gap | Detail |
|-----|--------|
| **Capture from chat** | Natural-language input only works in the app's own text box. Pasting a whole chat message, with names, filler words and emoji, usually fails or gets the title wrong. |
| **Screenshot → event** | Mainstream schedulers don't do this. Google Lens and Samsung's AI can sometimes suggest "add to calendar", but the result is hit-and-miss, tied to the OEM, and doesn't support custom reminders. |
| **Transparency of parsing** | Apps either silently guess or fail. None of them show *what* they understood and let you fix one piece with a tap. |
| **Ambiguity handling** | "9:30" with no am/pm, "around", "this weekend", "after Maghrib", "next Friday" (is that this week or next?). Apps guess without telling you. |
| **Reminders that fit the event** | A dinner date needs a "get ready" reminder and a "leave now" reminder. A tour needs a packing checklist the day before. Calendars give every event the same single "10 min before" alert. |
| **Reliable alarms** | Calendar notifications are easy to miss, and alarm apps don't understand events. Neither offers escalation (notification → heads-up → full-screen alarm). |
| **Delivery on aggressive OEMs** | On Xiaomi, Oppo, Vivo and some Samsung builds, battery optimisation silently kills reminders. Apps rarely detect this or guide you to fix it. |
| **Privacy** | Natural-language and OCR features in most apps depend on the cloud. |
| **Local language** | No support for Bangla or Banglish ("kal raat 9.30 e meeting"). |
| **Closing the loop** | Nothing produces a confirmation you can send back to the group ("Meeting confirmed: Sat 7 Nov, 9:30 PM ✅"). |

## 4. Unique value proposition

1. **Capture from anywhere:** paste, share sheet, screenshot, clipboard suggestion chip, Quick Settings tile, and later voice.
2. **Show what was understood:** the source text is shown with highlighted chips (`tomorrow → Sat, 7 Nov`, `9:30 pm → 21:30`, `meeting → Meeting`). Tapping a chip fixes that one piece.
3. **Handle ambiguity explicitly:** confidence scoring, a one-tap choice when the app is unsure ("9:30 AM or PM?"), and an *approximate* flag for words like "around" or "maybe".
4. **Reminder profiles per event type:** each type comes with its own reminder plan, such as get-ready, leave-by, packing checklist or deadline nudges.
5. **Escalating alarms:** a gentle notification, then a heads-up, then a full-screen alarm, with smart snooze options like "until 10 min before" or "when I'm free".
6. **Reliability Doctor:** checks exact-alarm permission, notification channels, battery optimisation and OEM autostart, then walks you through fixing each one.
7. **Private and offline-first:** OCR and parsing run on the device. No account is needed.
8. **Share back:** one tap produces a confirmation message for the group and an `.ics` invite.
9. **Bangla and Banglish parsing** (phase 2).
10. **Duplicate and conflict detection** when the same plan arrives twice, or when two plans overlap.

## 5. Functional requirements

### FR-1 Entries
- FR-1.1 Create, read, update and delete entries: title, type, start, end or duration, all-day, location, notes, people, colour, checklist.
- FR-1.2 Types: Meeting, Event, Task, Dinner, Dinner Date, Tour/Trip, Match, Birthday, Appointment, Custom.
- FR-1.3 Recurrence (RRULE subset) and exceptions.
- FR-1.4 Task-specific fields: due time, done/undone, priority.
- FR-1.5 Keep the original captured text or image as the entry's source, so it's always traceable.

### FR-2 Smart capture & parsing
- FR-2.1 Text input: paste, share (`ACTION_SEND text/plain`), quick-add bar.
- FR-2.2 Relative dates: today, tonight, tomorrow, day after tomorrow, weekdays, "next/this X", "in 2 hours", "on the 15th", explicit dates in several formats.
- FR-2.3 Times: 9:30, 9.30, 21:30, 9:30pm, "half past 9", noon, midnight, morning/evening, ranges ("7–9 pm").
- FR-2.4 Am/pm inference: from context words (dinner/tonight → PM), then from your past patterns, then by asking with a one-tap choice.
- FR-2.5 Extract the title, type, location ("at Gulshan", "@ Café X") and participants ("with team").
- FR-2.6 Approximate markers ("around", "approx", "maybe", "let's try") set an *approximate* flag.
- FR-2.7 One message containing several plans → several draft entries.
- FR-2.8 Every parse goes through a preview screen. Nothing is saved silently.
- FR-2.9 Parsing works offline.

### FR-3 Image / screenshot capture
- FR-3.1 Inputs: share an image (`ACTION_SEND image/*`), pick from gallery (Photo Picker, so no storage permission is needed), take a camera photo.
- FR-3.2 On-device OCR (ML Kit Text Recognition v2), then the FR-2 pipeline.
- FR-3.3 Chat-aware cleanup: remove status-bar text, timestamps, "seen" and "typing" labels; keep message bubbles.
- FR-3.4 Reference date: use a date found in the screenshot when there is one (for example, "Yesterday" headers or a date stamp), otherwise the image's capture time.
- FR-3.5 Optional and off by default: auto-detect new screenshots and offer "Create from screenshot?"

### FR-4 Reminders, notifications, alarms
- FR-4.1 Several reminders per entry; defaults come from the type's profile.
- FR-4.2 Exact delivery via `AlarmManager.setExactAndAllowWhileIdle` or `setAlarmClock`; reschedule after reboot, time change and app update.
- FR-4.3 Three levels: Notification, Heads-up, and Alarm (full-screen intent with sound and vibration).
- FR-4.4 Notification actions: Done, Snooze (5/10/30 min or a custom time), Open, Share confirmation.
- FR-4.5 Escalation if a reminder is ignored (configurable).
- FR-4.6 A morning agenda digest and an evening "tomorrow" digest (optional).
- FR-4.7 Separate notification channels per level, so system settings stay meaningful.

### FR-5 Views
- FR-5.1 A timeline home screen grouped as Now, Today, Tomorrow, This week, Later.
- FR-5.2 A calendar month view with an agenda below it.
- FR-5.3 Search and filter by type.
- FR-5.4 Home-screen widgets: next-up and today's agenda (Jetpack Glance).

### FR-6 Utilities
- FR-6.1 Reliability Doctor.
- FR-6.2 Export and import (JSON backup, `.ics`).
- FR-6.3 Optional sync with the device calendar through CalendarProvider (Google or Samsung accounts).
- FR-6.4 Share confirmation text.
- FR-6.5 Detect conflicts and duplicates.

## 6. Non-functional requirements

| Area | Requirement |
|------|-------------|
| Platform | Android 8.0+ (minSdk 26), targetSdk 36 |
| UI | Jetpack Compose, Material 3 with dynamic colour (Material You), light and dark themes, motion, haptics |
| Accessibility | TalkBack labels, 48dp touch targets, font scaling up to 200%, WCAG AA contrast |
| Performance | Cold start under 1 s on a mid-range device; text parse under 50 ms; OCR plus parse under 1.5 s |
| Reliability | 99%+ of reminders fire within ±1 minute, once Reliability Doctor's checks pass |
| Privacy | No network access is needed for core features. No analytics without opt-in. Data stays on the device. |
| Battery | No polling. Only system alarms and WorkManager. |
| Quality | Parser has 300+ unit-tested phrases. Unit, UI and screenshot tests run in CI. |
| Localisation | English at launch; Bangla planned |

## 7. UX principles

1. **Three taps or fewer** from a message to a saved reminder: share → check → save.
2. **Show what the app understood.** The parse preview highlights the source text instead of hiding it.
3. **Undo rather than confirm.** Don't interrupt with "Are you sure?" dialogs.
4. **Ask for permissions when they're needed.** Explain why at the moment of need, not in a wall at onboarding.
5. **Glanceable home screen.** The next item is always the hero card, with a live countdown.
6. **Gestures.** Swipe right to complete, swipe left to snooze or reschedule, long-press for quick actions.
7. **Personality without clutter.** A distinct colour and icon per type, gentle motion, and friendly empty states.

## 8. Platform constraints & risks

| Risk | Mitigation |
|------|------------|
| Exact alarms restricted (Android 12+ `SCHEDULE_EXACT_ALARM`; Android 14 denies it by default) | Declare `USE_EXACT_ALARM`, which Play policy allows for calendar and alarm apps. Fall back to `SCHEDULE_EXACT_ALARM` with a guided prompt. |
| Full-screen intents restricted on Android 14+ | Same alarm-app justification, plus `canUseFullScreenIntent()` check and a settings deep link |
| `POST_NOTIFICATIONS` runtime permission (13+) | Request it the first time a reminder is saved |
| OEMs killing background work | Reliability Doctor, plus deep links to OEM autostart settings |
| OCR accuracy on stylised chat UIs | Pre-processing, line clustering, and a mandatory preview |
| Ambiguous language | Confidence scores, user-pattern learning, and a one-tap choice |
| Screenshot auto-detect needs media permission | Opt-in only. The default flow uses the share sheet or Photo Picker. |
