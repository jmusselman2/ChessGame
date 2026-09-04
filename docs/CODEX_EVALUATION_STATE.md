# Codex Evaluation State

- **Evaluated `main` baseline:** `8afe530cf8dd467a6063c5514767eb7814bcc0f4`
- **Current milestone:** M5 — Local Android Chess
- **Status:** `DEFECT FOUND — REMEDIATED, AWAITING RE-EVALUATION`
- **Remediation:** implemented on `claude-autopilot`, not yet independently
  re-evaluated. Only Codex may move M5 to `PASS`, and only from a fresh
  evaluation of the fixed `main`.
- **Findings and disposition:**
  - M5-01: a non-terminal check was not presented on the local game screen.
    Independently confirmed against the newer `claude-autopilot` line, and
    remediated — `GameControls.statusFor` now reports the check `game-core`
    sees, and a terminal result still takes precedence (`D041`).
  - M5-02: local play exposed only current-position draw claims; no action
    bound a contemplated legal move for a prospective threefold or fifty-move
    claim. Independently confirmed and remediated — a destination tap whose
    move would add a claim declares that exact move and offers the claim,
    playing it, or backing out, and claiming ends the game without playing it
    (`D038`, `D041`). Current-position claims are unchanged.
  - Neither finding had been fixed by production work newer than the evaluated
    baseline: nothing between `8afe530` and the current line touches
    `android-app/.../ui/board` or `game-core`.
- **M3 artifacts:** `evals/M3/independent-re-evaluation-critic-report.md`,
  `evals/M3/independent-re-evaluation-test-report.md`,
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M3AdversarialTest.kt`,
  and
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M3ReferencePerftTest.kt`
- **M4 artifacts:** `evals/M4/critic-report.md`,
  `evals/M4/test-report.md`, and
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M4AdversarialTest.kt`
- **M5 artifacts:** `evals/M5/critic-report.md`, `evals/M5/test-report.md`,
  `evals/M5/remediation-report.md`,
  `android-app/app/src/test/java/com/jmussel/chessgame/ui/board/M5AdversarialTest.kt`,
  `android-app/app/src/test/java/com/jmussel/chessgame/ui/board/DeclaredDrawClaimTest.kt`,
  `android-app/app/src/androidTest/java/com/jmussel/chessgame/ui/board/M5LocalUiAdversarialTest.kt`,
  and
  `android-app/app/src/androidTest/java/com/jmussel/chessgame/ui/board/LocalDrawClaimUiTest.kt`

The two Codex M5 reports are the evaluator's record of what it observed and are
left as written. `evals/M5/remediation-report.md` is the remediation's own
account, including which evaluator assertions were changed and why.

## Completed milestones

- **M2 — Chess Domain Model:** `PASS` on
  `9d468b7ba718004c21cb8a8a20afd86b35fafd48`.
- **M3 — Chess Legal Move Engine:** `PASS` on
  `8afe530cf8dd467a6063c5514767eb7814bcc0f4`.
- **M4 — Undo Semantics:** `PASS` on
  `8afe530cf8dd467a6063c5514767eb7814bcc0f4`.

## Next action

`claude-autopilot` carries the M5 remediation and must be merged into `main` by
a human. After it reaches `main`:

1. Realign `codex-autopilot` exactly to the updated `origin/main`.
2. Independently evaluate M5 from the beginning against that baseline — a fresh
   evaluation, not a spot check of these two fixes. Cover in particular the
   local status line including check and terminal precedence, prospective and
   current-position draw claims and the binding of the declared move, promotion,
   undo, resignation, terminal input locks, orientation, and selection state.
3. Only Codex may then mark M5 `PASS`.

Do not proceed to M6, and do not mark M5 `PASS`, until that independent
re-evaluation passes.
