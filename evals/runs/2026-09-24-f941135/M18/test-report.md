# M18 Test Report

## Fresh verification

The fresh assessment was completed before any retained M18 report was read.

| Verification | Result |
| --- | --- |
| Required review sections | PASS - all four required sections plus the explicit limits section are present |
| Review authority labels | PASS - descriptive/nonbinding status, evidence, assumptions, and D044 relationship are explicit |
| Named revision `9941402` | PASS - resolves to the reviewed source snapshot |
| Server measurement at `9941402` | PASS - 32 files, 4,257 lines, seven core-mentioning files; matches the review |
| `game-core` measurement at `9941402` | FAIL - review says 22 files; Git has 23; 1,779 lines matches |
| Relative link check | PASS - 9 unique relative targets, 0 missing |
| M18 implementation commit scope | PASS - documentation only, no production code |
| Existing M18-01 physical-evidence regression | PASS before the new assertion |
| Extended `M18DocumentationRegressionTest.ps1` | EXPECTED FAIL - pins M18-U01's 22-versus-23 boundary |
| Complete build on the identical production tree | PASS earlier in this run - game-core 394, Android 521, server 593; lint and APK assemblies passed |
| `git diff --check` | PASS |

The count was derived with `git ls-tree -r --name-only 9941402 --
game-core/src/main/kotlin`, restricted to `.kt`. The 23 files comprise
`GameCore.kt` and 22 files in `core/chess/`. Counting their content at the same
revision produces the review's 1,779-line total, proving that the table did not
intentionally exclude `GameCore.kt` throughout.

No runtime or device verification can repair or refute this documentation
boundary. The exact Git snapshot is the authoritative test fixture.

## Historical follow-up

Before extension, the retained `M18DocumentationRegressionTest.ps1` passed its
M18-01 physical-device boundary, confirming the 2026-09-10 correction remains
effective. The retained four-section and D044 checks also agree with the fresh
results.

The new named-snapshot assertion then fails only on M18-U01:

> PLATFORM-REVIEW.md claims 22 game-core Kotlin files at 9941402; Git contains 23.

No broader runtime rerun was justified after comparison. The defect is wholly
inside the M18 review's measured documentation, and the run's complete build
already passed on the identical production tree.

## Remediation re-evaluation

| Verification | Result |
| --- | --- |
| Fetch and live branch inspection | PASS - all remotes fetched; amended `origin/codex-autopilot` checkpoint inspected |
| Remediation patch scope | PASS - only the two incorrect file-count claims changed from 22 to 23 |
| `M18DocumentationRegressionTest.ps1` | PASS - named-snapshot count and M18-01 physical-evidence boundary both hold |
| `game-core` measurement at `9941402` | PASS - 23 Kotlin files, 1,779 lines |
| Server measurement at `9941402` | PASS - 32 Kotlin files, 4,257 lines, seven core-mentioning files |
| Relative link check | PASS - 9 unique relative targets, 0 missing |
| Required review sections and authority labels | PASS |
| Device discovery | PASS - Kindle `G090MJ0574130HMR` connected; device execution remains not applicable |
| `git diff --check` | PASS |

The complete production delta from the failing evaluator checkpoint to the
amended checkpoint is the two-line documentation correction. The prior full
build remains applicable because no source, build, schema, configuration, or
test code changed. The exact regression supplies the required post-remediation
evidence.

**Final result: PASS. `M18-U01` is closed.**
