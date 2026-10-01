# Third-party licenses and resource audit

This document records the direct dependencies and bundled resources reviewed for NotifyMark Issue #13.

NotifyMark's own source code and project-specific resources are licensed under the Apache License 2.0 in the root `LICENSE` file. Third-party components remain under their own licenses.

## Direct application dependencies

| Component | Version | Scope | License |
|---|---:|---|---|
| AndroidX AppCompat (`androidx.appcompat:appcompat`) | 1.6.1 | Runtime / APK | Apache License 2.0 |
| AndroidX Core (`androidx.core:core`) | 1.13.0 | Runtime / APK | Apache License 2.0 |
| AndroidX DocumentFile (`androidx.documentfile:documentfile`) | 1.0.1 | Runtime / APK | Apache License 2.0 |
| Material Components for Android (`com.google.android.material:material`) | 1.12.0 | Runtime / APK | Apache License 2.0 |
| JUnit 4 (`junit:junit`) | 4.13.2 | Test only; not packaged in release APK | Eclipse Public License 1.0 |

Upstream license sources reviewed:

- AndroidX: https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt
- Material Components for Android: https://github.com/material-components/material-components-android/blob/master/LICENSE
- JUnit 4: https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt

## Build tooling

The repository uses Android Gradle Plugin 8.2.2 and Gradle 8.2.1. Gradle is distributed under Apache License 2.0. Build tooling is not part of the NotifyMark application license and remains subject to its upstream terms.

## Icons and bundled resources

Several small UI vector drawables use standard Material icon glyphs (for example `ic_add.xml` and `ic_delete.xml`). Google Material Design Icons are distributed under Apache License 2.0:

- https://github.com/google/material-design-icons/blob/master/LICENSE

Project-specific launcher/logo assets and their repository copies are treated as NotifyMark project resources under the root Apache License 2.0. Repository history does not record a separate third-party copyright or license notice for those assets, so this audit does not invent one.

No bundled font files or other separately licensed media resources were identified in `app/src/main/res` during this audit.

## Redistribution

A NotifyMark source or APK redistribution must preserve the root Apache License 2.0 terms for NotifyMark material and must also comply with the third-party licenses listed above. Third-party names and trademarks are not relicensed by NotifyMark.
