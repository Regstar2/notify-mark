# Roadmap

## Near-term work

0. **v0.9.1 repeat history (shipped in codebase):** calendar merges occurrence history; bulk skip/done aligns with repeat advance. Next: optional history detail UI and richer one-off analytics (see `docs/versions/v0.9.1-repeat-history-fix.md`).

1. Finish structural cleanup after the package refactor
   - narrow wildcard imports
   - extract small helpers from the largest activities

2. Strengthen tests around shared core logic
   - parser edge cases
   - markdown writer round-trips
   - repeat-series transitions
   - source display name edge cases

3. Improve user-facing documentation
   - expand markdown examples
   - document onboarding and storage-mode flows with screenshots or a short guide

4. Deepen the statistics feature
   - add broader historical coverage for one-off task events
   - introduce drill-down views and richer filters without duplicating task state

5. Prepare README and docs for broader public use
   - installation notes
   - limitations by Android version
   - SAF and Syncthing troubleshooting
