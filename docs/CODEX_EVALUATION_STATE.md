# Codex Evaluation State

- **Status:** `M1-M19 COMPLETE`
- **Evaluation branch:** `codex-autopilot`
- **Requested range:** M1 through M19 inclusive
- **Next milestone:** None - stop; M20.1 was not started
- **Run ID:** `2026-09-24-f941135`
- **Pinned baseline:** `f9411358d319d8501bfc58aa05470523a67bd6a8`
- **Last verified checkpoint:** M19 completion checkpoint (the commit containing
  this state; its live remote SHA is verified after publication)
- **Current findings:** None

## Live branch reconciliation

During M8, the live `codex-autopilot` history was rebased from the previously
verified M7 checkpoint `7a2b3bf57294b2e644287a3d09473e6cd979ca0d` onto two production commits:

- `6d168190929c008caa5249b38909031109389ec2` — Android launch splash
- `3e50441f6846de1213eb4fb38b6816bc4b720e34` — future-planning documentation

The rewritten M7 checkpoint is live at `2e3afb6e01f7a692d9b45822a65afaa24e2d2607`.
The original pinned baseline remains
`f9411358d319d8501bfc58aa05470523a67bd6a8`. After the evaluator paused, the
work was unstashed and the user directed the run to resume on the published
rewritten history.

The intervening diff changes the Android launch splash and planning documents;
the `server` and `game-core` trees are unchanged. M8's 49 focused server tests
were rerun on the rewritten tip, and a complete Android build passed all 521
tests, lint, and debug/release assembly. No conflict with M8 or the evaluation
workflow remains known. No production file was changed by the evaluator.

During M18, the evaluator checkpoint `cd8c389591298fa9654e4ad9fac6b292a3bcb738`
identified `M18-U01`: the platform review said 22 `game-core` Kotlin files at
`9941402`, while the named tree contains 23. Remediation commit
`12297c1bfa126ce980b0a3f60a0844d3796c9cad` changed only those two false
counts on `claude-autopilot`. The user reconciled the two-line documentation
fix by amending the M18 checkpoint. Its final live form is
`13009135359874ec95b1d0feffc12b454f51543f`.
The exact regression now passes, the 1,779-line count remains correct, all nine
relative links resolve, and `M18-U01` is closed.

## Evaluation boundaries

- Follow `docs/INDEPENDENT-EVALUATION.md` one milestone and one remotely
  verified evaluator checkpoint at a time.
- Select one new stable run ID before M1 and use it for the entire M1-M19 run.
- Begin each milestone with a fresh assessment. Prior reports under
  `evals/runs/` are historical comparison material only and may be consulted
  only after that milestone's fresh phase.
- M17.12 and M17.13 are ordinary requirements and must be fully evaluated.
- M17.4 does not exist and must not be reintroduced.
- M20.1 is outside this run and must not be started.
- Production code must not be changed on `codex-autopilot`. A confirmed
  production defect is checkpointed here and remediated separately on
  `claude-autopilot`.

Historical evaluation state remains available in the immutable reports and Git
history. It does not control this fresh run.

## Completion

M19.2-M19.12 passed fresh evaluation. The retained participant-identity and
migration regressions pass unchanged, the final full build passed 1,508 tests
with no failures, errors, or skips, and no M19 finding was opened. The unified
run summary is `evals/runs/2026-09-24-f941135/UNIFIED-M1-M19-SUMMARY.md`.

## Exact next action

Stop. The requested M1-M19 evaluation is complete. Do not begin M20.1.
