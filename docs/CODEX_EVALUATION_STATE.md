# Codex Evaluation State

- **Current baseline:** `c2f7af9fb34981861ac086d7e5fb3842ff135a5f`
- **Current milestone:** M2 — Chess Domain Model
- **Status:** `DEFECTS FOUND` — addressed on `claude-autopilot`, not yet
  independently re-evaluated
- **Evaluation artifacts:** `evals/M2/critic-report.md`,
  `evals/M2/test-report.md`, `evals/M2/re-evaluation-report.md`,
  `evals/M2/collection-immutability-audit.md`, and the M2 regression tests in
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/`

## Findings and their disposition

Every finding in the audit was independently validated and none was rejected;
all nine adversarial regressions are retained unchanged and now pass. Two of the
six families were legitimate product defects and four were shared-constant
robustness invariants — the distinction is recorded here because it is the
reason the fix is uniform even though the impact is not.

- **Confirmed → addressed (product defect):** `ChessGame.history` neither
  snapshotted a constructor-supplied list nor prevented mutation through its
  public JVM list. Either path changed `lastMove`, `lastMover`, and therefore
  undo eligibility with no game-state transition. It is now snapshotted at
  construction and published unmodifiable.
- **Confirmed → addressed (product defect):** `Square.ALL`,
  `PieceType.PROMOTION_CHOICES`, `Direction.ORTHOGONAL`/`DIAGONAL`/`ALL`, and
  `PseudoLegalMoves.KNIGHT_STEPS` were mutable shared lists read on live paths —
  square lookup, promotion validation, and move generation — so one indexed
  write changed the rules process-wide. All are now published unmodifiable.
- **Confirmed → addressed (robustness invariant):** `StandardPosition.BACK_RANK`
  was mutable, but is only read while building `BOARD` at initialization, so no
  reachable behavior changed. Fixed for the declared shared-constant invariant
  and so that the shared-collection surface is uniform rather than
  case-by-case.

The three earlier `DrawRuleState` findings remain resolved. Its input is
snapshotted and its published map, entries, keys, and values reject mutation in
the retained and expanded regressions.

## Exact next action

1. **Human integration:** merge `claude-autopilot` into `main`. Integration
   stays human-controlled; the autonomous loop does not do it.
2. **Codex realignment:** realign `codex-autopilot` to the updated
   `origin/main`.
3. **Codex re-evaluation:** independently re-evaluate M2 from the beginning,
   including this systematic collection audit, rather than trusting this note.

M2 is **not** independently passed. Only Codex's own re-evaluation of the
updated `main` can change that, and M3 does not begin until it does.

## Completed milestones

None. M2 has not passed; M1 was excluded as non-behavioral bootstrap work.
