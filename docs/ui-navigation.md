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

There is no pull-to-refresh on tasks or calendar; reload is via the toolbar control. Task cards with subtasks: swipe down on the parent row to expand, swipe up on the parent row or on any subtask row to collapse the list (subtasks have no expand/collapse of their own).

In multi-select mode, extending the selection by dragging runs after scroll handling with higher vertical thresholds and is disabled for the rest of that gesture once the nested list actually scrolls, so scrolling stays the default. Task rows also stop requesting parent touch disallow on gesture start while selecting, so `NestedScrollView` can intercept the same strokes for scrolling.

While dragging the selection range near the visible top or bottom of the scrolled content, the list auto-scrolls in that direction so you can extend the selection without lifting your finger.

A short horizontal drag or flick that **starts on the reminder/time strip** on a card (clock line on tasks, compact time pill on calendar day rows) switches main sections with softer thresholds than a global edge swipe — flings from that zone use lower velocity and distance requirements; slow drags that move far enough horizontally also count.

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
