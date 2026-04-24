# Architecture

## Project purpose

VaultTick is an Android app that reads tasks and reminders from user-selected markdown files and
folders, keeps them editable as markdown, and delivers local Android notifications at the right
time. The same task data powers the main list, calendar views, editor flows, bulk actions and
notification recovery.

Primary user scenarios:
- select one file, several files or a folder as the source of truth
- parse markdown tasks and reminder metadata
- view tasks in list or calendar form
- edit tasks and subtasks, then write changes back into markdown
- schedule, repeat and recover local reminders

## Main subsystems

### Tasks
- Shared model: [`ObsidianTask`](../app/src/main/java/com/regstar/obsidiannotification/core/model/ObsidianTask.java)
- Status and priority enums live in `core.model`
- The model is reused by list, calendar, reminders and editor

### Markdown parser / writer
- Parser: [`TaskParser`](../app/src/main/java/com/regstar/obsidiannotification/core/markdown/TaskParser.java)
- Syntax settings: [`TaskFormatSettings`](../app/src/main/java/com/regstar/obsidiannotification/core/markdown/TaskFormatSettings.java)
- Write-back and block replacement: [`NoteStore`](../app/src/main/java/com/regstar/obsidiannotification/core/storage/NoteStore.java)

### Sources / storage
- Persisted source selection and markdown IO: `core.storage.NoteStore`
- File scan filters: `core.source.NoteScanSettings`
- Change monitoring and cache recovery: `core.source.NoteChangeMonitor`
- Cached active tasks: `core.storage.TaskCache`

### Notifications
- Alarm scheduling and persisted reminder state: `core.notifications.ReminderScheduler`
- Notification display: `core.notifications.ReminderReceiver`
- Notification action handling: `core.notifications.ReminderActionReceiver`
- Recovery after reboot / package replace / time changes: `core.notifications.ReliabilityReceiver`

### Editors
- Task and subtask editor: `feature.editor.TaskEditActivity`
- Whole-file markdown editor: `feature.editor.MarkdownFileEditActivity`

### Calendar
- Calendar UI is part of `feature.main.MainActivity`
- It uses the same parsed `ObsidianTask` list and filters as the main task list
- Calendar modes aggregate the same tasks by day, week, month and year

### Settings / onboarding / about
- Settings: `feature.settings.SettingsActivity`
- Source management: `feature.sources.SourceManagementActivity`
- Onboarding: `feature.onboarding.OnboardingActivity`
- About screen: `feature.about.AboutActivity`

### Statistics / widgets / tiles
- There is no dedicated `statistics`, `widgets` or `tiles` package yet.
- Future integrations should aggregate the same `ObsidianTask` data rather than introducing a
  second storage or event model.

### Debug / support
- Debug reminder entry points: `core.debug.DebugReminderActions`
- Error log persistence: `core.storage.ErrorLog`

## Package structure

The project keeps the existing application id `com.regstar.obsidiannotification` for stability, but
the code is now organized under feature and core packages.

### `core.model`
- Shared task and reminder domain objects
- No UI code

### `core.markdown`
- Parsing and markdown syntax configuration
- Parse errors and parse results

### `core.storage`
- Source persistence
- Reading documents
- Writing markdown updates
- Task snapshots and undo snapshots

### `core.source`
- Source scanning settings
- Change monitoring
- Sync receiver

### `core.notifications`
- Alarm scheduling
- Reminder receivers
- Recovery logic

### `core.preferences`
- User, theme, onboarding and action preferences

### `core.debug`
- Debug-only helper flows that reuse the same production pipeline

### `feature.main`
- Main task list, calendar, drawer and bulk-action UI

### `feature.editor`
- Task/subtask editor and whole-file markdown editor

### `feature.settings`
- Settings screen and section wiring

### `feature.sources`
- Source selection and source inventory UI

### `feature.onboarding`
- First-run flow

### `feature.about`
- About screen

## Key classes

### Core
- [`ObsidianTask`](../app/src/main/java/com/regstar/obsidiannotification/core/model/ObsidianTask.java)
  - canonical task model
  - also carries subtask hierarchy, snooze metadata and overdue grace
- [`TaskParser`](../app/src/main/java/com/regstar/obsidiannotification/core/markdown/TaskParser.java)
  - parses one markdown document into tasks and parse errors
  - links nested checklist items as subtasks
- [`NoteStore`](../app/src/main/java/com/regstar/obsidiannotification/core/storage/NoteStore.java)
  - reads all configured sources
  - resolves task-to-document mapping
  - performs markdown mutations and bulk edits
- [`NoteChangeMonitor`](../app/src/main/java/com/regstar/obsidiannotification/core/source/NoteChangeMonitor.java)
  - refreshes task cache
  - protects against partial sync reads
  - reschedules reminders after source changes
- [`ReminderScheduler`](../app/src/main/java/com/regstar/obsidiannotification/core/notifications/ReminderScheduler.java)
  - translates active tasks into Android alarms
  - persists lightweight scheduled state

### Feature
- [`MainActivity`](../app/src/main/java/com/regstar/obsidiannotification/feature/main/MainActivity.java)
  - list UI, calendar UI, drawer, filters, bulk actions and task interactions
- [`TaskEditActivity`](../app/src/main/java/com/regstar/obsidiannotification/feature/editor/TaskEditActivity.java)
  - task editor sheet, subtask editor, preview and validation
- [`SettingsActivity`](../app/src/main/java/com/regstar/obsidiannotification/feature/settings/SettingsActivity.java)
  - user-configurable behavior and syntax settings

## Data flow

### Read flow
1. User selects one or more sources.
2. `NoteStore` reads markdown documents from those sources.
3. `TaskParser` parses each document into `TaskParseResult`.
4. `TaskParseResult.merge(...)` combines document results into one snapshot.
5. `MainActivity`, calendar views and debug flows render from the merged `ObsidianTask` list.
6. `NoteChangeMonitor` stores active tasks in `TaskCache` and asks `ReminderScheduler` to update
   alarms.

### Write flow
1. User edits a task, subtask or markdown file.
2. Editor builds markdown text or raw block replacement.
3. `NoteStore` locates the target document and writes the updated markdown back.
4. UI reloads via `NoteStore.readTaskSnapshot(...)`.
5. `NoteChangeMonitor` or explicit rescheduling updates alarms from the new active set.

### Notification flow
1. `ReminderScheduler` computes the next `ScheduledReminder` per active task.
2. Android alarm fires into `ReminderReceiver`.
3. User actions from the notification go through `ReminderActionReceiver`.
4. Action handler updates markdown via `NoteStore`, then updates alarms via `ReminderScheduler`.

### Calendar / statistics direction
- Calendar uses the same filtered `ObsidianTask` list as the main list.
- Year / month / week views aggregate those tasks by date, not by a separate model.
- A future statistics screen should keep the same rule: aggregate from `ObsidianTask`, do not
  create a parallel task/event store.

## Architectural principles

- One markdown engine: parsing and serialization must stay centralized around `TaskParser` and
  `NoteStore`.
- One task model: `ObsidianTask` is the shared source for list, calendar, notifications and editor.
- Source abstraction first: file, multiple files and folder modes should continue to go through the
  same `NoteStore` API.
- Status rules are shared: completed and skipped are explicit, overdue is derived.
- Subtasks are real tasks with parent metadata, not a different entity type.

## Fragile areas

These areas are the most regression-prone:
- markdown write-back in `NoteStore`
- task block capture / restore used by undo flows
- notification rescheduling after edits, bulk actions and recovery
- partial-read protection in `NoteChangeMonitor`
- nested subtask parsing and serialization
- any change that introduces a second parallel task model for calendar or editor

## Future development

The current structure leaves room for:
- extracting calendar aggregation into dedicated classes under `feature.calendar`
- extracting editor state holders from `TaskEditActivity`
- adding widgets, tiles or statistics as separate feature packages with read-only view models
  derived from `ObsidianTask`

When adding new features:
- prefer reusing `ObsidianTask`
- avoid bypassing `NoteStore` for markdown writes
- avoid creating separate task/event DTOs unless they are read-only view models
