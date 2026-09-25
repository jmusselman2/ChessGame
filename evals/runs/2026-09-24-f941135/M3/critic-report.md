# M3 Critic Report

## Fresh assessment

**Provisional fresh verdict:** PASS

This section was written before consulting any retained M3 evaluation report.

### Scope and evidence

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M3: `412571894129ebc2cb51ead9681405792a51782c`
- Requirements: `docs/BACKLOG.md` M3.1 through M3.14
- Production implementation: move geometry, attack detection, legal-move
  filtering, special moves, terminal detection, repetition, move-count draws,
  and draw claims in `game-core`
- Call path: public `ChessRules` queries and transitions through
  `LegalMoves`, `PseudoLegalMoves`, `Castling`, `EnPassant`, `Attacks`,
  `Repetition`, `MoveCountDraws`, and `InsufficientMaterial`
- Verification: dedicated requirement tests, adversarial public-API tests,
  published reference perft positions, and the complete core regression suite

### Requirement assessment

| Requirement | Fresh result | Evidence summary |
| --- | --- | --- |
| M3.1 sliding pieces | PASS | Rays respect edges, friendly/enemy blockers, and captures. |
| M3.2 knights | PASS | All eight jumps, edges, captures, friendly occupancy, and jumping behavior are covered. |
| M3.3 kings | PASS | Adjacent geometry and occupancy are correct; attack filtering remains in legal moves. |
| M3.4 pawns | PASS | One/two-square advances, direction, blocking, and diagonal captures are covered for both sides. |
| M3.5 attacks/check | PASS | Every piece type, defended squares, blocker semantics, and side-specific check are covered. |
| M3.6 self-check | PASS | Pins, checking rays, capture/block responses, and attacked king destinations are filtered. |
| M3.7 castling | PASS | Rights, home pieces, empty path, current check, transit attack, destination attack, and rook relocation are covered. |
| M3.8 en passant | PASS | Creation, expiry, capture removal, geometry, both sides, and discovered/self-check are covered. |
| M3.9 promotion | PASS | Advance/capture promotion offers Q/R/B/N only; no bare or automatic-queen move exists. |
| M3.10 mate/stalemate | PASS | Known mates/stalemates and escape/block/capture negatives classify correctly and finalize immediately. |
| M3.11 insufficient material | PASS | Required dead positions, same-colour promoted bishops, and live sparse counterexamples are distinguished. |
| M3.12 repetition | PASS | Full position identity, legal en-passant relevance, history reset, threefold claims, and automatic fivefold are covered. |
| M3.13 move-count draws | PASS | 50/75-move boundaries, checkmate precedence, increments, and pawn/capture resets are covered. |
| M3.14 draw claims | PASS | Current and declared-move claims finalize valid draws, reject invalid claims, and leave automatic draws claim-free. |

The adversarial suite checks the standard-position perft through depth four,
while eight published reference positions independently exercise Kiwipete,
castling, promotions, pins, en-passant check evasion, and other combined move
generation through depths two to four. All matched their reference node counts.
Public query/transition consistency is also adversarially checked: advertised
actions are accepted, finished games advertise no moves, and every terminal
reason rejects further moves.

### Fresh findings

No confirmed production defect was found. No evaluator or environment repair
was required.

## Historical comparison

After the fresh verdict was recorded, all retained M3 reports under
`evals/runs/2026-09-17-e2b3287/M3/` were reviewed. They documented four prior
defect families across the original evaluation and re-evaluations:

- inconsistent/corrupt en-passant markers could manufacture captures;
- finished games could still advertise legal moves;
- prospective threefold and fifty-move claims had no declared-move path;
- same-colour multi-bishop dead positions were not recognized.

The current source closes each boundary explicitly. En-passant generation and
recognition validate rank, empty target, opposing bypassed pawn, and capture
geometry. Public move queries return no moves after a stored result. Draw-claim
APIs accept and bind a legal declared move without playing it. Bishop-only
material is dead exactly when all bishops remain on one colour complex.

All 20 retained `M3AdversarialTest` cases pass, including exhaustive terminal
query/transition consistency, corrupt en-passant markers, legal-only repetition
identity, prospective claims, and sparse-material counterexamples. All eight
retained reference-perft cases also pass. This independently supports the
historical final PASS on the newer pinned baseline.

No additional production or evaluator change is justified. **Final M3
verdict: PASS.**
