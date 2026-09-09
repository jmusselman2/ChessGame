# M13 — Independent Evaluation: Critic Report

Baseline: `3de5e28dbcb29e5d86e163c39b26278686385c4c`

## Verdict

**PASS WITH CARRIED FINDINGS.** The complete M13 sweep found no new game-end,
rematch, colour, close, or resignation defect. M8-01 through M8-03, M10-01,
and M12-01 remain unresolved. No production code was changed.

## Requirement evaluation

- **M13.1 — Transactional finalization:** the guarded game write persists the
  terminal state, result, termination reason, first `endedAt`, active move
  history, command audit, and one `GameEnded` event atomically. The game-row
  lock and version advance make duplicate/racing terminal commands settle once.
- **M13.2 — Exactly-one next game:** after a completed command, the same outer
  transaction locks the series row. Only an active series still pointing at the
  finished game creates the next sequence and repoints `currentGameId`, so
  retries and concurrent settlement cannot create a second rematch.
- **M13.3 — Alternate colours:** the rematch takes White from the finished
  game's Black player and vice versa, preserving alternation independently of
  the random first-game assignment.
- **M13.4 — Close without rematch:** the locked settlement branch closes a
  marked series instead of creating a game, keeps the finished game as the
  current historical endpoint, preserves the first close time, and audits the
  closure once. A fresh series can later be opened for the pair.
- **M13.5 — Resignation:** either participant may resign regardless of turn;
  the expected-version guard applies, the result is final and non-undoable, and
  the same active-versus-closing settlement path creates the rematch or closes
  the series.

## Carried findings and impact

- M8-03 can leave an unmarked active series after friendship removal. M13 then
  correctly follows the state it was given and creates a rematch, but that
  series should have been marked earlier; the cross-boundary outcome remains
  attributable to M8-03.
- M10-01 affects unlocked display/refresh reconstruction, not the locked
  command/finalization transactions used to decide M13 outcomes.
- M12-01 can delay or suppress the post-commit realtime nudge and originating
  HTTP response behind a stalled socket. It does not roll back or duplicate the
  already-committed terminal transition and is carried separately.
- M8-01 and M8-02 remain unresolved without introducing an additional M13
  transition defect.

## Scope conclusion

All M13 requirements and acceptance criteria were reconciled against
`GameRepository.save`, `GameCommandService`, `SeriesService`, series locking,
schema uniqueness, audit payloads, HTTP resignation mapping, `game-core`
resignation semantics, retained milestone tests, and later end-to-end
idempotency callers. No additional evaluator regression was warranted.
