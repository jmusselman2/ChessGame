# M16 Critic Report

## Fresh assessment

**Fresh verdict: PASS**

This section was completed before consulting any retained M16 evaluation
report.

### Scope and current authority

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M16:
  `18dad2f3f1aca8abad86b3ec66103b8462605561`
- Requirements: `docs/BACKLOG.md` M16.1 through M16.7, current D021, D037,
  D039, D042, D053, D057-D059 and D065 authority, implementation, schemas,
  callers, and retained tests
- Verification: forced Android interruption/restart/dead-socket tests, forced
  server duplicate/idempotency/logging/race tests, current source and caller
  review, the complete build on the same production tree, and device discovery

### Requirement assessment

| Requirement | Fresh result | Evidence summary |
| --- | --- | --- |
| M16.1 network interruption | PASS | Failed and in-flight game/dashboard reads recover through queued canonical reloads; a different game supersedes the old read; lost command replies recover without resending; cold-start waiting and waking/retryable/terminal states remain distinct. Nineteen interruption cases pass, including the three old M14 response-order failures. |
| M16.2 app restart/reconnect | PASS | A new view model over the persisted session restores the same account, dashboard, current game, moves received while away, and realtime connection. Startup renews a refused or expiring token once and stops after a fresh token is also refused. Nine restart cases pass; the fresh M14 process-death check also restored the live dashboard. |
| M16.3 duplicate commands | PASS | Repeated move, undo, draw claim, and resignation commands remain exactly once through expected-version writes and return usable canonical refusal state. The same move at the new version remains a distinct legal command. All 11 API-boundary cases pass. |
| M16.4 series/rematch idempotency | PASS | Current D053/D065 authority permits parallel series only through the explicit `another` choice. Repeated default Play offers the existing series without creating anything; concurrent first-game opens serialize; active games and rematches are preserved; explicit parallel creation gets its own game. All 13 current cases pass. |
| M16.5 server logging | PASS | Request and command-decision context is present while bearer tokens, authorization headers, bodies/board state, and health noise remain absent. Console-oriented logging is retained. All six capture-based cases pass. |
| M16.6 quietly dead socket | PASS | OkHttp's 30-second engine ping bounds a peer that stops answering without closing. The raw-socket regression proves detection, its control proves the same peer remains stuck without keepalive, and reconnect delay backs off exponentially to a 60-second cap while resetting after useful traffic. |
| M16.7 racing command read | PASS | All four mutating commands use `loadForUpdate`; their game row and move history cannot straddle a competing write. Ordinary display loads remain unlocked. Six move-versus-undo cases, including the deterministic in-flight-write boundary, pass. |

Read-only recovery continues through `withServerWake`; mutating commands still
make one attempt only. Realtime messages remain nudges rather than state, so a
reconnect or foreground return reloads canonical HTTPS state. D057, D058, and
D059 preserve coherent reads, independent server fan-out, and forward-only
client installation around these M16 paths.

No fresh M16 production defect was found.

## Historical comparison

The retained `2026-09-17-e2b3287` M16 report passed M16 while carrying
`M10-01`, `M12-01`, and `M14-01` through `M14-03`. Those findings are now
closed by D057-D059. In particular, the historical Android selection's three
expected failures now pass inside the expanded 19-case
`NetworkInterruptionTest`.

The historical M16.4 description predates D053/D065. Its old one-series rule is
not reused: the fresh evaluation tested the current offer-first default plus
explicit parallel-series choice, including concurrent first-game creation and
rematch preservation. The current suite has 13 cases rather than the old ten.

No new regression was warranted. The exact historical failure boundaries and
the later M16.6/M16.7 corrections are already retained deterministically and
all pass under forced reruns.

**Final M16 verdict: PASS.**
