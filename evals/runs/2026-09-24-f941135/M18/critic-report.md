# M18 Critic Report

## Fresh assessment

**Fresh verdict: REMEDIATION REQUIRED**

This section was completed before consulting any retained M18 evaluation
report.

### Scope and current authority

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M18:
  `98faf92fca5f3b040c5057807c13afbfd0fcaa7b`
- Requirement: `docs/BACKLOG.md` M18.1, `docs/PLATFORM-REVIEW.md`, D044,
  `ARCHITECTURE.md` section 31, the named review snapshot `9941402`, and current
  source/document relationships.

### What passes

The review contains all four required classifications: chess-specific
concepts, proven platform concepts, abstractions worth extracting later, and
abstractions that should remain concrete. It additionally names what chess did
not prove. Its status, evidence/method, assumptions, and relationship to
binding decisions are explicit. D044 contains the binding result: extract
nothing until a second ruleset exists, then reconsider six named candidates.

All nine relative file links resolve. The two M18 implementation commits modify
documentation only. The later M18-01 correction accurately narrows the physical
evidence to one tester device and two real people. The reviewed snapshot
independently confirms the central server measurement: 32 Kotlin source files,
4,257 lines, and exactly seven files that mention `game-core`/`GameCore`.

### M18-U01 — the review undercounts `game-core` by one file

**Severity:** documentation correctness; blocks M18 because the milestone is
the review itself.

`docs/PLATFORM-REVIEW.md` says twice that `game-core` contained 22 files at
`9941402`, including in the measurement table and the chess-specific section.
The exact named Git snapshot contains 23 Kotlin files under
`game-core/src/main/kotlin`: 22 files under `core/chess/` plus the five-line
`core/GameCore.kt`. The review's stated 1,779-line total already includes all
23, which makes the file-count omission unambiguous rather than a different
scope convention.

The conceptual conclusion is unchanged: `GameCore.kt` is a ChessGame branding
constant, and the whole module remains ruleset-specific. The measured artifact
count is nevertheless false in the authoritative review. The evaluator-only
`evals/tools/M18DocumentationRegressionTest.ps1` now derives the reviewed
revision and documented count from the review and compares them with the Git
tree. It fails reliably with:

> PLATFORM-REVIEW.md claims 22 game-core Kotlin files at 9941402; Git contains 23.

No production document was changed on `codex-autopilot`.

## Historical comparison

The retained `2026-09-17-e2b3287` M18 evaluation found `M18-01`: the review
claimed two physical devices while M17 recorded only the external tester's
device as physical. The current review and M18 completion note contain the
dated correction, and the retained regression passed before the new count
assertion was added. `M18-01` is closed.

The retained report's four-section and D044 assessments agree with the fresh
review. Its carried M10/M12/M14 runtime findings are now closed by D057-D059
and are not carried forward.

The retained evaluation did not compare the review's named `game-core` file
count with the `9941402` Git tree. `M18-U01` is therefore a new finding, not a
relabeling of M18-01. It is narrow, deterministic, and blocks this
documentation-only milestone until the two false counts are corrected.

**Pre-remediation M18 verdict: REMEDIATION REQUIRED.**

## Completed remediation handoff

On `claude-autopilot`, change only the two `docs/PLATFORM-REVIEW.md` claims
about the `game-core` file count at `9941402` from 22 to 23: the measurement
table and the opening sentence of *Chess-specific concepts*. Keep the verified
1,779-line count unchanged. Do not rewrite the review, alter production code,
or begin M19/M20.1.

Then run:

1. `./evals/tools/M18DocumentationRegressionTest.ps1`
2. the relative-link and named-snapshot measurement checks recorded in
   `test-report.md`
3. `git diff --check`

The regression must pass with the prior M18-01 physical-evidence protection
still intact. Commit and push the remediation on `claude-autopilot`, publish it
through the project's normal branch reconciliation, and return the live commit
for M18 re-evaluation on `codex-autopilot`.

## Remediation re-evaluation

All remote refs were fetched before re-evaluation. The live remediation commit
on `origin/claude-autopilot` is
`12297c1bfa126ce980b0a3f60a0844d3796c9cad`; its complete patch changes only
the two false `game-core` file counts in `docs/PLATFORM-REVIEW.md` from 22 to
23 and preserves the verified 1,779-line count. The user reconciled that exact
patch by amending the M18 evaluator checkpoint on `codex-autopilot`.

The retained evaluator regression now passes. Independent recounting at
`9941402` confirms 23 `game-core` Kotlin files and 1,779 lines. The server
measurements remain 32 Kotlin files, 4,257 lines, and seven files mentioning
the core. All nine unique relative documentation targets resolve. The required
four classifications, explicit limitations, D044 relationship, and corrected
M18-01 physical-evidence boundary remain intact. No runtime or device behavior
is part of M18.1.

`M18-U01` is **CLOSED**.

**Final M18 verdict: PASS.**
