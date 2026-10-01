# Source System

## Supported storage modes

NotifyMark supports two markdown storage modes.

### Built-in storage

- Backed by app-owned files under the app files directory.
- Implemented by `InternalMarkdownSource`.
- Initialized automatically through `TaskSourceManager`.
- Uses the same markdown parser, writer, task model, UI, and reminder logic as external storage.

Current built-in defaults:
- folder name: `internal_markdown`
- default file: `tasks.md`

### External markdown files

- Backed by SAF-selected files or folders.
- Implemented by `ExternalMarkdownSource`.
- Source persistence is handled by `NoteStore`.
- Uses the same parser, writer, and reminder logic as built-in storage.

## Source abstraction

The source layer is built around:
- `TaskStorageMode`
- `TaskSource`
- `TaskSourceManager`

UI code asks `TaskSourceManager` for the active mode and source. It should not assume that tasks always come from SAF or always come from app-private files.

## External source persistence

External files and folders are saved by `NoteStore` as a list of source entries:
- note source
- folder source

The app stores references to those URIs and reuses persisted SAF permissions after app restarts.

## Persisted URI permissions

Write availability is inferred from Android persisted URI permissions.

Current access states shown in UI:
- writable
- read-only
- lost access

`SourceDisplayNameResolver` and `NoteStore.canWriteUri(...)` are the main places where access state is derived for source UI.

## Display names

Human-readable external source names are resolved through `SourceDisplayNameResolver`.

Resolution order:
1. `DocumentFile.name`
2. `OpenableColumns.DISPLAY_NAME`
3. `DocumentsContract.Document.COLUMN_DISPLAY_NAME`
4. tree-document metadata for folders
5. decoded fallback from document id or URI segment
6. safe fallback labels

Safe fallback labels:
- unnamed file
- unnamed folder
- unnamed source

## Important UI rule

The UI must not show these as the primary source name:
- raw `content://` URIs
- percent-encoded URI fragments
- internal `/data/user/0/...` paths

Those values may still exist internally for IO and debugging, but not as the default user-facing title.

## Clearing external connections

Clearing external connections removes the app's saved references to external files and folders.

It does not delete the real markdown files from disk.

Depending on the flow, the app may also stop using those sources immediately and fall back to built-in storage if that is the active mode chosen afterward.


## Backup and restore policy

NotifyMark treats app-owned Markdown files, local settings, reminder state, statistics, and persisted SAF URI references as local data that must not be copied by Android backup.

The manifest disables automatic backup and points to explicit exclusion rules for both generations of Android backup configuration:

- `app/src/main/res/xml/backup_rules_legacy.xml` covers Android 11 and lower;
- `app/src/main/res/xml/backup_rules.xml` covers Android 12+ cloud backup and device-to-device transfer.

All app backup domains are excluded, including credential-protected and device-protected files, databases, shared preferences, and app-specific external files.

SAF-selected Markdown documents are not owned by the app and are not copied by these rules. They remain under the selected document provider's own storage and backup policy. Persisted URI references are local app state and are excluded from backup.
