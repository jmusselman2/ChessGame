# M18 — Independent Evaluation: Test Report

Production baseline: `38be421dfd64c269687c893300f11e661bfa9c90`

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Required review-section audit | 4 sections | 0 | 0 | 0 | PASS |
| D044 and `ARCHITECTURE.md` §31 reconciliation | 2 records | 0 | 0 | 0 | PASS |
| `M18DocumentationRegressionTest.ps1` | 1 | 1 | 0 | 0 | EXPECTED FAIL |
| `git diff --check` | — | — | — | — | PASS |

The structural audit found the four acceptance-criterion sections:
`Chess-specific concepts`, `Proven platform concepts`, `Abstractions worth
extracting`, and `Abstractions that should remain concrete`. The review also
contains the useful non-required `What chess did not prove` analysis.

D044 names the same six conditional extraction candidates and explicitly bars
extraction before a second ruleset. `ARCHITECTURE.md` §31 points readers to the
measured review instead of preserving its older prediction, so those records
are mutually consistent.

The evaluator-only PowerShell regression failed with:

> PLATFORM-REVIEW.md claims two physical devices, but the M17 record identifies
> only the tester device as physical.

The test isolates the M17 backlog section before checking for an explicit
owner-used physical device or a statement that both devices were physical. It
therefore cannot pass on the duplicated unsupported assertion in M18 itself.
No runtime Gradle suite was repeated for this documentation-only milestone;
M15–M17 had just exercised the relevant implementation slices.
