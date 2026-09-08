# Codex Evaluation State

- **Evaluated `main` baseline:** `99414028bfd3e8b89953dc549ada77130bb3ae62`
- **Current milestone:** M8 — Friends
- **Status:** `DEFECT FOUND`
- **Completed milestones:** M2, M3, M4, M5, M6, M7
- **Unresolved findings:** M8-01 lets a nameless caller create a friendship the
  other side cannot list. M8-02 lets two concurrent re-adds both report that
  they restored the same removed friendship. M8-03 lets series creation race
  friend removal and commit an unmarked active series after removal.
- **Latest artifacts:** `evals/M8/critic-report.md`, `evals/M8/test-report.md`,
  and server `M8AdversarialTest` regressions.

## Next action

Claude remediates the complete M8 finding batch, the remediation is merged into
`main`, and Codex then realigns `codex-autopilot` to that baseline and performs
a fresh independent M8 evaluation. Retain every correct evaluator regression
unchanged. Do not begin M9 before M8 passes.
