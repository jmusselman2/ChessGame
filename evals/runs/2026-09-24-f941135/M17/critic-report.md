# M17 Critic Report

## Fresh assessment

**Fresh verdict: PASS**

This section was completed before consulting any retained M17 evaluation
report.

### Scope and current authority

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M17:
  `628a6e7d1293729075d7eff1e2b0ad5961bcd5c4`
- Requirements: `docs/BACKLOG.md` M17.1-M17.3 and M17.5-M17.13,
  current decisions D069-D078, implementation, schemas, callers, and retained
  tests. M17.4 does not exist.
- Verification: forced server and Android suites, fresh beta-signing proof,
  protected Kindle instrumentation, a disposable live-server emulator path,
  and read-only live database/advisor checks.

### Requirement assessment

| Requirement | Fresh result | Evidence summary |
| --- | --- | --- |
| M17.1 small beta distribution | PASS | The durable owner-recorded acceptance event remains in the authoritative backlog: a real person installed `0.1.2-beta`, completed onboarding, and played without developer intervention. That one-person event is not reproducible by an evaluator alone. Freshly, `scripts/verify-beta-apk.sh` proved the ordinary release remains unsigned, a supplied throwaway key produces a verifiable named beta APK, the HTTPS endpoint and cleartext policy are packaged correctly, incomplete signing configuration fails loudly, and the script restores the ordinary unsigned output. |
| M17.2 build naming | PASS | Server health includes the seven-character `RENDER_GIT_COMMIT` only when configured and preserves the exact old body otherwise; the dashboard uses `VERSION_NAME`. Ten deployment and 26 dashboard-section cases pass. The deployed `/health` returned 200 in 481 ms with `build 2fd13fa`, matching the live `origin/main` tip. |
| M17.3 home username | PASS | The dashboard shell receives the current user's server-owned username and renders it in the top row without adding it to other screens. Shell/chrome and complete app-wiring tests pass. |
| M17.5 all-users testing aid | PASS | The authenticated route is isolated, capped at 200, omits unnamed accounts and the caller, and returns only its documented fields. Android loading, empty, failure/retry, Add, Back, and reopen paths use the existing friend operation. |
| M17.6 friends in all-users | PASS | Non-friends precede friends, each recency ordered, and the cap fills with non-friends first. The `friend` flag, FRIENDS section, Friend/Added states, and two distinct empty messages are covered by the current six server and fourteen Android cases. |
| M17.7 window-safe game and rotation | PASS | The pure layout rule covers 19 windows; shell chrome is absent on game screens; local-game lifetime is view-model owned. On the physical API 22 Kindle, nine layout and four online-game tests passed across forced portrait/tall, landscape/wide, short-window, large-font, long-history, touch, active, loading/failure, and read-only states. A current live build on a disposable API 36 emulator also passed activity recreation with the game retained and Back starting a new game. |
| M17.8 bounded startup | PASS | The real client has a 30-second overall HTTP request timeout while WebSockets are exempt. Nine wake, 18 startup, two stalled-request, and two silent-socket cases pass, including a trickling peer and a request that never answers. |
| M17.9 promotion sizing | PASS | Local and online prompts share the fixed 52 dp choice with a 32 dp font-scale-independent glyph. Fresh Kindle instrumentation proved all four choices stay on one row in the narrow two-pane panel and the promotion prompt remains wholly visible. |
| M17.10 foreground refresh | PASS | A real background/foreground transition reloads the current screen, cancels and replaces realtime, while first start, rotation, and pre-ready return are guarded. All 19 interruption cases pass, including the four foreground boundaries. |
| M17.11 groups screen | PASS | Current client, pure-logic, flow, API, and server route suites pass. Fresh live evidence used a three-member group: the emulator account was marked You, exposed Play for a non-friend member, opened a new game, played `e2e4`, received `e7e5` live, and showed the game on its dashboard; the peer API dashboard showed the same version-2 game. Leaving removed the group, produced the required message, and left the game on both dashboards. |
| M17.12 indexes and timeouts | PASS | V10, index, connection-timeout, and lock-timeout tests pass. The live database reports successful Flyway V10; `friendships_user_b_id`, `moves_game_ply`, and `games_series_sequence` exist, while `moves_game_id` and `games_series_id` do not. The live performance advisor lists five other unindexed foreign keys and no `friendships_user_b_id_fkey`. Request connections enforce 30 s statement, 60 s idle-transaction, and 10 s lock limits while Flyway uses its separate uncapped pool; the locked-command regression returns 500, logs the database cause, preserves version 0, then succeeds once released. |
| M17.13 audit actor and series | PASS | Six route-driven audit cases cover move, undo, resignation, move-ended game, claim-ended game, rematch, leave, and ordered series history. The live database currently has 20 events across MoveMade, MoveUndone, DrawClaimed, PlayerResigned, GameEnded, and RematchCreated, with zero null actors and zero game events missing a series. SeriesLeft is covered locally; no live SeriesLeft row was required or manufactured. |

The beta service currently reports the same `2fd13fa` revision as live
`origin/main`. The signing verifier used a throwaway key and left only the
ordinary unsigned release APK. The live group accounts and game were evaluator
data; no production code or schema was changed.

No fresh M17 production defect was found.

## Historical comparison

The retained `2026-09-17-e2b3287` report evaluated only the M17.1-M17.2 scope
that existed at its `38be421` baseline. Its signing, build-label, live-health,
new-game broadcast, and real-person distribution conclusions agree with the
fresh result. The historical first-game broadcast suite was not in the initial
fresh selection, so all six cases were forced after comparison and pass: both
players are notified only for a newly created game, while an existing-series
offer emits no false creation event.

The retained report carried M10-01, M12-01, and M14-01 through M14-03. Those
findings are now closed by D057-D059 and their current regressions passed in
the fresh M14-M17 work. They are not carried forward.

M17.3 and M17.5-M17.13 were added after the retained report and therefore have
no old M17 verdict to reuse. Their current authoritative requirements were
evaluated directly above. Repository-wide inspection found no tracked APK,
bundle, keystore, private key, or keystore-properties file. The only three
non-evaluation-tree `M17.4` strings are governing statements that it does not
exist; no M17.4 requirement or implementation exists.

No additional evaluator regression is warranted. The retained broadcast
boundary, all current M17 regressions, physical/device paths, and live database
state are covered and passing.

**Final M17 verdict: PASS.**
