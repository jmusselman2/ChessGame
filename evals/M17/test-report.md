# M17 — Independent Evaluation: Test Report

Production baseline: `38be421dfd64c269687c893300f11e661bfa9c90`

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `scripts/verify-beta-apk.sh` | 6 checks | 0 | 0 | 0 | PASS |
| New-game broadcast and server build identity | 16 | 0 | 0 | 0 | PASS |
| Dashboard sections and build label | 23 | 0 | 0 | 0 | PASS |
| Live deployed `/health` and TLS | 1 probe | 0 | 0 | 0 | PASS |
| Tracked artifact/credential inspection | — | — | — | — | PASS |
| `git diff --check` | — | — | — | — | PASS |

The repository verifier ran through the installed Git Bash. Its six checks
proved the default release remains unsigned; a throwaway key produces a
verifiable signed APK; versionCode/versionName reach the manifest; the beta URL
and release network policy are packaged; incomplete signing input fails; and a
missing keystore fails. Cleanup restored the ordinary unsigned release output.

The server selection comprised `NewGameBroadcastTest` (6) and
`DeploymentTest` (10), forced with `--rerun-tasks` against the disposable
PostgreSQL instance on port 54999. The Android selection comprised
`DashboardSectionsTest` (23), also forced with `--rerun-tasks`. All selected
tests passed with no skips.

The live cold-start probe of
`https://chessgame-hit7.onrender.com/health` returned HTTP 200, certificate
verification result 0, build `38be421`, and 64.466259 seconds total. Git
inspection found no tracked APK, Android App Bundle, keystore, or
`keystore.properties` file. The real-user device session was not repeated;
its exact build evidence and result were reconciled from the retained M17.1
completion record.
