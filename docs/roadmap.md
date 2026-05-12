# Roadmap

## Near-term work

0. **v0.9.4 auto-skip (shipped in codebase):** global optional auto-skip after `due + grace + delay`; `NoteStore.markTaskSkipped`; `AutoSkipScheduler` / `AutoSkipReceiver`; settings in notifications section (see `docs/versions/v0.9.4-auto-skip.md`). Next: **v0.10.0 — Obsidian Tasks Plugin compatibility**.
1. **v0.9.3 editor UX (shipped in codebase):** group autocomplete from cached tasks; softer input hints (`text_tertiary`); field-level validation in `TaskEditActivity` (see `docs/versions/v0.9.3-editor-ux-polish.md`).
2. **v0.9.2 notification actions (shipped in codebase):** dedicated small icon + action icons; **Время** opens `ReminderTimePickerActivity` (time-only or date+time, DayNight dialog); compact shade labels (`+Nм`, **Время**, ✓/✗ for done/skip); `NotificationCompat` + rasterized bitmap `smallIcon` for Bluetooth watch bridges (Realme, etc.); `scheduleSnoozeUntil` (see `docs/versions/v0.9.2-notification-actions-upgrade.md`).
3. **v0.9.1 repeat history (shipped in codebase):** calendar merges occurrence history; bulk skip/done aligns with repeat advance. Next: optional history detail UI and richer one-off analytics (see `docs/versions/v0.9.1-repeat-history-fix.md`).

4. Finish structural cleanup after the package refactor
   - narrow wildcard imports
   - extract small helpers from the largest activities

5. Strengthen tests around shared core logic
   - parser edge cases
   - markdown writer round-trips
   - repeat-series transitions
   - source display name edge cases

6. Improve user-facing documentation
   - expand markdown examples
   - document onboarding and storage-mode flows with screenshots or a short guide

7. Deepen the statistics feature
   - add broader historical coverage for one-off task events
   - introduce drill-down views and richer filters without duplicating task state

8. Prepare README and docs for broader public use
   - installation notes
   - limitations by Android version
   - SAF and Syncthing troubleshooting
