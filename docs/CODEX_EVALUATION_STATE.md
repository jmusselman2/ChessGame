# Codex Evaluation State

- **Evaluated `main` baseline:** `8afe530cf8dd467a6063c5514767eb7814bcc0f4`
- **Current milestone:** M5 — Local Android Chess
- **Status:** `DEFECT FOUND`
- **Unresolved findings:**
  - M5-01: non-terminal check is not presented on the local game screen.
  - M5-02: local play exposes only current-position draw claims; no action binds
    a contemplated legal move for prospective threefold or fifty-move claims.
- **M3 artifacts:** `evals/M3/independent-re-evaluation-critic-report.md`,
  `evals/M3/independent-re-evaluation-test-report.md`,
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M3AdversarialTest.kt`,
  and
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M3ReferencePerftTest.kt`
- **M4 artifacts:** `evals/M4/critic-report.md`,
  `evals/M4/test-report.md`, and
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M4AdversarialTest.kt`
- **M5 artifacts:** `evals/M5/critic-report.md`, `evals/M5/test-report.md`,
  `android-app/app/src/test/java/com/jmussel/chessgame/ui/board/M5AdversarialTest.kt`,
  and
  `android-app/app/src/androidTest/java/com/jmussel/chessgame/ui/board/M5LocalUiAdversarialTest.kt`

## Completed milestones

- **M2 — Chess Domain Model:** `PASS` on
  `9d468b7ba718004c21cb8a8a20afd86b35fafd48`.
- **M3 — Chess Legal Move Engine:** `PASS` on
  `8afe530cf8dd467a6063c5514767eb7814bcc0f4`.
- **M4 — Undo Semantics:** `PASS` on
  `8afe530cf8dd467a6063c5514767eb7814bcc0f4`.

## Next action

Claude remediation must expose non-terminal check in local play and support
binding an exact contemplated legal move when claiming a prospective threefold
or fifty-move draw, while retaining current-position claims. Then another
independent M5 re-evaluation must pass before M6 begins.
