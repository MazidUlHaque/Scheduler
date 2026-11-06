<div align="center">

# 📅 Scheduler

**Turn any chat message or screenshot into a reminder in seconds.**

Paste it, share it, or screenshot it. Scheduler works out the *what*, *when* and *where*, then makes sure you don't forget.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![Status](https://img.shields.io/badge/status-planning-orange)
![License](https://img.shields.io/badge/license-PolyForm%20Noncommercial%201.0.0-blue)

</div>

---

## ✨ Highlights

| | Feature | Description |
|---|---|---|
| 📋 | **Paste & share capture** | Share *"let's sit for a meeting tomorrow around 9:30 pm"* from WhatsApp or any app. You get a Meeting tomorrow at 21:30. |
| 📸 | **Screenshot capture** | On-device OCR reads chat screenshots and turns them into entries |
| 🔍 | **Shows what it understood** | Highlighted chips show exactly what was detected. Tap any chip to fix it. |
| 🎚️ | **Importance levels & reminder chains** | The app suggests how important a plan is and a matching chain (e.g. 30 → 15 → 5 min). Accept it in one tap or change anything. |
| ⏰ | **Calm, clear alarms** | Big Stop and Snooze buttons, a ring limit, "I'm aware — no more reminders", and a discreet vibrate-only mode |
| ✏️ | **Full control & updates** | Edit, rename, reschedule or delete anything. Share a changed message and the app updates the existing entry. |
| 🍽️ | **Reminders per event type** | Dinner dates get a "get ready" and a "leave now" reminder; tours get a packing checklist |
| 🩺 | **Reliability Doctor** | Detects battery-optimisation and permission problems that silently kill reminders |
| 🔒 | **Private by design** | OCR and parsing run on your phone. No account and no cloud needed. |

## 📚 Documentation

- [Requirement Analysis](docs/requirements-analysis.md): market scan, gaps, unique features, requirements
- [Development Plan & Architecture](docs/development-plan.md): tech stack, modules, roadmap
- [Changelog](CHANGELOG.md)

## 🛠️ Tech stack

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · Coroutines/Flow · AlarmManager · WorkManager · ML Kit (OCR) · Glance

## 🗺️ Roadmap

- [x] Requirement analysis & plan
- [ ] Phase 0: Project foundation & CI
- [ ] Phase 1: Manual scheduling + reliable reminders & alarms
- [ ] Phase 2: Text capture (paste / share) with smart parsing
- [ ] Phase 3: Screenshot capture (on-device OCR)
- [ ] Phase 4: Smart reminders, widgets, UX polish
- [ ] Phase 5: Release on Google Play

## 🤝 Contributing

Bug reports and ideas are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first.

## 📄 License & credit

**Copyright © 2026 Mazidul Haque. All rights not expressly granted are reserved.**

This project is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE):

- ✅ You may use, study, modify and share it for **personal and other noncommercial purposes**.
- ✅ You **must keep the copyright notice and credit** to the author in any copy or derivative (see [NOTICE](NOTICE)).
- ❌ **Commercial use** (selling the app, or using it in a paid product or service) requires written permission from the author.

The name "Scheduler", the app icon and other branding are not licensed for reuse.
