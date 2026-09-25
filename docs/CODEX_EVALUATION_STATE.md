# Codex Evaluation State

- **Status:** `READY FOR FRESH M1 EVALUATION`
- **Evaluation branch:** `codex-autopilot`
- **Requested range:** M1 through M19 inclusive
- **Next milestone:** M1
- **Run ID:** pending selection immediately before M1 begins
- **Pinned baseline:** pending verification and recording of the live
  `origin/codex-autopilot` tip immediately before M1 begins
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

Immediately before beginning M1, verify the clean `codex-autopilot` checkout
and live remote tip, choose and record the new run ID, pin and record the
verified `origin/codex-autopilot` baseline, create the new run's M1 report
directory, and then begin the fresh M1 phase.
