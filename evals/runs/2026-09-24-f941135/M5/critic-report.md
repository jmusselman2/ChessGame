# M5 Critic Report

## Fresh assessment

**Provisional fresh verdict:** PASS

This section was written before consulting any retained M5 evaluation report.

### Scope and evidence

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M5: `23d7d4a4ce8967aabb2f22c36140bcb33c12d472`
- Requirements: `docs/BACKLOG.md` M5.1 through M5.7
- Production implementation: local board rendering, interaction, controls,
  layout, and pass-and-play screen under the Android board UI package
- Current callers: the local destination and view-model-owned local game state
- Verification: focused and complete Android unit suites, Android build/lint,
  complete core tests, and isolated rendered-UI instrumentation on the Kindle

### Requirement assessment

| Requirement | Fresh result | Evidence summary |
| --- | --- | --- |
| M5.1 board rendering | PASS | 8x8 rows, piece placement/glyphs, colours, labels, and starting grid derive from `game-core`. |
| M5.2 piece selection | PASS | Only the side to move selects; selection highlights and clears without changing the game. |
| M5.3 legal highlights | PASS | Destinations are filtered from `ChessRules`; pins, captures, castling, and promotion de-duplication are covered. |
| M5.4 local moves | PASS | Legal taps apply through `ChessRules`; captures, castling, promotion choice, check status, history, turn change, and terminal lock are covered. |
| M5.5 orientation | PASS | The side to move appears at the bottom; ranks/files and tap mapping reverse together, with manual flip preserving square identity. |
| M5.6 history/Undo/Claim Draw | PASS | Numbered history, guarded Undo, current claims, and declared-move claims are rendered and wired to core behavior. |
| M5.7 complete local game | PASS | A full Fool's mate was played through rendered board taps on the Kindle; checkmate, history, hidden terminal controls, and ignored post-game input were verified. |

The Android layer keeps chess rules in `game-core`: representative legal
destinations were compared against the engine, and local UI transitions were
checked against the core outcomes. The declared-move prompt preserves the
choice to claim, play, or cancel without silently committing the move.

### Device isolation

The preferred Kindle `G090MJ0574130HMR` (`KFDOWI`, Android 5.1.1/API 22) was
used. A detached disposable worktree added only a debug
`applicationIdSuffix = ".eval.m5"`, producing isolated app/test packages. The
owner package `com.jmussel.chessgame` was not replaced, cleared, or launched.
After verification, both evaluator packages and the disposable worktree were
removed, the USB stay-awake value was restored to `0`, and the owner package
was confirmed still installed.

### Fresh findings

No confirmed production defect was found.

Two evaluator/environment issues were corrected and rerun:

1. The first install selector expected `app-debug.apk`; the actual file was
   `android-app-debug.apk`. `adb` received no APK and changed nothing. The path
   selector was corrected and both isolated APKs then installed successfully.
2. The first six-test Compose run failed uniformly with no hierarchy because
   the Kindle was asleep, display off, and locked. Device state and logcat
   confirmed the activity was immediately stopped. The Kindle was woken and
   unlocked with temporary USB stay-awake; the exact complete-game test then
   passed, followed by the full six-test set and the nine-test layout set.

Neither issue is a production defect.

## Historical comparison

After the fresh verdict was recorded, all retained M5 reports under
`evals/runs/2026-09-17-e2b3287/M5/` were reviewed. The original evaluation
found two defects: live check was not presented, and the local UI exposed no
move-bound path for prospective threefold/fifty-move claims. The remediation
and independent re-evaluation later closed both.

Current `GameControls.statusFor` asks `ChessRules.isInCheck` while preserving
terminal-result precedence. Current `BoardInteraction` retains the exact
declared move and asks the core for only the claims that move adds; the screen
then offers claim, play, or cancel. The retained `M5AdversarialTest`,
`DeclaredDrawClaimTest`, `M5LocalUiAdversarialTest`, and both independent
re-evaluation suites all pass in the fresh host/device selections.

The historical final PASS used an API 36 emulator. This run independently
extends the device evidence to the preferred Android 5.1/API 22 Kindle,
including the complete rendered game, check/checkmate presentation,
prospective claim actions, orientation/tap mapping, and layout behavior.

No additional production or evaluator change is justified. **Final M5
verdict: PASS.**
