# M12 Critic Report

## Fresh assessment

**Fresh verdict: PASS**

This section was completed before consulting any retained M12 evaluation
report.

### Scope and current authority

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M12:
  `01641002aee13717a414972ef94a66009237bc5a`
- Requirements: `docs/BACKLOG.md` M12.1 through M12.3, ARCHITECTURE realtime
  topology, and D022, D032, D042, D057, D058, and D075
- Production implementation: authenticated WebSocket routing/configuration,
  `RealtimeHub`, game/new-game publishers, canonical HTTPS reads, Android
  socket/reconnect/reload callers, and current deployment constraints
- Verification: focused connection/broadcast/reconnect/fan-out/adversarial/
  keepalive suites, source/caller/topology audit, the complete build on the same
  production tree, and device discovery

### Requirement assessment

| Requirement | Fresh result | Evidence summary |
| --- | --- | --- |
| M12.1 WebSocket connection | PASS | `/ws` authenticates before registration, supports multiple sockets per user, registers before the `connected` greeting, unregisters on normal/exceptional close, and configures server ping/pong timeouts. Android obtains a current token at connect time and its own OkHttp ping detects half-open sockets. |
| M12.2 publish game updates | PASS | Accepted moves, undo, claims, resignations/rematches, and newly relevant series/game changes nudge only the participating users with game id and version. Refused commands and onlookers receive nothing. Per-connection concurrent sends, individual deadlines, failed-recipient removal, and preserved structured cancellation prevent one socket from blocking another or indefinitely withholding the command response. |
| M12.3 reconnect recovery | PASS | The server replays no backlog and subscribes before greeting, closing the reconnect gap. Android treats `connected` and `game-updated` only as reload triggers, reconnects with backoff, replaces an untrusted background socket on foreground, and installs canonical HTTPS responses in version order. Stale commands return recoverable canonical state. |

The process-local in-memory hub is deliberately constrained to the single
Render Free beta instance. Current architecture explicitly requires shared
pub/sub before any multi-process topology; sticky sessions alone are not
accepted as sufficient. This is a documented deployment boundary rather than a
hidden M12 failure.

No fresh M12 production defect was found.

## Historical comparison

The retained `2026-09-17-e2b3287` M12 evaluation found `M12-01`: fan-out awaited
connections sequentially, so a socket whose send suspended forever prevented
later recipients from receiving the nudge and withheld the HTTP response to an
already-committed command.

D058 is present in the current implementation and closes that exact path.
`RealtimeHub.publish` snapshots all recipients, launches one child coroutine per
connection, gives each its own configurable timeout, drops only the failed or
timed-out connection, and rethrows genuine request cancellation. The retained
`aStalledConnectionCannotPreventAnotherUserReceivingTheUpdate` regression
passes unmodified. Five current `RealtimeFanOutTest` cases cover timeout return,
simultaneous reachability, ordinary failure isolation, cancellation, and
logging behavior.

The historical report also marked the correctness of reconnect HTTPS snapshots
as blocked by then-open `M10-01`. D057 has since repaired that split row/history
read, its retained regression passed during M10, and the fresh M12 reconnect
suite passes. Historical comparison prompted an additional rerun of all 6
`NewGameBroadcastTest` cases; they pass and confirm that lifecycle-created games
reach the same participant-only nudge/reload path.

**Final M12 verdict: PASS. Historical `M12-01` remains closed.**
