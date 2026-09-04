# M5 — Local Android Chess: Critic Report

## Scope and verdict

Independently evaluated M5.1–M5.7 against production baseline
`8afe530cf8dd467a6063c5514767eb7814bcc0f4`. The review traced the real Android
path from `ChessApp` into `LocalGameScreen`, `BoardInteraction`, `GameControls`,
and `game-core`; reconciled the backlog with `PRODUCT`, architecture section 23,
and D038; inspected existing and adversarial host/UI tests; and exercised the
rendered Compose screen on a disposable emulator.

**Verdict: DEFECT FOUND.** Two product defects prevent M5 from passing. No
production code was changed, and M6 was not started.

## M5-01 — A non-terminal check is not presented

`PRODUCT` requires a check indication, and local play already presents whose
turn it is and terminal results as game status. The local status path does not
represent the non-terminal check state: `LocalGameScreen.statusFor` returns only
`"<side> to move"` whenever `game.result` is null and never asks
`ChessRules.isInCheck`.

The legal sequence `1. e4 f6 2. Qh5+` leaves Black in check according to
`game-core`, but the rendered local screen contains no text identifying check.
The independent Compose regression fails on that missing node. This is not a
terminal-state defect: the paired checkmate test passes, shows `CHECKMATE`, and
confirms Undo and both resignation controls remain absent after the game ends.

## M5-02 — Prospective claimable draws have no local action

`PRODUCT` requires draw entitlement to account for current and prospective
legal-move state. Architecture section 23 requires the engine to expose the
relevant prospective condition, and D038 makes the contemplated legal move a
binding part of a prospective threefold or fifty-move claim.

The remediated engine supplies declared-move overloads, but the local Android
path calls only:

- `ChessRules.availableDrawClaims(state.game)`; and
- `ChessRules.claimDraw(state.game, claim)`.

Selecting a legal destination immediately calls `ChessRules.applyMove`. No UI
state or action carries a contemplated move into the declared-move query or
claim overload. Therefore an entitled player cannot claim immediately before a
move that would create the third occurrence or reach 100 halfmoves. After the
ordinary tap commits that move, history and turn have changed and the
current-position claim is offered on the next player's turn. This violates the
binding semantics rather than merely omitting optional presentation.

The host-side adversarial tests prove both boundaries through the actual local
tap path and retain en passant as a positive special-move control.

## Related observations excluded from the defect batch

- Last-move highlighting is explicitly assigned to M14.10's canonical online
  screen acceptance criteria and was introduced there. M5.1–M5.7 do not require
  it for the local pass-and-play screen, so its absence locally is not reported
  as an M5 defect.
- `BoardRendering` exposes orientation-aware file/rank label data, but no M5 or
  product acceptance criterion requires visible coordinates. Not drawing those
  labels is not a defect.
- Activity/process recreation and leaving/reopening the local destination begin
  a fresh local game. The documented persistence promise is for the anonymous
  account/session, not local-game state, so this is a lifecycle limitation and
  not an M5 defect.

## Required handoff

Claude remediation must make non-terminal check visible in local play and add a
local claim path that binds an exact contemplated legal move for both
prospective claim types while retaining current-position claims. Another
independent M5 evaluation is required before M6 can begin.
