# M19 — Current Implementation Re-evaluation: Critic Report

Evaluated `main`: `1f17312848a6d6c9499c0accbd7eb6401f44e657`

## Historical boundary

The original `evals/M19/critic-report.md` evaluated
`38be421dfd64c269687c893300f11e661bfa9c90`, before M19 implementation began.
Its `0 of 12` verdict remains true of that baseline and has not been rewritten.
This report evaluates the later implementation independently.

M19.1 is now M20.1 and still requires human architecture sign-off. The N >= 3
resignation and continuation part of M19.8 is now M20.2. This evaluation treats
both as future work, not M19 failures.

## Verdict

**DEFECT FOUND — ONE FINDING.** M19.2 through M19.12 were evaluated in order.
M19.2, M19.4–M19.6, and M19.8–M19.12 pass their current acceptance criteria.
M19.3 and M19.7 share one participant-identity defect. Production code was not
changed; one expected-red evaluator regression records the defect, and
evaluation continued through migrations, the full local suites, and the live
beta smoke.

## Finding

### M19-01 — Table identity drops participant kind when UUIDs coincide

`Participant` defines identity as `(kind, ref)`. V9 likewise gives user and
non-user references separate columns and constraints, and the canonical table
key serializes each identity as `KIND:ref`. Consequently `USER:x` and
`SCRIPTED:x` are distinct participants even when the UUID text `x` is the same.

`TableRepository.findOrCreate` contradicts that model twice. It rejects a
participant list when `participants.map { it.ref }` contains a duplicate, and
it canonicalizes seat order with `sortedBy { it.ref }`. The first operation
prevents a valid mixed-kind table from being created. If only that guard were
fixed, the second operation would still leave equal-ref ordering dependent on
input order and could produce two keys for the same exact set.

The schema permits this state intentionally: uniqueness is separate for
`user_id` and `non_user_participant_id`, a non-user row has its own kind-qualified
identity, and `participant_set` includes the kind. Random UUID collision is very
unlikely, but imports, fixtures, and future identity generation are not entitled
to rely on disjoint UUID namespaces when the accepted model explicitly uses the
kind as part of identity.

`M19ParticipantIdentityRegressionTest.participantKindIsPartOfExactSetIdentity`
creates a user and scripted participant with the same UUID, asks for their table
in both input orders, and expects one table containing both identities. It fails
at the ref-only duplicate guard. This is one finding spanning M19.3's exact-set
identity and M19.7's kind discriminator.

## Sequential requirement evaluation

### M19.2 — Groups: PASS

V3 adds `groups` and `group_members`; repository transactions enforce creator
membership and friend-of-adder membership; routes cover create, list, add, and
unilateral leave. `InviteEligibility` resolves friend or shared-group reach and
the retained tests cover immediate membership, non-friend refusal, transitive
membership, rejoin concurrency, and leave without game effects. D064 correctly
keeps the relationship test out of table creation under D046.

### M19.3 — Tables and participants: DEFECT FOUND (M19-01)

V5 migrates pairs into `game_types`, `tables`, `table_participants`, and
`game_participants`; chess is the sole 2–2 registration; range checks read the
registration; exact-set uniqueness and parallel callers are tested; existing
chess API behaviour remains covered. Clean and upgrade migrations preserve
users, friendships, series, games, moves, events, dashboard, and history.
M19-01 is the one exception to exact-set correctness.

### M19.4 — Parallel series: PASS

V6 removes the active-table uniqueness index. Play returns a 409 offer instead
of silently reusing, `another=true` starts a distinct series, and a table-row
lock preserves the double-tap guarantee. Server, Android, and live-beta checks
all exercised the offer/open/start-another behavior.

### M19.5 — Friendship-independent series: PASS

V7 removes `close_after_current_game`; friend removal writes only the friendship
and leaves every series and game intact. The superseded D013 tests were replaced
with opposite assertions rather than silently deleted. The beta smoke confirmed
an active rematch remained readable and listed after unfriending.

### M19.6 — Seat rotation: PASS

V8 persists the cycle per series as D066 requires for parallel series. The
property suite covers N = 2, 3, and 4, cycle fairness, consecutive rotation-family
exclusion, two-cycles-back eligibility, and scripted-final-turn placement. Chess
alternates sides across automatic rematches locally and on beta.

### M19.7 — Non-user participants: DEFECT FOUND (M19-01)

V9 provides kind-qualified user/non-user references and opaque persistent
non-user state. Person-shaped callers filter to user ids, rotation filters by
kind, and no deck-builder vocabulary enters the shared type. M19-01 violates the
same kind-qualified identity at the repository boundary.

### M19.8 — Chess resignation and explicit series exit: PASS

The evaluated scope is the chess-only scope after the recorded split. Chess
resignation still ends its game and starts a rematch in an active series.
Explicit leave closes the series, audits who left, leaves the current game
unchanged and reachable, and prevents a later rematch. N >= 3 continuation is
M20.2 and is not a defect here.

### M19.9 — Undo storage sizing: PASS

`docs/UNDO-STORAGE.md` records real chess measurements, modelled deck-builder
sizes, retained-snapshot and write-amplification results, and recommendations.
D061 adopts full snapshots with append/truncate and transactionally enforced
barriers while rejecting replay. This was an analysis task and correctly
changed no implementation.

### M19.10 — Per-viewer state identity: PASS

`docs/GAME-STATE-VISIBILITY.md` audits every state surface and links reciprocally
to the backlog and D069/D070. It assesses `GameView.of`, specifies allowlist
projection and hidden-state exclusions, covers history and spectators, and
changes payload identity to `(gameId, version, viewer)` while preserving
canonical version semantics. `CLAUDE.md` includes the required conditional
reading pointer.

### M19.11 — Engagement timestamps: PASS

V4 adds nullable, unbackfilled `last_login_at` and `last_action_at`. `/me`
records login without making engagement availability-critical; accepted game
mutations record action inside their transaction; refused commands and reads do
not. Wire-shape tests and the live `/me` response confirm no timestamp is
exposed.

### M19.12 — Failure logging: PASS

Realtime send failure and timeout, rejected credentials, route exceptions, and
socket lifecycle paths are logged at the documented levels. Forced-failure
tests verify a line is produced while credentials and hidden state are absent;
successful delivery and ordinary socket lifecycle stay quiet at operational
levels.

## Beta disposition

Render already auto-deployed the evaluated `main`. A cold wake returned build
`1f17312` after 54.329 seconds, outside health-only mode. The retained
`current-beta-smoke.ps1` passed the available HTTPS/WSS flow with three
throwaway accounts.

Direct beta-database inspection, readback of Render logs, a signed distributable
APK, and restoration of a session that predated this evaluation were not
possible: the host exposes no beta database credential, Render API credential,
or project signing key, and the available AVDs began without the app installed.
The endpoint-configured debug APK nevertheless installed and reached onboarding
on `ChessPlayer1`; a newly claimed beta session then survived a force-stop and
cold relaunch to the dashboard. The repository's disposable-key release-signing
verification also passed all six checks. The remaining limits do not block the
local migration-preservation proof, automated logging proof, or API same-token
session-reuse smoke.

## Remediation handoff

Use the complete `Participant` as the distinctness key, and use a total
canonical order containing both `kind` and `ref` when producing table seats and
`participant_set`. Keep the schema and regression unchanged. Once M19-01 is
green, M20.1 remains the next human decision; this evaluation does not choose a
repository or module architecture.
