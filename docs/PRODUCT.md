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

Chess is the first implementation of a broader turn-based game platform. The eventual goal is a custom deck-building strategy game, which will ship as a separate app on the same platform (`D082`).

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
- username changes are outside MVP and wait for real authentication (`F13`,
  `D098`). When implemented, the authenticated owner may rename the shared user;
  every old normalized name remains permanently reserved as an unavailable,
  non-searchable tombstone.
- if an anonymous account is lost, its username remains reserved for MVP rather than being automatically recycled.
- entering a username that already exists attaches this installation to that existing user, with no verification (`D082`). A lost username is regained by typing it again, and anyone else who types it reaches the same account. This is a deliberately insecure prototype behaviour that must be replaced before untrusted or public use. Implemented by `M20.4`.
- an installation that already has a username cannot claim a different one, whether the other name is new or already someone's (`D083`). Entering its own name again changes nothing.

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

Account recovery is deferred, but the architecture must allow anonymous identities to be upgraded later. Until real authentication exists (`F13`), the prototype username claim above is the only way back into an account, and it is not secure.

### One account across products

ChessGame and the future Deck Builder are separate products and separate apps. Someone who installs one need not know the other exists, and neither app mentions the other. They share one account (`D082`): the same username entered in either app is the same user, so the friends list, groups and profile are the same in both. Each app shows them simply as the user's own. Games, series and history belong to the product they were played in, and each app shows only its own.

## Friends

Friends are part of the MVP.

Supported:

- add friend by exact unique username,
- list friends,
- remove friend,
- start/open a game series with a friend.

Post-MVP (`M22.4`, `D096`), each row in the dashboard's FRIENDS section also
shows a compact `W-L-D` record. It counts every finished game with that friend
across all chess series. Games still in progress do not count, and the server,
not the client, computes the totals.

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

Post-MVP (`M22.5`, `D099`), a friend or current shared-group member can open a
relationship profile. It shows the username, join month/year, lifetime chess
record against the viewer, and only groups they share. It is not public, and it
does not expose the subject's other group memberships.

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

For chess:

- a pair has at most one `ACTIVE` series at a time (`D095`, superseding `D053`
  rule 1),
- selecting Play for someone who already has an active series opens it,
- closed historical series remain available for history,
- a series ends only when a participant explicitly leaves it; removing a friend
  does not close it.

Leaving ends the current game as well as the series (`D095`). A game in which a
move has been played ends as a resignation by the leaver. A zero-move game is
deleted without a result. No closed series has an unfinished game.

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
the game starts directly. If they already have an active series with you, Play
opens that series (`D095`); it never creates or offers a parallel one.

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

Automatic rematches are always on for an active series. A series stops producing
rematches only when a participant explicitly leaves it. Leaving also ends its
current game (`D095`), so there is no closed-series game left to finish.

Playing the next game from the finished game's screen replaces the finished game, so
Back leads to where the finished game was opened from, however many games in a row the
player has played (`D106`). Finished games are found in History.

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

### Undo Requests

Once Alex has replied, Jordan cannot undo `Nf3` alone, but may send Alex an undo
request (`D091`). Jordan asks on Jordan's turn ("Ask to undo"), and may have one
request pending at a time. Jordan sees "Undo requested. Waiting for Alex." and has no
cancel, but can still play: a move withdraws the request.

Alex sees "Jordan asks to undo g1f3 and your b8c6", with Accept and Decline. Neither
asks for confirmation. Alex's own "Undo your move" counts as Accept while the request
waits.

- **Alex accepts:** `Nc6` and `Nf3` are both undone, and Jordan is to move again from
  the position before `Nf3`. Jordan sees "Alex accepted your undo request: g1f3 and
  b8c6 undone".
- **Alex declines:** nothing changes. Jordan sees "Alex declined your undo request"
  and may ask again straight away. Alex may again undo `Nc6` alone.
- **The game changes first:** Jordan moves, claims a draw, or resigns, or Alex
  resigns. The request is gone, and an answer to it is refused as out of date.

If Jordan asks just as Alex undoes `Nc6`, before either has seen the other, both
are combined whichever reaches the server first: `Nc6` and `Nf3` are undone. Alex sees
"Jordan had asked to undo g1f3, so both moves were undone". Jordan sees the accepted
note if the request got there first, or "Alex had undone b8c6, so g1f3 was undone
too" if Alex's undo did.

Each note stays until the next change to the game.

Leaving the series does not clear a request, and a request never expires on its own.
While it is pending, Alex's dashboard lists the game under YOUR TURN with "Jordan asks
to undo", and Jordan's row says "Undo requested". This is for online games; pass-and-play and games against the computer
already undo moves.

A game against the computer uses a takeback instead (`D086`, see *Playing the
Computer*).

### Redo

Redo is in pass-and-play, games against the computer, and online games (`D092`, built
by `M21.21`): a button labelled "Redo", offered only when there is an Undo to restore. It restores exactly the accepted Undo or computer takeback
that immediately preceded it, as one operation:

- one pass-and-play or unilateral online ply;
- both the human move and recorded computer reply when a computer takeback removed
  both, or only the human move when no reply had been recorded yet;
- both plies removed by an accepted or crossed online undo request.

Repeated Undo keeps the entire forward path. Repeated Redo walks forward through
those recorded operations in order, and alternating the two does not lose the rest
of the path. Redo restores recorded moves and their exact recorded states; it does
not replay the moves through the rules or ask the computer to choose again.

The path is persistent game data: it survives Back, rotation, process death and an
app restart. For an online game the server is authoritative, so another installation
sees the same availability. Only the player whose turn it is before the Redo may use
it online.

Post-MVP (`M22.2`, `D102`), an ordinary accepted move advances the retained
continuation when its canonical structured identity and complete predecessor state
exactly match the next recorded action. It consumes that action, restores its recorded
result and preserves everything still ahead. A different legal move starts a new
continuation and clears the remainder. Manual matching advances one action at a time;
it does not silently play an online opponent's retained reply. A recorded computer
reply is restored without another engine search when it becomes the computer's next
action.

Resignation, a successful draw claim, or a newly accepted game-ending move clears any
remaining continuation and stays final. Navigation, refresh, reconnect, recreation,
and undo-request changes that do not change move history do not clear it. A matching
move does change board/history state, so it clears pending gameplay requests like any
other accepted move.

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
- resigning a game never leaves or closes the series; leaving is a separate
  action (`M19.8`), and leaving resigns the current game itself when a move has
  been played in it (`D095`).

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

The promotion question also offers Cancel, on every game screen, which takes the pawn
back with nothing played (`D107`).

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

Draw offers by agreement are not part of the MVP. They were built after it, in
`M22.3` (`D097`): either player may offer during an unfinished game, one draw
offer may be pending, and it has no timer. After a decline, neither player may
offer again until an accepted move. A draw offer and an undo
request may coexist. Request-only changes preserve the other request; any board,
history or result change clears both. An accepted offer ends the game at once as
a draw by agreement, final, and the series goes on to its rematch.

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

Alex       12-7-3 W-L-D       [Play/Open]
Chris       4-2-1 W-L-D       [Play/Open]
Sam         0-0-0 W-L-D       [Play/Open]
```

Completed games belong in history rather than dominating the home screen.

The dashboard's top row shows the player's own username at its right-hand end, so
a player can always see the name a friend needs to add them. Screens with Back do
not show it. Tapping it opens Settings (`M23.5`, formerly `F35`). A long username
stays on one line without displacing dashboard navigation.

## Settings

The screen is titled **Settings**. It does not offer username editing, which remains
deferred until real authentication (`F5`, `F13`, `D098`). It opens from either:

- the username at the right-hand end of the dashboard's top row; or
- a clearly labeled **Settings** action on the startup screen, including while startup
  is waiting for the server or has failed.

Back returns to the dashboard or startup screen from which Settings was opened. Local
and computer game screens remain unchanged. Settings works without server startup and
its device-local choices still apply to startup, pass-and-play and computer games
while offline.

Settings initially has two sections, in order:

1. **Appearance.** The themes are Classic, Neon Hologram, Stained Glass, Illuminated
   Manuscript, Marble & Basalt and Clockwork Kingdom, in that order (`D108`). Roomy
   screens use a two-column thumbnail grid; narrow phones use a one-column card list.
   Choosing one applies it immediately, including to Settings itself. There is no Save
   or Apply button.
2. **Feedback.** Sound and Haptics are separate persisted device-local switches. Both
   default on; Haptics is one all-or-nothing switch rather than per-event controls.

Sound and haptics both respond to ordinary moves, captures, check and game end,
including an opponent move newly arriving while the app is in the foreground. A
compound event produces exactly one cue of each enabled kind, chosen in this order:
game end, check, capture, ordinary move. The cue follows the action rather than the
player, so an opponent move has no separate opponent-only cue. Haptic patterns differ
by event. Game-end sounds distinguish win, loss and draw; in pass-and-play either
side's decisive victory uses the win cue and a draw uses the draw cue.

Feedback can occur anywhere in the foreground app, not only on a game screen, and
never in the background. Loading or reopening an existing position, process
restoration, reconnect, refresh, a duplicate realtime hint or rediscovering a known
move does not replay it. An online cue occurs only when the app newly accepts or learns
an authoritative event; a retry or lost command answer cannot duplicate it. Sound
respects silent mode, Do Not Disturb and media volume. Haptics also require Android's
system vibration/touch-feedback setting. Feedback never determines, delays, repeats or
suppresses a game action. All six themes initially share the same feedback design;
theme-specific sound sets are only a possible future extension. Every shipped sound
asset needs a recorded distribution licence (`D109`).

## Game Screen

Display:

- opponent username,
- chess board,
- current turn,
- selected square,
- legal move highlights,
- last move highlight,
- check indication,
- move history in standard algebraic notation (SAN),
- Undo when legal,
- Claim Draw when a valid claim is available,
- Resign.

Board orientation:

- own side at the bottom, in online games and against the computer.
- **Pass-and-play is face to face (`D087`).** The board never moves: White stays at
  the bottom all game, and Black's pieces are drawn upside down so they face the
  player sitting across the phone, as on a real board between two people. Only the
  pieces turn; the status and controls read from the bottom.

Layout (`D073`), decided by the window's size, not the device's orientation:

- **Wide windows (landscape):** the board on the left, as tall as the window allows,
  and a panel on the right with Back, the status and the controls, then the move list.
  The board never moves. Squares are at least 44 dp.
- **Tall windows (portrait):** Back, the board, then the status and controls, then the
  move list. Squares are at least 48 dp, except on a window too narrow for that, where
  the board is as wide as the window.
- **A window too short for either** keeps a full-size board, which scrolls with the
  status and controls above the move list.
- The move list always scrolls on its own, apart from everything else (`D105`). Where
  the controls do not all fit, they scroll in their own region, never with the list.
- The game screens, Review included, have their own Back instead of the app's top row.
- Rotating keeps a local game in progress, and so do closing the app, Back and losing
  the process, because local games are saved (see *Local Games*, `M21.2`).

Interaction:

```text
tap piece
→ show legal moves
→ tap destination
→ submit move
```

Drag-and-drop is not required for MVP. Post-MVP (`M22.12`), a piece may also be
dragged: held for a long press, moved and dropped. A drop onto a legal destination is
the same move the two taps would make, promotion choice and draw-entitling prompt
included; a drop anywhere else, or a drag that is cancelled, returns the piece and
changes nothing. A quick swipe across the board still scrolls the screen.

Post-MVP `M23.1` adds slim captured-piece rows to every live game screen (`D104`).
Each row names a player and colour and shows pieces captured by that player, ordered
queen, rook, bishop, knight, pawn. An empty row shows only its player and colour, at
the same height, and a screen reader hears that it has no captures; repeated pieces
compact to counts on narrow screens. Only the side ahead in the current position's
live non-king material, on the project owner's 9/5/3.25/3/1 queen/rook/bishop/knight/
pawn scale, shows `+N`; quarter-point advantages are displayed exactly. This balance
is separate from the captured-piece totals so promotions remain correct. In one
column the rows sit immediately above and below the board according to its
orientation. In two panes they move to the right panel so the board keeps its maximum
size. They are display-only, use the board's piece artwork, and do not appear in
completed-game review.

Post-MVP `M23.2` makes the move history follow the position on screen (`D105`).
Review shows only the moves through its displayed ply and highlights the individual
SAN move that produced that position; a live game highlights the last move in active
history. A new current move scrolls into sight, while ordinary refresh or recomposition
does not undo a player's manual history scroll. In one-column and two-pane layouts the
move history has its own bounded vertical scroll: the board, Back, identification,
status, captured-piece summary, review navigation and game actions stay outside that
scroll and remain available. The treatment is visible and accessible without relying
on colour alone.

### Online Completed-Game Review

Post-MVP (`M22.10`, `D100`), a finished online game offers Review both on its
game-ending screen and when opened from History. Start, Previous, Next and End
step through server-projected recorded positions and the SAN move list. Review never
replays coordinate moves through the installed client rules and never shows
coordinates as a notation fallback. Active online games never offer review and keep
their live server-authoritative board (`D101`). Post-MVP `M23.2` (`D105`) limits the
rendered list to the prefix through the selected ply, highlights that ply's move and
keeps it visible without scrolling those four review controls away.

## Local Games

*Decided 2026-09-29 (`D084`). Built by `M21.1`–`M21.4`; games against the computer
join in `M21.7`.*

A local game is one whose players are all on this phone: pass-and-play, or a game
against the computer. It never uses the server.

- **Saved.** A local game survives closing the app, Back and a restart of the
  phone, and picks up where it left off. Every move, Undo, resignation and draw claim
  is saved as it happens (built by `M21.2` for pass-and-play).
- **One unfinished local game of each kind** (`D090`, superseding `D084`'s one of
  either kind; built by `M21.13`). An unfinished pass-and-play game and an unfinished
  game against the computer can both be kept, and each entry resumes its own:
  "Local game" opens the unfinished pass-and-play game, or a new one when there is
  none; "Play the computer" opens the unfinished game against the computer, or asks
  for a level. A finished game is not resumed; the entry starts a new one. Neither
  entry asks about or deletes the other kind's game.
- **New game.** The pass-and-play screen offers New game once a move has been played,
  or the game has ended. If the game is unfinished it asks first ("Start a new
  game?"), and confirming deletes it. A finished game is kept, so nothing is asked.
- **Past local games.** Finished local games are kept and listed newest first. Each
  opens read-only, with its result and moves, and the board can be stepped through
  move by move. There are no series, standings or statistics for local games.
  Built by `M21.3`: History offers "Past local games", whose lines read like
  "Pass-and-play • 1 Oct 2026 • White won by checkmate". A game opens at its final
  position; Start, Previous, Next and End step through it, and nothing on the board
  can be tapped. A pass-and-play game is shown face to face, as it was played.
- **Works offline.** Local games do not wait on the server. If the app cannot reach
  it at startup, local games, new local games and past local games are still
  available next to Retry. Online play needs the server as before.
  Built by `M21.4`: while the startup screen shows (starting, waking the server, or
  failed) it offers "Local game" and "Past local games". If the server answers while
  the player is in a local game, they stay in it, and Back then goes to the
  dashboard.
- **Stays on this phone.** Local games are not backed up or transferred to a new
  phone, and uninstalling the app deletes them.

## Playing the Computer

*Decided 2026-09-29 (`D086`). Built by `M21.6`–`M21.7`.*

- **Starting.** "Play the computer" sits next to the local game, including when
  offline. A new game asks for one of four levels: Very Easy, Easy, Medium or Hard
  (`D089`, which superseded `D086`'s three). The player's colour is random for the
  first game.
- **The board** stays turned to the player's colour, and the opponent shows as
  "Computer" with its level. The player cannot move while the computer is thinking.
- **Takeback.** After the computer has replied, Undo takes back its reply and the
  player's move before it, returning the player to their previous turn. It can be
  repeated back to the player's first move. If the computer has not replied yet, Undo
  stops it and takes back the player's move alone. A game-ending move is still
  final, whoever made it. Redo (`M21.21`): a two-ply takeback restores that exact
  human move and recorded computer reply without another engine search; a one-ply
  takeback restores the human move and then resumes the ordinary computer turn.
- **Draws and resignation.** The player may resign, with the usual confirmation, and
  may claim any draw they are entitled to. The computer never resigns, offers a draw,
  or claims one. Automatic draws apply as in every game.
- **After the game.** The finished game is kept in past local games. **Play again**
  starts a new game at the same level with the colours swapped. There is no automatic
  rematch and no series.

*As built by `M21.7`:* "Play the computer" is in the top row next to "Local game", and
on the startup screen while the server is unavailable. With nothing unfinished it asks
for Easy, Medium or Hard; if a pass-and-play game is unfinished it first asks whether
to delete it, and "Local game" asks the same way about an unfinished game against the
computer. The game screen says "Computer (Medium) • You played Black", shows "The
computer is thinking…" while it is, and offers Undo, any draw the player may claim,
and Resign. A finished game offers Play again and Leave. Past local games list it as
"Computer (Medium) • You played Black • date • result", and review it from the
player's side with every piece upright. When the top row does not fit on one line, the
player's name moves to a line of its own.

*Decided 2026-10-01 (`D089`), lands with `M21.10`–`M21.12`:* the levels become Very
Easy, Easy, Medium and Hard. Very Easy and Easy play as today's Easy and Medium did;
the new Medium develops its pieces and castles rather than making aimless moves; Hard
stays the strongest. The game screen gains New game, which offers the level choice
at any time, asking first when the game is unfinished because that game is deleted
and not kept.

*As built by `M21.10`:* the game screen always shows New game, next to Play again and
Leave once the game is over. On an unfinished game it first asks "Start a new game?",
as pass-and-play does; "Keep playing" returns to the game, and a computer that was
thinking carries on. Agreeing, or New game on a finished game, shows the level choice
in place of the board, headed "New game", with "Back to the game" (and Back) returning
to the game as it is. Nothing is deleted until a level is chosen; then any search
stops, the unfinished game is deleted, and the new game starts at that level with a
random colour. A finished game stays in past local games. Play again is unchanged.

*As built by `M21.11`–`M21.12`:* the level choice, from "Play the computer" and from
New game, lists Very Easy, Easy, Medium and Hard, weakest first. The game screen and
past local games name the level the same way: "Computer (Very Easy)" to "Computer
(Hard)". Games saved before the update keep how they play: one at the old Easy or
Medium now reads Very Easy or Easy, the same engine renamed, and one at the old Hard
is still Hard. On a slow phone the computer may take a little longer than its time
budget to finish looking one move ahead, rather than answer without looking.

*Decided 2026-10-02 (`D090`), built by `M21.13`:* "Play the computer" no longer asks
about an unfinished pass-and-play game, and "Local game" no longer asks about an
unfinished game against the computer. One of each can be kept, and the player can
switch between them freely. New game still deletes only the game on screen.

## Deferred Features

Do not initially build the features below. `docs/MVP.md` distinguishes work that
is scheduled beyond the MVP from the complete list still not scheduled;
`docs/BACKLOG.md` holds scheduled work and `docs/FUTURE.md` holds what is known
about unscheduled items (`D072`).

- ratings,
- matchmaking,
- public games,
- tournaments,
- chat,
- contact syncing,
- push notifications,
- chess clocks,
- draw offers by agreement (built post-MVP in `M22.3`),
- custom themes (scheduled as `M23.6`, formerly `F8`),
- optional sound and haptic feedback (scheduled as `M23.7`, formerly `F42`),
- elaborate animations (scheduled as `M23.8`, formerly `F9`),
- spectators,
- account recovery UI,
- username changes,
- detailed statistics,
- deck-building mechanics.
