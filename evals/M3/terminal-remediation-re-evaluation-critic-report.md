# M3 — Terminal Remediation Re-evaluation: Critic Report

## Scope and verdict

Independently re-evaluated M3.1–M3.14 from the beginning against synchronized
`origin/main` baseline `2be1f0635291a239f0181d76f079311ccfb02024`.
The review covered movement geometry, attacks and check, self-check, castling,
en passant, promotion, terminal positions, insufficient material, repetition,
move-count draws, and draw claims. Existing tests and the remediation commit
were inspected rather than treated as proof.

The terminal-state remediation passes. The six earlier en-passant regressions
also remain resolved, and the standard-position perft oracle passes through
depth four at 20, 400, 8,902, and 197,281 nodes.

M3 nevertheless remains **DEFECT FOUND**. Continued from-scratch review found
two independent defect batches: prospective threefold-repetition and
fifty-move claims required by the product and architecture have no public
game-core path, and the insufficient-material detector misses promoted
same-colour bishop sets that are dead positions.

## Terminal-state remediation — independently verified

- Every `TerminationReason` was attached to the standard position in turn.
  For both `GameState` and `ChessGame`, `legalMoves` was empty and `isLegal`
  returned false for all 20,160 representable moves (every distinct from/to
  pair, with no promotion and each of the four promotion choices).
- `applyMove` rejected a normally legal opening move through both overloads for
  every terminal reason, so the query and transition boundaries now agree.
- A genuine checkmate produced through `applyMove` also closed both queries.
- The `hasNoLegalMoves` decoupling preserves live-position classification:
  result-free checkmate and stalemate positions still produce their correct
  `isCheckmate`, `isStalemate`, and `terminalResult` values, while the starting
  position remains non-terminal.
- Move, draw-claim, and undo availability queries were compared with their
  transitions on representative live and terminal states. No new
  "advertised but rejected" inconsistency was introduced by the remediation.
- `ResignTest.aResignedGameIsOver` still enforces D017: the resigned game is
  over and neither side may undo. Its corrected move-query assertion now also
  requires an empty legal-move list.

## Additional independent move-generation oracle

A temporary FEN/perft harness exercised published non-starting positions for
castling, en passant, promotion, checks, pins, and tactical move generation.
The selected Kiwipete and perft-suite node counts all matched, including
Kiwipete through depth three (48, 2,039, 97,862), position 3 through depth four
(14, 191, 2,812, 43,238), positions 4–6 through depth three, an en-passant
check-evasion case, an all-promotions case, and an open-castling case. The
passing exploratory harness was removed; it did not justify a retained
regression. The oracle values came from the independent `freeeve/pgn`
perft suite: <https://github.com/freeeve/pgn/blob/v3/perft_test.go>.

## Confirmed defect — prospective draw claims have no claim path

1. **Requirement or invariant:** `docs/PRODUCT.md:294` requires claim
   entitlement from "current/prospective legal move state" and
   `docs/ARCHITECTURE.md:624` requires the engine to expose enough information
   for "any relevant prospective legal move condition." M3.12 and M3.14 own
   repetition/move-count claims and their game-core action.
2. **Adversarial scenarios:**
   - Black is to move in a legal knight-shuffle sequence. The current position
     has occurred twice, and the legal declared move `f6g8` would restore the
     initial position for its third occurrence.
   - White is to move after 99 halfmoves without a pawn move or capture. The
     legal declared quiet move `d1d2` would make the halfmove clock 100.
3. **Implementation evidence:** `ChessRules.availableDrawClaims(state)`,
   `canClaimDraw(state, claim)`, and `claimDraw(state, claim)` accept no
   prospective move (`ChessRules.kt:73–106`). `Repetition.canClaimThreefold`
   tests only the current position's count (`Repetition.kt:43`), and
   `MoveCountDraws.canClaimFiftyMove` tests only the current halfmove clock
   (`MoveCountDraws.kt:12`). There is therefore nowhere to declare, validate,
   or bind the legal move that creates the entitlement.
4. **Existing coverage:** current-position claims at three occurrences / 100
   halfmoves are well covered. No baseline test exercises the prospective form
   named by the authoritative documents.
5. **Coverage sufficiency:** insufficient. The two new regressions first prove
   that the declared move is legal and that applying it would produce the exact
   threshold, then fail because the player about to make it has no claim
   available. Both failures are deterministic.
6. **Classification:** **CONFIRMED DEFECT**. It predates and is independent of
   the terminal-state remediation.
7. **Remediation constraint:** add a move-aware claim path that validates the
   declared legal move and its resulting repetition/halfmove condition without
   weakening current-position claims. Do not merely make pre-threshold claims
   unconditional: a pawn move, capture, illegal move, or a move leading to a
   different position must not qualify.

## Confirmed defect — promoted same-colour bishops are not recognized as dead

1. **Requirement or invariant:** `docs/PRODUCT.md:262,290` requires standard
   insufficient-material termination, and M3.11 owns that automatic result.
   FIDE Law 5.2.2 defines a dead position as one in which neither player can
   checkmate by any possible legal sequence. Bishops never leave their square
   colour, so any bishop-only remainder in which every bishop occupies the same
   colour complex cannot produce mate, including multiple bishops obtained by
   promotion. See <https://handbook.fide.com/chapter/e012023>.
2. **Adversarial scenario:** White legally plays `e3g5`, capturing Black's last
   non-king piece. White's surviving bishops stand on `c1` and `g5`, two
   same-colour squares, against the bare black king. The pre-capture position is
   live; the capture itself creates the dead position.
3. **Implementation evidence:** `InsufficientMaterial.isDraw` returns false
   whenever the two bishops belong to the same side, without checking their
   square colours (`InsufficientMaterial.kt:29`), and rejects every bishop-only
   position with more than two non-kings (`InsufficientMaterial.kt:24`). It
   therefore assumes two bishops always cover both colour complexes, which is
   false after same-colour bishop promotions.
4. **Existing coverage:** `twoBishopsForOneSideAreNotADraw` places its bishops
   on `c1` and `f1`, opposite-colour squares that really can mate. Despite its
   broad name, it does not cover two bishops of one side on the same colour.
5. **Coverage sufficiency:** insufficient for promotion-created material. The
   new regression first proves the capture legal, then checks the resulting
   material predicate and terminal state. It fails deterministically at the
   material predicate.
6. **Classification:** **CONFIRMED DEFECT**. This is independent of both the
   terminal-query remediation and the prospective-claim gap.
7. **Remediation constraint:** after ruling out pawns, rooks, and queens, treat
   a bishop-only position as dead when every bishop is confined to the same
   square colour, regardless of bishop count or ownership. Preserve the
   opposite-colour two-bishop and knight cases where cooperative mate remains
   possible.

No production code was changed by this evaluation. M4 was not evaluated.
