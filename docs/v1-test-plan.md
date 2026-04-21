# v1.0.0 test plan

Цель: проверить, что ObsidianNotification готов к первому ограниченному релизу.

## Android matrix

- Android 8 / API 26: базовая совместимость, фоновые alarm-ы, SAF.
- Android 10 / API 29: SAF, папки, Syncthing-папка.
- Android 12 / API 31: exact alarm permission behavior.
- Android 13 / API 33: `POST_NOTIFICATIONS`.
- Android 14 / API 34: фоновые ограничения, exact alarms.
- Android 15 / API 35: целевой SDK и runtime-поведение.

## Parser

- `@due(2026-04-20 19:00)`
- `@due(2026-04-20T19:00)`
- `@due(20.04.2026 19:00)`
- `@due(2026-04-20)`
- `@due(19:00)`
- `@repeat(15m)`
- `@repeatUntilDone(15m)`
- задача без чекбокса
- выполненная задача `- [x]`
- task inside fenced code block
- custom keywords from settings

## Notifications

- one-shot future reminder
- past one-shot reminder should not schedule
- repeat should schedule next future trigger
- repeatUntilDone should continue until task is completed
- notification tap opens app
- notification action Done updates markdown
- notification action Snooze schedules new alarm
- notification sound/vibration repeats on each repeat

## Syncthing

- edit task on PC, wait sync, app updates list
- mark task done on PC, wait sync, app cancels repeats
- partially synced empty/temporary file does not clear active alarms
- conflict file does not corrupt selected source
- folder scan excludes `.obsidian`, archive, templates, temp files

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
- remove source permission and select source again
