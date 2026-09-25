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

For testing, the Friends screen also has "Browse all users": a page listing
every user. People you could add come first, each with Add, then your friends,
marked as friends. Each group is most recently active first. Add uses the same
add as the exact-username box, and the page stays open so several people can be
added in a row. It is a testing aid and part of the MVP. Removing it, or
restricting it to admins, is post-MVP work with no deadline (`D071`, `M17.5`,
`M17.6`; `F12` in `docs/FUTURE.md`).

Friendships are mutual immediately.

No approval workflow is required for the chess MVP. A future per-user setting to
require approval for friend requests and/or game invites is anticipated, and
`friendships.status` (`D047`) is the room already made for it.

Not included in the chess MVP:

- chat,
- blocking,
- followers,
- contact syncing,
- social feeds,
- detailed profiles.

### Removing a Friend

Removing a friend affects the friends list only (`D053`, superseding `D013`). It
does not:

- end or close any series,
- disable an automatic rematch,
- terminate or alter any game,
- delete completed games, move history, or historical series records.

A series is left running until a participant explicitly leaves it. Friendship
matters only when a game is first started — see *Starting a Game* — not after.
Removing a friend does not take them out of any group you share.

## Groups

Groups are part of the MVP (`D076`, superseding `D049`'s "deck-builder only").

A group is a named set of people who can all play each other. Adding a friend to
a group lets every member play them, friends or not. One friendship with whoever
added them is enough (`D049`).

Supported:

- create a group with a name (1–48 characters; names need not be unique),
- list the groups you are in, with how many members each has,
- open a group to see its members,
- add one of your own friends to a group you are in,
- Play any other member, exactly as Play works for a friend,
- leave a group.

Membership works like friendship: it takes effect at once, with nothing to
accept. There is no owner and no admin. Nobody can rename or delete a group or
remove anyone else from it. Leaving is the only way out.

Leaving a group removes the Play button for its members who are not friends. It
does not end, alter or close any series or game with them.

Groups are reached from the Friends screen. They do not appear on the dashboard,
and group members who are not friends are not listed under Friends. A game with a
group member appears on the dashboard like any other game.

Group changes are not pushed live. A group screen shows what is current when it
is opened, and when you come back to the app.

Not included in the MVP: renaming, deleting, removing members, group chat, games
with more than two players, and group statistics.

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

A `GameSeries` represents an ongoing sequence of games between the same two
players.

For MVP:

- a pair may have **more than one** `ACTIVE` series at a time (`D053`,
  superseding `D011`), the same way two people could sit at two boards at once,
- selecting Play for a friend who already has an active series **offers** opening
  it or starting another; it does not silently reuse one,
- closed historical series remain available for history,
- a series ends only when a participant explicitly leaves it; removing a friend
  does not close it.

Recommended lifecycle:

```text
ACTIVE
CLOSED
```

The unit a series belongs to is being generalised from the friend pair to a
*table* (a chosen set of 2–4 participants) as part of the deck-builder platform
work; see `D048` and `docs/PLATFORM-REVIEW.md`. Chess remains two-player.

## Starting a Game

A friend, or another member of a group you are in (`D076`), can be selected and
the game starts directly. If they already have an active series with you, the player is offered opening it or starting another
(`D053`); a new series is never silently reused or silently duplicated.

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

For MVP, automatic rematches are always on for an active series (`D053`). A
series stops producing rematches only when a participant explicitly leaves it.

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
- if a participant has already left the series, no rematch follows: that game
  was its last (`D068`),
- resigning a game never leaves or closes the series; leaving is a separate
  action (`M19.8`).

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

The dashboard's top row shows the player's own username at its right-hand end, so
a player can always see the name a friend needs to add them. Screens with Back do
not show it. Tapping it will open user settings; until user settings exist, it is
plain text (`M17.3`, `F35`).

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

Layout (`D073`), decided by the window's size, not the device's orientation:

- **Wide windows (landscape):** the board on the left, as tall as the window allows,
  and a panel on the right with Back, the status, the controls and the move list. The
  panel scrolls on its own and the board never moves. Squares are at least 44 dp.
- **Tall windows (portrait):** Back, the board, then the status and controls, then the
  move list, which scrolls on its own. Squares are at least 48 dp, except on a window
  too narrow for that, where the board is as wide as the window.
- **A window too short for either** keeps a full-size board and scrolls the whole
  screen.
- The game screens have their own Back instead of the app's top row.
- Rotating keeps a local game in progress. Closing the app does not.

Interaction:

```text
tap piece
→ show legal moves
→ tap destination
→ submit move
```

Drag-and-drop is not required for MVP.

## Deferred Features

Do not initially build the features below. The complete list of what is not in the
MVP is `docs/MVP.md`'s *Explicitly Not Required for MVP*, and `docs/FUTURE.md`
holds what is known about each item (`D072`).

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
- detailed statistics,
- deck-building mechanics.
