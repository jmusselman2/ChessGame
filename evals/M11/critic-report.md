# M11 — Independent Evaluation: Critic Report

Baseline: `3de5e28dbcb29e5d86e163c39b26278686385c4c`

## Verdict

**PASS WITH CARRIED FINDINGS.** The complete M11 sweep found no new undo
defect. M8-01 through M8-03 and M10-01 remain unresolved. No production code
was changed.

## Requirement evaluation

- **M11.1 — UndoMove:** the server derives the caller's side, checks the
  expected version and terminal state, and delegates the exact unanswered-move
  rule to `game-core`. Only the last mover can undo; an opponent response locks
  the previous move; undoing that response exposes the prior move again; and no
  terminal move can be undone.
- An accepted undo restores the saved pre-move position and active history,
  increments the monotonic game version, rewrites persisted active moves in the
  same transaction, and appends `MoveUndone`. `GameView.canUndo` is computed by
  the same authoritative rule used by the command.
- **M11.2 — Move-vs-undo concurrency:** both commands acquire the game-row lock
  before reading the row and move history. The first valid transition advances
  the version; the second observes that version and returns `STALE_VERSION`.
  Retained repeated races prove the final position/history and audit trail
  contain only the winning transition and that the loser can continue from the
  returned state.

## Carried findings and impact

- M8-01 through M8-03 remain at the identity/friendship/series boundary and do
  not change the correctness of undo decisions inside an established game.
- M10-01 affects unlocked display and refresh loads. Undo command evaluation
  itself is not blocked: `undoMove` uses `loadForUpdate`, holding the row lock
  across its history read and decision, and its response reload occurs inside
  the same command transaction. A later ordinary `GET` can still exhibit the
  already-recorded torn refresh.

## Scope conclusion

All M11 requirements and acceptance criteria were reconciled against the
current D016/D017 decisions, `game-core` undo semantics, command service,
persistence, HTTP mapping, audit behavior, retained milestone tests, and the
later M16.7 row-lock regression. No additional evaluator regression was needed.
