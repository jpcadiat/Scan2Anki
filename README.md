<p align="center">
  <img src="scan2anki.svg" alt="Scan2Anki logo" width="128">
</p>

# Scan2Anki

*[Version française](README.fr.md)*

Scan2Anki is an Android app that turns a printed two-column vocabulary list into
[AnkiDroid](https://github.com/ankidroid/Anki-Android) flashcards. Photograph the
pages, check that the columns were split correctly, clean up the text, and send
the word pairs to an AnkiDroid deck in one tap.

## Features

- **Multi-page capture** from the camera (with a flashlight toggle) or from the
  gallery. Pages can be deleted while capturing.
- **Text recognition (OCR)**
  - On-device with Google ML Kit: free, offline, and the default. Latin or
    Chinese script, selectable in Settings.
  - Optional Google Cloud Vision for harder pages. It needs your own API key,
    and you can test the key from Settings.
- **Column editor**: shows the detected text lines over the photo. You can move
  or add the split between the two columns, delete stray lines such as titles,
  and draw exclusion zones over text to ignore. A live preview shows the
  resulting pairs, and the layout is saved per page so you can reopen and adjust
  it later.
- **Review screen**: edit, add or delete pairs, swap the front and back columns,
  and re-run OCR on a page (on-device or cloud).
- **Clean-up rules**, applied to the front, back, or both columns, with a
  preview of every change before it is applied:
  - trim junk characters (plus extra characters of your choice),
  - strip trailing page numbers,
  - cut everything after a separator,
  - fix casing (sentence case or lowercase).

  Your clean-up settings are remembered between sessions.
- **AnkiDroid export**
  - Choose the target deck and note type from the lists in AnkiDroid, or type
    their names.
  - The default note type (*General* / *Généralités*) is created automatically
    if it is missing. It produces two cards per pair: front → back and
    back → front.
  - Any other note type with at least two fields can be used. The pair goes into
    the first two fields and the remaining fields are left empty.
  - Duplicates already in AnkiDroid are skipped and reported.
  - A *Check connection* button in Settings tests the link with AnkiDroid and
    asks for the permission it needs.
- **No leftover data**: scans exist only for the current session. Only your
  settings are kept when the app restarts.
- **English and French** user interface.

## Requirements

- Android 8.0 (API 26) or newer.
- [AnkiDroid](https://play.google.com/store/apps/details?id=com.ichi2.anki)
  installed on the same device (also on
  [F-Droid](https://f-droid.org/packages/com.ichi2.anki/)).
- Optional: a Google Cloud Vision API key for cloud OCR.

## Getting started

### Build from source

You need a JDK (17 or newer) to start Gradle, and the Android SDK (platform 36).
The Gradle wrapper downloads the right Gradle version. Gradle runs on JDK 25,
which it provisions automatically if it is not installed
(see `gradle/gradle-daemon-jvm.properties`).

```bash
git clone https://github.com/jpcadiat/Scan2Anki.git
cd Scan2Anki
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. To install it
on a connected device:

```bash
./gradlew installDebug
```

You can also open the project in Android Studio and run the `app` configuration.

### First run

1. Install and open AnkiDroid at least once. Create the deck you want to fill.
2. Open Scan2Anki, go to **Settings → AnkiDroid → Check connection**, and allow
   access to AnkiDroid when Android asks. You can also do this later: the
   permission request appears the first time you send cards.
3. Optionally, set a default deck and a default note type.

## How to use it

1. **Capture**: take a photo of each page of the list, or pick images from the
   gallery. Tap **Done — review pairs** when all pages are in.
2. **Adjust columns**: for each page, check the line that splits the front
   column from the back column. Tap a line to select it and delete it (for
   example a page title). Draw an exclusion zone to ignore an area. The preview
   updates as you go.
3. **Review**: correct any OCR mistakes, add or remove rows, and use **Clean up**
   to fix common problems across the whole list. From a page's menu you can
   reopen the column editor or re-run OCR.
4. **Send to AnkiDroid**: check the deck and note type at the bottom of the
   screen, then tap **Send to AnkiDroid**.

## Cloud OCR (optional)

On-device OCR is enough for most clean, printed lists. For poor photos, dense
layouts, or mixed scripts, Google Cloud Vision is often more accurate.

1. Create a project in the [Google Cloud Console](https://console.cloud.google.com/).
2. Enable the **Cloud Vision API** and **billing** for that project. Google
   rejects requests from projects without billing, even within the free tier.
3. Create an API key. Restricting it to the Cloud Vision API is a good idea.
4. Paste the key in **Settings → Text recognition** and tap **Test API key**. If
   the test fails, the app shows the error message returned by Google.

Once a key is saved, **Re-run OCR (Cloud)** appears in each page's menu on the
Review screen.

## Privacy

- On-device OCR never leaves your phone.
- Cloud OCR sends the page image to Google Cloud Vision, and only when you
  choose it for a page.
- The API key and your preferences are stored locally with Android DataStore.
- Scanned pages and word pairs are deleted when the app starts again.
- The app has no analytics or tracking. Its permissions are the camera, internet
  access (used only for cloud OCR), and AnkiDroid's database permission.

## Project structure

Single-module Kotlin app built with Jetpack Compose (Material 3), Hilt, Room,
DataStore, CameraX, ML Kit, OkHttp and kotlinx.serialization.

```
app/src/main/java/
├── com/scan2anki/
│   ├── ui/          Compose screens (Capture, Zone editor, Review, Settings), dialogs, navigation, theme
│   ├── vm/          ViewModels for each screen
│   ├── ocr/         OcrEngine interface, ML Kit and Cloud Vision implementations
│   ├── parse/       ColumnParser (OCR lines → word pairs) and OcrCleanup rules
│   ├── data/        Room database: session, pages, word pairs, per-page zone layout
│   ├── settings/    DataStore-backed user preferences
│   ├── ankidroid/   AnkiDroid integration (decks, note types, sending notes)
│   └── util/        Image helpers (EXIF rotation, decoding)
└── com/ichi2/anki/  AnkiDroid public API (vendored, see below)
```

The Room schemas are exported to `app/schemas/` and covered by migration tests.

## Tests

Unit and UI tests run on the JVM with Robolectric, so no device is needed:

```bash
./gradlew testDebugUnitTest
```

## Contributing

Bug reports and pull requests are welcome. Please run the test suite before you
submit a change, and add tests for new behaviour. User-facing strings live in
`app/src/main/res/values/strings.xml` (English) and
`app/src/main/res/values-fr/strings.xml` (French). Please update both.

## License

Scan2Anki is licensed under the
[European Union Public Licence v. 1.2](LICENSE) (EUPL-1.2). The EUPL is
available in all official EU languages, including
[French](https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:32017D0863),
from the [European Commission](https://interoperable-europe.ec.europa.eu/collection/eupl/eupl-text-eupl-12).
Every language version is equally valid.

### Third-party code

`app/src/main/java/com/ichi2/anki/` contains a copy of the
[AnkiDroid API](https://github.com/ankidroid/Anki-Android/tree/main/api)
(© Timothy Rae, Mark Carter and contributors). It is licensed under the
**GNU LGPL v3.0 or later**, except `FlashCardsContract.kt`, which carries an
all-permissive notice in its header. This code is not covered by the EUPL.

Scan2Anki is an independent project. It is not affiliated with or endorsed by
AnkiDroid, Anki or Google.
