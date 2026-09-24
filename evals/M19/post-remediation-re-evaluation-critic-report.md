# M19-01 — Independent Post-Remediation Re-evaluation: Critic Report

Evaluated `main`: `83de79ceed51577b9b8cffa38ca1b937e029537a`

## Scope and verdict

The `M19-01` remediation in `7f0846e` was re-evaluated independently from the
original requirement, schema, migration, production repository, callers, and
retained tests. The implementation-track remediation report was treated as a
claim to verify, not as proof.

**Verdict: PASS.** The participant exact-set identity defect is closed. No new
M19 defect was found, no evaluator regression was needed, and no production
code was changed.

## Finding closure

`Participant` identity is the complete `(kind, ref)` value. `findOrCreate` now
uses `Participant` equality when rejecting duplicates, so a `USER` and a
`SCRIPTED` participant with the same UUID remain distinct while an actual
repeated participant is still refused before any write.

Seat and key ordering use one total comparator: UUID first, then kind. The kind
breaks the only tie that the former ref-only order left dependent on caller
order. Consequently the same mixed-kind set produces one table and one
`participant_set` in either input order.

The ordering is migration-compatible. V5 migrated only user pairs and wrote
their lower UUID before their higher UUID. Because those refs are distinct, the
kind tie-breaker is never consulted and every migrated all-user key is
unchanged. V9's separate user/non-user references and kind-qualified composite
foreign keys intentionally allow equal UUID text in the two namespaces.

## Caller and invariant review

- `SeriesService` creates chess tables from two users, whose keys retain V5's
  form.
- `GameSeriesRepository` accepts complete participants and reaches the corrected
  repository boundary.
- `participantSetOf` and table seat insertion share the same canonical order.
- Database uniqueness still converges concurrent creation of one exact set on
  one table.
- Schema constraints still reject a participant taking two seats through the
  same user or non-user reference.
- Existing V2-to-V9 and V4-to-current fixtures preserve users, tables, series,
  games, moves, events, dashboard, and history state.

M19.2 through M19.12 therefore pass their current acceptance criteria. M19.1
remains M20.1, and the N >= 3 continuation work remains M20.2; neither was
reclassified by this re-evaluation.
