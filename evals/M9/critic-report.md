# M9 — Independent Evaluation: Critic Report

Baseline: `3de5e28dbcb29e5d86e163c39b26278686385c4c`

## Verdict

**PASS WITH CARRIED FINDINGS.** The complete M9 sweep found no new M9 defect.
M8-01 through M8-03 remain unresolved and continue to constrain series access
at the friendship boundary. No production code was changed.

## Baseline reconciliation

The evaluated production baseline is `origin/main` at `3de5e28`. The two
commits after the previous recorded baseline (`3180f84` and `3de5e28`) change
decisions and documentation, not the M9 implementation. The evaluator branch
began this milestone clean at `be08b3e`, synchronized with
`origin/codex-autopilot`.

## Requirement evaluation

- **M9.1 — Start/open active series:** the ordered pair plus partial unique
  index enforces one active series. A losing concurrent insert re-reads the
  winner, closed history does not block a fresh series, and the authenticated
  route returns the documented created/reopened and refusal statuses.
- **M9.2 — Initial game:** opening a game-less active series locks and re-reads
  the series row, creates game 1 and attaches it in one transaction, and uses an
  injectable coin toss for the first colour assignment. Reopening returns the
  same current game. The later M16.4 race correction is present in the baseline
  and its end-to-end regression remains retained.
- **M9.3 — Close-after-current lifecycle:** marking, closing, and conditional
  closing are guarded and idempotent. The first close timestamp is preserved,
  closed series remain readable, and their pair can subsequently open a new
  active series.
- **M9.4 — Dashboard discovery:** one joined series/game query and one batched
  opponent query return every active series, including the documented game-less
  representation, with current version, side, turn, move number, and close
  marker. Closed series are excluded and the route is authenticated.

## Carried findings and blocked validation

- **M8-01:** a nameless caller can create a friendship. Consequently the M9
  series route can be reached through an externally unusable friendship and
  dashboard mapping can omit the nameless opponent. This is the same authority
  defect already proved in M8, not a separate M9 implementation defect.
- **M8-02:** concurrent removed-row friendship reactivation still violates the
  duplicate response contract. It does not defeat M9's database-enforced
  one-active-series invariant.
- **M8-03:** friendship removal can race `POST /series` after its stale friend
  check and leave an unmarked active series. Therefore race-safe authorization
  of series creation remains **BLOCKED BY M8-03**. The series uniqueness,
  initial-game atomicity, lifecycle, and dashboard behavior remain independently
  evaluable and passed.

## Scope conclusion

All M9 requirements and acceptance criteria were reconciled against the schema,
repositories, authenticated routes, implementation history, retained milestone
tests, M16.4 idempotency coverage, and later series/rematch callers. No new
evaluator regression was warranted beyond the retained deterministic coverage;
the unresolved boundary failures are already pinned by `M8AdversarialTest`.
