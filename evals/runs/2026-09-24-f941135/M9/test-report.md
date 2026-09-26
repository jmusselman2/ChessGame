# M9 Test Report

## Fresh verification

This verification and the `M9-U01` finding were recorded before consulting any
retained M9 report.

| Verification | Result |
| --- | --- |
| All `server.series` and `server.dashboard` tests plus `InitialSchemaTest` and `TableRepositoryTest` | PASS - 158 tests, 0 failures, 0 errors, 0 skipped |
| `ChessApiClientTest`, `ChessAppTest`, and `DashboardSectionsTest` | PASS - 179 tests, 0 failures, 0 errors, 0 skipped |
| `M9AdversarialTest` | EXPECTED FAIL - 1 test, 1 failure, 0 errors, 0 skipped; POST returned 201 and persisted an invisible series/game |
| Direct PostgreSQL catalog inspection | PASS - lifecycle, identity, foreign-key, uniqueness, and access-path constraints match the current schema |
| Current requirements, source, migrations, API models, and caller inspection | DEFECT CONFIRMED - series creation omits the caller-public-identity gate |
| Complete `build --continue --rerun-tasks` | EXPECTED FAIL - game-core 394/394 and Android 521/521 passed; server ran all 591 tests with only `M9AdversarialTest` failing; 134 tasks executed |
| Corrected `:server:ktlintTestSourceSetCheck --rerun-tasks` | PASS |
| Corrected `M9AdversarialTest` rerun | EXPECTED FAIL - 1 test, 1 production assertion failure, 0 errors, 0 skipped |

The 158 focused server tests comprise 124 series/dashboard cases and 34 schema
and table-identity cases. They cover concurrent first Play, offers and deliberate
parallel series, atomic initial game creation, random colors, automatic
rematches, explicit leave and its races, table identity, dashboard contents,
privacy, authentication, and the current schema.

The 179 Android tests comprise 42 API-client, 111 app orchestration, and 26
dashboard-section cases. They confirm the current clients understand 201, 409
offers, start-another, series exit, and dashboard data. They do not excuse the
server boundary because a non-first-party authenticated client can call it.

### Evaluator/environment corrections

- The first catalog constraint query ordered by a display alias PostgreSQL did
  not recognize in that context. The corrected query ordered by the underlying
  expression and completed successfully.
- The catalog's generic missing-foreign-key-index query reports composite-key
  components individually and therefore over-reports `kind` columns. Its output
  was treated as a review aid, not as a defect assertion.
- The complete build initially reported a ktlint violation in the new evaluator
  helper's line wrapping. The evaluator source was corrected and the isolated
  ktlint task passed. The corrected regression then reproduced the same sole
  production failure, so formatting is no longer an evaluator failure.
- No ADB device was connected. Device interaction is not required to prove this
  authenticated server persistence/visibility defect.

## Historical follow-up

The retained M9 report selected 54 tests from `OpenSeriesTest`,
`InitialGameTest`, `SeriesLifecycleTest`, `DashboardTest`, and
`SeriesIdempotencyTest`; that entire selection is included in the fresh
158-test server run and passes. Its earlier environment-only PostgreSQL startup
failure did not recur. Historical `M8-01` coverage no longer closes this path:
D045 blocks nameless friendship creation, but D046 now permits direct series
creation without a friendship, which is the path pinned by `M9-U01`.

The complete build's server test task ran 591 tests and reported exactly one
failure. The 590 pre-existing server tests therefore passed; the sole failure
is the intentionally red `M9AdversarialTest` that must turn green after
production remediation.
