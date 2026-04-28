# NotifyMark v1.0.0 test plan

Goal: verify that NotifyMark is ready for a limited first release.

## Android matrix

- Android 8 / API 26: baseline compatibility, alarms, SAF.
- Android 10 / API 29: SAF folders, Syncthing folder flow.
- Android 12 / API 31: exact alarm permission behavior.
- Android 13 / API 33: `POST_NOTIFICATIONS`.
- Android 14 / API 34: background limits and exact alarms.
- Android 15 / API 35: target SDK runtime behavior.

## Parser

- `@due(2026-04-20 19:00)`
- `@due(2026-04-20T19:00)`
- `@due(20.04.2026 19:00)`
- `@due(2026-04-20)`
- `@due(19:00)`
- `@repeat(15m)`
- `@repeatUntilDone(15m)`
- plain reminder line without checkbox
- completed task `- [x]`
- task inside fenced code block
- custom keywords from settings

## Notifications

- one-shot future reminder
- past one-shot reminder should not schedule
- repeat should schedule next future trigger
- repeat-until-done should continue until task is completed
- notification tap opens app
- notification action Done updates markdown
- notification action Snooze schedules new alarm
- notification sound and vibration repeat on each nag

## Syncthing

- edit task on desktop, wait for sync, app updates list
- mark task done on desktop, wait for sync, app cancels repeats
- partially synced empty or temporary file does not clear active alarms
- conflict file does not corrupt selected source
- folder scan excludes `.obsidian`, archive, template, and temp files

## Editing

- add task through UI
- edit existing task through UI
- delete task through UI
- open full markdown file at task line
- save full markdown file
- conflict dialog appears when file changed after editor opened

## Reliability

- reboot phone after scheduling
- update app after scheduling
- change timezone
- change system time
- revoke notification permission
- revoke exact alarm permission
- revoke source permission and select source again
