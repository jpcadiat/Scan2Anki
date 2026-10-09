# Publishing Scan2Anki on Google Play

Checklist for the first Play Store release. Steps marked **(once)** only need doing
for the first release.

## 1. Developer account (once)

1. Sign up at <https://play.google.com/console/signup>. Pick a **personal** account
   unless you have a registered organisation (that needs a D-U-N-S number).
2. Pay the one-time US$25 fee and complete identity verification. Verification can
   take a few days.
3. Personal accounts must run a **closed test with at least 12 testers opted in for
   14 consecutive days** before applying for production access (step 7). Start
   recruiting testers early: they need a Google account and an Android device.

## 2. Host the privacy policy (once)

The policy lives in [`docs/privacy-policy.md`](privacy-policy.md) (French:
[`privacy-policy.fr.md`](privacy-policy.fr.md)).

1. Merge this branch into `main`.
2. GitHub → repository **Settings → Pages** → *Deploy from a branch* → `main`,
   folder `/docs`.
3. After the first deploy, the policy is at
   <https://jpcadiat.github.io/Scan2Anki/privacy-policy.html>. That URL goes into the
   Play Console.

## 3. Create the app (once)

Play Console → **Create app**:

| Field | Value |
| --- | --- |
| App name | Scan2Anki |
| Default language | English (United States) – en-US |
| App or game | App |
| Free or paid | Free (cannot be changed to paid later) |

The package name `com.scan2anki` is fixed when the first bundle is uploaded.

## 4. First build and upload (once)

1. Push a release tag, e.g. `git tag v1.0.0 && git push origin v1.0.0`.
2. When the *Release* workflow finishes, download the `scan2anki-1.0.0-aab`
   artifact from the workflow run.
3. Play Console → **Testing → Internal testing → Create new release**.
   - Accept **Play App Signing** (Google holds the app-signing key). The keystore
     in the `SCAN2ANKI_KEYSTORE_*` secrets becomes the **upload key**. Keep a
     backup of it; if it is lost, you can ask Google to reset the upload key.
   - Upload the `.aab` by hand. The Play API cannot create the first release of a
     new app.
4. Add yourself as an internal tester and install from the opt-in link to smoke
   test the Play-signed build. Test sending cards with AnkiDroid installed: the
   R8-minified build has been checked on an emulator for capture, OCR, the
   column editor and review, but not for the AnkiDroid send step.

## 5. App content (once)

Play Console → **Policy and programs → App content**:

- **Privacy policy:** the GitHub Pages URL from step 2.
- **Ads:** No.
- **App access:** All functionality is available without special access. In the
  reviewer notes, say that sending cards requires AnkiDroid
  (`com.ichi2.anki`, free on Play), that Cloud Vision is optional and needs the
  user's own API key, and that it sits behind a year-of-birth question: enter a
  year for someone 13 or over to test it.
- **Content rating:** answer the questionnaire (category *Reference, News, or
  Educational*; no violence, user interaction, sharing location or purchases).
  The expected result is the lowest rating everywhere (Everyone / PEGI 3 / USK 0).
- **Target audience:** age groups **9–12, 13–15, 16–17 and 18+**. "Appeals to
  children": yes. Including under-13s puts the app under the
  [Families policy](https://support.google.com/googleplay/android-developer/answer/9893335):
  stricter review, and Console answers must stay consistent with the privacy
  policy.
- **Families policy declarations:** no ads. ML Kit runs for every user; it sends
  no advertising ID or hardware identifiers. Google Cloud Vision is behind a
  neutral age screen (year of birth, asked only when the user turns it on);
  under-13s cannot enable it.
- **News app / COVID-19 / Government / Financial features / Health:** No.
- **Data safety:** see below.

### Data safety answers

Based on what the app does and on
[Google's ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure).
Re-check if dependencies change.

- Does your app collect or share any of the required user data types? **Yes**
- Is all user data encrypted in transit? **Yes** (ML Kit and Cloud Vision both use
  HTTPS)
- Do you provide a way for users to request that their data is deleted? **No**.
  Nothing is stored by the developer. Settings are deleted by uninstalling.

| Data type | Collected | Shared | Optional? | Purpose | Why |
| --- | --- | --- | --- | --- | --- |
| Photos and videos → Photos | Yes | No | Yes, optional | App functionality | Sent to Google Cloud Vision only when the user enables cloud OCR with their own key. Google acts as a service provider, so this is not "sharing". |
| App info and performance → Diagnostics | Yes | No | No | Analytics | ML Kit performance metrics, API configuration, error codes |
| Device or other IDs | Yes | No | No | Analytics | ML Kit per-installation identifier |

The Photos upload only happens for users who passed the age check. The age
result stays on the device and is not "collected".

Everything else (location, contacts, personal info, messages, app activity, web
history, files, calendar, etc.): **not collected**. Word pairs go to AnkiDroid on
the device and never leave it, so they are not "collected".

## 6. Store listing

Play Console → **Grow users → Store presence → Main store listing**.

- **Text:** copy from `fastlane/metadata/android/en-US/` (`title.txt`,
  `short_description.txt`, `full_description.txt`). Add a French translation
  (**Manage translations → fr-FR**) from `fastlane/metadata/android/fr-FR/`.
- **App icon:** 512 × 512 PNG, 32-bit, max 1 MB. Export it from `scan2anki.svg`.
- **Feature graphic:** 1024 × 500 PNG or JPEG, no transparency.
- **Phone screenshots:** 2–8, 16:9 or 9:16, each side between 320 and 3840 px.
  Suggested: capture screen with pages, column editor, review screen, settings.
- **Category:** Education. **Tags:** flashcards, language learning.
- **Contact details:** an email address (required, shown publicly) and the GitHub
  URL as the website.

## 7. Closed test → production

1. **Testing → Closed testing → Create track**, add the 12+ testers (email list or
   Google Group), and promote the internal release to it.
2. Wait until at least 12 testers have stayed opted in for 14 consecutive days.
3. **Dashboard → Apply for production**, answer the questions about the test, and
   wait for review (usually a few days).
4. Create a production release (promote the tested build), with a staged rollout
   if you like.

## 8. Automated uploads from CI (optional, once)

When `PLAY_SERVICE_ACCOUNT_JSON` is set, `.github/workflows/release.yml` uploads
each tagged AAB to Play.

1. In Google Cloud Console, create (or pick) a project and enable the
   **Google Play Android Developer API**.
2. **IAM → Service accounts → Create**. No Cloud roles are needed. Create a
   **JSON key** and download it.
3. Play Console → **Users and permissions → Invite new users**, and enter the
   service account's email. Under **App permissions**, add Scan2Anki with
   *Release apps to testing tracks* (plus *Release to production…* if CI should
   publish to production).
4. GitHub → **Settings → Secrets and variables → Actions**:
   - Secret `PLAY_SERVICE_ACCOUNT_JSON`: the full contents of the JSON key.
   - Variable `PLAY_TRACK` (optional): `internal` (default), `alpha` (= closed
     testing), `beta` (open testing) or `production`.
   - Variable `PLAY_RELEASE_STATUS` (optional): `completed` (default). Set it to
     `draft` while the app has never been reviewed: until then, Play rejects other
     statuses with *"Only releases with status draft may be created on draft
     app"*. Draft releases then have to be rolled out from the Console.
5. Delete the downloaded JSON key from your computer.

## Version numbers

The release workflow derives `versionCode` from the tag: `v1.2.3` → `10203`.
Play rejects a versionCode it has already seen, so every uploaded tag needs a
new `major.minor.patch`. A pre-release suffix doesn't count: `v1.2.3-beta` and
`v1.2.3` both produce `10203`.
