# Repeat Series Architecture

## Core rule

One markdown line controls only the current head occurrence of a repeating task.

- current markdown line = current head occurrence
- past occurrences = local history only
- future occurrences = computed from repeat rule

The markdown line must never rewind back to an older occurrence after the next head was already established.

## Ownership model

### Head occurrence

The active markdown-backed occurrence.

- `@due(...)` on the line points to this occurrence
- edits in markdown or in the UI act on this occurrence
- scheduler plans notifications for this occurrence only

### History occurrences

Past occurrences live in `OccurrenceHistoryStore`.

Each record stores:

- `seriesId`
- `occurrenceDueAt`
- `occurrenceStatus`
- `resolvedAt`
- task/source/group/tag/priority snapshots

These records are the future basis for statistics and reconciliation.

### Upcoming occurrences

Future occurrences are computed from `RepeatRule` and are not stored as separate mutable markdown rows.

## Stable identity

Repeat series use a stable markdown id:

- `@id(...)`

Old tasks without an explicit id are upgraded on first series advance:

- a new UUID is generated
- the same id is written to markdown
- the same id is written to occurrence history

## Repeat semantics

`@repeat(...)` and `@repeatUntilDone(...)` are different mechanisms.

### `@repeat(...)`

Controls when the next head occurrence should appear.

Examples:

- `@repeat(15m)`
- `@repeat(2w)`
- `@repeat(1w) @days(mon,wed,fri)`
- `@repeat(1mo) @monthday(last)`

### `@repeatUntilDone(...)`

Controls nag reminders after the current due time, while the current head occurrence is still unresolved.

It does **not** advance the series.

### `@grace(...)`

Controls when the current unresolved occurrence becomes overdue.

## Series advance

When the user completes or skips a repeating task:

1. current head occurrence is written to local history
2. next due is calculated from the previous due, not from tap time
3. markdown line is rewritten to the new head due
4. transient markers like `[x]`, `@skipped`, `@snoozed(...)` are cleared
5. notifications are rescheduled for the new head

For one-shot tasks the old behavior remains: markdown keeps the final state directly.

## External completion from markdown / Obsidian

External `[x]` / skipped changes for repeating tasks do not advance the series immediately.

Flow:

1. detect completed/skipped repeat task
2. store a pending candidate in `OccurrenceHistoryStore`
3. wait for stabilization window
4. if the file stays in the same completed/skipped state, advance the series
5. if the state flips back before the window ends, cancel the pending candidate

## Defaults and compact syntax

Resolved defaults are applied after parsing:

- `repeatUntilDone` default from `ActionPreferences`
- `grace` default from `ActionPreferences`

Parser keeps explicit values separate from resolved values so writer/UI can decide whether a field should be written back.

Supported compact syntax:

- `@r(...)`
- `@rud(...)`
- `@g(...)`
- `#tag`

Legacy forms remain readable:

- `@repeat(...)`
- `@repeatUntilDone(...)`
- `@grace(...)`
- `@tag(...)`

## Current implementation notes

Foundation introduced in this phase:

- `RepeatRule`
- `TaskDefaultsResolver`
- `TaskMarkdownWriter`
- `OccurrenceHistoryStore`
- `RepeatSeriesManager`

Still intentionally left for follow-up:

- richer repeat editor UI for weekly/monthly selection
- history/statistics screen
- bulk repeat-series advance parity for all multi-select flows
- deeper in-app help/examples for the new syntax
