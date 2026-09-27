# M13 Critic Report

## Fresh assessment

**Fresh verdict: PASS**

This section was completed before consulting any retained M13 evaluation
report.

### Scope and current authority

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M13:
  `77b012a49383e8b017230f99ac3c43145b88c96b`
- Requirements: `docs/BACKLOG.md` M13.1 through M13.5, with D053 and D068
  superseding the removed close-after-current lifecycle
- Production implementation: `GameRepository.save`, `GameCommandService`,
  `SeriesService`, resignation routes/rules, series repositories and schema,
  realtime new-game publication, and their current callers
- Verification: focused finalization/rematch/resignation/leave/audit/
  idempotency suites, source and transaction-boundary review, schema-history
  reconciliation, the complete build on the same production tree, and device
  discovery

### Requirement assessment

| Requirement | Fresh result | Evidence summary |
| --- | --- | --- |
| M13.1 transactional finalization | PASS | The guarded save commits status, result, reason, first `ended_at`, move history, command audit, and one `GameEnded` event together. Row locking and expected-version writes make terminal races settle once; later commands cannot refinalize the game. |
| M13.2 exactly-one next game | PASS | A completing command calls `settleAfter` inside its outer transaction. The locked series row creates a rematch only while active and still pointing at the finished game, then atomically advances `currentGameId`; retries and concurrent settlement create nothing further. |
| M13.3 alternate colours | PASS | The stored seat rotation advances one seat for each rematch, reversing White and Black for chess across successive games and independently for parallel series. |
| M13.4 close without rematch | PASS | Current D068 authority replaces the historical marked-to-close state: explicit leave closes immediately without touching the current game. That game may still finish normally, but a closed series receives no rematch. Leave-versus-finish is serialized by the same series-row lock and produces one of the two valid complete outcomes. |
| M13.5 resignation | PASS | Either participant may resign regardless of turn. Expected-version, membership, finality, audit, realtime, and non-undoability rules match other terminal commands; an active series gets exactly one reversed-colour rematch and a closed series gets none. |

The old `close_after_current_game` column exists only in the immutable V1
migration and explanatory migration/documentation history; V7 drops it. No
current server implementation retains `markCloseAfterCurrentGame`,
`closeIfMarked`, or a `SeriesClosed` transition.

No fresh M13 production defect was found.

## Historical comparison

The retained `2026-09-17-e2b3287` M13 report passed the original implementation
while carrying then-open M8, M10, and M12 findings. Those findings do not remain
open in the current run: D053 removed friendship-driven series closure, D057
repaired coherent game reconstruction, and D058 isolated stalled realtime
recipients. More importantly, the historical M13.4 marked-to-close assessment
is no longer authoritative; the fresh evaluation tested the current D068
leave-immediately lifecycle, including mid-game leave, leave after a rematch,
idempotent/concurrent leave, and the leave-versus-game-ending race.

Historical comparison prompted a separate rerun of all 11 current
`DuplicateCommandTest` cases. They pass and confirm that retries of terminal and
nonterminal commands do not duplicate a finalization or rematch. No new
evaluator regression was warranted because current retained coverage directly
exercises each identified risk.

**Final M13 verdict: PASS.**
