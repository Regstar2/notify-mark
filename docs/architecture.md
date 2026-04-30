# NotifyMark Architecture

## What NotifyMark is

NotifyMark is an Android app that reads tasks and reminder-like lines from markdown files, shows them in a task list and calendar, and schedules local Android notifications for them.

The app supports two storage modes:
- built-in app-owned markdown storage
- external markdown files and folders selected through Android SAF

The source of truth stays in markdown. NotifyMark does not keep a second authoritative task database.

## Main user scenarios

- Start quickly with built-in markdown storage and create tasks inside the app.
- Connect existing external markdown files or folders.
- Use the same task UI for task lists, calendar views, editing, and notifications.
- Review task statistics across current state, local occurrence history, groups, tags, and files.
- Complete, skip, snooze, or edit tasks while keeping markdown in sync.

## Main subsystems

- `ui`: Android activities and view-building code.
- `core.tasks`: task model, parser, writer, grouping, repeat rules, repeat-series support, cache.
- `core.source`: internal/external source abstraction, SAF metadata, markdown document IO, sync monitor.
- `core.reminders`: alarm scheduling, notification receivers, recovery from system events.
- `core.stats`: aggregation of snapshot task state and local occurrence history into statistics-ready reports.
- `prefs`: user-editable settings that influence parsing, display, and reminders.
- `debug` and `support`: debug helpers and shared support utilities such as error logging.

## Data flow

1. A storage mode is resolved by `TaskSourceManager`.
2. The active `TaskSource` returns markdown documents through `NoteStore`.
3. `TaskParser` converts markdown into `ObsidianTask` objects and parse warnings.
4. UI activities render those tasks and collect user actions.
5. `ReminderScheduler` derives alarms from active tasks.
6. `OccurrenceHistoryStore` records repeat-occurrence outcomes that later feed historical analytics.
7. `StatisticsRepository` combines current parsed tasks with local occurrence history for the statistics screen.
8. Notification actions and editor flows call back into `NoteStore`.
9. `TaskMarkdownWriter` rewrites task lines while keeping markdown as the source of truth.
10. The app re-parses documents after meaningful changes to resync UI, reminders, and statistics.

## Source of truth

Markdown documents are the source of truth for task content and current head occurrences.

Local support data exists for:
- cached active tasks
- scheduled reminder state
- preferences
- repeat occurrence history

That support data helps with resilience and statistics preparation, but it does not replace markdown as the canonical current task document.

## Boundaries that matter

- UI should not parse markdown by itself.
- Reminder code should not mutate markdown directly.
- Source/storage code should not format user-facing source labels in ad hoc ways; `SourceDisplayNameResolver` owns that responsibility.
- Parser and writer should stay shared across built-in and external storage modes.
- Statistics code should aggregate task and history data, but user-facing phrasing and localization should stay in UI/resources.

## Important current constraints

- The package namespace still uses the historical `com.regstar.obsidiannotification` identifier.
- Navigation is activity-based rather than route-based.
- `MainActivity` and `TaskEditActivity` remain large and central, even after the package split.
- Historical statistics are currently strongest for data already captured in local occurrence history; one-off tasks still do not emit a full generic event stream.
