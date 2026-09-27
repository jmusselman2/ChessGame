# M11 Test Report

## Fresh verification

The focused verification and source assessment were completed before any
retained M11 report was read.

| Verification | Result |
| --- | --- |
| `UndoMoveTest`, `MoveVersusUndoTest`, and `GameViewTest` | PASS - 31 tests, 0 failures, 0 errors, 0 skipped |
| `UndoEligibilityTest`, `MoveHistoryTest`, `TerminalUndoLockTest`, and `M4AdversarialTest` | PASS - 39 tests, 0 failures, 0 errors, 0 skipped |
| Focused aggregate | PASS - 70 tests, 0 failures, 0 errors, 0 skipped |
| Current requirements, command/rule/persistence implementation, response mapping, and caller inspection | PASS - no production defect found |
| Complete `build --continue --rerun-tasks` on the same production/evaluator tree | PASS - 134 tasks; game-core 394, Android 521, server 593; lint and both APK assemblies passed |
| `adb devices -l` | Kindle `G090MJ0574130HMR` (`KFDOWI`) connected; device interaction not applicable to this server/rule milestone |
| `git diff --check` | PASS |

The 31 server cases comprise 17 authoritative undo tests, 6 concurrency tests,
and 8 canonical-view tests. The concurrency suite runs 15 fresh-game rounds for
each state/audit property and contains the deterministic lock-wait probe that
prevents a mutating read from straddling an in-flight row/history save. All
PostgreSQL-backed tests executed rather than skipping.

The 39 game-core cases comprise 11 eligibility tests, 15 exact history
round-trips, 10 terminal locks, and 3 adversarial terminal/repetition checks.
They verify that undo restores the full state rather than merely moving a piece
back, and that it never crosses a terminal boundary.

The complete build was run immediately before M11 on the identical production
and evaluator tree; the only subsequent local change before focused M11
verification was the evaluation-state document. Repeating the complete build
would add no new code coverage, while the M11 selections were forced with
`--rerun-tasks` after the M10 checkpoint.

## Historical follow-up

The retained M11 report's 23 server tests are a subset of the fresh 31-case
server run and all still pass. Its carried M10 read-coherence concern is now
closed by D057, and the later M16.7 mutating-read lock regression remains green.
No historical result conflicts with the fresh assessment.
