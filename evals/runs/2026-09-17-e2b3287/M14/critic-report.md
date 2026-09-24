# M14 — Independent Evaluation: Critic Report

Baseline: `3de5e28dbcb29e5d86e163c39b26278686385c4c`

## Verdict

**DEFECT FOUND.** The complete M14 reconciliation found three independent
client-side response-ordering defects. Each has a deterministic evaluator
regression in `NetworkInterruptionTest`. M8-01 through M8-03, M10-01, and
M12-01 remain unresolved. No production code was changed.

## Findings

### M14-01 — A realtime update is lost while its game first opens

`onRealtimeMessage` identifies the visible game only from a `Ready` state. A
game that is still in `Loading(gameId)` therefore makes its own `game-updated`
message look like an update for another game: the dashboard is refreshed, but
the in-flight game read is not queued for a second pass. If that read was
already decided before the opponent's move, it installs the older version and
the open game remains stale until some unrelated refresh occurs.

The regression holds the initial game response after it has captured version
1, applies an opponent move at version 2, delivers the matching realtime
message, and releases the read. Expected version 2; the client remains at
version 1.

### M14-02 — A delayed command response can regress a newer game view

Command and reload jobs run independently, and `show` accepts every returned
view without comparing its version with the view already displayed. A move
response can therefore be delayed after the server commits it, a realtime
reload can install a still newer opponent move, and the old command response
can then overwrite that canonical view. This visibly removes the opponent's
move and can restore controls calculated for an obsolete position.

The regression holds the reply for the player's move at version 2, advances
the server with the opponent's move, observes the realtime reload reach version
3, and then releases the old response. Expected version 3; the screen regresses
to version 2.

### M14-03 — An older dashboard read can erase the post-game rematch

Ordinary dashboard loads serialize refresh demand with
`dashboardReloadWanted`, but `followSeries` replaces `dashboardJob` directly
and bypasses that mechanism. When an older dashboard request is still in
flight, a terminal game refresh can start a second request that sees the new
automatic rematch. The older untracked request can finish last and overwrite
the dashboard with the pre-rematch response.

The regression holds a dashboard response containing no series, completes the
game, lets the completion refresh discover the follow-up series, and then
releases the older response. Expected one series; the dashboard is erased to
zero entries.

## Requirement evaluation

- **M14.1–M14.4 — dashboard and history surfaces:** DTO mapping, turn
  partitioning, friend actions, history authorization, result presentation,
  and read-only game reconstruction are present and their retained component
  and server tests pass. The dashboard's response-order guarantee is defective
  at the M14.16 boundary as M14-03 describes.
- **M14.5–M14.9 — shell, session, onboarding, friends, and landing:** the
  navigation graph, DataStore-backed anonymous-session lifecycle, username
  flow, friend discovery/removal, and authenticated dashboard routing reconcile
  with the stated requirements and later recovery tests.
- **M14.10–M14.15 — canonical game and commands:** canonical rendering,
  server-authoritative move/undo/draw/resignation commands, refusal recovery,
  and websocket-triggered HTTPS reloads are implemented. M14-01 and M14-02
  violate the canonical-state guarantee under legal request orderings.
- **M14.16 — completion and rematch:** terminal presentation and next-game
  discovery are implemented, but M14-03 can leave the dashboard behind the
  server after the rematch is created.
- **M14.17 — history and review:** history remains reachable and completed
  games render without live command controls; the relevant server slice passes.
- **M14.18 — two-client verification:** the milestone record contains the
  completed 2026-08-31 two-emulator play-through, including auth, friendship,
  three games, realtime, undo, draw claim, resignation, rematch, history,
  canonical database comparison, local Fool's mate, and process-death session
  restore. The same three named AVDs remain installed; no emulator was running
  during this independent checkpoint, so that historical device evidence was
  reconciled rather than re-enacted. Current debug APK assembly and Android
  lint both pass.

## Record consistency

The milestone summary above M14.1 is stale: it still says M14.18 is incomplete
because no AVD was installed. The later authoritative M14.18 section says
`DONE`, names the two emulators used, and records the completed play-through;
the SDK currently lists those AVDs as well. Its evidence commit,
`6b6682f786505d152d4c944bc93ee507e7a0b5ab`, is an ancestor of the evaluated
baseline. This is a documentation
inconsistency, not a separate runtime finding, and the detailed completion
record plus its implementation history were used for reconciliation.

## Carried findings and impact

- M8-01 through M8-03 remain defects in friendship/series concurrency and are
  not reclassified as Android defects.
- M10-01 means an HTTPS reload itself can receive a mixed repository snapshot;
  M14-01 and M14-02 are separate client ordering failures that occur even when
  every individual server response is internally canonical.
- M12-01 can stall the realtime notification and originating command response.
  M14-02 shows the client also mishandles an old response if it eventually
  arrives after a newer reload; neither finding subsumes the other.

## Remediation handoff

Track the identity of the game being displayed through loading and failure
states, and route a matching update through `loadGame`. Make all installations
of a same-game view monotonic by version (including successful and refused
command responses). Finally, give completion refreshes the same single-owner,
queued dashboard-load discipline as `loadDashboard`; do not replace an active
dashboard job with an uncoordinated fetch.
