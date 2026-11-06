# Requirement Analysis - Kokhon

> Status: Draft v2 · Author: Md. Mazid-Ul-Haque · Last updated: 2026-11-06
>
> v2: added importance levels and reminder chains, the alarm-screen and discreet-mode requirements, updating entries from new text, and full edit control. Removed the "share confirmation back" feature.

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

## 3. Market scan - what regular schedulers do

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
| **Plans change** | When a plan changes ("let's move it to 10 pm"), you have to find the old event and edit it by hand. Sharing the new message just creates a duplicate. |
| **Confusing alarm screens** | Snooze and Stop look alike or need a swipe gesture. People panic, press the wrong one, and the alarm keeps ringing in front of everyone. Ring length, repeat count and how to stop it are rarely configurable. |
| **One importance for everything** | A tea break and a flight get the same reminder. You can't say "this one matters" and get a stronger reminder chain in one tap. |

## 4. Unique value proposition

1. **Capture from anywhere:** paste, share sheet, screenshot, clipboard suggestion chip, Quick Settings tile, and later voice.
2. **Show what was understood:** the source text is shown with highlighted chips (`tomorrow → Sat, 7 Nov`, `9:30 pm → 21:30`, `meeting → Meeting`). Tapping a chip fixes that one piece.
3. **Handle ambiguity explicitly:** confidence scoring, a one-tap choice when the app is unsure ("9:30 AM or PM?"), and an *approximate* flag for words like "around" or "maybe".
4. **Importance level → reminder chain:** the app suggests an importance level (Low / Normal / Important / Critical) from the text and event type, and each level comes with a matching reminder chain. You accept with one tap or change anything. Example of an Important chain: 30 min (notification) → 15 min (notification) → 5 min (alarm).
5. **Calm, unmistakable alarm screen:** Stop and Snooze are big, different-coloured, clearly labelled buttons. Ringing has a time limit. A Discreet mode (vibrate only) avoids awkward moments, and an "I'm aware - no more reminders" button silences the rest of the chain.
6. **Update from a new message:** share the changed message ("let's do 10 pm instead") and the app finds the existing event and shows what will change, instead of creating a duplicate.
7. **Full control:** edit, rename, reschedule, change type or importance, turn reminders off, duplicate or delete any entry, with undo.
8. **Reliability Doctor:** checks exact-alarm permission, notification channels, battery optimisation and OEM autostart, then walks you through fixing each one.
9. **Private and offline-first:** OCR and parsing run on the device. No account is needed.
10. **Bangla and Banglish parsing.**
11. **Duplicate and conflict detection** when the same plan arrives twice, or when two plans overlap.

## 5. Functional requirements

### FR-1 Entries
- FR-1.1 Create, read, update and delete entries: title, type, start, end or duration, all-day, location, notes, people, colour, checklist.
- FR-1.2 Types: Meeting, Event, Task, Dinner, Dinner Date, Tour/Trip, Match, Birthday, Appointment, Custom.
- FR-1.3 Recurrence (RRULE subset) and exceptions.
- FR-1.4 Task-specific fields: due time, done/undone, priority.
- FR-1.5 Keep the original captured text or image as the entry's source, so it's always traceable.
- FR-1.6 **Full control** over every entry: edit any field, rename, reschedule (including quick "+1 hour" / "tomorrow same time"), change type and importance, duplicate, cancel, delete. Destructive actions offer Undo.
- FR-1.7 Recurring entries: edit or delete "this one only" or "this and all following".
- FR-1.8 Edit history per entry (what changed, and whether it came from a new message or a manual edit).
- FR-1.9 **Status and the look of past entries.** Statuses: Upcoming, Ongoing, Completed, Cancelled (and Missed for tasks that weren't ticked off).
  - When an event's end time passes, it becomes **Completed** automatically.
  - Completed entries are **faded**, with a **green check badge**, and sink below active ones in the timeline (a collapsible "Done" section).
  - Cancelled entries are faded and struck through, with a grey badge.
  - A tap restores any entry to active (e.g. if it was postponed).

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
- FR-2.10 **Update from new text** - two ways in, one review screen:
  - **a. Targeted update:** select an entry first, choose *Update from message*, then paste the text or pick a screenshot. Only that entry is compared.
  - **b. Central capture:** paste or share a message normally. If it looks like a change (words like "move", "shift", "instead", "postpone", "cancel", "make it", or the same type, people or title within a nearby time window), the preview lists **all matched entries**, ranked by how well they match, each with its match reason.
  - For each candidate, a **before → after** diff of every changed field (date, time, place, title, importance), with the app's suggested choice preselected.
  - Actions: **Update this one**, **Update selected** (several at once), **Create new instead**, or **Cancel**. "Cancel" in the message (e.g. "meeting is off") offers **Mark cancelled**.
  - Nothing is overwritten silently. Every update is recorded in the entry's edit history and can be undone.
- FR-2.11 Suggest an importance level from the text ("important", "urgent", "must", "don't forget") and the event type (a flight or exam is Critical by default). You can always change it.

### FR-3 Image / screenshot capture
- FR-3.1 Inputs: share an image (`ACTION_SEND image/*`), pick from gallery (Photo Picker, so no storage permission is needed), take a camera photo.
- FR-3.2 On-device OCR (ML Kit Text Recognition v2), then the FR-2 pipeline.
- FR-3.3 Chat-aware cleanup: remove status-bar text, timestamps, "seen" and "typing" labels; keep message bubbles.
- FR-3.4 Reference date: use a date found in the screenshot when there is one (for example, "Yesterday" headers or a date stamp), otherwise the image's capture time.
- FR-3.5 Optional and off by default: auto-detect new screenshots and offer "Create from screenshot?"

### FR-4 Reminders, notifications, alarms

#### FR-4.1 Importance levels and reminder chains
- Every entry has an **importance level**: Low, Normal (the general default for all events), Important, or Critical.
- Each level maps to a **reminder chain**: an ordered list of steps. Each step has an *offset* (e.g. 30 min before) and a *style*:
  - **Silent:** only shown in the list/widget
  - **Notification:** normal sound
  - **Heads-up:** pops up on screen
  - **Alarm:** full-screen and rings
- Default chains (all editable in Settings):

  | Level | Default chain |
  |-------|---------------|
  | Low | 10 min · notification |
  | Normal | 15 min · notification |
  | Important | 30 min · notification → 15 min · notification → 5 min · alarm |
  | Critical | 1 day · notification → 1 h · heads-up → 30 min · alarm → 5 min · alarm |

- The app **suggests** a level (from text and type). In the editor and the parse preview, you see the suggested chain and can:
  1. accept it with one tap,
  2. pick another level or a saved chain, or
  3. customise the steps for this entry only: add, remove or reorder steps, change offsets and styles.
- Custom chains can be **saved as named presets** (e.g. "Exam", "Flight", "Gentle") and reused.
- Users can set which level each event type defaults to (e.g. Match → Important).

#### FR-4.2 Alarm screen: clear and calm
- **Stop** and **Snooze** are two large, clearly labelled buttons with different colours and icons. No swipe gestures, no ambiguity.
  - **Stop** = "stop this reminder" (the rest of the chain still fires).
  - **Snooze** shows its length in the label ("Snooze 5 min").
- A third, clearly separated action: **"I'm aware - turn off remaining reminders"** for this entry.
- The screen shows the title, time, and *what happens next* ("Next reminder: 5 min before - alarm").
- Pressing a **volume or power button** silences the sound immediately without dismissing, so you can step away calmly before choosing Stop or Snooze. Configurable.
- **Ring limit:** an alarm rings for at most *N* seconds (default 60, configurable), then becomes a silent notification. It never rings forever.
- **Auto-snooze limit:** maximum number of snoozes (configurable, default 3), then it stops.
- **Gentle start:** volume ramps up over a few seconds (configurable), with a choice of vibration only, sound only, or both.

#### FR-4.3 Avoiding awkward moments
- **Discreet mode:** one toggle (also available as a Quick Settings tile) that turns all alarms into vibrate-only notifications, for a meeting, mosque, cinema and similar. It can be set to end automatically (e.g. after 1 h).
- **Respect Do Not Disturb** by default; only Critical entries may bypass it, and only if the user allows it.
- **Sound follows ringer mode:** if the phone is on silent or vibrate, alarms vibrate instead of ringing, unless the entry is Critical and the user has opted in.

#### FR-4.4 Turning reminders off
- Per entry: turn **all reminders off** (the entry stays in the schedule), or turn off a single step.
- From the notification: **Done**, **Snooze**, **Turn off reminders** actions. No need to open the app.
- Marking an entry **Done** or **Cancelled** cancels all of its pending reminders.
- Turned-off reminders can be turned back on.

#### FR-4.5 Delivery
- Exact delivery via `AlarmManager.setExactAndAllowWhileIdle` or `setAlarmClock`; reschedule after reboot, time change and app update.
- Changing an entry's time automatically moves its whole reminder chain.
- Separate notification channels per style, so system settings stay meaningful.
- Optional morning agenda digest and evening "tomorrow" digest.

### FR-5 Views
- FR-5.1 A timeline home screen grouped as Now, Today, Tomorrow, This week, Later.
- FR-5.2 A calendar month view with an agenda below it.
- FR-5.3 Search and filter by type.
- FR-5.4 Home-screen widgets: next-up and today's agenda (Jetpack Glance).

### FR-6 Utilities
- FR-6.1 Reliability Doctor.
- FR-6.2 Export and import (JSON backup, `.ics`).
- FR-6.3 Optional sync with the device calendar through CalendarProvider (Google or Samsung accounts).
- FR-6.4 Detect conflicts and duplicates.

## 6. Non-functional requirements

| Area | Requirement |
|------|-------------|
| Platform | Android 8.0+ (minSdk 26), compileSdk and targetSdk 37 |
| UI | Jetpack Compose, Material 3 with dynamic colour (Material You), light and dark themes, motion, haptics |
| Accessibility | TalkBack labels, 48dp touch targets, font scaling up to 200%, WCAG AA contrast |
| Performance | Cold start under 1 s on a mid-range device; text parse under 50 ms; OCR plus parse under 1.5 s |
| Reliability | 99%+ of reminders fire within ±1 minute, once Reliability Doctor's checks pass |
| Privacy | No network access is needed for core features. No analytics without opt-in. Data stays on the device. |
| Battery | No polling. Only system alarms and WorkManager. |
| Quality | Parser has 300+ unit-tested phrases. Unit, UI and screenshot tests run in CI. |
| Localisation | English at launch; Bangla planned |

## 7. UX principles (HCI-based)

| # | Principle | How the app applies it |
|---|-----------|------------------------|
| 1 | **Thumb zone (Fitts's law)** | Every primary action sits in the bottom third, as a large target. The top of the screen is only for reading. |
| 2 | **Natural mapping** | The home screen is a time line: position shows time, with a "Now" line and past plans above it. The reminder editor is a ruler with draggable markers, so distance before the event is visible. |
| 3 | **Keep context** | Capturing or updating from a chat opens a half-height sheet over the chat, not a new screen. |
| 4 | **Visible system status** | One plain sentence says where you are: "2 plans left today. Next is Dinner in 1 h 20 min." |
| 5 | **Show what was understood** | Parsed words are marked like a highlighter pen. Solid = understood, dashed = a guess. Tapping a word changes it. |
| 6 | **Recognition over recall** | Options explain their effect ("Important - three reminders, the last one rings"). Time is nudged with −15 / +15 instead of typed. |
| 7 | **Fewer choices per step (Hick's law)** | Each screen has one primary button. Rare settings are folded away (progressive disclosure). |
| 8 | **Error prevention on alarms** | Stop and Snooze differ in size, colour, shape and position. Each button says what happens next. A volume press silences first, so the user can decide calmly. Ringing has a time limit. |
| 9 | **User control & freedom** | Every suggestion can be changed. Delete and update come with Undo. Nothing is overwritten without a confirmation question. |
| 10 | **Proactive but polite** | When the user copies a message that contains a plan, a dismissible "Make it a plan" suggestion appears. It never interrupts. |
| 11 | **State shown by position + colour + icon** | Completed plans are faded, have a green check, and sit above the Now line, so colour isn't the only signal (colour-blind safe). |
| 12 | **Legibility & accessibility** | A hyperlegible font, 16–17 px body text, 44 px+ touch targets, WCAG AA contrast, TalkBack labels, and support for 200% font size. |
| 13 | **Plain language** | Labels are sentences people say ("Move Team meeting to 10:00 PM?"), not system jargon. |

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
