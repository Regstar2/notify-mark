# Notification System

## Main components

Reminder handling is split across a small set of Android components:

- `ReminderScheduler`
  - computes desired alarms from active tasks
- `ReminderReceiver`
  - receives alarm broadcasts and posts notifications
- `ReminderActionReceiver`
  - handles done, snooze, skip, and open-source actions
- `ReliabilityReceiver`
  - rebuilds state after reboot, app update, timezone, date, or system time changes
- `NoteChangeMonitor`
  - re-reads markdown on a timer and resynchronizes reminders
- `NoteSyncReceiver`
  - executes the scheduled background markdown sync

## Scheduling flow

1. Tasks are parsed from markdown.
2. Active tasks are passed to `ReminderScheduler`.
3. The scheduler decides which future reminders should exist.
4. Android alarms are scheduled per task key / occurrence identity.
5. `ReminderReceiver` publishes the visible notification when an alarm fires.

## Task identity and notification identity

Scheduling uses the parsed task identity (`taskKey`) and current occurrence information.

For nag reminders (`@repeatUntilDone(...)`), one active occurrence keeps one stable notification identity so the notification shade does not fill up with duplicates for the same unresolved task.

## Repeat behavior

`@repeat(...)`
- advances the task series to the next due occurrence
- is handled through task/repeat logic and rescheduling

`@repeatUntilDone(...)`
- schedules follow-up nag alarms for the current unresolved occurrence
- stops when the task is completed or skipped

## Notification actions

The current notification action receiver supports:
- mark done
- snooze
- skip
- open the underlying note or app

Those actions update markdown through `NoteStore` and then trigger reminder resync logic.

## Permissions and Android constraints

Manifest permissions currently used:
- `POST_NOTIFICATIONS`
- `VIBRATE`
- `SCHEDULE_EXACT_ALARM`
- `RECEIVE_BOOT_COMPLETED`

Platform constraints:
- notification permission is required on modern Android versions
- exact alarm access affects reminder accuracy
- reboot, package replacement, timezone change, time change, and date change all require reminder recovery

## Reliability strategy

The app does not blindly trust one read of an external markdown file.

`NoteChangeMonitor` protects against:
- partially synced files
- temporarily empty reads
- transient external source problems

If a sync looks suspicious, cached active tasks can be used to avoid wiping valid reminders too aggressively.
