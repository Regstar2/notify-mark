# Roadmap

## Near-term work

0. **v0.10.1-beta.1 release baseline (current):** Android metadata is synchronized to `versionName 0.10.1-beta.1` / `versionCode 26`; the planned Git tag is `v0.10.1-beta.1`. The baseline combines the shipped v0.10.0 compatibility work and v0.10.1 UI fixes. Next: validate the beta build and publish the prerelease (see `docs/versions/v0.10.1-beta.1.md`).
1. **v0.10.1 UI fixes (shipped in codebase):** compact repeat rows on task cards and improved long-title editing/display, including safer markdown preview and preservation checks (see `docs/versions/v0.10.1-repeat-card-display-compacting.md` and `docs/versions/v0.10.1-long-task-title-editor-ui.md`).
2. **v0.10.0 Obsidian Tasks compatibility (shipped in codebase):** partial emoji metadata parsing/write-back, compatibility modes, recurrence mapping with documented limitations, and reminder resolution rules (see `docs/versions/v0.10.0-obsidian-tasks-plugin-compatibility.md`).
3. **v0.9.4 auto-skip (shipped in codebase):** global optional auto-skip after `due + grace + delay`; `NoteStore.markTaskSkipped`; `AutoSkipScheduler` / `AutoSkipReceiver`; settings in notifications section (see `docs/versions/v0.9.4-auto-skip.md`).

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
