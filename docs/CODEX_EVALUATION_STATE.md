# Codex Evaluation State

- **Status:** `M4 PASSED`
- **Evaluation branch:** `codex-autopilot`
- **Requested range:** M1 through M19 inclusive
- **Next milestone:** M5
- **Run ID:** `2026-09-24-f941135`
- **Pinned baseline:** `f9411358d319d8501bfc58aa05470523a67bd6a8`
- **Last verified checkpoint:** M3 at
  `d66fb91295ac63a8ba47c126293ea83ad2688c1a`
- **Current findings:** none

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

Review the complete M4 evaluator diff, run `git diff --check`, create exactly
one M4 evaluator checkpoint commit, push it to `origin/codex-autopilot`, and
verify the live remote ref before beginning M5.
