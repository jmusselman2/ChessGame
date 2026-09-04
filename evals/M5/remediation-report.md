# M5 — Local Android Chess: Remediation Report

Written by Claude on `claude-autopilot`, after independently validating the
findings in `evals/M5/critic-report.md` and `evals/M5/test-report.md`. Those two
reports are the evaluator's record of what Codex observed and are left as
written.

## Baseline reconciliation

Codex evaluated M5 against production baseline
`8afe530cf8dd467a6063c5514767eb7814bcc0f4`. `claude-autopilot` and `main` had
since moved on by three commits (`0db6f29`, `aefa391`, `c04cbe7` — the M17.1
beta APK work and a documentation move). None of them touch
`android-app/.../ui/board` or `game-core`:
`git diff 8afe530 claude-autopilot -- android-app/ game-core/` is confined to
`android-app/app/build.gradle.kts`. Both findings therefore reproduce unchanged
on the newer line, and neither was already fixed by newer production work.

## M5-01 — A non-terminal check is not presented

**Independently validated: legitimate.**

`LocalGameScreen.statusFor` returned `"${game.sideToMove} to move"` for every
position with no result and never consulted `ChessRules.isInCheck`. `PRODUCT`'s
*Game Screen* lists a check indication among what a board displays, the engine
answers the question already, and the online screen already presents it as
`"Your move • Check"` (`OnlineGame.turnFor`). A local player left in check was
told only whose move it was.

Fixed by moving the status text to `GameControls.statusFor`, beside the other
questions the screen asks `game-core`, and appending `— Check` when
`ChessRules.isInCheck` says so. A terminal result still takes precedence, so a
checkmated game reads `BLACK wins — CHECKMATE` and the terminal controls and
input locks are untouched. No chess rule was added to the UI.

Codex's rendered regression
(`M5LocalUiAdversarialTest.aNonTerminalCheckIsShownToTheLocalPlayer`) is kept
exactly as written; it was a correct statement of the requirement.

### One related observation, deliberately not acted on

The same `PRODUCT` list also names a last-move highlight, which the local screen
does not draw. Codex evaluated it and excluded it from the defect batch,
assigning it to `M14.10`'s canonical online screen criteria. That reasoning cuts
against `M5-01` too, since `M14.10` names check state in the same criterion.
The distinction that survives is narrower than the one the report gives: the
status line is the local screen's own presentation of game state and was
misleading without the check, whereas the last-move highlight is board
decoration built for server-supplied `lastMove` data the local screen has no
analogue for. That is a judgement call and it is recorded here rather than
silently resolved — but expanding the remediation to last-move highlighting
would have overruled an evaluator's explicit non-finding, so it was left alone.

## M5-02 — Prospective claimable draws have no local action

**Independently validated: legitimate.**

`PRODUCT` *Draw Semantics* requires claim entitlement to be decided from "the
game history and current/prospective legal move state", `ARCHITECTURE` §23
requires the engine to expose "any relevant prospective legal move condition",
and `D038` made the declared move a binding part of such a claim and added the
`ChessRules` overloads. Local play called only the no-move overloads, and
`BoardInteraction.play` committed the move on the destination tap. So the
entitlement was real and unreachable: a player one move from the third
occurrence or from the hundredth halfmove had to play the move, after which the
position — and the claim — belonged to the other player's turn.

Fixed by declaring such a move instead of playing it. A destination tap whose
move would *add* a claim (`availableDrawClaims(game, move)` less
`availableDrawClaims(game)`) raises a `DeclaredMove` holding that exact move and
those claims; the screen offers the claim, playing the move anyway, or backing
out. Claiming calls `ChessRules.claimDraw(game, claim, declaredMove)`, which
ends the game from the position in front of the player and never plays the move.
The engine remains the source of truth for both the entitlement and the
legality of the declaration. `D041` records the decision, including why the
alternative — leaving the tap alone and listing prospective claims beside the
board — was rejected.

Claims the current position already carries are excluded from that subtraction,
so `GameControls.availableDrawClaims` / `canClaimDraw` / `claimDraw` keep their
existing meaning and behaviour exactly, as `D038` requires of the no-move
overloads.

### Codex regressions modified, and why

`M5AdversarialTest`'s two prospective tests asserted that the destination tap
committed the move and that no prospective action existed anywhere. That is a
characterization of the defect, not a requirement — the same report asks for a
remediation that lets the claim be made before the move, which cannot coexist
with "the tap commits it". Both scenarios, both engine-oracle assertions, and
the `assertFalse(GameControls.canClaimDraw(...))` boundary (current-position
claims are unchanged — `D038`) are kept unchanged. The two assertions that
described the defect now assert the remediation: the tap declares the move and
offers the claim, and `BoardInteraction.playDeclaredMove` from there reaches
exactly the state the original assertions described. The tests were renamed to
match what they now prove. Nothing was weakened or removed.

`LocalGameTest.aGameCanBeFinishedByClaimingARepetitionDraw` and
`GameControlsTest.shuffled` play a move that creates the third occurrence, which
is now declared first. Both gained an explicit "play it anyway" step; every
assertion they made is unchanged.

## Added coverage

`DeclaredDrawClaimTest` (19 host-side cases) covers prospective threefold and
fifty-move claims, the claim ending the game without playing the declared move,
exact-move binding, one halfmove early, a different move from the same piece,
pawn and capture clock resets, an illegal destination, claiming with no
declaration, a claim the declaration does not entitle, promotion still asking
for the piece, a current-position claim not being declared again, cancelling and
carrying on, re-declaring and playing, backing out by tapping the board, board
orientation and selection state, terminal input locking after a claim,
resignation while a move is declared, and undo dropping the declaration.

`GameControlsTest` gained three status cases: whose move it is, a live check,
and terminal results taking precedence.

`LocalDrawClaimUiTest` (3 rendered Compose cases) covers what only the screen
can answer: that `LocalGameScreen` draws the prompt, and that its buttons claim
the draw without playing the move, play the move instead, and back out. Codex's
`M5LocalUiAdversarialTest` is unchanged and both of its cases now pass.

## Status

M5 is **not** marked `PASS`. Only Codex may do that, from a fresh independent
evaluation after this reaches `main`.
