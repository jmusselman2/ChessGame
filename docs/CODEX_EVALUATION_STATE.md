# Codex Evaluation State

- **Status:** `REMEDIATION REQUIRED`
- **Evaluation branch:** `codex-autopilot`
- **Requested range:** M1 through M19 inclusive
- **Next milestone:** M9 remediation re-evaluation; do not begin M10
- **Run ID:** `2026-09-24-f941135`
- **Pinned baseline:** `f9411358d319d8501bfc58aa05470523a67bd6a8`
- **Last verified checkpoint:** M8 at
  `88239f1a120e328e1707f044da03e50a1ed6a6a1`
- **Current findings:** `M9-U01` - a nameless authenticated caller can create a
  series and game that the named participant cannot discover on the dashboard

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

Create and remotely verify the M9 evaluator checkpoint containing the reports,
state, and smallest reliable regression for `M9-U01`. Remediate the production
defect separately on `claude-autopilot`, then return to this branch and re-run
M9. Do not begin M10 before M9 passes and its passing checkpoint is pushed and
remotely verified.
