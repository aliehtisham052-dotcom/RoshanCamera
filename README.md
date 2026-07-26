# Roshan Camera

**Fast, lightweight GPS proof-camera for Android.**
By [Innovation-313](https://ehtisham.shop) · Native Kotlin · Offline-first · Ad-light

---

## What it is

Most GPS-stamp camera apps are slow, heavy, and only *write* a location onto a
photo — they never prove it. Roshan Camera is built around two promises:
**speed** and **truth**.

### The four pillars

| Pillar | What it means |
|---|---|
| **Instant capture** | The shutter fires immediately. Stamping happens afterwards, on a background thread. The UI never waits. |
| **Location you can trust** | A photo is stamped only once a GPS lock is confirmed. Live accuracy (in metres) is always visible. Weak signal is stated plainly — a wrong location is never stamped. |
| **Proof, not decoration** | Every photo carries a tamper-proof SHA-256 hash and a QR code. Anyone can scan it and verify the photo was taken at that place, at that time, and has not been altered. |
| **Light and clean** | Target install size ~8 MB. No forced ads, no tracking, no background services. Everything stays on the device. |

### Who it is for

Delivery riders · construction and contracting · agriculture · survey and
inspection · insurance claims · field attendance.

---

## اردو میں

**روشن کیمرہ** — اینڈرائیڈ کے لیے تیز اور ہلکا جی پی ایس پروف کیمرہ۔

موجودہ ایپس سست ہیں اور صرف مقام لکھتی ہیں، ثابت نہیں کرتیں۔ روشن کیمرہ دو
وعدوں پر بنا ہے: **رفتار** اور **سچائی**۔

- **فوری تصویر** — شٹر فوراً چلتا ہے، مہر بعد میں پس منظر میں لگتی ہے۔
- **قابلِ بھروسہ مقام** — مہر تبھی لگتی ہے جب جی پی ایس لاک مکمل ہو۔
- **ثبوت** — ہر تصویر پر SHA-256 ہیش اور کیو آر کوڈ، جسے کوئی بھی جانچ سکتا ہے۔
- **ہلکی اور صاف** — تقریباً ۸ میگابائٹ، کوئی زبردستی اشتہار یا ٹریکنگ نہیں۔

---

## Project status

v1.0 feature-complete and building. Not yet tested on a real device, and not
yet published.

- [x] Project skeleton, CI, branding
- [x] CameraX capture surface (instant shutter)
- [x] Location engine — pre-warmed `FusedLocationProvider`, lock confirmation, live accuracy
- [x] Reverse-geocoder with cache, falling back to coordinates when offline
- [x] Canvas stamp renderer on a background thread
- [x] SHA-256 hash + QR proof layer, with a verify screen
- [x] Gallery (MediaStore, thumbnailed), share, business name on stamp
- [x] Localisation: English, Roman Urdu, Urdu
- [ ] Real-device testing
- [ ] Play Store listing, signed AAB

Planned after v1.0: PDF reports, before/after pairs, voice notes, photo map,
custom watermark, altitude and compass, team sharing.

---

## Building

Requires JDK 17 and the Android SDK (compileSdk 36).

```bash
./gradlew assembleDebug      # debug APK
./gradlew testDebugUnitTest  # unit tests
./gradlew lintDebug          # lint
```

If `gradlew` is missing, generate the wrapper once with `gradle wrapper --gradle-version 8.14.3`.

CI runs tests, lint and a debug build on every push to `main`, and reports the
resulting APK size.

---

## A note on this repository

This repository is **public**. Signing keystores, tokens, `keystore.properties`
and any personal or customer data must never be committed — `.gitignore` guards
the known cases, but the rule comes first.

---

## Licence

Copyright © Innovation-313. All rights reserved.
