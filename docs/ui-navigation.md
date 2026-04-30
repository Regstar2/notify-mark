# UI Navigation

NotifyMark uses activity-based navigation rather than a dedicated route graph.

## Screens

### `MainActivity`

Primary app entry point.

Owns:
- task list screen
- calendar screen
- statistics screen
- drawer navigation
- filter sheet
- source summary card

Can open:
- `SettingsActivity`
- `SourceManagementActivity`
- `OnboardingActivity`
- `TaskEditActivity`
- `MarkdownFileEditActivity`

## `SettingsActivity`

Settings hub with section-based navigation inside one activity.

Current sections:
- basic
- sources
- notifications
- format
- scan
- advanced

From the sources section it opens `SourceManagementActivity`.

## `SourceManagementActivity`

Screen for:
- choosing the active storage mode
- replacing external sources
- adding external sources
- creating a new external markdown file
- clearing saved external connections

This screen depends on source/storage state through `TaskSourceManager`, `NoteStore`, and `SourceDisplayNameResolver`.

## `OnboardingActivity`

First-run setup flow for:
- built-in storage
- external markdown files

It can open `SourceManagementActivity` when the user chooses the external path.

## `TaskEditActivity`

Sheet-style editor for:
- creating a task
- editing a task
- creating a subtask
- editing a subtask

Depends on:
- `NoteStore`
- `TaskMarkdownWriter`
- task defaults/preferences

## Statistics section

The statistics surface currently lives inside `MainActivity` as a third top-level section next to tasks and calendar.

It uses:
- `StatisticsRepository`
- `OccurrenceHistoryStore`
- current parsed tasks already loaded for the main UI

The section provides:
- period chips
- filter sheet for group, tag, and source
- summary cards
- historical timeline
- breakdown blocks by group, file, and tag
- insights and subtask summary

## `MarkdownFileEditActivity`

Full-file markdown editor used when the user wants to edit the original document directly.

## State holders

There are no dedicated ViewModel classes at the moment.

State is held directly in activities, especially:
- `MainActivity`
- `TaskEditActivity`
- `SettingsActivity`
- `SourceManagementActivity`

That is workable today, but it is also one of the main maintenance pressures documented in technical debt.
