# M12 — Independent Evaluation: Critic Report

Baseline: `3de5e28dbcb29e5d86e163c39b26278686385c4c`

## Verdict

**DEFECT FOUND.** The complete M12 sweep found one new realtime fan-out defect.
M8-01 through M8-03 and M10-01 remain unresolved. No production code was
changed.

## Finding

### M12-01 — One stalled socket blocks every later realtime recipient

`RealtimeHub.publish` walks user ids and each user's connections sequentially,
awaiting `connection.send` before visiting the next recipient. It drops a
connection only after `send` throws; there is no timeout, child coroutine, or
other isolation for a send that remains suspended under backpressure or a
half-open transport.

The deterministic evaluator regression registers a connection for the first
user whose send suspends indefinitely, followed by a reachable connection for a
second user. Once the first send is known to have started, the second user never
receives the update. The interleaving does not depend on connection-set order
because the ordered `userIds` collection puts the stalled user's independent
bucket first.

This contradicts M12.2's fan-out outcome and the documented best-effort
boundary: a failed recipient should be dropped without preventing another
player from hearing about a move. It also affects command availability because
game routes await `realtime.announce` before sending the HTTP response. The
database command has already committed, yet one stalled client can withhold the
response and all later notifications, prompting retries that then look stale.

Remediation should isolate or bound per-connection sends while preserving
best-effort semantics and removing failed connections. It must also preserve
structured cancellation rather than treating cancellation of the publishing
request as an ordinary dead-socket exception.

## Requirements that passed

- The WebSocket route authenticates before session registration, supports
  multiple sockets per user, registers before its connected greeting, and
  unregisters on normal close. Ping and pong timeouts are configured.
- Accepted move, undo, draw-claim, and new-game events carry only game id and
  version to the participating users; refused commands and unrelated onlookers
  receive nothing. Commands persist with no listener present.
- Reconnection intentionally replays no event backlog, and dashboard/game HTTPS
  reads provide the recovery path for missed nudges.

## Carried findings and blocked validation

M12.3's claim that the canonical HTTPS reload cannot leave a game in an
incorrect position is **BLOCKED BY M10-01**: the unlocked repository load can
return an old row combined with newly committed move history. Reconnection,
absence of replay, stale-command refusal, and subsequent retry remain
independently covered, but the refresh snapshot itself is not reliable under
the interleaving already proved by `M10AdversarialTest`.

M8-01 through M8-03 remain unresolved at earlier identity/friendship/series
boundaries and are not the cause of M12-01.

## Scope conclusion

All M12 requirements and acceptance criteria were reconciled against the hub,
WebSocket route and configuration, command/new-game publishers, Android-facing
message contract, retained reconnect tests, relevant later callers, and the
carried canonical-read defect. M12-01 is the complete new finding batch.
