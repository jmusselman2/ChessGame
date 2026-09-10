# M15 — Independent Evaluation: Test Report

Baseline: `38be421dfd64c269687c893300f11e661bfa9c90`

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Deployment, database URL, migration, and reset-guard tests | 33 | 0 | 0 | 0 | PASS |
| Beta endpoint, wake policy, and app-startup tests | 30 | 0 | 0 | 0 | PASS |
| Live deployed `/health` over TLS | 2 probes | 0 | 0 | 0 | PASS |
| Secret tracking and ignore-rule inspection | — | — | — | — | PASS |
| `git diff --check` | — | — | — | — | PASS |

The server selection comprised `DeploymentTest`, `DatabaseConfigTest`,
`DisposableDatabaseTest`, and `MigrationsTest`, forced with `--rerun-tasks`
against the disposable PostgreSQL instance on port 54999. The Android selection
comprised `BetaEndpointTest`, `ServerWakeTest`, and `AppStartupTest`, also forced
with `--rerun-tasks`.

The live cold probe of `https://chessgame-hit7.onrender.com/health` returned
HTTP 200, certificate verification result 0, build `a4bb699`, and 64.958576
seconds total. A second warm probe returned HTTP 200, certificate verification
result 0, build `38be421`, and 0.276544 seconds total. `/` returned the expected
404, independently showing this was the application rather than a static
health-only host.

An initial combined Gradle invocation caused the server task to select more
than the requested classes; it reproduced the already-recorded M10-01 and
M12-01 adversarial failures before being interrupted. Separate module-scoped
invocations applied the intended selections and passed. Those earlier findings
remain expected-red and do not change the M15 result.
