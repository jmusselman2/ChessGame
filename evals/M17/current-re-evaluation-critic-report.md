# M17.3 and M17.5–M17.10 — Current Independent Re-evaluation: Critic Report

Evaluated `main`: `83de79ceed51577b9b8cffa38ca1b937e029537a`

## Scope and verdict

This evaluation reconciled the current backlog criteria, `D071`, `D073`,
`D074`, `D075`, architecture and product documentation with the integrated
implementation and its retained tests. M17.4 has no implementation and remains
intentionally blocked on the project owner's settings decision.

**Verdict: PASS for M17.3, M17.5, M17.6, M17.7, M17.8, M17.9, and the later
M17.10 addition.** No new product defect was found and no evaluator regression
or production change was needed.

## Individual dispositions

### M17.3 — PASS

The username is derived from `currentUser` and rendered only on the dashboard's
non-Back shell row. Startup, onboarding, game screens, and every pushed screen
omit it. The text is non-clickable, one line, and ellipsized inside the row's
remaining bounded width, so the three navigation controls retain their space.

### M17.4 — BLOCKED, not a defect

There is no settings implementation to evaluate and no product decision about
its contents. This evaluation did not invent one.

### M17.5 and M17.6 — PASS

`GET /users` is behind authentication and returns named users other than the
caller. It splits one 200-row budget: non-friends first and friends second,
with `last_seen_at DESC NULLS LAST` and newest-account tie-breaking inside each
group. Removed friends re-enter the non-friend query. Responses are constructed
as exactly `userId`, `username`, and `friend`; engagement timestamps do not
cross the API boundary.

Android uses the existing `befriend` path for exact lookup and All Users. The
page covers loading, two distinct empty presentations, unavailable/transport
failure and retry, refused Add, successful Add, Back-to-Friends, reopen/reload,
and friend rows without Add. Jobs from earlier visits are cancelled before a
fresh load, and only one Add is enabled at a time.

### M17.7 — PASS

`GameLayoutSpec` is pure and chooses from actual post-inset window dimensions.
It covers narrow, short, portrait, landscape, large, and large-font windows;
the two-pane 44 dp exception is the explicit `D073` owner decision, while
one-column boards retain the 48 dp floor except where width or height makes that
impossible. Board sizing and clipping, dp-based glyphs, input mapping in both
orientations, independent move/panel scrolling, and game-owned Back controls
are shared by local and online screens.

Local state belongs to `ChessAppViewModel`: activity recreation keeps it,
explicit Back discards it, and reopening starts a new game. Online finished
games remain read-only. The focused JVM rules and nine rendered Pixel 7 tests
passed, including 19 forced viewports and font scales through 2.0.

### M17.8 — PASS

The real shared Ktor/OkHttp client installs a 30-second `HttpTimeout` for
ordinary requests. Ktor WebSocket requests are exempt and remain governed by
the existing ping/reconnect policy. A real loopback peer that trickles a header
forever is terminated by `HttpRequestTimeoutException`; without the configured
limit the same test remains waiting. Never-finishing startup attempts terminate
and startup reaches `Ready` or retryable `Failed`; reloads retain `D037` wake
retry, while command code still reports a transport failure without resending.

The previously missing evidence is now present on the integrated baseline:
the aggregate build passed, and a fresh Pixel 7 debug installation configured
for the beta endpoint completed startup and the live local-game recreation
test. The exact obsolete network failure cannot be made to recur against the
healthy endpoint, so the real-client trickling-peer regression is the
deterministic proof of that boundary.

### M17.9 — PASS

Local and online prompts share `PromotionChoice`: a fixed 52 dp Material touch
target with a 32 dp glyph converted from dp to sp at the current density. All
four fit on one row in the 280 dp panel. Pixel instrumentation independently
verified the row, visibility, and a rendered glyph height of at least 28 dp
across the two-pane viewport set.

### M17.10 — PASS (current-baseline addition)

Although it landed after the requested M17.3/M17.5–M17.9 batch, it is part of
the pinned `origin/main` baseline and was evaluated too. A genuine non-rotation
stop arms one foreground refresh; first start, configuration recreation, and a
return before startup completes do nothing. A valid return immediately reloads
the visible dashboard/game, cancels the old socket loop, and starts a new one.
The four lifecycle regressions passed. The implementation track's two-device
timing was reviewed as corroboration; this evaluation did not repeat the full
two-account move-away-return scenario.

## Cross-cutting review

Cancellation and navigation ownership remain in the view model, not in
composition. The layout work changes no game-core rules, canonical online
state, server persistence, or navigation stack. The All Users testing aid is
consistent across `D071`, product, architecture, and removal notes. M17.8 and
M17.10 preserve the separation between HTTPS canonical reloads and WebSocket
nudges. No new dependency class or database migration was introduced by this
M17 tranche.
