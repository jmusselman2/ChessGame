# Independent Evaluation Records

Evaluation reports are organized as immutable, named runs:

- [`runs/2026-09-17-e2b3287/`](runs/2026-09-17-e2b3287/README.md) is the
  historical collection selected before the unified M1-M19 evaluation. Its
  name records the chosen anchor commit; its manifest records each file's
  actual provenance.
- [`tools/`](tools/) contains reusable evaluation scripts rather than report
  evidence.
- Future evaluations belong under `runs/<run-id>/`, with one milestone
  directory per evaluated milestone.

Reports in a completed run are historical records and must not be overwritten.
[`docs/CODEX_EVALUATION_STATE.md`](../docs/CODEX_EVALUATION_STATE.md) identifies
the active evaluation, its baseline, progress, findings, and current reports.
