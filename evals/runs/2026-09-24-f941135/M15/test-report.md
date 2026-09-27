# M15 Test Report

## Fresh verification

The initial automated, artifact, external, and source assessment was completed
before any retained M15 report was read.

| Verification | Result |
| --- | --- |
| `DeploymentTest`, `WebSocketKeepAliveTest`, `DatabaseConfigTest`, `DisposableDatabaseTest`, and `MigrationsTest` | PASS - 34 tests, 0 failures, 0 errors, 0 skipped |
| `BetaEndpointTest`, `ServerWakeTest`, `AppStartupTest`, and `ChessAppTest` | PASS - 143 tests, 0 failures, 0 errors, 0 skipped |
| Release APK assembly against deployed HTTPS endpoint | PASS - 51 tasks; beta URL present, cleartext forbidden, debug domain exception absent |
| Fresh Docker build | PASS - JDK 24 server-only build to non-root JRE 24 Alpine runtime; no Android SDK or secret required |
| Fresh Docker runtime inspection | PASS - user `chessgame` uid 100, `/app/bin/server`, host-selected port, expected health-only response without host configuration |
| Docker contents and configuration | PASS - V1-V10 migrations present; no database/Supabase value baked into image environment |
| Public deployed `/health` | PASS - HTTP 200 in 0.345 s, `ChessGame server is healthy (build 2fd13fa)` |
| Public unauthenticated WSS upgrade | PASS - HTTP 401 |
| Git/deployment reconciliation | PASS - live build equals `origin/main` `2fd13fa`; current `main` contains `Dockerfile` and `render.yaml` |
| Supabase project readback | PASS - `ChessGame Dev` is `ACTIVE_HEALTHY`, PostgreSQL 17.6.1; 14 expected public tables and 10 Flyway history rows |
| Supabase Data API safety probe | PASS for current configuration - publishable-key request to `public.users` returned 503 because no schema is exposed |
| Current official provider-term review | PASS - material D032 limits/capabilities remain current |
| Complete `build --continue --rerun-tasks` on the same production/evaluator tree | PASS - 134 tasks; game-core 394, Android 521, server 593; lint and both APK assemblies passed |
| `adb devices -l` and owner-app check | Kindle `G090MJ0574130HMR` (`KFDOWI`) connected; owner app remains versionCode 9000, versionName `splash-test` |
| `git diff --check` | PASS |

All PostgreSQL-backed server cases ran rather than skipping. The 34-test server
selection adds current keepalive coverage to the retained M15 set. The 143-test
Android selection is a strict superset of the old beta endpoint, wake, and
startup selection and includes the complete app-state command/reload boundary.

The release APK was not installed on the Kindle because replacing its differently
signed owner app would violate the device-protection rule. Its endpoint and
network-security configuration were inspected directly. Runtime endpoint,
authentication, dashboard, move, and authenticated WSS behavior had just been
proved by the fresh M14 read-only-emulator play-through against the identical
production tree and deployed service.

The Docker image was built from the checked-out source, then run without beta
credentials to prove the failure-safe health-only mode. The container was
removed after inspection. Public `/health` proved the deployed instance has its
host configuration and database rather than health-only mode.

## Provider and database notes

Render account-level plan, instance-count, billing, and usage metrics were not
read because the connector had no user-confirmed workspace selection. The
public service, live Git ref, committed read-back configuration, current
official terms, and recorded owner no-payment-method confirmation supplied the
non-mutating evidence required here; no deploy or provider setting was changed.

The Supabase table readback emitted an RLS-disabled advisory. A direct REST
probe returned 503, confirming the current no-exposed-schema barrier. This does
not erase the F34 risk: broad grants plus disabled RLS would become critical if
`public` were exposed.

## Historical follow-up

The retained report's 33 server and 30 Android tests are covered by the fresh
34 and 143-test selections. Its carried M10, M12, and M14 findings have been
closed by retained regressions already rerun in their current milestones. No
additional M15 evaluator regression was warranted.
