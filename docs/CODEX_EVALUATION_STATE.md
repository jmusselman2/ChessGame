# Codex Evaluation State

- **Evaluated `main` baseline:** `0da5f42b7deacde1aaa698f09d32b19a72e6df89`
- **Current milestone:** M7 — Identity and Username
- **Status:** `DEFECT FOUND`
- **Completed milestones:** M2, M3, M4, M5, M6
- **Unresolved findings:** M7-01 replaces a stored anonymous identity after
  transient refresh failures (HTTP 429/503). M7-02 consumes the last-seen
  throttle window before persistence succeeds, suppressing initial, later, and
  route-integrated retries. M7-03 accepts a correctly signed token with no
  expiration claim.
- **Latest artifacts:** `evals/M7/critic-report.md`,
  `evals/M7/test-report.md`, and Android/server `M7AdversarialTest` regressions.

## Next action

Claude remediates all three M7 defect classes, the remediation is merged into
`main`, and Codex then realigns `codex-autopilot` to that baseline and performs
a fresh independent M7 evaluation. Do not begin M8 before M7 passes.
