# VaultTick Agent Notes

- Application id and namespace remain `com.regstar.obsidiannotification` for now. Do not rename it casually.
- Keep the split between `core.*` and `feature.*`.
- `ObsidianTask` is the shared task model. Do not introduce a parallel task/event model for calendar, notifications or editor.
- Markdown parsing goes through `core.markdown.TaskParser`.
- Markdown write-back and source access go through `core.storage.NoteStore`.
- Notification scheduling goes through `core.notifications.ReminderScheduler`.
- Be careful with:
  - task block replacement and undo
  - partial-read protection in `core.source.NoteChangeMonitor`
  - status transitions: completed/skipped are persisted, overdue is derived
  - subtask parsing and serialization
- Prefer small, responsibility-focused refactors over large rewrites.
- Add JavaDoc to public contracts with non-obvious behavior. Do not comment obvious code.
- Preserve user-visible Russian copy unless the task explicitly changes it.
- After structural changes, run `.\gradlew.bat assembleDebug` and `.\gradlew.bat testDebugUnitTest`.
