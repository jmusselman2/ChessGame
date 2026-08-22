# Chess MVP — Product Specification

## Product Vision

Build a lightweight Android chess application designed primarily for repeated asynchronous games between friends.

The app should minimize administrative friction:

```text
Open app
→ immediately see games
→ make move
→ leave
```

Chess is the first implementation of a broader turn-based game platform. The eventual goal is a custom deck-building strategy game.

## Core Experience

A new user should be able to:

1. Install the Android app.
2. Enter a globally unique username.
3. Avoid a traditional password/login flow.
4. Add friends by username.
5. Start a game with a friend.
6. Close the app and return later.
7. Immediately see active games and whose turn it is.
8. Make a legal chess move.
9. Undo their own latest unanswered non-final move.
10. Finish a game.
11. Continue seamlessly into an automatic rematch while the series remains active.

## Identity

### Username

Visible usernames are globally unique identities.

Each user has:

```text
userId
username
usernameNormalized
lastSeenAt
createdAt
```

Rules:

- `userId` is the immutable internal identifier.
- `username` is the human-facing identity.
- usernames are case-insensitively unique.
- `Jordan` and `jordan` cannot be separate accounts.
- usernames are 3–24 characters.
- allowed characters: letters, numbers, underscore, hyphen.
- no spaces.
- username changes are outside MVP.
- if an anonymous account is lost, its username remains reserved for MVP rather than being automatically recycled.

### Authentication

Authentication should be effectively invisible.

First launch:

```text
Anonymous identity created
→ Choose username
→ Dashboard
```

Returning launch:

```text
Restore identity
→ Dashboard
```

No conventional password/login screen is required for MVP.

Account recovery is deferred, but the architecture must allow anonymous identities to be upgraded later.

## Friends

Friends are part of the MVP.

Supported:

- add friend by exact unique username,
- list friends,
- remove friend,
- start/open a game series with a friend.

Friendships are mutual immediately.

No approval workflow is required for MVP.

Not included:

- chat,
- blocking,
- followers,
- groups,
- contact syncing,
- social feeds,
- detailed profiles.

### Removing a Friend

Removing a friend must not delete:

- the current game,
- completed games,
- move history,
- historical series records.

If the pair has an active series:

1. the current game remains playable until it ends,
2. automatic rematch is disabled for that series,
3. when the current game ends, no new game is created,
4. the series becomes `CLOSED`.

If the friendship is later restored, a new active series may be started.

## Last Seen

Track `lastSeenAt` internally from the beginning.

Update on meaningful activity such as:

- app foreground/open,
- move,
- undo,
- starting a game,
- other meaningful authenticated interaction.

Do not continuously heartbeat.

The MVP does not need to prominently display last-seen information.

## Game Series

A `GameSeries` represents the ongoing sequence of games between two friends.

For MVP:

- one `ACTIVE` series per friend pair,
- selecting Play/Open should open the existing active series if one exists,
- do not silently create parallel active series,
- closed historical series remain available for history,
- removing a friend causes the current active series to close after its current game finishes.

Recommended lifecycle:

```text
ACTIVE
CLOSED
```

## Starting a Game

A friend can be selected and the game starts directly.

No per-game invite code.

No game acceptance flow.

Initial White/Black assignment is random.

## Automatic Rematches

Automatic rematches are the default for an active series.

When a game ends normally and the series remains active:

```text
Game ends
→ result saved
→ next game created automatically
→ colors alternate
→ next game is ready
```

No:

- rematch request,
- acceptance,
- acknowledgement,
- waiting state.

Future versions may allow automatic rematches to be disabled manually.

For MVP, automatic rematches are on unless the friendship is removed and the current series is being closed.

## Takebacks / Undo

A player may undo their own most recent move without opponent confirmation while:

- the game is active,
- their move is the latest active move,
- the opponent has not responded.

Example:

```text
Jordan: Nf3
```

Jordan may undo.

After:

```text
Jordan: Nf3
Alex: Nc6
```

Jordan may not undo `Nf3`.

Alex may undo `Nc6`.

If Alex undoes `Nc6`, Jordan's `Nf3` becomes the latest unanswered move again and may be undone.

### Final Moves

A game-ending move is immediately final.

It cannot be undone.

There is no pending-final state.

## Resignation

Resignation is part of MVP.

The UI should confirm before submitting resignation.

Once accepted by the server:

- the game ends,
- resignation cannot be undone,
- result is saved,
- an automatic rematch is created if the series remains active,
- otherwise the series closes after the game.

## Chess Rules

MVP supports standard chess including:

- standard movement,
- captures,
- check,
- self-check prevention,
- checkmate,
- stalemate,
- castling,
- en passant,
- pawn promotion,
- insufficient material,
- repetition draws,
- move-count draws,
- resignation.

Pawn promotion must allow:

- Queen,
- Rook,
- Bishop,
- Knight.

Do not auto-promote to Queen.

### Draw Semantics

The engine must distinguish claimable draws from automatic draws.

Claimable:

- threefold repetition,
- fifty-move rule.

Automatic:

- fivefold repetition,
- seventy-five-move rule,
- stalemate,
- insufficient material.

A claimable draw requires an explicit `ClaimDraw` action by an entitled player.

The engine must determine whether a valid draw claim exists according to the game history and current/prospective legal move state.

Draw offers by agreement are not part of MVP.

## Dashboard

Returning users should go directly to the dashboard.

Recommended hierarchy:

```text
YOUR TURN

Alex
White • Move 18

Sam
Black • Move 7


THEIR TURN

Chris
White • Move 24


FRIENDS

Alex       [Play/Open]
Chris      [Play/Open]
Sam        [Play/Open]
```

Completed games belong in history rather than dominating the home screen.

## Game Screen

Display:

- opponent username,
- chess board,
- current turn,
- selected square,
- legal move highlights,
- last move highlight,
- check indication,
- move history,
- Undo when legal,
- Claim Draw when a valid claim is available,
- Resign.

Board orientation:

- own side at the bottom.

Interaction:

```text
tap piece
→ show legal moves
→ tap destination
→ submit move
```

Drag-and-drop is not required for MVP.

## Deferred Features

Do not initially build:

- AI opponent,
- ratings,
- matchmaking,
- public games,
- tournaments,
- chat,
- contact syncing,
- push notifications,
- chess clocks,
- draw offers by agreement,
- custom themes,
- elaborate animations,
- spectators,
- account recovery UI,
- username changes,
- multiple simultaneous active series against one friend,
- detailed statistics,
- deck-building mechanics.
