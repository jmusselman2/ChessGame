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
- `game-core` builds independently as pure Kotlin/JVM.
- Android app builds and launches.
- Ktor server builds and starts.
- Android and server both consume `game-core`.
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
- Draw offers by agreement are not implemented.

### Undo

- Latest unanswered non-final move can be undone by its player.
- Previous move locks after opponent responds.
- If opponent undoes, prior player's move may become undoable again.
- Game-ending move cannot be undone.
- Undo restores complete prior state, not merely piece positions.
- Undo behavior is concurrency-safe on server.

### Android Local Game

- Chess board renders.
- Board orientation follows player color.
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
  MVP. Removing it, or restricting it to admins, is post-MVP work (`F10`).

### Multiplayer

- Game series can be created/opened.
- A pair may have several `ACTIVE` series; Play offers an existing one rather than
  reusing it silently (`D053`, superseding `D011`'s one series per pair).
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
- A participant can leave a series. Leaving closes it at once; its current game
  can still be finished, and no next game is created (`D053`, `D068`, `M19.8`;
  this replaced `D013`'s "closing after the current game" state).
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

This is the binding and complete list of what is not in the MVP. What is known
about each item, and what is still to decide, is in `docs/FUTURE.md` under the
same `F` number (`D072`). The two lists hold the same items; changing one means
changing both.

### Chess Play

- `F2` draw offers by agreement (`D019`)
- `F3` drag-and-drop moves (`PRODUCT.md`)
- `F7` AI opponent
- `F10` clocks
- `F11` dead-position detection by search (`D038`)

### Presentation

- `F8` custom themes (`PRODUCT.md`)
- `F9` elaborate animations (`PRODUCT.md`)

### Identity and Accounts

- `F5` username changes
- `F12` removing the "All users" page, or restricting it to admins (`D071`)
- `F13` account recovery UI (`PRODUCT.md`)
- `F14` approval setting for friend requests and game invites (`D047`, `D049`)

### Social

- `F6` detailed profiles (`PRODUCT.md`)
- `F15` blocking (`PRODUCT.md`)
- `F16` chat
- `F17` followers (`PRODUCT.md`)
- `F18` social feeds (`PRODUCT.md`)
- `F19` contact syncing (`PRODUCT.md`)
- `F25` push notifications
- `F27` "suggest friends" for table co-participants (`D049`)

### Series and Statistics

- `F1` win/loss/draw record per friend
- `F4` turning automatic rematches off by hand (`PRODUCT.md`)
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

Several active series per friend pair was on this list until `D053` made it MVP
behaviour.
