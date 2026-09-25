# M5 Kindle Checklist

## Device

- Preferred device: Kindle `G090MJ0574130HMR`
- Model: `KFDOWI`
- Android: 5.1.1 / API 22
- Owner package preserved: `com.jmussel.chessgame`
- Isolated evaluator package: `com.jmussel.chessgame.eval.m5`

## Checks

- PASS — isolated debug app and instrumentation APK installed without replacing
  or clearing the owner package.
- PASS — a complete Fool's mate was played through rendered board taps.
- PASS — board orientation and square hit targets followed the side to move.
- PASS — move history updated through the complete game.
- PASS — live check and final checkmate status were visible.
- PASS — Undo and resignation controls were absent after checkmate, and further
  board input left the terminal state unchanged.
- PASS — prospective draw claim, play-declared-move, and cancel choices worked.
- PASS — board/layout checks passed at the Kindle viewport, including large
  font, scrolling, all square centers in both orientations, and promotion UI.

## Evaluator correction and cleanup

The first Compose attempt ran while the Kindle was asleep and locked, causing
all six tests to report no hierarchy. After wake/unlock, the exact failed test
and both complete suites passed. USB stay-awake was temporary and restored to
its original value `0`.

Both evaluator packages were uninstalled after testing. The owner package was
confirmed still installed, and the disposable worktree was removed.
