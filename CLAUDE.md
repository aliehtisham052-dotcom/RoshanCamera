# CLAUDE.md — Roshan Camera

Working notes for anyone (human or assistant) picking up this repository.

## What this app is

A GPS proof-camera for Android. Two promises drive every decision: **speed**
(the shutter never waits) and **truth** (a location is stamped only when it is
verified, and every photo carries a hash the world can check).

If a proposed change makes capture slower, the app heavier, or the proof weaker,
it is the wrong change — no matter how useful the feature sounds.

## Project shape

```
app/src/main/java/com/innovation313/roshancamera/
  RoshanCameraApp.kt   Application. Deliberately does no start-up work.
  MainActivity.kt      Placeholder launch screen for now.
app/src/main/res/
  values/              colours, strings (en), theme
  values-ur/           Urdu strings
  drawable/            ic_launcher_foreground / _monochrome (vector)
  mipmap-*/            legacy raster launcher icons
branding/              icon source SVGs and the Play Store 512px icon
gradle/libs.versions.toml   single source of truth for versions
```

- `applicationId` is `com.innovation313.roshancamera` and is pinned by a unit
  test. Changing it after release breaks updates for every installed user.
- minSdk 24, targetSdk 35, compileSdk 35, JVM target 17.
- View binding is on; no Compose. Views keep the APK small, which is a stated
  product goal, not a style preference.

## Verification

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew lintDebug           # lint
./gradlew assembleDebug       # debug APK
```

CI runs all three on every push to `main` and prints the resulting APK size as a
build notice. **When a build fails, read the CI check-run annotations first.**
Guessing at causes wastes a cycle; the annotation usually names the file and line.

## Conventions

- Kotlin official code style.
- All user-facing text goes in `strings.xml`; never hard-code it in a layout or
  in Kotlin. Urdu translations live in `values-ur/`.
- Colours come from `colors.xml`. The brand palette is gold `#D9A441` on ink
  `#0D0F14`.
- Nothing that touches the camera or the stamp pipeline runs on the main thread.

## Hard rules

- **This repository is public.** No keystores, tokens, `keystore.properties`,
  personal emails, or customer data — ever.
- The release signing keystore is held by the owner personally. Losing it means
  the app can never be updated again.
- No background location. Location is read while the camera screen is in the
  foreground, and not otherwise.
- Nothing is uploaded anywhere. All photos and data stay on the device.

## Size budget

Target install size is roughly 6–8 MB against competitors at 40–50 MB. That
number is a marketing claim on the store listing, so it is a real constraint:
weigh every new dependency against it, and check the APK size notice in CI.
