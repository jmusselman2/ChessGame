# M4 — Undo Semantics: Test Report

## Existing coverage re-verified

- `MoveHistoryTest`: 15 exact restoration/history tests.
- `UndoEligibilityTest`: 11 D016 sequence and ownership tests.
- `TerminalUndoLockTest`: 10 terminal/non-terminal locking tests.
- `M2CollectionImmutabilityTest`: 14 collection-boundary tests, including
  `ChessGame` constructor, copy, published history, and derived moves.

The clean focused baseline passed all 50 tests with no failures, errors, or
skips.

## Added adversarial coverage

`M4AdversarialTest` adds three tests:

- an exhaustive matrix proving every terminal reason locks guarded undo for
  both sides while leaving history readable,
- a prospective fifty-move claim proving the declaration adds no move/history
  and leaves nothing undoable,
- undo/replay at the threefold threshold proving repetition counts and the
  entire game snapshot restore exactly.

## Verification

- Focused M4 run after expansion: **PASS**, 39 tests, 0 failures, 0 errors,
  0 skipped.
- Final complete `:game-core:test --rerun-tasks`: **PASS**, 394 tests,
  0 failures, 0 errors, 0 skipped.
- `:game-core:ktlintCheck`: **PASS**.
- `git diff --check`: **PASS**.
