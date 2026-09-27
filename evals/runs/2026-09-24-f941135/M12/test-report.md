# M12 Test Report

## Fresh verification

The initial focused verification and source assessment were completed before
any retained M12 report was read.

| Verification | Result |
| --- | --- |
| `RealtimeConnectionTest`, `GameUpdateBroadcastTest`, `ReconnectRecoveryTest`, `RealtimeFanOutTest`, `M12AdversarialTest`, and `WebSocketKeepAliveTest` | PASS - 31 tests, 0 failures, 0 errors, 0 skipped |
| Historical-comparison follow-up: `NewGameBroadcastTest` | PASS - 6 tests, 0 failures, 0 errors, 0 skipped |
| Focused aggregate | PASS - 37 tests, 0 failures, 0 errors, 0 skipped |
| Current requirements, hub/routes/configuration, publishers, topology, and Android caller inspection | PASS - no production defect found |
| Complete `build --continue --rerun-tasks` on the same production/evaluator tree | PASS - 134 tasks; game-core 394, Android 521, server 593; lint and both APK assemblies passed |
| `adb devices -l` | Kindle `G090MJ0574130HMR` (`KFDOWI`) connected; device interaction not applicable to this service/recovery-contract milestone |
| `git diff --check` | PASS |

The fresh 31 cases comprise 8 authenticated connection tests, 8 update
broadcast tests, 8 reconnect recovery tests, 5 fan-out isolation tests, the
retained stalled-recipient adversarial regression, and the server keepalive
configuration check. All PostgreSQL- and WebSocket-backed cases ran rather than
skipping.

After the historical report identified later new-game publication as part of
its retained selection, all 6 current `NewGameBroadcastTest` cases were added as
a justified comparison-phase follow-up. They cover participants, multiple
connections, onlookers, closed series, dashboard refresh, and duplicate offers.

The complete build was run on the identical production/evaluator tree before
only M11/M12 report and state documents changed. It already includes all server,
Android realtime, lint, and packaging coverage; the M12-specific suites were
then forced with `--rerun-tasks` after the M11 checkpoint.

## Historical follow-up

The retained red `M12AdversarialTest` now passes unmodified under D058. Current
fan-out coverage proves that one stalled socket cannot starve later recipients,
the publish returns at its deadline, healthy connections remain subscribed,
failures are isolated, and cancelling the publisher does not misclassify live
connections as dead. The previous reconnect-snapshot block from `M10-01` is
also closed by D057.
