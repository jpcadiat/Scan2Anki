---
title: Scan2Anki privacy policy
---

# Scan2Anki privacy policy

*[Version française](privacy-policy.fr.md)*

Effective date: 9 October 2026

Scan2Anki turns photos of printed vocabulary lists into AnkiDroid flashcards. It
has no user accounts, no advertising, and no servers of its own. The developer
does not receive any of your photos, text, or settings.

## Camera and photos

Scan2Anki uses the camera to photograph the pages you want to import, and the
Android photo picker to import pictures you choose from your gallery. The app
can only see the photos you select.

Photos are kept in the app's private storage only for the current import
session; they are deleted the next time the app starts a new session. They are never uploaded anywhere
unless you choose cloud text recognition (see below).

## Text recognition

**On-device (default).** Text is recognised on your phone with Google ML Kit.
Your photos stay on the device. ML Kit itself sends limited diagnostic data to
Google: device information (such as model and Android version), the app's
package name and version, a per-installation identifier that does not identify
you or your device, performance metrics, and API configuration. Google uses it
for diagnostics and usage analytics and does not pass it on to third parties.
See [Google's ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure).

**Google Cloud Vision (optional).** If you enter your own Google Cloud Vision API
key and choose cloud text recognition, the photo of the page is sent over HTTPS
directly from your phone to Google's Cloud Vision API, billed to your own Google
Cloud project. That processing is governed by Google's terms and privacy policy
for Google Cloud, not by the developer of Scan2Anki. Your API key is stored only
in the app's private storage on your device and is excluded from Android
backups.

## AnkiDroid

When you send cards, Scan2Anki uses AnkiDroid's on-device API to read your deck
and note type names and to add the new notes. This exchange happens entirely on
your phone.

## Settings

Your settings (default deck, note type, clean-up rules, OCR script, and API key)
are stored only on your device. Uninstalling the app or clearing its data
deletes them.

## Children

Scan2Anki is not directed at children under 13 and does not knowingly collect
personal information from anyone.

## Changes

If this policy changes, the new version will be published at this address with
a new effective date.

## Contact

Questions about this policy: open an issue at
<https://github.com/jpcadiat/Scan2Anki/issues>.
