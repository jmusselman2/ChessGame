# Chess MVP — Definition of Done

## MVP Goal

The MVP is complete when:

> A new Android user can install the app, enter a globally unique username without creating a traditional password-based account, automatically return as that same user on later launches, add another user by username, start a chess series with that friend, play a complete legal game asynchronously across two Android devices, undo an unanswered non-final move, correctly handle claimable and automatic standard-chess draws, immediately progress into an automatically created rematch after a game ends while the series remains active, and reopen the app later to see current games and whose turn it is.

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
- Removing a friend disables the next automatic rematch for that series.
- Series closes when the current game ends.

### Multiplayer

- Game series can be created/opened.
- One `ACTIVE` series per pair for MVP.
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
- No next game is created when the series is closing.
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

- AI opponent
- matchmaking
- ratings
- chat
- push notifications
- clocks
- draw offers by agreement
- public games
- tournaments
- username changes
- account recovery UI
- multiple simultaneous active series per friend pair
- detailed statistics
- deck-building systems
