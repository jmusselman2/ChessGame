# M6 Critic Report

## Fresh assessment

**Provisional fresh verdict:** PASS

This section was written before consulting any retained M6 evaluation report.

### Scope and evidence

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M6: `563887caeec97f41546c3185f968ad7efd7dc2b5`
- Requirements: `docs/BACKLOG.md` M6.1 through M6.5 and D030
- Production implementation: `compose.yaml`, database initialization and
  migrations, server database configuration, Exposed table mappings, and
  `GameRepository`
- Current callers: server startup migration and the game command/persistence
  paths
- Verification: direct disposable PostgreSQL checks, focused integration
  suites, complete server build, dependency resolution, schema inspection, and
  a live migrated server health request

### Requirement assessment

| Requirement | Fresh result | Evidence summary |
| --- | --- | --- |
| M6.1 disposable PostgreSQL | PASS | The healthy `chessgame-postgres` PostgreSQL 18 container exposes host port 55432, contains separate `chessgame_dev` and `chessgame_test` databases, and passed a create/insert/select/drop round trip in the test database. |
| M6.2 access library decision | PASS | D030 records Exposed 1.5.0 with HikariCP 7.1.0 and PostgreSQL JDBC 42.7.13 against all selection criteria; the server runtime classpath resolves the declared Exposed core/JDBC/date-time/JSON modules, pool, and driver. |
| M6.3 repeatable migrations | PASS | Ten forward-only SQL migrations are classpath-backed and managed by Flyway 13.4.0. Fresh/reset/reapply/idempotence coverage passed, and a real server startup validated all ten and reported schema version 10 current. Destructive reset remains guarded to disposable URLs. |
| M6.4 required schema constraints | PASS | Direct catalog inspection found the current primary, foreign, unique, and product check constraints across the evolved V1-V10 schema. The 21-case schema suite confirmed required rejection behavior. |
| M6.5 transactional persistence | PASS | Representative canonical states round-trip through `GameRepository`; guarded version updates, move-history replacement, audit events, losing concurrent writes, and late-failure rollback are covered against PostgreSQL. |

The SQL migrations remain the schema source of truth; Kotlin mappings do not
generate or mutate the schema. Database calls use Hikari-backed data sources
with auto-commit disabled and explicit Exposed transactions. The adversarial
coverage proved exactly one same-version competing write can win and that a
failure after state mutation rolls back state, version, history, and audit
together.

A read-only PostgreSQL design review also considered primary keys, foreign-key
coverage, constraints, pooling, transaction length, and lock behavior. Five
current foreign keys do not have a containing index. That observation is not an
M6 acceptance failure: M6.4 requires the referential constraints themselves,
the current evolved schema deliberately includes later index work, and no
required M6 query or integrity behavior failed. It is retained as an audit
observation rather than misclassified as a production defect.

### Fresh findings

No confirmed production defect or evaluator defect was found.

The expected error logs emitted by failure-injection, lock-timeout, user-create,
and `last_seen_at` tests were followed by passing assertions and a successful
Gradle build; they are test evidence, not test failures.

## Historical comparison

After the fresh verdict was recorded, both retained M6 reports under
`evals/runs/2026-09-17-e2b3287/M6/` were reviewed. That evaluation also passed
M6 and added `M6AdversarialTest` for a widened simultaneous-save race and a
late history-insert failure. Both regressions pass today and still assert the
complete state, version, history, and audit outcomes rather than merely the
thrown exception.

The retained evidence exercised only V1 and 410 server tests. The current
baseline has evolved through V10 and passed 590 server tests. Direct catalog
inspection confirmed the current table/participant schema retains the required
integrity constraints, and the post-comparison `DatabaseConfigTest` plus
`IndexesTest` run passed all 13 tests, including encoded credentials, retained
TLS/query properties, password redaction, the friends lookup index, and removal
of two redundant indexes.

The earlier report noted raw `+` in URL user-info and possible stricter
cross-row consistency as robustness improvements. Current configuration accepts
percent-encoded credentials (including `%2B`) and the current schema has added
participant-reference and lifecycle checks. A literal unescaped `+` remains a
URL-encoding concern, not a demonstrated failure of the documented local
database, migration, constraint, or transactional-persistence criteria. No
additional evaluator or production change is justified for M6.

**Final M6 verdict: PASS.**
