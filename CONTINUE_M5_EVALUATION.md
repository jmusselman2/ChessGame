# Continue the Independent ChessGame Milestone Evaluation

Continue the independent ChessGame milestone evaluation in
`C:\Codex\ChessGame` on the existing `codex-autopilot` branch.

## Fixed baseline and branch history

- Production baseline: `8afe530cf8dd467a6063c5514767eb7814bcc0f4`
- Last pushed evaluator checkpoint: `f889357269707fc9f7949a893108d0fe80251c68`
- M3: `PASS`
- M4: `PASS`
- Current milestone: M5 — Local Android Chess
- The branch contains evaluator-only M3/M4 commits that must be preserved.

Start by checking `git status`, `git rev-parse HEAD`, and
`docs/CODEX_EVALUATION_STATE.md`. Reconcile the following continuation notes
with the actual filesystem and running processes before acting.

## Work completed in the interrupted M5 evaluation

The M5.1–M5.7 backlog requirements, relevant `PRODUCT.md`,
`ARCHITECTURE.md`, `DECISIONS.md`, `DEVELOPMENT.md`, implementation history,
Android production code, and existing board/local-game tests were inspected.

The production path was traced as:

`MainActivity` → `ChessApp` → `Destination.LocalGame` → `LocalGameScreen` →
`BoardInteraction` / `GameControls` → `game-core`.

Verified passing behavior includes:

- the local game is reachable from the application shell;
- starting state, selection/deselection, engine-derived destinations, illegal
  tap rejection, capture, castling, promotion choices, orientation, move
  history, undo, current-position claims, resignation, and terminal tap locks;
- ordinary Compose recomposition retains local state through `remember`;
- activity/process recreation and leaving/reopening the local destination start
  a new local game. The repository promises persisted anonymous-session
  restoration, not persisted local-game restoration, so this was classified as
  a documented lifecycle limitation rather than an M5 defect;
- the previously documented manual Fool's-mate/device play-through remains
  repository evidence, but it is not a substitute for this independent review.

Clean automated baseline results already obtained:

- `:android-app:testDebugUnitTest --rerun-tasks`: `PASS`, 370 tests, 0
  failures/errors/skips.
- Clean `:android-app:connectedDebugAndroidTest --rerun-tasks` on Android 17:
  `PASS`, 1/1 existing instrumentation test.

## Current evaluator changes

Two untracked evaluator test files were added; no production code was changed:

- `android-app/app/src/test/java/com/jmussel/chessgame/ui/board/M5AdversarialTest.kt`
- `android-app/app/src/androidTest/java/com/jmussel/chessgame/ui/board/M5LocalUiAdversarialTest.kt`

`M5AdversarialTest` contains three host-side tests:

1. en passant selected and executed through the actual local tap path;
2. prospective threefold entitlement reported by the declared-move
   `game-core` oracle while the local UI exposes no action before committing
   that move;
3. the equivalent prospective fifty-move gap.

The focused host-side run passed all three:

```powershell
$env:GRADLE_USER_HOME='C:\Codex\ChessGame\.gradle'
.\gradlew.bat :android-app:testDebugUnitTest --tests '*M5AdversarialTest' --rerun-tasks --console=plain
```

`M5LocalUiAdversarialTest` contains two rendered Compose tests:

1. a deliberately failing regression requiring a non-terminal check to be
   visible to the local player;
2. a passing checkmate case requiring the result to be shown and Undo/Resign
   controls to remain absent.

The Compose test sources now compile. An initial import mistake for
`assertExists`/`assertDoesNotExist` was corrected by removing those imports;
they are interaction members in this Compose version.

## Confirmed M5 defect batch

Revalidate, but do not casually discard, these two product-level findings:

### 1. Non-terminal check is not presented in the local UI

`docs/PRODUCT.md` requires a check indication. The online screen implements
one. `LocalGameScreen.statusFor` returns only `"<side> to move"` for every
non-terminal position and never queries `ChessRules.isInCheck`. A legal local
sequence such as `1. e4 f6 2. Qh5+` therefore leaves the checked player with no
check indication.

This is distinct from terminal handling: checkmate and all other terminal
results are presented from `game-core` and interaction controls are locked.

### 2. Prospective threefold and fifty-move claims are unreachable locally

The remediated M3 engine supports claims bound to a contemplated legal move via
the declared-move overloads required by `PRODUCT`, architecture §23, and D038.
The local UI calls only:

- `ChessRules.availableDrawClaims(state.game)`; and
- `ChessRules.claimDraw(state.game, claim)`.

Its destination tap immediately commits the move. There is no local UI state or
action that carries a declared move into the prospective query/claim overload.
Consequently, both prospective threefold and prospective fifty-move
entitlements cannot be exercised before the move; the move is committed,
history and turn change, and only then is a current-position claim shown to the
other side to move.

The host-side tests intentionally characterize this mismatch without
prescribing a speculative remediation interaction or weakening D038's binding
rule.

Inspect reasonably related M5 presentation gaps before finalizing the batch.
In particular, `PRODUCT.md` also mentions a last-move highlight while
`LocalGameScreen` does not pass `lastMove` to `ChessBoard`; decide carefully
whether that is an M5 requirement or belongs to later online UI scope. Likewise,
`BoardRendering` computes file/rank labels but `ChessBoard` does not draw them;
do not call this defective unless repository requirements genuinely require
visible coordinates. Distinguish real product defects from polish, robustness,
or overly restrictive test assumptions.

## Emulator state and next verification

The prior turn was interrupted while restarting the disposable
`ChessPlayer2` AVD (`emulator-5556`) headlessly. A bounded boot-status command
may still have been running when the turn was aborted; inspect rather than
assuming.

- `emulator-5554` (`ChessPlayer1`) is booted but has a differently signed
  `com.jmussel.chessgame` installation. Do **not** uninstall it or erase its app
  data merely to run tests.
- `emulator-5556` was used as the disposable target. The runner removed its
  conflicting app, then it went offline during a later test-APK install. It was
  restarted with:

```powershell
Start-Process -FilePath 'C:\Users\Carla\AppData\Local\Android\Sdk\emulator\emulator.exe' `
  -ArgumentList @('-avd','ChessPlayer2','-port','5556','-no-window','-no-audio','-no-snapshot-save') `
  -WindowStyle Hidden
```

Check boot readiness with the SDK's `adb.exe`, wait for
`sys.boot_completed=1`, confirm the conflicting package is absent, then run the
expanded connected tests on `emulator-5556`:

```powershell
$env:GRADLE_USER_HOME='C:\Codex\ChessGame\.gradle'
$env:ANDROID_SERIAL='emulator-5556'
.\gradlew.bat :android-app:connectedDebugAndroidTest --rerun-tasks --console=plain
```

Expected product result after successful installation: three instrumentation
tests total, with the existing context test and terminal-screen test passing,
and `aNonTerminalCheckIsShownToTheLocalPlayer` failing because no node contains
“Check”. Record the actual result; do not report an install/offline failure as a
product failure.

Also run:

- the complete Android unit suite after adding the three tests (expected total:
  373 if no other tests changed);
- Android lint/formatting and an appropriate debug build;
- `git diff --check`;
- any other focused tests needed to validate the coherent M5 batch.

Do not change production code.

## Required stop path

If the two findings remain legitimate, M5 is `DEFECT FOUND` and M6 must not
begin.

1. Add concise M5 critic/test reports under `evals/M5/`.
2. Update `docs/CODEX_EVALUATION_STATE.md` concisely with:
   - baseline `8afe530cf8dd467a6063c5514767eb7814bcc0f4`;
   - current milestone M5;
   - status `DEFECT FOUND`;
   - the complete coherent finding batch;
   - M3 and M4 still listed as independently passed;
   - exact next action: Claude remediation followed by another independent M5
     re-evaluation.
3. Preserve the existing M3/M4 evaluation artifacts.
4. Commit all M5 evaluator changes on `codex-autopilot`.
5. Push `codex-autopilot` (ordinary push; do not reset/re-align it).
6. Stop without evaluating M6.

The final response must report:

- production baseline SHA;
- M5 `PASS` or `DEFECT FOUND`;
- requirements evaluated;
- automated tests/results, clearly separating product failures from emulator
  infrastructure attempts;
- manual-only acceptance criteria remaining;
- adversarial tests added or changed;
- all defects found;
- updated checkpoint status;
- whether M6 began;
- current milestone/status;
- pushed `codex-autopilot` SHA;
- exact next action.
