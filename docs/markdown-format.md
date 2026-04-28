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
