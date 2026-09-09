# M10 — Independent Evaluation: Critic Report

Baseline: `3de5e28dbcb29e5d86e163c39b26278686385c4c`

## Verdict

**DEFECT FOUND.** The complete M10 sweep found one new canonical-read defect.
M8-01 through M8-03 also remain unresolved. No production code was changed.

## Finding

### M10-01 — A refresh can combine an old game row with new move history

`GameRepository.load` reconstructs one `StoredGame` with two SQL statements in
a default PostgreSQL `READ COMMITTED` transaction: it first reads `games`, then
reads the matching `moves`. Each statement receives a new snapshot. If a move
commits after the first statement but before the second, the load returns the
old version and old position together with the newly committed history.

The deterministic evaluator regression pauses the reader immediately before
its history query, after its game-row query has completed. A writer commits
`e2-e4`, then the reader resumes. The returned object reports version 0 and the
initial position but carries the new move in its history. This is not any
committed canonical state.

The same repository load serves `GET /games/{gameId}` and supplies the canonical
game attached to command rejections. M10.2 promises that a stale client can
refresh from that state; during a concurrent accepted command, that promise is
false. Later `M16.7` correctly changed mutating commands to `loadForUpdate`, so
command decisions no longer consume a torn read, but display/refresh reads still
use the unprotected two-statement path.

A remediation should make the row and history reconstruction share one stable
snapshot (or one atomic query/representation) without turning ordinary display
reads into unnecessary writers. The regression should then return either the
entire pre-move state or the entire post-move state, never a hybrid.

## Requirements that passed

- `MakeMove` authenticates, checks participant/version/game/turn, delegates
  legality and application to `game-core`, performs a guarded transactional
  save, increments the version, and audits only accepted commands.
- Version conflicts at both pre-check and guarded-write boundaries return a
  machine-readable stale response. The later row-lock correction prevents
  mutating commands from deciding against split row/history snapshots.
- `ClaimDraw` derives availability from canonical chess state, distinguishes
  claim types, finalizes accepted claims, and refuses invalid, stale,
  non-participant, wrong-turn, and already-finished requests.
- The two-client HTTP flow enforces alternating authoritative turns, visibility,
  participant isolation, and terminal-game refusal.

## Carried findings

M8-01 through M8-03 remain unresolved. M8-01 can also make opponent-name
materialization fail or disappear in later game views; M8-03 can authorize a
series from a stale friendship check. They do not account for M10-01, which is
independently reproducible between a valid pair's game row and move history.

## Scope conclusion

All M10 requirements and acceptance criteria were reconciled against
`GameCommandService`, `GameRepository`, HTTP request/response mapping, database
transactions, retained milestone and later concurrency tests, implementation
history, and the relevant M11–M14 callers. M10-01 is the complete new finding
batch for this milestone.
