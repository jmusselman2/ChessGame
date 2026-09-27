# M15 Critic Report

## Fresh assessment

**Fresh verdict: PASS**

This section was completed before consulting any retained M15 evaluation
report.

### Scope and current authority

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M15:
  `de9acdd91755f5ec00b911e26d6221f83dfc67cf`
- Requirements: `docs/BACKLOG.md` M15.1 through M15.5, D032 through D037,
  current deployment/database/Android implementation, schemas, callers, and
  retained tests
- External evidence: official Render and Supabase terms current on 2026-09-27,
  the public deployed service, the connected Supabase project, current Git
  remote refs, and the immediately preceding fresh M14 live Android path

### Requirement assessment

| Requirement | Fresh result | Evidence summary |
| --- | --- | --- |
| M15.1 provider and current terms | PASS | Render still supplies public managed TLS, WebSockets, health checks, Docker deployment, secrets, one Free instance, 750 monthly Free-instance hours, 15-minute idle spin-down, roughly one-minute wake, 5 GB Hobby bandwidth, and 500 Hobby build minutes. Supabase Free still supplies two active projects, 500 MB database size, 1 GB storage, 5 GB egress plus 5 GB cached egress, 50,000 MAU, inactivity pausing, and no automatic backup/PITR. D032 records the owner's accepted hard-$0 boundary and no-payment-method confirmation. |
| M15.2 deployed Ktor server | PASS | The exact two-stage `Dockerfile` rebuilt successfully without an Android SDK or secrets. Its runtime is non-root, accepts `PORT`, carries V1-V10 migrations, and reports health-only when deliberately run without host configuration. The public HTTPS `/health` returned 200, warm in 0.345 s, as build `2fd13fa`, exactly the live `origin/main`; unauthenticated WSS returned 401. The fresh M14 path proved authenticated HTTPS and WSS against the same service. |
| M15.3 beta Supabase environment | PASS | The connector reports `ChessGame Dev` active and healthy on PostgreSQL 17.6.1, with all 14 current public tables and ten Flyway history rows. The committed URL shape uses the IPv4 shared session pooler on port 5432 with `sslmode=require`; parsing, decoding, redaction, and reset safety pass focused tests. Secrets remain absent from tracked configuration and the image. |
| M15.4 Android beta endpoint | PASS | A release APK rebuilt with the deployed HTTPS URL embedded, cleartext forbidden, and no debug domain exception. Endpoint/WSS mapping, 150-second capped wake policy, waking-versus-failed UI, retry action, and the no-blind-command-retry boundary pass focused tests. The immediately preceding fresh M14 live clients authenticated, loaded dashboards, played moves, and held realtime connections against this endpoint. |
| M15.5 destructive reset guard | PASS | The live-address, loopback-only guard runs before both destructive reset paths, fails closed on remote/unparseable targets, and accepts only the exact conspicuous override. Migration remains forward-only and unrestricted. The real integration regression confirms the schema survives refusal. |

Current official documentation confirms the material provider assumptions:
[Render Free](https://render.com/docs/free),
[Render WebSockets](https://render.com/docs/websocket),
[Render bandwidth](https://render.com/docs/outbound-bandwidth),
[Render build pipeline](https://render.com/docs/build-pipeline),
[Supabase billing](https://supabase.com/docs/guides/platform/billing-on-supabase),
[Supabase pausing](https://supabase.com/docs/guides/platform/free-project-pausing),
[Supabase backups](https://supabase.com/docs/guides/platform/backups), and
[Supabase connection modes](https://supabase.com/docs/guides/database/connecting-to-postgres).
Account billing remains a human-attested property: it is not exposed by the
public service, and no Render workspace was selected for account-level reads in
this evaluation. Nothing observed contradicts the recorded owner confirmation.

The current Supabase changelog announces PostgreSQL 17.11 for existing projects
beginning 2026-09-28. Its action cases concern `ltree`, legacy-cipher
`pgcrypto`, float `btree_gist`, and custom selectivity operators; none occurs in
the repository schema, so this creates no present M15 prerequisite.

### Supabase direct-access risk

Supabase metadata raised a critical advisory because all 14 public tables have
RLS disabled. Current project history also records broad `anon` and
`authenticated` grants. This would expose the entire schema if `public` were
enabled in the Data API. A fresh publishable-key request to
`/rest/v1/users` returned 503, consistent with the documented no-exposed-schema
setting; the app also accesses game data only through Ktor. Therefore this is
not a current direct-exposure defect and does not fail M15. It remains the
high-priority latent risk already recorded as F34: a dashboard change could
expose everything at once, so grants/default privileges and RLS should still be
fixed deliberately. No live schema or provider setting was changed here.

No fresh M15 production defect was found.

## Historical comparison

The retained `2026-09-17-e2b3287` M15 report also passed, while carrying then-
open `M10-01`, `M12-01`, and `M14-01` through `M14-03`. Those findings do not
remain open: D057 repaired coherent game reads, D058 isolated realtime
recipients, and D059 made game/dashboard result installation monotonic.

The historical cold/warm measurements (64.96 s and 0.28 s) remain useful design
observations, not a promised bound. Fresh official documentation still says a
Free wake takes about one minute; current Android policy retains substantial
headroom. The live service now reports the current `origin/main` evaluator
checkpoint instead of the historical build, and its database-backed mode is
still active.

Historical comparison required no new regression: the fresh selections are
strict supersets of the old deployment/database/endpoint selections and all
pass. The fresh Docker build, release APK inspection, provider readback, and
Supabase/Data-API checks add current evidence the earlier report did not have.

**Final M15 verdict: PASS.**
