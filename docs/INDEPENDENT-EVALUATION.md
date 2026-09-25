# Fresh M1-M19 Independent Evaluation

This runbook governs the fresh, continuous, sequential independent evaluation
of M1 through M19 on `codex-autopilot`. Production implementation and
remediation remain a separate workflow on `claude-autopilot` under
`docs/AUTONOMOUS-DEVELOPMENT.md`.

The evaluator must not modify production code. Evaluator tests and fixtures,
current-run reports, and `docs/CODEX_EVALUATION_STATE.md` are the only normal
evaluation changes. Completed reports under `evals/runs/` are immutable.

## Initialize the run

Immediately before beginning M1:

1. Fetch remote refs, verify a clean `codex-autopilot` working tree, and verify
   the live `origin/codex-autopilot` tip.
2. Choose one stable new run ID for the entire M1-M19 evaluation.
3. Pin the verified `origin/codex-autopilot` tip as the evaluation baseline.
4. Record the run ID and baseline in `docs/CODEX_EVALUATION_STATE.md`.
5. Create reports only under `evals/runs/<run-id>/`.

Do not silently substitute `origin/main` or `origin/claude-autopilot` for the
requested evaluation baseline.

## One milestone at a time

For each milestone, in order:

1. Work only on the current milestone.
2. Completely evaluate every requirement and acceptance criterion for that
   milestone.
3. Write the milestone reports.
4. Update `docs/CODEX_EVALUATION_STATE.md`.
5. Run `git diff --check` and inspect the complete milestone diff.
6. Create exactly one milestone-specific evaluator checkpoint commit.
7. Push that commit to `origin/codex-autopilot`.
8. Verify that the live `origin/codex-autopilot` ref resolves to the checkpoint
   commit.
9. Only after successful remote verification, immediately begin the next
   milestone.

Never combine evaluator work for multiple milestones in one commit. Do not
pause for confirmation between passing milestones.

Every milestone checkpoint must contain all three of these files:

- `evals/runs/<run-id>/M<n>/critic-report.md`
- `evals/runs/<run-id>/M<n>/test-report.md`
- `evals/runs/<run-id>/M<n>/kindle-checklist.md`

When device testing is not applicable, `kindle-checklist.md` must explicitly
record `NOT APPLICABLE` and explain why. Never omit it.

## Fresh-first evaluation

For each milestone:

1. Begin with the authoritative requirements, decisions, architecture, schema,
   production implementation, retained tests, and current callers.
2. Do not use an old evaluation report as the initial test plan and do not adopt
   its findings or verdicts before completing the fresh phase.
3. Independently assess every criterion and run independently selected
   verification.
4. Record provisional findings and results.
5. Only then inspect earlier reports for that milestone under `evals/runs/`.
6. Compare the fresh work with the historical work for missed criteria, useful
   adversarial cases, retained regressions, and previous evaluator mistakes.
7. Run any additional verification justified by that comparison.
8. Finalize the milestone reports from current evidence.

Historical evidence may suggest additional checks, but it never substitutes
for current verification or silently determines a verdict.

## Evaluator and environment failures

If an evaluator test, fixture, command, configuration, or test environment is
defective, diagnose and repair the evaluator or environment, rerun the affected
verification, and continue automatically. Document material evaluator
corrections in the test report. Never report an evaluator or environment
problem as a production defect or hand it to Claude for production remediation.

Missing credentials or unavailable devices constrain only the evidence that
genuinely requires them. Record the unavailable evidence and continue every
other reliable check. An unavailable optional external validation is not by
itself a reason to stop.

## Device evidence

Discover currently available devices with `adb devices -l`; read device
properties when necessary to distinguish physical devices from emulators. Do
not rely on serial numbers from historical reports.

For one-device evidence, prefer:

1. a connected physical Kindle;
2. a connected physical Android phone;
3. an existing Android emulator.

For a two-device scenario, prefer:

1. connected Kindle plus connected phone;
2. one available physical device plus one emulator;
3. two emulators only when physical devices are unavailable or unsuitable.

Protect existing physical-device state. Prefer an isolated evaluator
application ID or another non-destructive installation. Do not replace or
uninstall the owner's existing ChessGame installation or erase its data. Record
relevant device settings before temporary changes, restore them afterward, and
remove evaluator-only installations. The device report records exact models,
Android/API versions, serials, isolation method, and cleanup result.

## Confirmed production-defect gate

A suspected failure is not enough to stop the run. Before declaring a
production defect:

1. Complete every reliably testable criterion in the current milestone.
2. Reproduce the suspected bug reliably.
3. Rule out evaluator-test, fixture, command, configuration, timing, and
   environmental errors.
4. Add the smallest reliable evaluator regression.
5. Rerun the relevant focused and retained verification.
6. Record the complete finding batch.
7. Assign stable identifiers in order as `M<n>-U01`, `M<n>-U02`, and so on.
8. Write the critic report, test report, and Kindle checklist.
9. Set `docs/CODEX_EVALUATION_STATE.md` to `REMEDIATION REQUIRED`.
10. Record the failed milestone, pinned baseline, checkpoint commit, every open
    finding, and the exact next resumable action.
11. Commit and push the milestone evaluator checkpoint to `codex-autopilot`.
12. Verify the live remote ref resolves to that checkpoint, then stop without
    changing production code or beginning the next milestone.

The final response at this gate must provide a ready-to-use Claude remediation
handoff containing the baseline and evaluator checkpoint commits, defect IDs,
violated requirements and acceptance criteria, deterministic reproduction,
expected and actual behavior, evaluator regression paths and exact commands,
affected production area, required focused and aggregate verification, and
instructions to fix production code on `claude-autopilot` without weakening,
deleting, skipping, or rewriting the evaluator regressions.

## M19 completion

After M19 passes:

1. Run all retained evaluator regressions.
2. Run `.\gradlew.bat build --continue`; document only genuine external
   limitations.
3. Write `evals/runs/<run-id>/UNIFIED-M1-M19-SUMMARY.md` as part of the M19
   checkpoint.
4. Set the state to `M1-M19 COMPLETE`.
5. Commit, push, and remotely verify the M19 checkpoint.
6. Stop without beginning M20.1.

M17.12 and M17.13 are ordinary requirements and must be fully evaluated. M17.4
does not exist and must not be reintroduced. M20.1 is outside this run.
