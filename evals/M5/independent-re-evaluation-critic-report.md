# M5 — Independent Remediation Re-evaluation: Critic Report

## Scope and verdict

M5.1–M5.7 were re-evaluated from the beginning against the synchronized
production baseline `0da5f42b7deacde1aaa698f09d32b19a72e6df89`. The review
reconciled the backlog, product specification, architecture section 23,
decisions D038 and D041, retained evaluator reports and tests, remediation,
Android implementation, `game-core`, host tests, and rendered Compose behavior.

**Verdict: PASS.** No legitimate unresolved M5 defect was found. Production
code was not changed.

## Prior finding closure

### M5-01 — Non-terminal check presentation: CLOSED

`GameControls.statusFor` asks `game-core` whether the side to move is in check.
An ordinary position presents only the turn, while a terminal result takes
precedence over the live-check label. Retained coverage passed, and new coverage
proved the behavior with a different live-check sequence (`Bxf7+`, followed by
`Kxf7`) and with a checkmating move from a halfmove-clock boundary position.
Terminal controls and board input remain locked.

### M5-02 — Prospective move-bound draw claims: CLOSED

The Android interaction state retains the exact contemplated `Move`, obtains
claim availability from `ChessRules.availableDrawClaims(game, move)`, and
submits that same move to `ChessRules.claimDraw`. A successful claim terminates
the game before the move is played and does not append it to history. The core,
not Android-side duplicated chess logic, remains the rules authority.

Retained coverage passed for prospective threefold and fifty-move claims,
different-move and illegal-move rejection, current-position claims, capture and
pawn-move halfmove resets, promotion, cancellation, playing instead of claiming,
undo, resignation, terminal locks, selection cleanup, and orientation. New
generalized coverage compared the UI interaction result with the core for every
legal destination in representative normal, capture, castling, en-passant,
check, halfmove-99, third-repetition, and promotion positions.

## Remaining M5 scope

The production flow was audited for startup, board rendering, auto-flipped
pass-and-play orientation, selection and deselection, core-sourced legal
destinations, legal and illegal input, captures, castling, en passant,
promotion, check, checkmate, stalemate and draw presentation, turn indication,
history, undo, resignation, terminal-state locks, and Compose state cleanup.
The complete rendered game path was exercised on an API 36 emulator.

Activity/process persistence and online canonical-state behavior are not M5
acceptance criteria in this repository. The earlier retained manual rendering
record remains useful, while the new instrumentation coverage provides automated
proof of the complete pass-and-play terminal path.
