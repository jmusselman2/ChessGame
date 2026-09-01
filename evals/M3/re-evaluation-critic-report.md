# M3 — Chess Legal Move Engine: Re-evaluation Critic Report

## Scope and verdict

Re-evaluated the complete M3 milestone against synchronized `origin/main`
baseline `d80ac61523ed323bd5e80800affda00265ac20b3`. The review covered M3.1
through M3.14: movement geometry, attacks and check, self-check, castling, en
passant, promotion, terminal positions, insufficient material, repetition,
move-count draws, and draw claims.

The six previously reported en-passant defects are resolved. Their unchanged
regressions pass, generation and recognition agree, valid White and Black
captures still work, and standard-position perft passes at 20, 400, 8,902, and
197,281 nodes through depth four.

M3 nevertheless remains **DEFECT FOUND** because the public move-query API
advertises playable moves after the game is final.

## Confirmed defect — finished games still advertise legal moves

1. **Requirement or invariant:** M3.10 records a terminal result immediately,
   M3.14 says a valid draw claim finalizes the game, and D017 says there is no
   pending-final state. Once `GameState.isOver` is true, no further move can be
   legal or available.
2. **Adversarial scenario:** attach a final result to the standard position and
   query the public rules API. `ChessRules.legalMoves` still returns the 20
   opening moves and `ChessRules.isLegal(state, e2e4)` still returns `true`,
   even though `ChessRules.applyMove` rejects the same move because the game is
   over.
3. **Implementation evidence:** `ChessRules.legalMoves` and
   `ChessRules.isLegal` delegate directly to `LegalMoves` without checking
   `state.isOver` (`ChessRules.kt:9-15`). `applyMove` does check and reject the
   final state (`ChessRules.kt:116-121`). The public query and transition APIs
   therefore disagree about the same move.
4. **Existing test coverage:** `ChessRulesApplyMoveTest` verifies that applying
   a move in a finished game is rejected, and `CheckmateStalemateTest` verifies
   that a stored result is retained. No existing test asks whether a finished
   game still publishes or recognizes legal moves.
5. **Coverage sufficiency:** insufficient. Clients and server policy can use
   `legalMoves` or `isLegal` to decide which actions to offer or validate. A
   move cannot coherently be legal when the canonical transition rejects it
   solely because the game was finalized.
6. **Classification:** **CONFIRMED DEFECT**.
7. **Smallest automated proof:** the two focused tests added to
   `M3AdversarialTest`: one requires the legal-move list to be empty and the
   other requires a normally legal opening move to be reported illegal after a
   result is set.

## Other audit results

- The complete en-passant defect batch is resolved; all six regressions pass
  unchanged.
- The depth-four perft result strengthens broad move-generation coverage and
  passes.
- M3.1–M3.9 and the rule calculations in M3.10–M3.14 revealed no other
  confirmed defect in this cycle.
- `EnPassant.targetAfter` is called by the integrated transition only after
  `ChessRules.isLegal` accepts the move. Its narrower helper precondition does
  not create an invalid integrated state and is not classified as a defect.

M4 was not evaluated.
