# Codex Evaluation State

- **Status:** `M13 PASSED`
- **Evaluation branch:** `codex-autopilot`
- **Requested range:** M1 through M19 inclusive
- **Next milestone:** M14
- **Run ID:** `2026-09-24-f941135`
- **Pinned baseline:** `f9411358d319d8501bfc58aa05470523a67bd6a8`
- **Last verified checkpoint:** M12 passing checkpoint at
  `77b012a49383e8b017230f99ac3c43145b88c96b`
- **Current findings:** none; current D053/D068 finalization, rematch,
  resignation, and explicit-leave lifecycle pass focused verification

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

## Exact next action

Create and remotely verify the M13 evaluator checkpoint. Then begin the fresh
M14 assessment before consulting historical M14 reports.
