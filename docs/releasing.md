# Releasing Kokhon

Releases are signed with **your** key. The key never goes into the repository; GitHub Actions gets it
from encrypted repository secrets.

## 1. Create the signing key (once, on your own computer)

You need Java (Android Studio includes it). In a terminal:

```bash
keytool -genkeypair -v \
  -keystore kokhon-release.jks \
  -alias kokhon \
  -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a password and your name. **Keep `kokhon-release.jks` and its password safe and backed up** (for example in a
password manager and an offline copy). If you lose them you can never update the app on Google Play.
Never commit the file: `*.jks` is already in `.gitignore`.

## 2. Add the key to GitHub (once)

Turn the key into text:

```bash
base64 -w 0 kokhon-release.jks > kokhon-release.txt      # Linux
base64 -i kokhon-release.jks -o kokhon-release.txt       # macOS
```

On GitHub: **Settings → Secrets and variables → Actions → New repository secret**, and add:

| Secret | Value |
|---|---|
| `KOKHON_KEYSTORE_BASE64` | the contents of `kokhon-release.txt` |
| `KOKHON_KEYSTORE_PASSWORD` | the keystore password |
| `KOKHON_KEY_ALIAS` | `kokhon` |
| `KOKHON_KEY_PASSWORD` | the key password (same as the keystore password unless you chose another) |

Then delete `kokhon-release.txt`.

## 3. Make a release

1. Update `versionCode` (+1 every release) and `versionName` in `app/build.gradle.kts`, and move the
   *Unreleased* notes in `CHANGELOG.md` under the new version.
2. Tag and push:

   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

3. The **Release** workflow tests, builds and signs the app, then publishes a GitHub release with:
   - `kokhon-v1.0.0.apk`: install directly on a phone (share with friends)
   - `kokhon-v1.0.0.aab`: upload to Google Play

## 4. Google Play (optional)

1. Create a developer account at play.google.com/console (one-time fee).
2. Create the app "Kokhon - Know When", fill in the listing from [`play-store/listing.md`](play-store/listing.md)
   and the Data safety form from the same file.
3. Privacy policy URL: `https://github.com/MazidUlHaque/Scheduler/blob/main/docs/privacy-policy.md`
4. Upload the `.aab` to the internal testing track first, test on your phone, then promote to production.
5. Exact alarms: Kokhon declares `USE_EXACT_ALARM`, which Google Play allows for apps whose core function is
   alarms and calendar reminders. The declaration form asks why: "Kokhon is a scheduling app; its core function
   is ringing reminders and alarms at the time the user set."
6. Full-screen alarms: declare `USE_FULL_SCREEN_INTENT` for "Alarm clock / calendar reminders".

## Building a signed release on your computer

Create `keystore.properties` in the project root (it is git-ignored):

```properties
KOKHON_KEYSTORE_FILE=/full/path/to/kokhon-release.jks
KOKHON_KEYSTORE_PASSWORD=...
KOKHON_KEY_ALIAS=kokhon
KOKHON_KEY_PASSWORD=...
```

Then run `./gradlew bundleRelease assembleRelease`.
