<div align="center">

# NotifyMark

### Markdown tasks that can actually remind you

A local-first Android reminder app backed by readable Markdown files.

[Overview](#overview) · [Task format](#task-format) · [Storage](#storage-options) · [Build](#build) · [Documentation](#documentation)

</div>

---

## Overview

NotifyMark reads tasks from Markdown, presents them as a task list and calendar, schedules Android reminders, and writes state changes back to the source file.

It is designed around a simple rule:

> Your task data should remain useful even without the application.

```markdown
- [ ] Submit the report @due(2026-08-10 18:00) @priority(high) #university
- [ ] Take a walk @repeat(daily) @grace(30m)
- [ ] Pay for hosting @due(2026-08-15) @repeat(monthly) @repeatUntilDone
```

## What it does

| Read | Organize | Remind | Write back |
|---|---|---|---|
| Markdown task files and folders | Lists, calendar, groups, priorities, tags | Exact local Android reminders | Done, snooze, skip, and task state changes |

NotifyMark supports:

- app-owned Markdown storage for a quick start;
- selected external Markdown files;
- selected folders through Android Storage Access Framework;
- nested checklist subtasks;
- local notifications with done, snooze, skip, and open-source actions;
- recurring tasks and repeat-until-done behavior;
- calendar views powered by the same parser and task engine;
- partial compatibility with Obsidian Tasks emoji metadata.

## Task format

### Native NotifyMark metadata

```markdown
- [ ] Prepare presentation
  @due(2026-08-12 14:30)
  @repeat(weekly)
  @grace(20m)
  @snooze(15m)
  @group(University)
  @priority(high)
  @tag(study)
```

Supported metadata includes:

| Metadata | Purpose |
|---|---|
| `@due(...)` | due date and optional time |
| `@repeat(...)` | recurrence rule |
| `@repeatUntilDone(...)` | continue reminding until completion |
| `@grace(...)` | allowed delay before overdue state |
| `@snooze(...)` | default snooze interval |
| `@group(...)` | logical task group |
| `@priority(...)` | task priority |
| `@tag(...)` and `#tags` | searchable labels |

Legacy inline `@YYYY-MM-DD` date and time fragments are also supported where implemented by `TaskParser`.

### Obsidian Tasks compatibility

NotifyMark partially understands the emoji format used by Obsidian Tasks:

```markdown
- [ ] Example task 📅 2026-08-12 ⏰ 14:30 🔁 every week
```

Supported fields include due date, reminder time, a subset of recurrence phrases, completion markers, priorities, IDs, and selected stored metadata.

Compatibility can be configured under:

```text
Settings → Format → Task line compatibility
```

See [v0.10.0 compatibility notes](docs/versions/v0.10.0-obsidian-tasks-plugin-compatibility.md) for the exact limitations.

## Storage options

```text
NotifyMark
├── Built-in storage
│   └── app-owned Markdown files
│
├── External file
│   └── one selected Markdown document
│
└── External folder
    └── Markdown collection through Android SAF
```

The same task engine is used for every source type. External files can belong to an Obsidian vault, a Syncthing folder, or any ordinary Markdown collection available through Android SAF.

> [!NOTE]
> NotifyMark is local-first. The application does not require a proprietary cloud task format.

## Reminder flow

```text
Markdown source
      │
      ▼
Task parser
      │
      ├── task list
      ├── calendar
      └── reminder scheduler
              │
              ▼
       Android notification
              │
       ┌──────┼───────┐
       ▼      ▼       ▼
      Done  Snooze   Skip
       │      │       │
       └──────┴───────┘
              │
              ▼
       Markdown write-back
```

## Build

Requirements:

- JDK 17;
- Android SDK;
- Gradle Wrapper from the repository.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

Debug APK:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Project notes

- The package namespace remains `com.regstar.obsidiannotification` to avoid a risky Android identity change during structural refactoring.
- Obsidian remains a compatibility target, not the product name.
- Parser behavior is the source of truth for supported task syntax.
- External storage access uses Android SAF rather than unrestricted filesystem access.

## Documentation

- [Architecture](docs/architecture.md)
- [Project structure](docs/project-structure.md)
- [Source system](docs/source-system.md)
- [Task model](docs/task-model.md)
- [Markdown format](docs/markdown-format.md)
- [Notification system](docs/notification-system.md)
- [UI navigation](docs/ui-navigation.md)
- [Technical debt](docs/technical-debt.md)
- [Roadmap](docs/roadmap.md)

---

<div align="center">

**Readable files. Local reminders. No lock-in.**

</div>
