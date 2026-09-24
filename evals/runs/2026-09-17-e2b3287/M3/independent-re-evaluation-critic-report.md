# M3 — Independent Re-evaluation: Critic Report

## Scope and verdict

Independently re-evaluated M3.1–M3.14 from the beginning against synchronized
`origin/main` baseline `8afe530cf8dd467a6063c5514767eb7814bcc0f4`.
Requirements, decisions, production implementation, callers, the complete M3
test suite, prior Codex reports, and retained regressions were inspected rather
than treating the remediation or green CI as proof.

**Verdict: PASS.** No legitimate unresolved M3 product or chess-rule defect was
found. No production code was changed.

## Prior defect classes

- **En-passant marker validation:** closed. Generation and recognition require
  the correct target rank, an empty target, the opposing bypassed pawn, pawn
  capture geometry, and legality after removing both pawns. All six retained
  corrupt-marker/geometry regressions pass. A new pinned-capture test also
  confirms that a pseudo-available but illegal en-passant capture does not alter
  repetition identity.
- **Terminal move queries:** closed. Every terminal reason closes `legalMoves`,
  `isLegal`, and `applyMove` consistently through `GameState` and `ChessGame`.
  Live checkmate/stalemate calculation remains independent of a stored result.
- **Prospective draw claims:** closed. Current-position claims retain their exact
  thresholds. A prospective claim requires a specific legal declared move and
  is bound to the position and clock that move would produce without playing
  it. Pawn moves, captures, illegal moves, different quiet moves, and moves in a
  finished game do not create entitlement. Checkmate takes precedence. The
  claim action ends the original position with the correct claimed-draw reason.
- **Same-color multi-bishop dead positions:** closed. Once pawns, rooks, and
  queens are absent, bishop-only material is recognized as dead exactly when all
  bishops occupy one color complex, independent of count or ownership. Tests
  cover promotions and captures that create that material.

## Broader rule audit

- Current threefold and fifty-move claims remain claimable rather than automatic.
  Fivefold repetition and 150 halfmoves end play automatically; the counters
  reset on every pawn move and capture, including en passant and promotion.
- Repetition identity includes placement, side to move, castling rights, and only
  a genuinely legal en-passant opportunity. Irreversible transitions prevent
  stale history from manufacturing an occurrence.
- Sparse but live material was not overclassified. Concrete mating positions
  prove that one knight per side and opposite-color bishops can still mate with
  cooperative play. Existing coverage also preserves two knights, bishop–knight,
  both bishop color complexes, and all pawn/rook/queen configurations as live.
- Server and Android callers were traced. Their command currently carries no
  declared move, so they intentionally ask only the current-position question;
  game-core now exposes the move-bearing prospective path required for a future
  transport.

The governing standard was checked against the current
[FIDE Laws of Chess](https://handbook.fide.com/chapter/e012023), especially
Articles 5.2.2, 9.2, 9.3, and 9.6.

## Move-generation audit

The standard-position oracle passes through depth four. The published Kiwipete
and perft-suite positions also match through the depths used by the earlier M3
evaluation, exercising castling, en passant, promotion, checks, pins, and
tactical captures. Those formerly temporary reference checks are now retained
in `M3ReferencePerftTest`; their oracle is the
[`freeeve/pgn` perft suite](https://github.com/freeeve/pgn/blob/v3/perft_test.go).

M3 is independently complete. M4 may now begin.
