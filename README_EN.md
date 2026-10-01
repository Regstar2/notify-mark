<div align="center">

# NotifyMark

An Android app for local reminders backed by Markdown tasks. It reads app-owned storage or selected files and folders, displays tasks in list and calendar views, schedules notifications, and writes status changes back to Markdown.

[Русский](README.md) · **English**

![Android](https://img.shields.io/badge/platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Java](https://img.shields.io/badge/language-Java%2017-007396?style=for-the-badge&logo=openjdk&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-0A7EA4?style=for-the-badge)

[Quick start](#quick-start) ·
[Task formats](#task-formats) ·
[Documentation](#documentation) ·
[Limitations](#limitations)

</div>

---

## About

NotifyMark keeps reminders next to ordinary Markdown tasks and does not require a separate server. A source can be an app-owned file, an individual document, or a folder selected through the Android Storage Access Framework (SAF).

The same task engine handles every source: it parses Markdown lines, displays tasks and recurring occurrences, schedules local notifications, and updates the source line after a task is completed, snoozed, or skipped.

## Project status

| Area | Status |
|---|---|
| Main Android client | Implemented; first public beta is being prepared |
| Native NotifyMark format | Implemented |
| Obsidian Tasks compatibility | Partial, with documented limitations |
| Version metadata | Beta baseline: `versionName 0.10.1-beta.1`, `versionCode 26`; planned Git tag: `v0.10.1-beta.1` |

## Features

- app-owned Markdown storage for immediate use;
- individual files and folders selected through SAF;
- due date and time, recurrence, grace period, and snooze settings;
- tags, groups, priorities, and nested subtasks;
- task list and calendar views;
- notification actions for complete, snooze, skip, and open;
- schedule restoration after reboot, app update, and time or time-zone changes;
- Quick Settings tiles for opening tasks and creating a task;
- status write-back to the original Markdown file.

## Quick start

Build a debug APK from the repository root:

```powershell
.\gradlew.bat assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

After installation, open the app, use app-owned storage or connect a Markdown file/folder, and grant notification access. Android may also request exact-alarm access for reminders scheduled at a specific time.

## Requirements

- Android 8.0 or newer (`minSdk 26`);
- Android SDK 35 for builds;
- JDK 17;
- notification permission;
- exact-alarm access for time-specific reminders;
- persisted SAF access for external files and folders.

## Usage

### Connecting a source

1. Open source management.
2. Select app-owned storage, an individual file, or a folder.
3. Confirm access in the Android system picker for external sources.
4. Create a task in the app or add a supported task line to Markdown.

### Handling a reminder

A notification can complete the task, snooze it, skip the current occurrence, or open the source task. Recurring tasks keep per-occurrence history and advance according to their recurrence rule.

### Task formats

NotifyMark supports its native directives:

```markdown
- [ ] Prepare the report @due(2026-08-10 18:00) @priority(high) #study
- [ ] Check the backup @repeat(1w) @snooze(30m)
```

Implemented metadata includes `@due(...)`, `@repeat(...)`, `@repeatUntilDone(...)`, `@grace(...)`, `@snooze(...)`, `@group(...)`, `@priority(...)`, `@tag(...)`, `#tags`, and the legacy `@YYYY-MM-DD` date form.

The app also provides partial compatibility with Obsidian Tasks metadata: `📅`, `⏰`, `🔁`, `✅`, `❌`, priority markers, and several auxiliary emoji fields. It does not implement the full Obsidian Tasks recurrence grammar; exact behavior is documented in the [v0.10.0 compatibility notes](docs/versions/v0.10.0-obsidian-tasks-plugin-compatibility.md).

## Privacy

- tasks and settings are processed locally;
- external files are available only after the user selects them through SAF;
- the app does not request the `INTERNET` permission and does not perform its own HTTP/socket requests; GitHub links are opened only after a user action in an external app;
- app-owned Markdown files, settings, and persisted SAF references are excluded from Android cloud backup and device-to-device transfer; external Markdown files remain with the selected document provider and are not copied by the app backup mechanism;
- completing, skipping, and editing tasks changes the selected Markdown files.

Keep a backup or versioned synchronization for important notes before connecting them.

## Troubleshooting

Run unit tests with:

```powershell
.\gradlew.bat testDebugUnitTest
```

When notifications do not appear, check notification access, exact-alarm access, and vendor-specific background restrictions. If an external file is no longer updated, verify the persisted SAF permission and the document provider's availability.

## Build

```powershell
.\gradlew.bat assembleDebug
```

The project uses Java 17, `compileSdk 35`, and `targetSdk 35`. The namespace and `applicationId` remain `com.regstar.obsidiannotification` to avoid changing the Android application identity during the ongoing refactor.

## Testing

Project command:

```powershell
.\gradlew.bat testDebugUnitTest
```

This README does not claim a passing result for the current branch: the command is taken from the repository configuration, but it was not executed as part of this GitHub beta-baseline preparation.

## Documentation

| Task | Document |
|---|---|
| Architecture | [docs/architecture.md](docs/architecture.md) |
| Project structure | [docs/project-structure.md](docs/project-structure.md) |
| Markdown sources | [docs/source-system.md](docs/source-system.md) |
| Task model | [docs/task-model.md](docs/task-model.md) |
| Markdown format | [docs/markdown-format.md](docs/markdown-format.md) |
| Notification system | [docs/notification-system.md](docs/notification-system.md) |
| UI navigation | [docs/ui-navigation.md](docs/ui-navigation.md) |
| Technical debt | [docs/technical-debt.md](docs/technical-debt.md) |
| Roadmap | [docs/roadmap.md](docs/roadmap.md) |
| Beta baseline / changelog | [docs/versions/v0.10.1-beta.1.md](docs/versions/v0.10.1-beta.1.md) |

## Limitations

- Obsidian Tasks compatibility is partial and does not reproduce the complete plugin syntax;
- external-file behavior depends on SAF and the selected document provider;
- reminder timing depends on Android permissions and background restrictions;
- the package identifier still contains the previous project name;
- the README does not identify a ready public build or a confirmed distribution channel;
- third-party Android libraries and some standard UI icons retain their own licenses; they are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## License

NotifyMark source code and project-specific resources are distributed under the **Apache License 2.0**. The full license text is available in [LICENSE](LICENSE).

Release APKs may be used, copied, modified, and redistributed under Apache License 2.0 as it applies to NotifyMark material. APKs also contain third-party components that remain under their own licenses; their versions, scope, and license information are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

The NotifyMark license does not relicense third-party components, names, or trademarks.
