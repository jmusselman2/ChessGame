# M4 — Undo Semantics: Critic Report

## Scope and verdict

Independently evaluated M4.1–M4.3 against synchronized `origin/main` baseline
`8afe530cf8dd467a6063c5514767eb7814bcc0f4`. The review covered the backlog,
product and architecture requirements, D016–D018 and D029, production state and
transition code, the complete existing undo/history tests, immutable-history
coverage, server and Android callers, and adversarial game sequences.

**Verdict: PASS.** No legitimate unresolved M4 product defect was found. No
production code was changed.

## Findings

- Every played move records its complete prior `GameState`. Undo therefore
  restores board placement, side to move, castling rights, en-passant target,
  halfmove/fullmove counters, repetition counts, result, and active history by
  snapshot rather than by attempting to reverse special moves.
- Eligibility matches D016: only the latest mover may undo; the other side's
  reply locks the prior move; undoing that reply exposes the prior move again;
  and the turn returns to the player whose move was removed.
- Every `TerminationReason`, including both claimed draws, makes
  `undoableSide` null, `canUndo` false for both sides, and guarded `undo`
  reject. Prospective claims create no move record and are equally final.
- Undoing and replaying a move that creates the third repetition produces three
  occurrences again, not a phantom fourth. This confirms the full draw-rule
  snapshot is restored before replay.
- `ChessGame` snapshots its incoming history and publishes an unmodifiable
  list, so eligibility cannot be changed by mutating a retained collection.

`undoLastMove` is a public, unguarded mechanical restoration helper and can
unwind a terminal snapshot. This is intentional under D029, not the product
action: `ChessRules.undo`, local Android controls, API views, and the server
command all use the eligibility guard, and the server additionally refuses a
finished game before transition. No caller exposes the helper as an undo bypass.

M4 is independently complete. M5 is the next milestone.
