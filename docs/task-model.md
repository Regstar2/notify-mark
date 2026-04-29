# Task Model

## Core model

The main task object is `ObsidianTask`.

It represents one parsed task or reminder line together with the metadata needed to:
- render it in UI
- schedule reminders
- write changes back to markdown
- relate it to parent tasks and repeat history

## Main fields in `ObsidianTask`

Identity and source:
- `taskKey`
- `seriesId`
- `sourceName`
- `lineNumber`
- `rawLine`

Content:
- `title`
- `tags`
- `priority`
- `group`

Reminder data:
- `reminderAt`
- `repeatInterval`
- `repeatMode`
- `repeatRule`
- `snoozeDuration`
- explicit and resolved grace values
- explicit and resolved repeat-until-done values

Status data:
- `completed`
- `skipped`

Hierarchy:
- `parentTaskKey`
- `parentLineNumber`
- `indentLevel`
- `subtasks`

## Supported task shapes

The parser currently supports:
- checkbox tasks such as `- [ ] ...`
- completed checkbox tasks such as `- [x] ...`
- plain reminder-like lines without a checkbox, if they contain reminder syntax
- nested checklist items used as subtasks

## Status model

Visible task state is derived by `TaskStatus`:
- `WAITING`
- `OVERDUE`
- `SKIPPED`
- `COMPLETED`

`OVERDUE` is calculated from `reminderAt + grace`.

## Repeat data

Two repeat concepts coexist intentionally:

- `repeatRule` / `@repeat(...)`
  - controls when the next head occurrence should appear
- resolved repeat-until-done interval / `@repeatUntilDone(...)`
  - controls nag reminders while the current occurrence is still unresolved

## Series and occurrences

Repeat tasks use a series-based model:
- one markdown line represents the current head occurrence
- past occurrences move into local occurrence history
- future occurrences are computed from the repeat rule

This keeps markdown editable while still preserving historical occurrence data for follow-up reporting and reconciliation.

## Relation to markdown

Each parsed task keeps the original raw line and source position so NotifyMark can:
- mark a task done
- mark it skipped
- increment snooze counters
- rewrite repeat series to the next due occurrence

without switching to a second task-storage format.
