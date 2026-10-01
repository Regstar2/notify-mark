<div align="center">

<img src="docs/assets/icon-transparent.png" width="120" alt="NotifyMark">

# NotifyMark

An Android app for local reminders backed by Markdown tasks: it reads selected notes, schedules notifications, and writes task changes back to the source Markdown.

[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-0A7EA4?style=for-the-badge&logo=android&logoColor=white)](#requirements)

[Русский](README.md) · **English**

[Quick start](#quick-start) ·
[Documentation](#documentation) ·
[Releases](../../releases) ·
[Feedback](#feedback)

</div>

---

## About

NotifyMark keeps reminders next to ordinary Markdown tasks and does not require a separate server. A source can be app-owned storage, an individual Markdown document, or a folder selected through the Android Storage Access Framework (SAF).

Markdown remains the source of truth: the app parses task lines, shows them in list and calendar views, schedules local reminders, and updates the source line after completion, skip, or editing.

## Project status

| Area | Status |
|---|---|
| Main Android client | **Beta** |
| First public version | Prepared as `v0.10.1-beta.1`; the GitHub Release is not published yet |
| Native NotifyMark format | Implemented |
| Obsidian Tasks compatibility | Partial, with documented limitations |
| Release pipeline | Signed APK, security audit, signature verification, and SHA-256 are automated |
| UI language | Russian; English UI localization is not part of this beta |

Beta does not mean confirmed stability. Before publishing the final tag, the workflow reruns CI and release checks; manual verification of the final APK on a device remains a separate release gate.

## Features

- app-owned Markdown storage;
- individual files and folders through SAF;
- due date/time, recurrence, grace period, and snooze;
- tags, groups, priorities, and subtasks;
- task list and calendar views;
- notification actions for complete, snooze, skip, and open;
- schedule restoration after reboot, app update, and system time changes;
- Quick Settings tiles for opening tasks and creating a task;
- status write-back to the source Markdown;
- partial Obsidian Tasks metadata compatibility.

## Quick start

Until the first public beta is published, the reproducible path is a debug build from source:

```powershell
.\gradlew.bat assembleDebug
adb install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

After launch, use app-owned storage or connect a Markdown file/folder and grant the required Android permissions.

After the public beta is published, end users should use the signed APK from [GitHub Releases](../../releases), not a debug build.

## Requirements

- Android 8.0 or newer (`minSdk 26`);
- notification permission on Android versions that request it;
- exact-alarm special access when reminders must fire at a precise time;
- SAF access for external files and folders.

Building from source requires JDK 17 and Android SDK 35.

## Installation

### Public beta

After `v0.10.1-beta.1` is published:

1. Open [GitHub Releases](../../releases/tag/v0.10.1-beta.1).
2. Download `NotifyMark-v0.10.1-beta.1.apk` and `NotifyMark-v0.10.1-beta.1.apk.sha256`.
3. Verify the APK SHA-256 against the published checksum file.
4. Allow APK installation from the selected source if Android requests it.
5. Install the APK and open NotifyMark.

PowerShell checksum verification:

```powershell
$Expected = ((Get-Content ".\NotifyMark-v0.10.1-beta.1.apk.sha256" -Raw).Trim() -split "\s+")[0]
$Actual = (Get-FileHash ".\NotifyMark-v0.10.1-beta.1.apk" -Algorithm SHA256).Hash
$Actual.ToLowerInvariant() -eq $Expected.ToLowerInvariant()
```

If a debug/development build signed with a different key is already installed, Android may reject an in-place update. Back up important Markdown and settings that cannot be restored before removing such a build.

## Usage

### Connecting a source

1. Open source management.
2. Select app-owned storage, an individual file, or a folder.
3. Confirm access in the system SAF picker for an external source.
4. Create a task in the app or add a supported task line to Markdown.

### Android permissions

- **SAF** — grants NotifyMark access only to a file or document tree selected by the user. Persistable read/write permission is retained for external sources; access can stop working if the document provider revokes the grant, the file moves, or the source becomes unavailable.
- **Notifications** — required to display reminders. Modern Android versions request this as a runtime permission.
- **Exact alarms** — `SCHEDULE_EXACT_ALARM` is used for precise reminder times. Android versions with special access may require an additional approval; without it reminder precision can be limited.
- **Boot completed** — `RECEIVE_BOOT_COMPLETED` is used to restore schedules after reboot.
- **Vibrate** — used for notification vibration.

### Task format

Native NotifyMark syntax:

```markdown
- [ ] Prepare the report @due(2026-10-05 18:00) @priority(high) #study
- [ ] Check the backup @repeat(1w) @snooze(30m)
```

Implemented metadata includes `@due(...)`, `@repeat(...)`, `@repeatUntilDone(...)`, `@grace(...)`, `@snooze(...)`, `@group(...)`, `@priority(...)`, `@tag(...)`, `#tags`, and the legacy `@YYYY-MM-DD` form.

Obsidian Tasks support is limited to a subset of emoji metadata. Exact behavior is documented in [v0.10.0 compatibility notes](docs/versions/v0.10.0-obsidian-tasks-plugin-compatibility.md).

## Privacy

- tasks and settings are processed locally;
- the app does not request `INTERNET` and does not perform its own HTTP/socket requests;
- external Markdown is available only after the user selects it through SAF;
- app-owned Markdown, settings, and persisted SAF references are excluded from Android cloud backup/device transfer;
- completing, skipping, and editing tasks changes the selected Markdown files.

**Back up important notes or use versioned synchronization before connecting them.** A beta can still contain write-back defects that have not been found by manual QA.

## Troubleshooting

If reminders do not appear, check:

1. notification permission;
2. exact-alarm special access;
3. vendor background restrictions;
4. persisted SAF access to the external source.

Unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

## Build

```powershell
.\gradlew.bat assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

A signed release build requires the configured release keystore; see [docs/release-signing.md](docs/release-signing.md).

## Testing

Project CI runs through:

```powershell
.\scripts\ci.ps1
```

The script builds the debug APK, runs unit tests, and executes the security audit. The release workflow additionally builds the signed APK, verifies it with `apksigner`, and checks SHA-256.

## Documentation

| Task | Document |
|---|---|
| Documentation index | [docs/README.md](docs/README.md) |
| Markdown format | [docs/markdown-format.md](docs/markdown-format.md) |
| Sources and SAF | [docs/source-system.md](docs/source-system.md) |
| Notification system | [docs/notification-system.md](docs/notification-system.md) |
| Obsidian Tasks compatibility | [docs/versions/v0.10.0-obsidian-tasks-plugin-compatibility.md](docs/versions/v0.10.0-obsidian-tasks-plugin-compatibility.md) |
| APK signing and publishing | [docs/release-signing.md](docs/release-signing.md) |
| `v0.10.1-beta.1` release notes | [docs/releases/v0.10.1-beta.1_EN.md](docs/releases/v0.10.1-beta.1_EN.md) |
| Change history | [CHANGELOG.md](CHANGELOG.md) |
| Roadmap | [docs/roadmap.md](docs/roadmap.md) |

## Feedback

- [Report a bug](../../issues/new?template=bug_report.yml)
- [Request a feature](../../issues/new?template=feature_request.yml)
- [Open issues](../../issues)

The app also links to GitHub Issues. Before external distribution, the repository must be accessible to the intended audience; private Issues are not a working public feedback channel.

Do not attach private notes in full. Remove personal data and unrelated Markdown content from logs and screenshots before posting them.

## Limitations

- this is a beta with no stability promise until the final tagged APK completes manual QA;
- Obsidian Tasks compatibility is partial: query blocks/query language, full natural-language recurrence grammar, and natural-language dates are not implemented;
- `every ... when done` and several other recurrence forms are preserved as metadata but are not mapped to a native recurrence rule;
- `⏳` and `🛫` alone do not trigger Android notifications;
- external-file behavior depends on SAF and the selected document provider;
- reminder timing depends on Android permissions and background restrictions;
- `applicationId` remains `com.regstar.obsidiannotification` to preserve Android application identity;
- the app UI in `v0.10.1-beta.1` is Russian-only; the English README and release notes do not imply an English-localized UI.

## License

NotifyMark is distributed under the [MIT License](LICENSE). Third-party licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
