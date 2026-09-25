# M4 Critic Report

## Fresh assessment

**Provisional fresh verdict:** PASS

This section was written before consulting any retained M4 evaluation report.

### Scope and evidence

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M4: `d66fb91295ac63a8ba47c126293ea83ad2688c1a`
- Requirements: `docs/BACKLOG.md` M4.1 through M4.3
- Current implementation: `ChessGame`, `MoveRecord`, and the undo APIs in
  `ChessRules`
- Current tests: move-history round trips, undo eligibility sequences,
  terminal undo locks, and M4 adversarial regressions

### M4.1 — Active move history

PASS. Every applied move records the complete pre-move `GameState`; mechanical
undo restores that snapshot and removes exactly one history record. Round-trip
coverage includes quiet moves, captures, double advances, en passant,
castling, promotion, cleared repetition history, counters, and a game-ending
move. Board, side to move, castling rights, en-passant target, halfmove/fullmove
counters, repetition state, and result are therefore restored together rather
than reconstructed piecemeal.

### M4.2 — Latest unanswered move

PASS. `undoableSide` identifies only the latest mover. The other side cannot
undo that move; after a reply, the reply's mover is the only eligible side;
undoing the reply exposes the prior move as the latest unanswered move again.
Undo restores the mover's turn and supports choosing a different replacement
move. Longer sequences unwind one move at a time.

### M4.3 — Terminal action lock

PASS. A stored terminal result makes `undoableSide` null, `canUndo` false for
both sides, and guarded `undo` reject the action. Coverage includes every
`TerminationReason`, including checkmate, stalemate, insufficient material,
fivefold repetition, the seventy-five-move rule, claimed draws, and
resignation. History remains readable. A prospective draw claim does not play
its declared move or create an undoable history record.

### Fresh findings

No confirmed production defect was found. No evaluator or environment repair
was required.

## Historical comparison

After the fresh verdict was recorded, both retained M4 reports under
`evals/runs/2026-09-17-e2b3287/M4/` were reviewed. Their PASS covered the same
three adversarial boundaries exercised here: every terminal reason, a
prospective claim that creates no history, and undo/replay at the threefold
threshold. All retained cases pass on the pinned baseline.

The historical report also distinguished public `undoLastMove` as an
intentionally unguarded mechanical restoration helper rather than the product
action. A current caller audit confirms production Android and server flows use
guarded `undoableSide`/`canUndo`/`undo`; no production caller bypasses
eligibility through `undoLastMove`. Its only non-core call is a persistence
test fixture.

No additional production or evaluator change is justified. **Final M4
verdict: PASS.**
