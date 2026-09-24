# Historical Evaluation Collection — 2026-09-17 / `e2b3287`

This directory preserves the evaluation reports and records that existed
before the unified M1-M19 evaluation. The user selected the following
historical checkpoint as the collection's anchor:

- **Commit:** `e2b3287279a9a645eb3f133ba92c46ce5832db60`
- **Short commit:** `e2b3287`
- **Timestamp:** `2026-09-17T00:13:03-05:00`
- **Subject:** `Independently re-evaluate implemented M19`

The anchor was selected because it is the most recent evaluation-report commit
before 2026-09-23 that added a complete independent M19 re-evaluation. This
directory is a historical collection anchored to that commit, not a
byte-for-byte snapshot of its tree: some retained reports were added or updated
later. [`MANIFEST.md`](MANIFEST.md) records each report's original path, first
known commit, and last pre-archive commit.

Reusable scripts are not archived as reports. They now live in
[`evals/tools/`](../../tools/).

To inspect the exact anchor tree, use:

```powershell
git ls-tree -r e2b3287 -- evals
git show e2b3287:evals/M19/re-evaluation-critic-report.md
```

To follow an individual report through the move, use:

```powershell
git log --follow -- evals/runs/2026-09-17-e2b3287/M19/re-evaluation-critic-report.md
```
