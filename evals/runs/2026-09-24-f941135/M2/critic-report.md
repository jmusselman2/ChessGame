# M2 Critic Report

## Fresh assessment

**Provisional fresh verdict:** PASS

This section was written before consulting any retained M2 evaluation report.

### Scope and evidence

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M2: `23cc0f8d91f84420ebd457a631d6cca5cb18dd51`
- Requirements: `docs/BACKLOG.md` M2.1 and M2.2
- Current implementation: all production sources under
  `game-core/src/main/kotlin/com/jmussel/chessgame/core/chess`
- Current callers: `game-core`, Android UI/state conversion, and server persistence
  conversion call sites
- Current tests: domain type, standard-position, collection-immutability, and
  domain-invariant suites

### M2.1 — Core chess types

PASS. The domain provides side, piece type, piece, square, board, move, game
state, result, castling-right, and draw-rule representations in the pure
`game-core` JVM module. The module declares only Kotlin test support; production
imports are Kotlin/JDK collection support and contain no Android, server,
database, transport, or persistence dependency.

The exposed value objects enforce their local invariants. `Square` admits only
the 64 board coordinates, `Move` rejects a no-op move and invalid promotion
types, `GameResult` rejects outcome/reason disagreement, `GameState` rejects a
nonpositive fullmove number, and `DrawRuleState` rejects a negative halfmove
clock or nonpositive repetition count. Board placement, draw counts, move
history, and shared rule registries cannot be mutated through retained caller
aliases or published collection views.

### M2.2 — Initial standard position

PASS. `StandardPosition.newGame()` produces 32 pieces on the correct ranks and
files, White to move, all four castling rights, no en-passant target, no played
move history, halfmove clock 0, fullmove number 1, and no result. The starting
position is recorded exactly once for repetition accounting, as required by the
current authoritative backlog clarification.

### Fresh findings

No confirmed production defect was found. No evaluator repair was required.

## Historical comparison

After the fresh verdict was recorded, all retained M2 reports under
`evals/runs/2026-09-17-e2b3287/M2/` were reviewed.

The historical reports had found and then re-evaluated these mutation and
validation defects:

- retained constructor aliases and a mutable published map in
  `DrawRuleState.positionCounts`;
- acceptance of nonpositive repetition counts;
- retained/published mutable `ChessGame.history`;
- mutable shared `Square.ALL`, `PieceType.PROMOTION_CHOICES`,
  `StandardPosition.BACK_RANK`, direction lists, and knight-step lists.

Current source snapshots constructor inputs, wraps published collections with
JDK unmodifiable views, and validates positive occurrence counts. The retained
`M2DomainInvariantTest` and `M2CollectionImmutabilityTest` suites directly
exercise those paths and all 17 tests pass. Their iterator, list-iterator, and
sublist mutation cases also pass. The historical final re-evaluation's PASS
therefore remains supported, while this run independently reaches the same
result from the newer baseline and larger 394-test core suite.

No additional production or evaluator change is justified. **Final M2
verdict: PASS.**
