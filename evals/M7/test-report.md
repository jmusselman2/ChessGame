# M7 — Independent Evaluation: Test Report

Baseline: `0da5f42b7deacde1aaa698f09d32b19a72e6df89`

## Added evaluator coverage

- Android `M7AdversarialTest` proves that HTTP 429 and 503 refresh responses
  preserve the stored account, remain retryable, and do not trigger sign-up.
- Server `M7AdversarialTest` injects PostgreSQL failures into both initial and
  later last-seen writes, then verifies that the next meaningful activity can
  retry immediately. It also stress-tests simultaneous first resolution of one
  authentication subject.

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Retained Android M7 authentication tests | 13 | 0 | 0 | 0 | PASS |
| Retained server M7 focused tests | 48 | 0 | 0 | 0 | PASS |
| Android M7 adversarial regressions | 2 | 2 | 0 | 0 | DEFECT PROVED |
| Server M7 adversarial regressions | 3 | 2 | 0 | 0 | DEFECT PROVED |

The server adversarial pass is the concurrent subject-resolution test. The two
failures are the initial and later last-seen retry variants. Both Android
adversarial tests fail because the stored account is replaced after a transient
refresh response.

All three retained live Supabase tests executed and passed; none were skipped.
The configured project exposed one EC/ES256 signing key and allowed anonymous
sign-in at evaluation time. No secret values were recorded.

Additional results:

- `:android-app:ktlintTestSourceSetCheck` — PASS;
- `:server:ktlintTestSourceSetCheck` — PASS;
- `git diff --check` — PASS;
- aggregate pre-adversarial baseline build — PASS with 1,202 JVM tests, zero
  failures, errors, or skips (394 game-core, 398 Android, 410 server).

One concurrent Gradle retry encountered a compiler read collision while another
process rebuilt `game-core.jar`. The affected retained Android command passed
when rerun sequentially, so the collision is infrastructure evidence only and
not a product finding.
