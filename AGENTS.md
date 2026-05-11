# AGENTS.md

Project name: **NotifyMark**

## Ground rules

- Do not rewrite the project from scratch.
- Do not change markdown task syntax without an explicit request.
- Do not change notification scheduling behavior without an explicit request.
- Do not change source or storage behavior without an explicit request.
- Do not change SAF permission handling without an explicit request.
- Do not delete real user markdown files when clearing external connections.

## Branding and user-facing text

- The product name is `NotifyMark`.
- `Obsidian` may appear only as a compatibility example, not as the product name.
- Do not show raw `content://` URIs as the main user-facing source name.
- Do not show encoded URI fragments as the main user-facing source name.
- Do not show internal `/data/user/0/...` app paths in normal UI.

## Architecture expectations

- Keep markdown as the source of truth for current task content.
- Keep source/storage logic separate from UI display formatting.
- Use the shared parser and writer instead of inventing screen-specific markdown logic.
- Keep reminder scheduling in `core.reminders`, not in UI classes.
- Prefer small, focused changes over broad rewrites.

## Documentation expectations

- Update docs in `docs/` when architecture or user-visible behavior changes.
- Use JavaDoc for Java files.
- Use KDoc for Kotlin files.
- Record non-trivial deferred problems in `docs/technical-debt.md` instead of silently expanding scope.

## Practical refactoring guidance

- Check the real project structure before proposing new package moves.
- Avoid moving files just for symmetry.
- Preserve behavior first; improve structure second.
- Run at least `gradlew.bat assembleDebug` after substantial refactors.
- Run available tests when touching parser, source, or reminder code.
- When the user is iterating on device and a USB-debuggable phone is expected: after **each** agent change set that affects the app, run `gradlew.bat installDebug` yourself (do not only suggest it). If `adb` reports no device or `unauthorized`, note that once in the reply; otherwise install without asking.
