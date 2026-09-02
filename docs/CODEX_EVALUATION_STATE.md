# Codex Evaluation State

- **Current baseline:** `d80ac61523ed323bd5e80800affda00265ac20b3`
- **Current milestone:** M3 — Chess Legal Move Engine
- **Status:** `DEFECT FOUND — remediation applied on claude-autopilot, not yet
  independently re-evaluated by Codex`
- **Evaluation artifacts:** `evals/M3/critic-report.md`,
  `evals/M3/test-report.md`, `evals/M3/re-evaluation-critic-report.md`,
  `evals/M3/re-evaluation-test-report.md`, and
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M3AdversarialTest.kt`

## Findings and disposition

- **Resolved and independently verified:** the complete six-scenario
  en-passant defect batch. All six regressions pass unchanged. Eligibility is
  derived consistently from target rank, target occupancy, capture geometry,
  and the opposing bypassed pawn.
- **Confirmed:** a finished game still advertises legal moves.
  `ChessRules.legalMoves` returns moves and `ChessRules.isLegal` returns `true`
  after `GameState.result` finalizes the game, while `ChessRules.applyMove`
  rejects that same transition. The two new regressions fail on the current
  baseline.

The standard-position legal-move oracle passes through depth four (20, 400,
8,902, and 197,281 nodes). No production code was changed by this evaluation.

## Remediation applied on `claude-autopilot` (pending Codex re-evaluation)

An independent review on `claude-autopilot` confirmed the terminal-state query
inconsistency and addressed it. This section records what was done; it is **not**
an independent re-evaluation, and M3 is **not** `PASS`.

- **Confirmed independently.** By reading the source, `ChessRules.legalMoves` and
  `ChessRules.isLegal` (the `GameState` overloads) delegated straight to
  `LegalMoves` with no `state.isOver` check, while `ChessRules.applyMove`,
  `claimDraw`, and `resign` all guard on `!state.isOver`. The two committed
  regressions `aFinishedGameHasNoLegalMoves` and
  `noMoveIsLegalAfterTheGameIsFinished` failed on the baseline. Real-world path:
  `server` `GameCommandService.makeMove` gates on `ChessRules.isLegal` and then
  calls `applyMove`, so a move submitted into an already-finished game slipped
  the gate and made `applyMove` throw instead of returning
  `CommandResult.IllegalMove`.

- **Fix (`game-core/.../chess/ChessRules.kt`).** `legalMoves(state)` now returns
  an empty list and `isLegal(state, move)` now returns `false` when
  `state.isOver`, matching `applyMove`. The guard is placed on the `ChessRules`
  entry point where the other lifecycle guards already live (`D028`);
  `LegalMoves` stays the pure geometry + self-check layer. `hasNoLegalMoves` was
  repointed at `LegalMoves.forSideToMove` directly so it remains a pure
  position-geometry predicate for `terminalResult`, leaving
  `isCheckmate` / `isStalemate` / `terminalResult` behaviour unchanged.

- **Delegating paths covered.** `ChessRules.legalMoves(game: ChessGame)` and
  `ChessRules.isLegal(game: ChessGame, move)` forward to the fixed `GameState`
  overloads. New regression `theChessGameQueryOverloadsAlsoStopAtAFinishedGame`
  locks this, and `aRealCheckmateEmptiesTheLegalMoveList` proves it for a
  genuine terminal result produced by `applyMove` (not a synthetic
  `copy(result = ...)`).

- **Audited and found already-consistent — no change made.**
  `availableDrawClaims` / `canClaimDraw` vs `claimDraw`:
  `Repetition.canClaimThreefold` and `MoveCountDraws.canClaimFiftyMove` already
  begin with `!state.isOver`, so a finished game advertises no claim
  (`ClaimDrawTest.anAutomaticDrawNeedsNoClaim`,
  `aClaimInAFinishedGameIsRejected`). `undoableSide` / `canUndo` / `undo`
  already return `null` / reject once `game.isOver`. `resign` is guarded and has
  no paired "can resign" query. `terminalResult` returns the stored
  `state.result` before recomputing anything. No further legitimate instance of
  the pattern was found.

- **Evaluation artifacts and adversarial tests preserved.** No evaluation report
  or existing `M3AdversarialTest` case was weakened or removed; the perft oracle
  and all en-passant regressions are retained.

- **One existing feature test corrected.** `ResignTest.aResignedGameIsOver` had
  asserted `ChessRules.legalMoves(resignedGame).isNotEmpty()` ("the position
  still has moves in it") — the exact pre-fix contract this defect is about. Its
  D017 purpose (the game is over; neither side may undo) is unchanged and now its
  move-query assertion agrees with `applyMove`. This is the only baseline test
  that encoded the old contract; `CheckmateStalemateTest`'s `legalMoves` /
  `isLegal` assertions are all on in-progress positions and were not touched.

## Exact next action

The accepted defect paths were fixed on `claude-autopilot` but have **not** been
independently re-evaluated by Codex. Proceed:

1. Human integration of the reviewed `claude-autopilot` remediation into `main`.
2. Realign `codex-autopilot` to the updated `origin/main`.
3. Independently re-evaluate M3 from the beginning.

Do not proceed to M4, and do not mark M3 `PASS`, until that independent
re-evaluation passes.

## Completed milestones

- **M2 — Chess Domain Model:** `PASS` on
  `9d468b7ba718004c21cb8a8a20afd86b35fafd48`.
