# M9 Critic Report

## Fresh assessment

**Fresh verdict:** REMEDIATION REQUIRED

This section was written before consulting any retained M9 evaluation report.

### Scope and current authority

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M9: `88239f1a120e328e1707f044da03e50a1ed6a6a1`
- Requirements: `docs/BACKLOG.md` M9.1 through M9.4, interpreted through
  current D046, D048, D050, D052, D053, D063-D068, PRODUCT, and ARCHITECTURE
- Production implementation: authenticated series/dashboard routes,
  `SeriesService`, series/table/game repositories, dashboard queries, schema
  migrations V1 and V5-V9, API models, and Android callers
- Verification: focused server and Android suites, current source/schema/caller
  audit, direct PostgreSQL catalog inspection, and device discovery

The original one-active-series and close-after-current text is superseded.
Current M9 authority requires: Play starts the first series/game or returns a
409 offer of existing active series; `another=true` deliberately starts a
parallel series; series creation has no friendship gate; initial colors are
random; explicit leave closes the series immediately without touching its last
game; and the dashboard efficiently lists active series plus a closed series
whose last game is still unfinished.

### Requirement assessment

| Requirement | Fresh result | Evidence summary |
| --- | --- | --- |
| M9.1 start/open series | FAIL | Table-row locking, exact-set identity, 201 creation, newest-first 409 offers, `another=true`, parallel series, no-friendship creation, validation, authentication, and concurrent first taps pass. However, a nameless authenticated caller can create a one-sided series the named participant cannot discover (`M9-U01`). |
| M9.2 initial game | PASS | Series creation and game 1 attachment are one transaction; the standard position starts at version 0 with White to move; the two users occupy opposite seats; an injectable random choice determines initial White/Black; and repeats do not create a second first game. |
| M9.3 current lifecycle | PASS | The obsolete close-after-current mechanism and column are gone. Explicit leave row-locks and closes the series once, records the actor, leaves the current game untouched and playable, suppresses later rematch creation, is idempotent, and serializes correctly against game completion. |
| M9.4 dashboard discovery | FAIL | For named participants the bounded four-query loader returns every current series/game, side, turn, move number, version, and active flag without private fields; closed series remain only while their last game is unfinished. `M9-U01` demonstrates a persisted current game omitted from the named participant's dashboard solely because the creator is nameless. |

The PostgreSQL catalog confirms the current lifecycle constraints, exact table
set uniqueness, series/table/current-game foreign keys, per-series game sequence
uniqueness, participant uniqueness, and indexes supporting the table, series,
game, and participant access paths. The dashboard batches tables, seats, and
opponents instead of issuing a query per series. The catalog also reports a few
foreign-key columns without a leading index; none is on a current M9 lookup or
delete path, and no acceptance or performance failure reproduced from them.

### M9-U01 - Nameless caller can create a one-sided invisible series

Authentication creates an internal user before that user claims a username.
`POST /series` validates the requested opponent but never requires the caller to
have a name. A fresh authenticated subject can therefore post `Alex`, receive
201, and commit both an active series and its initial game.

The other side cannot use the result. `GET /dashboard` resolves the nameless
caller as Alex's opponent, then `DashboardEntry.of` drops the row because no
public `UserSummary` can be built. Other series/game response paths require the
opponent summary to exist. The database consequently contains a live game Alex
did not ask for and cannot discover from the dashboard.

The Android onboarding flow normally keeps a nameless user away from Play, but
the server treats the client as untrusted. D046's one narrow exception removes
only the friendship/acquaintance check at series creation; it does not authorize
the client to bypass the public-identity invariant. The same invariant is
already enforced for friendship creation by D045.

The retained evaluator regression
`M9AdversarialTest.aNamelessCallerCannotStartAOneSidedInvisibleSeries` proves the
failure without prescribing an exact refusal status: after the request, Alex's
dashboard is empty, but one series and one game exist and the response is
successful. Remediation should refuse the creation before any table, series, or
game mutation unless both participants have renderable public identities.

No other fresh M9 production defect was found.

## Historical comparison

The retained `2026-09-17-e2b3287` M9 evaluation passed 54 series tests while
carrying its earlier `M8-01` finding. At that baseline, `POST /series` required
a friendship, so a nameless caller could reach series creation only through the
already-defective nameless-friendship path. The evaluator therefore reasonably
classified the downstream invisible dashboard row as the same M8 defect rather
than a separate M9 defect.

Current authority and implementation changed that topology. D045 now prevents
nameless friendship creation, while D046 intentionally removes the friendship
gate from `POST /series`. A newly authenticated nameless caller can therefore
reach series creation directly. `M9-U01` is a distinct current M9 boundary
failure, not a duplicate of the closed historical M8 finding. The historical
54-test selection is contained within the fresh 158-test server selection and
all of those retained behaviors still pass.

## Claude remediation handoff

Work only on `claude-autopilot`; do not edit production code on
`codex-autopilot`.

- Finding: `M9-U01` - a nameless authenticated caller can create a one-sided
  invisible series.
- Reproduction: run
  `./gradlew :server:test --tests "*M9AdversarialTest" --rerun-tasks` (or the
  Windows wrapper equivalent). Current behavior is POST 201, one persisted
  series, one persisted game, and an empty dashboard for the named opponent.
- Production boundary: `SeriesRoutes.kt` obtains `authenticatedUser()` and
  validates only the requested opponent before invoking `SeriesService`.
  `UserRepository.find(caller.userId)` can resolve the caller's stored public
  identity.
- Required invariant: reject the request before any table, series, or game
  mutation unless the caller and requested opponent both have renderable public
  identities. Preserve all current M9 semantics: no friendship requirement,
  409 newest-first existing-series offers, `another=true` parallel creation,
  randomized first colors, and explicit leave behavior.
- Production proving test: add or update the smallest route-level test that
  pins the chosen refusal status/body and proves zero series and game rows. Do
  not weaken or delete `M9AdversarialTest`.
- Verification: rerun `M9AdversarialTest`, the focused series/dashboard/schema
  selection, and the complete build. Update authoritative decision/API text if
  the selected public refusal contract is new.

After the remediation is committed and pushed on `claude-autopilot`, return to
the evaluator workflow for a fresh M9 re-evaluation. Do not begin M10 until the
M9 passing checkpoint is pushed and the live remote ref is verified.

**Final M9 verdict: REMEDIATION REQUIRED.**
