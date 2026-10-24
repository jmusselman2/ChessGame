# Chess MVP — Definition of Done

## MVP Goal

The MVP is complete when:

> A new Android user can install the app, enter a globally unique username without creating a traditional password-based account, automatically return as that same user on later launches, add another user by username, start a chess series with that friend, play a complete legal game asynchronously across two Android devices, undo an unanswered non-final move, correctly handle claimable and automatic standard-chess draws, immediately progress into an automatically created rematch after a game ends while the series remains active, and reopen the app later to see current games and whose turn it is.

**This goal has been met.** `M14.18` proved it across two clients and `M17.1`
proved it with a real tester on their own physical device. This document remains
the specification of what "done" meant; `docs/BACKLOG.md` is the source of truth
for task-level status.

## Required Capabilities

### Foundation

- Monorepo exists.
- `chess-core` builds independently as pure Kotlin/JVM.
- Android app builds and launches.
- Ktor server builds and starts.
- Android and server both consume `chess-core`.
- Formatting/static-analysis tooling is configured.
- CI verifies builds/tests/checks.

### Chess Core

- Initial standard chess position.
- Legal movement for all pieces.
- Captures.
- Check detection.
- Self-check prevention.
- Checkmate.
- Stalemate.
- Castling.
- En passant.
- Pawn promotion to Q/R/B/N.
- Insufficient-material draw.
- Threefold repetition claim detection.
- Fifty-move claim detection.
- Fivefold repetition automatic draw.
- Seventy-five-move automatic draw.
- Resignation result.
- Extensive automated tests.

### Draw Claims

- `ClaimDraw` exists as a game action/command.
- A valid threefold-repetition claim can end the game.
- A valid fifty-move claim can end the game.
- Invalid claims are rejected.
- Fivefold repetition ends automatically.
- Seventy-five moves ends automatically.
- Draw offers by agreement are not part of the MVP; they were built as post-MVP
  work in `M22.3` (`D097`).

### Undo

- Latest unanswered non-final move can be undone by its player.
- Previous move locks after opponent responds.
- If opponent undoes, prior player's move may become undoable again.
- Game-ending move cannot be undone.
- Undo restores complete prior state, not merely piece positions.
- Undo behavior is concurrency-safe on server.

### Android Local Game

- Chess board renders.
- Board orientation follows player color; pass-and-play is fixed with White at the
  bottom and Black's pieces upside down (`D087`).
- Piece selection works.
- Legal moves are highlighted.
- Move submission works.
- Move history displays.
- Undo visibility follows rules.
- Claim Draw visibility follows rules.
- Resign action works.
- Complete game can be played locally.

### Identity

- Supabase anonymous identity works.
- Session restores across normal launches.
- Unique username can be claimed.
- Case-insensitive uniqueness is enforced in PostgreSQL.
- Ktor verifies the authenticated user.
- `lastSeenAt` is tracked.
- Lost anonymous usernames are not automatically recycled.
- Claiming a username that already exists attaches this installation to that
  existing user, with no verification. It is a deliberately insecure prototype
  flow (`D082`, `M20.4`). The Supabase anonymous session stays the credential, and
  many installations may map to one user. A claimed name therefore always
  resolves to its existing user, never to a new one.

### Friends

- Add friend by username.
- Friendships are mutual immediately.
- Duplicate/self friendship is prevented.
- Friends list loads.
- Remove friend works.
- Historical games survive friend removal.
- Current game survives friend removal.
- Removing a friend affects the friends list only: the series and its automatic
  rematches carry on (`D053`, superseding `D013`'s "disables the next rematch and
  closes the series after the current game").
- An "All users" page lists every user so testers can add friends without typing
  exact names (`D071`, `M17.5`, `M17.6`). It is a testing aid and part of the
  MVP. Removing it, or restricting it to admins, is post-MVP work (`F12`).

### Groups

Groups are MVP (`D076`, superseding `D049`'s "deck-builder only"; `M17.11`).

- Create a group by name.
- List your groups; open one to see its members.
- Add one of your own friends to a group you are in.
- Play any other member of a group you are in, friend or not, through the same
  Play as for a friend.
- Leave a group. Games and series with its members carry on.

### Multiplayer

- Game series can be created/opened.
- Post-MVP `M22.1` restores at most one active chess series per pair. Play opens
  it rather than offering or creating another (`D095`, superseding `D053` rule 1).
- Initial colors are random.
- Server is authoritative.
- `MakeMove` uses expected game version.
- Stale commands are rejected.
- Two devices can alternate moves.
- `UndoMove` is server-authoritative.
- `ClaimDraw` is server-authoritative.
- Move-vs-undo race is handled correctly.
- Realtime WebSocket updates work.
- Reconnect reloads canonical state.

### Game Completion

- Final move immediately finalizes game.
- Completed result is persisted.
- Exactly one next game is created when the series remains active.
- A participant can leave a series. Post-MVP `M22.1` makes leaving end its current
  game too: a started game is a resignation by the leaver and an unstarted game
  is deleted without a result. No rematch is created (`D095`, superseding `D068`).
- Rematch colors alternate.
- Series points to the new current game when rematch is created.
- No rematch confirmation is required.
- Resignation follows the same series lifecycle rules.

### Dashboard and History

- Dashboard shows Your Turn.
- Dashboard shows Their Turn.
- Dashboard shows Friends.
- Opening app leads directly to useful dashboard after identity restore.
- Completed games are viewable in history.
- Completed game is read-only.
- Closed series remain historically viewable.

### Reliability

- App restart restores state.
- Network interruption recovers.
- WebSocket loss does not corrupt state.
- Duplicate command handling is safe.
- Stale version handling is safe.
- Automatic rematch creation is idempotent.
- Series closing is idempotent.
- Server logging supports debugging without logging secrets.

## Explicitly Not Required for MVP

This is the binding and complete account of work outside the MVP. Work already
scheduled in backlog milestones is summarized first. The remaining unscheduled items
are listed by topic under their `F` numbers; `docs/FUTURE.md` carries the detail for
exactly those items, and changing either unscheduled list means changing both (`D072`).

Scheduled beyond the MVP: the AI opponent (formerly `F7`) and saved local games are
backlog milestone `M21` (`D084`–`D086`). Matching play through a retained Redo
continuation, draw offers by agreement, drag-and-drop moves, detailed profiles,
the win/loss/draw record per friend, turning automatic rematches off by hand, one
active series per party, readable move notation with board coordinates, and online
ply-by-ply review (formerly `F1`–`F4`, `F6`, `F38`, `F39` and `F41`) are milestone
`M22`. They are post-MVP work and do not change the MVP's definition of done.
Captured-piece presentation, move-history focus and independent scrolling, board
accessibility and large text, user settings, custom themes, optional sound and haptic
feedback, and elaborate animations are milestone `M23` (the latter five formerly
`F40`, `F35`, `F8`, `F42` and `F9`). They are also post-MVP work and do not change the
MVP's definition of done. Search-based dead-position detection (formerly `F11`, then
`M23.4`) was dropped by the owner on 2026-10-23 and is not planned.
Username changes returned to `F5` under `D098` and wait for real authentication
(`F13`).

### Chess Play

- `F10` clocks

### Identity and Accounts

- `F5` username changes (`D098`; depends on `F13`)
- `F12` removing the "All users" page, or restricting it to admins (`D071`)
- `F13` account recovery UI (`PRODUCT.md`)
- `F14` approval setting for friend requests and game invites (`D047`, `D049`)

### Social

- `F15` blocking (`PRODUCT.md`)
- `F16` chat
- `F17` followers (`PRODUCT.md`)
- `F18` social feeds (`PRODUCT.md`)
- `F19` contact syncing (`PRODUCT.md`)
- `F25` push notifications
- `F27` "suggest friends" for table co-participants (`D049`)

### Series and Statistics

- `F22` ratings
- `F30` detailed statistics, including per-table statistics (`D048`)
- `F31` series revival (`D048`)

### Public Play

- `F20` spectators (`docs/GAME-STATE-VISIBILITY.md`)
- `F21` matchmaking
- `F23` public games
- `F24` tournaments

### Other Clients

- `F32` iOS or web clients (`D002`)

### Deck-Builder Platform

- `F26` deck-building systems
- `F28` deck-builder solo mode (`D048`)
- `F29` AI players in the deck-builder's normal modes (`D051`)
- `F37` N ≥ 3 resignation and continue-among-remainder (`D052`; formerly backlog
  `M20.2`, moved by `D082`)

### Operations

- `F33` database backups
- `F34` locking down direct database access: row-level security and the `anon`
  grants
- `F36` replacing Exposed with jOOQ, with a database wipe

Several active series per friend pair was on this list until `D053` made it MVP
behaviour. `D095` later reversed that rule, and `M22.1` restored one active chess
series per pair.
