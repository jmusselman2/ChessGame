# Continuous Independent Milestone Evaluation

This runbook governs the independent evaluation performed on the
`codex-autopilot` branch. The evaluator processes milestones sequentially from
the milestone recorded in `docs/CODEX_EVALUATION_STATE.md` through M14.

## Milestone checkpoint loop

For each milestone:

1. Fetch remote refs and verify the branch, working tree, `HEAD`, `origin/main`,
   and `origin/codex-autopilot` are safe and unambiguous.
2. Reconcile the milestone's requirements, decisions, architecture, schema,
   implementation, retained tests, relevant history, and later production
   callers.
3. Evaluate every requirement and acceptance criterion. Continue through the
   entire milestone after finding a defect so the report contains the complete
   finding batch.
4. Add the smallest reliable evaluator regression for each legitimate defect
   and run all retained and new milestone verification.
5. Write `evals/M<n>/critic-report.md` and `evals/M<n>/test-report.md`, then
   update `docs/CODEX_EVALUATION_STATE.md`.
6. Run `git diff --check`, inspect the complete diff, and create exactly one
   milestone-specific evaluator checkpoint commit.
7. Push `codex-autopilot` with an ordinary push and verify that
   `origin/codex-autopilot` resolves to the new commit before starting the next
   milestone.

A checkpoint commit contains only that milestone's evaluator tests and
fixtures, reports, and evaluation-state update. Work for multiple milestones
must never be combined in one commit.

## Defects and continuation

A `DEFECT FOUND` verdict completes the evaluation checkpoint for that
milestone. Record the complete finding batch and a coherent remediation handoff,
commit and push the checkpoint, carry the unresolved findings forward in the
state document, and immediately begin the next milestone. A defect is not a
reason to end the evaluation run.

Do not change production code or weaken, delete, skip, or rewrite a correct
requirement-backed test. If an earlier defect prevents reliable validation of a
later milestone, mark the affected validation `BLOCKED BY <finding-id>`, perform
and document everything that remains reliable, checkpoint that milestone, and
continue.

## Completion and stopping

Do not pause for confirmation between milestones and do not treat an individual
milestone checkpoint as completion. The run is complete only after M14 has been
fully evaluated, its dedicated checkpoint commit has been pushed, and
`origin/codex-autopilot` has been verified at that exact commit.

The only permitted early stop is a genuine external blocker that prevents safe
or reliable progress, such as irreconcilable repository state, unavailable
required credentials or infrastructure, or a repeated push failure. Report the
exact blocker and the next resumable action.

