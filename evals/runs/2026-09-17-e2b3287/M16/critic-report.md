# M16 — Independent Evaluation: Critic Report

Production baseline: `38be421dfd64c269687c893300f11e661bfa9c90`

## Verdict

**PASS WITH CARRIED FINDINGS.** The complete M16 sweep found no new hardening
defect. Its retained server hardening checks pass, as do the reconnect,
restart, dead-socket, and backoff cases that originally closed M16. M10-01,
M12-01, and M14-01 through M14-03 remain unresolved; the three M14 client
ordering regressions are expected-red inside the M16 resilience test class.
No production code was changed.

## Requirement evaluation

- **M16.1 — network interruption:** failed game reads recover on reconnect,
  reload demand during a running read is queued, moving to a different game
  supersedes the prior read, a lost command response is recovered canonically
  without resending the command, socket reconnection waits through cold start,
  and waking/retryable/terminal states remain distinct. The broader client
  ordering failures M14-01 through M14-03 remain visible in the same harness;
  they are carried rather than assigned duplicate M16 identifiers.
- **M16.2 — app restart/reconnect:** a new view-model over the persisted session
  restores the same player and reloads server state, including moves received
  while away. Startup renews exactly once when `/me` returns `401`, while an
  unrecoverable second refusal terminates rather than looping (`D039`).
- **M16.3 — duplicate commands:** move, undo, draw-claim, and resignation
  duplicates are rejected through the expected-version contract, remain
  exactly-once at the HTTP boundary, and return canonical state a client can
  use after losing the first response.
- **M16.4 — series/rematch idempotency:** concurrent and repeated opens converge
  on one active series/game, an in-progress game is preserved, a finished game
  advances to its server-created rematch, and a friendship break produces a new
  series without reviving history. The row lock added after the milestone's
  original race was found is present and the concurrency cases pass.
- **M16.5 — server logging:** request logs retain method/path/status and command
  decision context while excluding authorization headers, tokens, bodies, and
  `/health` noise. Runtime logging is console-oriented for the hosting platform.
- **M16.6 — quietly dead socket:** OkHttp's engine-level ping interval, rather
  than Ktor's ineffective plugin setting for this engine, bounds silent-peer
  detection. End-to-end raw-peer coverage proves the flow terminates, and
  exponential reconnect delay reaches a 60-second cap so a sleeping free
  instance is not polled every few seconds (`D042`).
- **M16.7 — racing command read:** all mutating commands use
  `GameRepository.loadForUpdate`, so their row and move-history decision cannot
  straddle a competing write. Ordinary display loads remain unlocked as
  required. The deterministic in-flight-write regression passes.

## Carried findings and impact

- M10-01 still permits an unlocked read-only refresh to combine an older game
  row with newer history. M16.7 deliberately protects mutating reads only, so
  it does not close M10-01.
- M12-01 still permits one indefinitely stalled server-side recipient to block
  later realtime recipients and the originating command response. Client ping
  and reconnect hardening does not make server fan-out independent.
- M14-01, M14-02, and M14-03 still violate client response ordering while a
  game opens, a command response is delayed, or completion races an older
  dashboard fetch. Their evaluator tests account for all failures in the M16
  Android selection; the other 23 selected cases pass.

## Scope conclusion

Every M16.1–M16.7 criterion was reconciled against the accepted decisions,
current implementation, retained regressions, milestone history, and later
callers. The expected-red earlier findings remain genuine, but no smallest new
M16 regression was warranted because no distinct defect was found.
