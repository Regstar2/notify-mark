# Technical Debt

### Historical package namespace

**Where:** `com.regstar.obsidiannotification` across the whole project  
**Problem:** The product is now NotifyMark, but the Android package namespace and related technical identifiers still use the historical name.  
**Why not fixed now:** Changing namespace or application identity is riskier than user-facing branding and was explicitly out of scope for this step.  
**What to do later:** Plan a separate namespace migration with manifest, package, deep-link, persistence, and signing impact review.

### Large activity classes

**Where:** `app/src/main/java/com/regstar/obsidiannotification/ui/MainActivity.java`, `app/src/main/java/com/regstar/obsidiannotification/ui/TaskEditActivity.java`, `app/src/main/java/com/regstar/obsidiannotification/ui/SettingsActivity.java`  
**Problem:** Activity classes still own a lot of rendering and orchestration logic directly.  
**Why not fixed now:** The goal of this step was structure, branding, and documentation without behavior changes.  
**What to do later:** Extract screen-specific helpers or controllers in small safe steps, backed by more tests.

### Broad wildcard imports after package split

**Where:** many Java files under `ui`, `core.source`, `core.tasks`, and `core.reminders`  
**Problem:** Wildcard imports helped keep the package refactor low-risk, but they hide exact dependencies and make future moves noisier.  
**Why not fixed now:** Narrowing imports everywhere is mechanical cleanup, but it would add noise to an already large structural change.  
**What to do later:** Replace wildcard imports opportunistically when touching files for real behavior changes.

### Inline user-facing strings and legacy encoding risk

**Where:** multiple activity and core Java files  
**Problem:** Many strings are still inline in Java rather than centralized in resources, and the codebase has a history of encoding issues in Cyrillic literals.  
**Why not fixed now:** Full string extraction is a bigger UI/content pass and could accidentally change text behavior.  
**What to do later:** Gradually extract stable UI strings into resources and normalize file encoding carefully.

### Storage and UI still meet directly in some screens

**Where:** `MainActivity`, `SettingsActivity`, `SourceManagementActivity`, `TaskEditActivity`  
**Problem:** UI activities still call storage and reminder layers directly for many flows.  
**Why not fixed now:** Introducing new orchestration layers would be behavior-adjacent and outside this refactor.  
**What to do later:** Extract narrow controller or use-case helpers around edit, source switching, and bulk actions.

### Test coverage is stronger in core than in UI

**Where:** `app/src/test/java/com/regstar/obsidiannotification/core/*`  
**Problem:** Parser, grouping, source naming, and reminder scheduling have unit tests, but activity-level UI flows rely mostly on manual verification.  
**Why not fixed now:** Adding UI automation would expand scope well beyond the requested structural refactor.  
**What to do later:** Add focused instrumentation or screenshot tests for source screens, task editor, and main task interactions.
