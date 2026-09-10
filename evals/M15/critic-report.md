# M15 — Independent Evaluation: Critic Report

Baseline: `38be421dfd64c269687c893300f11e661bfa9c90`

## Verdict

**PASS WITH CARRIED FINDINGS.** The hosted-beta milestone satisfies its stated
deployment, database, Android endpoint, and destructive-reset requirements. No
new M15 defect was found and no production code was changed. M10-01, M12-01,
and M14-01 through M14-03 remain unresolved and are carried forward.

## Requirement evaluation

- **M15.1 — free-tier selection:** `D032` records the accepted hard-$0
  constraint and the consequences of Render and Supabase free service. The
  current provider documentation still supports the material assumptions:
  Render free web services are single-instance, sleep after inactivity, and
  include 750 instance hours monthly; Supabase Free provides two active
  projects, a 500 MB database, finite egress, inactivity pausing, and no
  automatic backups or point-in-time recovery. The project owner previously
  confirmed that no payment method is attached.
- **M15.2 — hosted server:** `Dockerfile` builds only the JVM server, uses a
  smaller non-root runtime image, accepts host-provided configuration, and
  starts the installed distribution. `render.yaml` records one free instance,
  `main` auto-deployment, `/health`, and unsynchronised secret variables. A live
  probe on 2026-09-09 returned HTTP 200 with successful certificate validation;
  a cold request completed in 64.96 seconds and a warm request in 0.28 seconds.
  The response changed from build `a4bb699` during the cold deploy window to
  build `38be421`, independently confirming current-branch auto-deployment.
  The detailed milestone record retains the authenticated HTTPS/WSS Android
  play-through and provider-usage evidence.
- **M15.3 — beta database:** the implementation preserves PostgreSQL URL query
  parameters, including `sslmode=require`, percent-decodes credentials, and
  redacts passwords from diagnostics. The committed configuration names the
  Supavisor session pooler on port 5432 and the same Supabase project on both
  sides. `.env` is ignored and untracked; the repository contains only the
  non-secret template. The retained verifier evidence covers encrypted access,
  Flyway schema application, token verification, and a database-backed request.
- **M15.4 — Android beta endpoint:** the endpoint remains a Gradle build input,
  release traffic refuses cleartext, HTTPS maps to WSS, and development keeps
  emulator loopback. Read-only startup/reload work has capped retry and a
  configurable deadline, while commands are not blindly retried. The retained
  cold-start device play-through covers authentication, dashboard load, a
  canonical move, and websocket connection.
- **M15.5 — destructive-reset guard:** `Migrations.reset` asks the live JDBC
  connection for its address and calls the narrow loopback-only guard before
  `Flyway.clean`. Unparseable and remote targets fail closed, the deliberately
  conspicuous escape hatch accepts one exact value, and forward-only migration
  remains unrestricted. The integration regression proves refusal leaves the
  migrated schema and `users` table intact.

## Reconciliation notes

The plan predates current provider wording, so its limits were checked against
the current official Render and Supabase documentation rather than accepted as
timeless facts. The operational tradeoffs remain materially unchanged. Live
Supabase inspection was not repeated because it would require production
credentials; the repository's recorded verification, implementation, and
regressions provide sufficient evidence without exposing or mutating beta data.

## Carried findings

M10-01 can mix a stale game row with newer move history; M12-01 can let one
stalled websocket block later recipients and a command response; M14-01 loses
a realtime update during initial game load; M14-02 permits an older command
response to regress a newer view; and M14-03 permits an old dashboard response
to erase automatic-rematch state. None prevents evaluating or operating the
M15 deployment controls, and none is reclassified here.
