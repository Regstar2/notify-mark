# Project Structure

This file documents the package layout that exists after the structural refactor. It describes the actual codebase, not an idealized target.

## `app/src/main/java/com/regstar/obsidiannotification/ui`

Responsibility:
- app screens and activity-level state
- manual Android view construction
- activity-to-activity navigation
- rendering tasks, calendars, editor sheets, and source screens

Files:
- `MainActivity.java`
- `TaskEditActivity.java`
- `MarkdownFileEditActivity.java`
- `SettingsActivity.java`
- `SourceManagementActivity.java`
- `OnboardingActivity.java`

Add here:
- new activity screens
- activity-only UI helpers tied to one screen

Do not add here:
- markdown parsing
- reminder scheduling rules
- SAF metadata resolution
- storage persistence details unless they are purely UI-facing glue

Allowed dependencies:
- `core.tasks`
- `core.source`
- `core.reminders`
- `core.stats`
- `prefs`
- `support`

## `app/src/main/java/com/regstar/obsidiannotification/core/tasks`

Responsibility:
- task model
- parse results and parse errors
- markdown parser and writer
- repeat rules and repeat-series helpers
- task grouping and task cache

Files include:
- `ObsidianTask.java`
- `TaskParser.java`
- `TaskMarkdownWriter.java`
- `RepeatRule.java`
- `RepeatSeriesManager.java`
- `OccurrenceHistoryStore.java`
- `TaskGrouping.java`
- `TaskCache.java`

Add here:
- task-domain logic that is independent from a specific Android screen

Do not add here:
- activity UI
- SAF picker interactions
- notification display code

Allowed dependencies:
- Java time/util classes
- `prefs` where parsing or default resolution depends on settings
- limited `core.source` references where task code needs source metadata contracts already used by the app

## `app/src/main/java/com/regstar/obsidiannotification/core/source`

Responsibility:
- storage mode abstraction
- internal markdown storage
- external SAF-backed markdown storage
- source persistence and markdown document IO
- source display name and access status resolution
- background source sync monitoring

Files include:
- `TaskSource.java`
- `TaskSourceManager.java`
- `InternalMarkdownSource.java`
- `ExternalMarkdownSource.java`
- `NoteStore.java`
- `SourceDisplayNameResolver.java`
- `NoteChangeMonitor.java`
- `NoteSyncReceiver.java`

Add here:
- behavior that answers "where do markdown documents come from?" or "how do we read/write them?"

Do not add here:
- task row rendering
- notification UI
- parser-specific title formatting for task list display

Allowed dependencies:
- Android content/URI APIs
- `core.tasks`
- `prefs`
- `support`

## `app/src/main/java/com/regstar/obsidiannotification/core/reminders`

Responsibility:
- alarm scheduling
- notification publishing
- notification actions
- reboot/time-change recovery

Files include:
- `ReminderScheduler.java`
- `ReminderReceiver.java`
- `ReminderActionReceiver.java`
- `ReminderSchedule.java`
- `ScheduledReminder.java`
- `ReliabilityReceiver.java`

Add here:
- alarm and notification logic

Do not add here:
- markdown parsing
- source display formatting
- editor UI

Allowed dependencies:
- `core.tasks`
- `core.source`
- `prefs`
- `support`
- Android alarm/notification APIs

## `app/src/main/java/com/regstar/obsidiannotification/core/stats`

Responsibility:
- statistics aggregation
- separation between snapshot metrics and historical metrics
- timeline, breakdown, summary, and insight-ready models for the statistics screen

Files include:
- `StatisticsRepository.java`
- `StatisticsReport.java`
- `StatisticsSummary.java`
- `StatisticsTimelineBucket.java`
- `StatisticsBreakdownRow.java`
- `StatisticsFilters.java`
- `StatisticsPeriod.java`
- `StatisticsInsight.java`

Add here:
- analytics aggregation logic that combines current tasks with local history
- stable data models consumed by statistics UI

Do not add here:
- markdown parsing
- reminder scheduling
- Android activity rendering
- localized UI strings

Allowed dependencies:
- `core.tasks`
- `core.source` only through existing task/history access points
- Java time/util classes

## `app/src/main/java/com/regstar/obsidiannotification/prefs`

Responsibility:
- shared preferences access for app settings

Files:
- `ActionPreferences.java`
- `EditPreferences.java`
- `OnboardingPreferences.java`
- `ThemePreferences.java`
- `UserPreferences.java`

Add here:
- new preference accessors with stable defaults

Do not add here:
- screen layout logic
- markdown parsing logic

## `app/src/main/java/com/regstar/obsidiannotification/debug`

Responsibility:
- debug-only or support-only reminder helpers

Files:
- `DebugActionResult.java`
- `DebugReminderActions.java`

## `app/src/main/java/com/regstar/obsidiannotification/support`

Responsibility:
- small shared support utilities

Files:
- `ErrorLog.java`

## Tests

Unit tests mirror the same split under:
- `app/src/test/java/com/regstar/obsidiannotification/core/tasks`
- `app/src/test/java/com/regstar/obsidiannotification/core/source`
- `app/src/test/java/com/regstar/obsidiannotification/core/reminders`
- `app/src/test/java/com/regstar/obsidiannotification/core/stats`

When adding tests, prefer placing them next to the domain area they verify instead of creating a flat test package again.
