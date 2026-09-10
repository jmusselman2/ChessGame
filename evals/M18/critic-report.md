# M18 — Independent Evaluation: Critic Report

Production baseline: `38be421dfd64c269687c893300f11e661bfa9c90`

## Verdict

**DEFECT FOUND.** The architecture review contains all four required analyses
and reaches a disciplined no-premature-extraction decision, but it overstates
the physical-device evidence supporting its definition of “proven.” One
evaluator-only documentation regression records M18-01. No production code or
authoritative product documentation was changed. M10-01, M12-01, and M14-01
through M14-03 remain unresolved and are carried forward.

## Finding

### M18-01 — The review claims a second physical device that M17 did not record

`docs/PLATFORM-REVIEW.md` defines a proven platform concept as implemented,
tested, and exercised “by two real people on two physical devices” in M17.1.
The M18 completion note in `docs/BACKLOG.md` repeats the same assertion.

The authoritative M17.1 completion record proves that one other person
installed the beta on their own physical Android device and played with the
project owner. It does not identify the device used by the owner as physical.
The narrower summaries in `ARCHITECTURE.md` §29 and `MVP.md` accurately say
that the real tester used a physical device. M18 therefore upgrades a one-device
fact into a two-device fact without evidence.

This does not undermine the named reusable concepts, which also rest on code,
tests, concurrency failures and repairs, emulator runs, and a real-user game.
It does make the review's explicit evidence standard inaccurate. The regression
isolates the M17 section before looking for evidence of an owner-used physical
device, so M18's own repeated assertion cannot satisfy it.

## Requirement evaluation

- **Chess-specific concepts:** the review identifies all of `game-core`, the
  serialized chess position, chess-shaped move schema and API vocabulary,
  colour alternation, the chess undo predicate, and Android's local replay.
- **Proven platform concepts:** monotonic guarded versions, truth-carrying
  refusals, invalidation-only realtime, provider-independent identity, social
  invariants, series continuity, atomic settlement, command-validation order,
  audit, and throttled activity tracking are each tied to implementation and
  tests. M18-01 qualifies only the physical-device sentence used to describe
  their evidence standard.
- **Abstractions worth extracting:** seat, server-computed capabilities,
  versioned state document, command-service shape, `RealtimeHub`, and the
  account/social layer are named as candidates only after a second concrete
  implementation exists. This matches accepted decision D044.
- **Abstractions that should remain concrete:** no generic rules interface,
  shared move/action table, command hierarchy, generic services, premature
  Kotlin Multiplatform conversion, repository split, or service split is
  proposed. The conclusions reconcile with the dependency rules in
  `ARCHITECTURE.md`.
- **Unproved areas:** the additional analysis correctly separates hidden
  information, in-game randomness, hidden-information undo, whole-history
  rewrite cost, multi-action turns/reactions, and scale from what chess proved.
  Its later deck-builder design notes are explicitly subordinate to accepted
  decisions rather than presented as implemented architecture.

## Remediation handoff

Unless there is separate evidence that the project owner also played on a
physical device, replace both M18 assertions with the fact M17 actually records:
two real people played, and the external tester installed and played on their
own physical Android device. Keep the stronger two-device wording only if the
missing device evidence is added to M17.

## Carried findings

M10-01, M12-01, and M14-01 through M14-03 concern runtime snapshot, realtime,
and client-ordering behavior. They do not change this document-review finding
and are carried without reclassification.
