# Markdown Format

This document describes the markdown that the current parser accepts. It is based on `TaskParser`, not on a speculative syntax.

## Checkbox tasks

Active task:

```markdown
- [ ] Buy medicine @due(2026-04-20 19:00)
```

Completed task:

```markdown
- [x] Buy medicine @due(2026-04-20 19:00)
```

Skipped task marker:

```markdown
- [ ] Buy medicine @due(2026-04-20 19:00) @skipped
```

## Plain reminder-like lines

A line without a checkbox can still become a task if it contains reminder syntax:

```markdown
Call clinic @due(2026-04-20 19:00)
```

Plain lines without reminder syntax are ignored by the task parser.

## Supported due forms

Examples currently accepted by the parser:

```markdown
@due(2026-04-20 19:00)
@due(2026-04-20T19:00)
@due(20.04.2026 19:00)
@due(2026-04-20)
@due(20.04.2026)
@due(19:00)
```

The parser also understands some shorthand reminder forms embedded directly in the text.

## Repeat schedule

Simple interval:

```markdown
@repeat(15m)
@repeat(2h)
@repeat(1d)
@repeat(2w)
@repeat(1mo)
```

Weekly selector:

```markdown
@repeat(1w) @days(mon,wed,fri)
@repeat(1w) @days(weekdays)
```

Monthly selector:

```markdown
@repeat(1mo) @monthday(15)
@repeat(1mo) @monthday(last)
```

Compact alias:

```markdown
@r(1d)
```

## Repeat until done

```markdown
@repeatUntilDone(15m)
@rud(15m)
```

This is separate from `@repeat(...)`. It only controls nag reminders for the current unresolved occurrence.

## Other supported metadata

```markdown
@grace(30m)
@g(30m)
@snooze(30m)
@group(home)
@priority(high)
@tag(personal)
@id(123e4567-e89b-12d3-a456-426614174000)
```

Tags are also read from regular markdown tags:

```markdown
#personal
```

## Obsidian Tasks emoji (partial compatibility)

When **Settings → Format → Совместимость строк задач** is not *Только NotifyMark*, the parser also reads Obsidian Tasks-style markers on the same checkbox line (ISO dates `YYYY-MM-DD` only in v0.10.0):

```markdown
- [ ] Buy milk 📅 2026-05-13
- [ ] Meeting ⏰ 2026-05-13 14:30 📅 2026-05-13
- [ ] Weekly review 🔁 every week 📅 2026-05-18
```

Native `@due(...)`, `@repeat(...)`, etc. still work and take priority when both native and emoji fields conflict on reminder time. Obsidian `tasks` fenced query blocks are not supported. See [v0.10.0 release notes](versions/v0.10.0-obsidian-tasks-plugin-compatibility.md).

## Auto-skip overdue tasks (app setting)

Auto-skip is **not** a markdown token on the task line. Turn it on under **Settings → Notifications** (notification actions) or in the **task editor**, and set the delay in minutes.

When enabled, a due occurrence can be marked skipped after: **reminder time + overdue grace** (from `@grace(...)` / `@g(...)` on the line, otherwise the app default grace minutes) **+ the configured auto-skip delay**. Snoozing a notification does **not** move that auto-skip deadline.

## Subtasks

Nested markdown checklist items become subtasks:

```markdown
- [ ] Prepare report @due(2026-04-22 10:00)
  - [ ] Gather data @due(2026-04-22 08:30)
  - [ ] Check numbers @repeatUntilDone(15m)
```

## Parser limits worth knowing

- Fenced code blocks are ignored by the task parser.
- Not every plain markdown bullet becomes a task; it must still contain reminder syntax.
- Validation for weekly and monthly repeat selectors exists in parser logic, so invalid combinations may produce parse warnings instead of meaningful tasks.
