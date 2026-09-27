# M14 Critic Report

## Fresh assessment

**Fresh verdict: PASS**

This section was completed before consulting any retained M14 evaluation
report.

### Scope and current authority

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M14:
  `2fd13fa16e13d3af6d8d39bb9e123d6add9878ea`
- Requirements: `docs/BACKLOG.md` M14.1 through M14.18, current decisions,
  architecture, implementation, schemas, callers, and retained tests
- Production implementation: Android shell, session and onboarding flows,
  dashboard, friends, history, canonical online-game presentation and
  commands, realtime reloads, completion/rematch flow, and server endpoints
- Verification: complete Android JVM suite, focused server/API suites, Android
  instrumentation, live beta health, a fresh two-client play-through, a local
  terminal-game play-through, process-death session restore, source review,
  and device discovery

### Requirement assessment

| Requirements | Fresh result | Evidence summary |
| --- | --- | --- |
| M14.1-M14.4 dashboard and history | PASS | Current DTOs, grouping, turn state, friends, history authorization and result presentation passed Android and server verification. The live dashboards showed the correct YOUR TURN/THEIR TURN state, and history showed the finished draw and resignation with colour, result, and move count. |
| M14.5-M14.9 shell, session, onboarding, friends, and landing | PASS | Two fresh anonymous clients completed username onboarding, added a mutual friendship, started a series, and reached the authenticated dashboard. Force-stop/restart restored the same anonymous account directly to its dashboard. |
| M14.10-M14.12 canonical game, moves, and realtime | PASS | The clients loaded one canonical game, submitted legal moves to the server, and received opponent state through websocket-triggered reloads without manual refresh. The full interruption/recovery suite and live-auth tests passed. |
| M14.13 undo | PASS | The opponent's unanswered move exposed Undo; acceptance returned both clients to the initial position at version 2. |
| M14.14 draw claim | PASS | A threefold sequence exposed the claim only to the eligible player and both clients converged at version 13 on `Drawn by threefold repetition claim`. |
| M14.15 resignation | PASS | Resignation was available off-turn. Cancelling preserved version/history; confirmation ended game 2 at version 3 with the correct win/loss text. |
| M14.16 completion and rematch | PASS | Each terminal result produced exactly one next game, reversed colours, and refreshed both dashboards. Game 3 showed the correct opposite turn labels. Current D053/D068 explicit-leave authority, rather than the removed friendship/close-after-current lifecycle, governs series exit. |
| M14.17 history and read-only review | PASS | Completed games were reachable from history and reconstructed their final board and move list without Undo, Resign, draw-claim, or next-game controls. The remaining Leave series action operates on the series, not the reviewed game. |
| M14.18 end-to-end Android verification | PASS | A fresh two-client API 36 play-through covered auth, friendship, series creation, realtime moves, undo, threefold claim, resignation, two automatic rematches, history/review, and session restore. A separate local Fool's mate proved terminal lockout. Protected Kindle instrumentation covered orientation, large-font, scrolling, hit targets, promotion, local checkmate lockout, draw controls, and active/read-only online layouts. |

The live beta health endpoint answered 200 and identified build `35da49a`.
Both throwaway clients used that HTTPS endpoint. No production file, deployed
service, persistent device account, or owner-app data was changed by the
evaluator.

The first emulator attempts encountered package-manager stalls, a Pixel
Launcher ANR, and one offline wiped instance. Those were environment failures,
not application failures. Repeating the evidence on two simultaneous read-only
API 36 instances completed the required path, and their overlays were then
discarded.

No fresh M14 production defect was found.

## Historical comparison

The retained `2026-09-17-e2b3287` M14 report identified three deterministic
client ordering defects:

- `M14-01`: a matching realtime update could be dropped while its game first
  opened;
- `M14-02`: a delayed command response could overwrite a newer game view; and
- `M14-03`: an older dashboard read could erase a discovered rematch.

D059 is now authoritative. The current view model tracks the shown game across
loading, ready, and failed states; installs same-game views only forwards by
version; and numbers dashboard reads so only the freshest response installs.
The retained exact interruption regressions and stale-response-ordering test
all pass. The complete 521-test Android suite also contains these regressions
and passes without skips.

The historical report's older 416-test count, stale M14.18 summary, and open
findings describe its evaluated baseline rather than the current production
tree. The fresh run re-enacted M14.18 instead of adopting the old device record.
No additional evaluator regression was justified because the retained tests
directly preserve each historical failure boundary.

**Final M14 verdict: PASS.**
