# M3 — Chess Legal Move Engine: Critic Report

## Scope

Evaluated the complete M3 milestone against synchronized `origin/main`
baseline `9d468b7ba718004c21cb8a8a20afd86b35fafd48` after M2 independently passed.
The review covered movement geometry, attacks/check, self-check, castling, en
passant, promotion, terminal positions, insufficient material, repetition,
move-count draws, and draw claims.

Existing tests and implementation were inspected rather than treated as proof.
A standard-position perft test independently confirmed 20, 400, and 8,902
nodes through depth three. No new defect was found in the non-en-passant M3
requirements.

## Confirmed defect — incomplete en-passant eligibility validation

1. **Requirement or invariant:** M3.8 requires en-passant eligibility and
   expiration to be correct (`docs/BACKLOG.md:569-589`). A valid capture needs
   an empty target on rank 6 for White or rank 3 for Black, a side-to-move pawn
   attacking that target, and an opposing pawn on the bypassed square.
2. **Adversarial scenarios:** a target is present while the bypassed square is
   empty, holds a friendly pawn, or holds a non-pawn; the target is on an
   impossible rank; the alleged move does not follow pawn-capture geometry; or
   the target itself is occupied and should be an ordinary capture only.
3. **Implementation evidence:** `EnPassant.isCapture` checks only that the move
   lands on the target, the mover is a pawn, and the file changes
   (`EnPassant.kt:33-40`). `availableMoves` checks that the target is empty and
   attacked by a side-to-move pawn (`EnPassant.kt:47-55`), but neither path
   validates target rank or the opposing pawn on the captured square. An
   occupied target can enter through ordinary pawn generation while
   `isCapture` still removes the piece behind it, producing a double capture.
4. **Existing coverage:** `EnPassantTest` thoroughly covers targets created by
   legal double advances, expiry, valid captures, self-check, and ordinary
   diagonal capture without a target. It never supplies a stale/corrupt target
   inconsistent with the board or tests `isCapture` geometry independently.
5. **Coverage sufficiency:** insufficient for the public `GameState` and
   `EnPassant` boundary. Normal `ChessRules.applyMove` produces consistent
   markers, but constructed or persistence-rebuilt states are accepted by the
   domain and must not manufacture captures or remove unrelated pieces.
6. **Classification:** **CONFIRMED DEFECT**.
7. **Smallest automated proof:** the six focused tests in
   `M3AdversarialTest` cover each independently discoverable invalid marker
   path. All six fail on the current implementation.

## Pattern audit

The same marker-consistency pattern was checked elsewhere in M3. Castling
rights are revalidated against king/rook placement, empty path, and attacks;
promotion choices are generated only for a pawn reaching the final rank; draw
markers and terminal results are guarded by their state thresholds. No related
defect was confirmed outside en passant.

M3 remains `DEFECT FOUND`; M4 was not evaluated.
