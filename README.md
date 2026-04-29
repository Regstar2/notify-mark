# NotifyMark

NotifyMark is an Android app for local reminders backed by markdown task files.

It can read tasks from:
- built-in app-owned markdown storage
- externally selected markdown files
- externally selected markdown folders through Android SAF

The same task engine powers both modes. NotifyMark parses markdown tasks, shows them in task and calendar screens, schedules local Android reminders, and writes status changes back to markdown.

## What it supports

- checkbox tasks and reminder-like markdown lines
- `@due(...)`, `@repeat(...)`, `@repeatUntilDone(...)`
- `@grace(...)`, `@snooze(...)`, `@group(...)`, `@priority(...)`
- `#tags` and `@tag(...)`
- subtasks from nested markdown checklists
- local reminder actions for done, snooze, skip, and open-source flows
- built-in markdown storage for quick start
- external markdown files and folders for Obsidian vaults, Syncthing folders, and regular markdown collections

## Internal docs

- [Architecture](docs/architecture.md)
- [Project structure](docs/project-structure.md)
- [Source system](docs/source-system.md)
- [Task model](docs/task-model.md)
- [Markdown format](docs/markdown-format.md)
- [Notification system](docs/notification-system.md)
- [UI navigation](docs/ui-navigation.md)
- [Technical debt](docs/technical-debt.md)
- [Roadmap](docs/roadmap.md)

## Build

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- The package namespace remains `com.regstar.obsidiannotification` for now to avoid risky Android identity changes during a structural refactor.
- The app still works with Obsidian markdown files, but Obsidian is now a compatibility target rather than the product name.
