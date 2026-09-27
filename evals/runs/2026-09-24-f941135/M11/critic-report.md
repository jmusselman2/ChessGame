# M11 Critic Report

## Fresh assessment

**Fresh verdict: PASS**

This section was completed before consulting any retained M11 evaluation
report.

### Scope and current authority

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M11:
  `9e00fd6d5a582e4457b71dcd3c1a4eedbd15db04`
- Requirements: `docs/BACKLOG.md` M11.1 and M11.2, current PRODUCT takeback
  rules, ARCHITECTURE sections 21-22, and D016, D017, and D021
- Production implementation: `ChessRules` undo eligibility/restoration,
  `GameCommandService.undoMove`, `GameRepository`, HTTP response mapping,
  `GameView.canUndo`, persistence/audit behavior, and current callers
- Verification: focused server and game-core suites, concurrency/source/caller
  audit, the complete build on the same production tree, and device discovery

### Requirement assessment

| Requirement | Fresh result | Evidence summary |
| --- | --- | --- |
| M11.1 `UndoMove` | PASS | The server derives the caller's side, checks current version and terminal state, and delegates eligibility/restoration to `game-core`. Only the latest mover can undo an unanswered non-final move; undoing a reply exposes the preceding move again. Accepted undo restores the complete saved position/history, advances the monotonic version, rewrites active persistence transactionally, records `MoveUndone`, and returns canonical state. `GameView.canUndo` uses the same rule. |
| M11.2 move-vs-undo concurrency | PASS | Both otherwise-legal commands lock the game row before reading its split row/history representation. Repeated simultaneous races allow exactly one transition; the loser receives `STALE_VERSION` with the winning canonical state. Position, active history, audit trail, and recovery all match the winner without a mixed state. |

The rule-layer verification includes quiet moves, captures, double pawn moves,
en passant, castling, promotion, counters, repetition history, irreversible
history resets, multi-move unwind, claim/resignation locks, and every automatic
terminal reason. The server verification covers route authentication,
participant isolation, stale and duplicate requests, audit/version behavior,
the capability projection, and 75 repeated move-vs-undo races plus the later
M16.7 lock-wait probe.

No fresh M11 production defect was found.

## Historical comparison

The retained `2026-09-17-e2b3287` M11 evaluation also found no M11 defect. Its
23-test selection comprised 17 `UndoMoveTest` cases and 6
`MoveVersusUndoTest` cases. Those same 23 cases pass now without skips.

The historical report carried M8 identity/series findings and the then-open
M10 torn display-read finding. Those findings have since been remediated and
independently closed. The M11 command path continues to use `loadForUpdate`, so
the move-vs-undo decision cannot straddle an in-flight save; ordinary canonical
display reads are now also coherent under D057. The fresh 70-test selection
adds 8 view-mapping cases and 39 rule/history/terminal-lock cases, with no
changed conclusion.

No additional evaluator regression was needed for M11.

**Final M11 verdict: PASS.**
