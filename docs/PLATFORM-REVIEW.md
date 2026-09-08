# Post-Chess Platform Review

**Reviewed at:** `9941402`, 2026-09-08 (`M18.1`)

## What this document is

Chess was never the product. It was the first ruleset, built to find out which
parts of a turn-based multiplayer application are about *chess* and which are
about *turn-based multiplayer* — before a second game exists to guess with
(`README`, `CLAUDE.md`).

This is the answer, taken from the code that was actually built and played
rather than from the intentions recorded before it. `ARCHITECTURE.md` §31 listed
fourteen concepts "expected to survive beyond chess". That list was written at
the start. This document checks it, and it does not agree with it everywhere.

**This document is descriptive.** It records what was found and what it implies;
it does not change any rule. Where a conclusion needed deciding rather than
observing, it was decided in `D044` and this document defers to it, to
`ARCHITECTURE.md`, and to `PRODUCT.md` as usual.

## The measurement

The cheapest question first: how much of the server is about chess?

| | files | lines |
|---|---|---|
| `game-core` (all chess) | 22 | 1,779 |
| `server` main source | 32 | 4,257 |
| …of which mention `game-core` at all | 7 | 1,638 |
| …of which are *shaped* by chess | 5 | ~1,444 |

The seven are `api/ApiTypes.kt`, `db/GameRepository.kt`, `db/GameStateDocument.kt`,
`game/GameCommandService.kt`, `game/GameRoutes.kt`, `series/SeriesService.kt`,
and `Application.kt`. Two of those barely count: `Application.kt` uses
`GameCore.NAME` for a banner, and `SeriesService` touches chess on exactly two
lines, both `ChessGame.newGame()` ([SeriesService.kt:139](../server/src/main/kotlin/com/jmussel/chessgame/server/series/SeriesService.kt:139),
[:166](../server/src/main/kotlin/com/jmussel/chessgame/server/series/SeriesService.kt:166)).

So roughly two-thirds of the server — authentication, identity, usernames,
friendships, series lifecycle, dashboard, history, realtime, persistence
plumbing, logging — never mentions chess. That is the strongest single piece of
evidence in this review, and it was not designed in at the end; it is what
`ARCHITECTURE.md` §6's rule ("these are not chess-engine concepts") produced when
followed for seventeen milestones.

The Android side is less clean and for a good reason (§ *Chess-specific
concepts*, the client replay).

## Chess-specific concepts

These exist because the game is chess. A second ruleset replaces them; it does
not generalise them.

**Everything in `game-core`.** All 22 files, all 1,779 lines. `Board`, `Piece`,
`Square`, `Side`, `CastlingRights`, `EnPassant`, `Repetition`, `DrawRuleState`,
`InsufficientMaterial`, `PseudoLegalMoves`, `Attacks`, `ChessRules`. This is
correct and was the point: the module has no platform ambitions and no
`UniversalGame` in it.

**The stored position.** `GameStateDocument` — eight rows of eight characters,
castling string, en-passant square, halfmove clock, repetition counts. A
persistence DTO for one ruleset, correctly kept out of `game-core`.

**The `moves` table's columns.** `from_square`/`to_square` with
`check (from_square ~ '^[a-h][1-8]$')`, `promotion in ('QUEEN', 'ROOK',
'BISHOP', 'KNIGHT')`, `side in ('WHITE', 'BLACK')`. A deck-builder action is not
a from/to pair, and no widening of this table will make it one.

**Chess vocabulary in the API and the schema.** `white_user_id`/`black_user_id`
rather than seats; `result in ('WHITE_WINS', 'BLACK_WINS', 'DRAW')`;
`side_to_move`; and in `GameView`, `board`, `inCheck`, `halfmoveClock`,
`moveNumber`, `availableDrawClaims`. These name chess but they are *instances* of
platform shapes — see *Abstractions worth extracting*, where the seat and the
capability list are the parts worth keeping.

**Alternating colours on rematch.** `D014` alternates White and Black from the
previous game ([SeriesService.kt:129](../server/src/main/kotlin/com/jmussel/chessgame/server/series/SeriesService.kt:129)).
Seat alternation is general; that there are exactly two seats and that swapping
them is fair is a fact about chess.

**Undo as `PRODUCT.md` defines it.** `D016`'s rule — the mover may take back the
latest unanswered move — is only safe because chess is a game of complete
information played one action at a time. It is discussed under *What chess did
not prove*, because it is the rule most likely to be unimplementable in the next
game rather than merely different.

**The Android client's local replay.** `OnlineGame.replayOf` rebuilds the game
from the server's move list to preview legal destinations
([OnlineGame.kt:155](../android-app/app/src/main/java/com/jmussel/chessgame/ui/game/OnlineGame.kt:155)),
and nine Android files import `game-core`. This is why Android looks more
chess-coupled than the server: the client runs a full copy of the rules. It works
because both players may see everything. It is chess-specific in the strongest
sense — see *What chess did not prove*.

## Proven platform concepts

"Proven" here means: implemented, tested, and exercised by two real people on two
physical devices playing a real game (`M17.1`), with a named piece of evidence
that it does the job. Not "seems reusable".

**Optimistic concurrency by a monotonic version (`D021`).** Every accepted
mutation increments `games.version`; every command carries the version it was
decided against; the write is guarded by
`update ... where id = ? and version = ?`
([GameRepository.kt:196](../server/src/main/kotlin/com/jmussel/chessgame/server/db/GameRepository.kt:196)).
This is the single most portable thing built. It has nothing to do with chess: it
says *the state you decided against is the state you are changing*, which is true
of any authoritative turn-based server. It was stress-tested by
`MoveVersusUndoTest`, `StaleVersionTest`, `DuplicateCommandTest`, and — the
strongest evidence — by `M16.7`, where the version check was found to be
*passable while wrong* under READ COMMITTED, and the fix (`loadForUpdate`, a row
lock for mutating reads,
[GameRepository.kt:126](../server/src/main/kotlin/com/jmussel/chessgame/server/db/GameRepository.kt:126))
is itself general. A concept that survived being broken and repaired is better
evidence than one that was never tested.

**A refusal that carries the truth.** A rejected command answers with
`RejectionReason` *and* the canonical state
([ApiTypes.kt](../server/src/main/kotlin/com/jmussel/chessgame/server/api/ApiTypes.kt)),
so a client that was wrong is corrected by the same round trip that refused it.
This is what makes stale clients self-healing without a reconciliation protocol,
and it is ruleset-independent.

**Realtime as invalidation, never as state (`D022`).** `RealtimeMessage` carries
a type, a game id, and a version — nothing else
([RealtimeHub.kt:12](../server/src/main/kotlin/com/jmussel/chessgame/server/realtime/RealtimeHub.kt:12)).
The client reloads over HTTPS. `RealtimeHub` contains no chess and no game
concepts at all: it is a user-keyed multiset of connections with best-effort
delivery. It survives message loss by construction, which `ReconnectRecoveryTest`
covers and `M16.1`/`D042` hardened after a real dead socket was found on a real
phone. **Reusable as written**, with one recorded limit: it is process-local, and
more than one server process needs shared pub/sub first (`ARCHITECTURE.md` §12).

**Identity separated from the auth provider.** `users.auth_subject` is the
Supabase subject; `users.id` is the internal identity everything else references.
Nothing outside `auth/` knows what Supabase is. Swapping the provider touches one
column and one verifier. `D043` sharpened the rule that makes this safe — an
identity is given up only on evidence, never on the absence of a success — and
that rule is about accounts, not chess.

**Username, friendship, and series as database-enforced invariants.**
Case-insensitive uniqueness through `username_normalized`; the ordered-pair
primary key that makes a reversed duplicate friendship unrepresentable; the
partial unique index `game_series_one_active_per_pair`. These push race-safety
into constraints instead of application logic, and none of them mentions a game.
`AddFriendTest`, `UsernameClaimTest`, and `OpenSeriesTest` cover the races.

**The series as the unit of continuity.** A pair of players, a current game, a
status, and `close_after_current_game`. Everything the product promises about
continuity — automatic rematch, colour alternation, friend removal that lets the
current game finish and then closes the series (`D013`) — is expressed in those
four columns, and `SeriesService` implements all of it while knowing one thing
about chess: how to construct a new game. **This is the most valuable structural
result in the review**: the lifecycle survived first contact with a real ruleset
almost completely uncontaminated by it.

**Settling a finished game inside the command's transaction.** The finished game,
its result, and the next game are one commit
([GameCommandService.kt:271](../server/src/main/kotlin/com/jmussel/chessgame/server/game/GameCommandService.kt:271)),
so no client sees a series whose game is over and whose successor does not exist.
Idempotence comes from re-reading the series under a row lock and deciding from
what it says there (`settleAfter`), which covers retries, duplicates, and
simultaneous arrivals alike. `SeriesIdempotencyTest` and
`SeriesClosesAfterLastGameTest` cover it. The pattern — *decide under the lock,
from the locked row, not from what you read before it* — is the same one
`openWithGame` uses for two players tapping "Play" at once, and it is general.

**The command validation order.** Participant → version → still running → turn →
rules → persist
([GameCommandService.kt:301](../server/src/main/kotlin/com/jmussel/chessgame/server/game/GameCommandService.kt:301)).
The version is checked before anything else the caller believes, because if it is
wrong then everything else they believe may be too. Only the fifth step is chess.

**Append-only audit that is not event sourcing (`D020`).** `game_events` records
`MoveMade`, `MoveUndone`, `DrawClaimed`, `PlayerResigned`, `GameEnded`,
`RematchCreated`, `SeriesClosed`; loading a game never replays them. The split —
canonical current state, active history, separate audit — is a platform decision
that a second ruleset inherits unchanged. Only the event *names* are chess.

**`lastSeenAt` as a throttled side effect, not a heartbeat (`D010`, `D043`).**
Written at most once per window, from ordinary authenticated traffic, and the
window is spent only after the write lands.

## Abstractions worth extracting

Worth extracting **when the second game exists** — not now. `D044` records why
now is wrong and what the trigger is. Each of these is named here so the
extraction is a deliberate act with a written expectation, rather than a
rediscovery.

**A seat, in place of a colour.** `white_user_id`/`black_user_id` and
`yourSide`/`sideToMove` are a two-seat game's spelling of *which participant is
this, and whose turn is it*. A deck-builder has seats too, possibly more than
two, possibly with turn order rather than strict alternation. The concept to keep
is the seat and the mapping from a user id to it (`GameCommandService.sideOf`);
the concept to leave behind is that a seat is a colour and that there are two.
Sequencing on top of seats — "whose turn" — is a separate axis and chess proved
only the simplest case of it.

**A capability list computed by the server.** `GameView` tells the client
`yourTurn`, `canUndo`, and `availableDrawClaims`: *what you may do right now*,
answered by the authority, so the client renders buttons rather than deciding
entitlement ([ApiTypes.kt](../server/src/main/kotlin/com/jmussel/chessgame/server/api/ApiTypes.kt)).
This is the pattern that lets a client stay dumb about the rules, and it is
exactly what a game with hidden information needs, because there the client
*cannot* compute entitlement even in principle. **The most important thing to
carry forward from the API design**, and the one that scales *up* in importance
rather than down.

**A versioned, guarded state document.** `state jsonb` + `version bigint` +
a guarded update + a DTO in the server that converts to and from the domain. The
payload is chess; the shape — one authoritative row, one opaque serialised
domain state, a version guarding it, and the domain type never learning about
JSON — is a template. `GameStateDocument`'s serialisation settings
(`encodeDefaults` on so a stored document always carries every field,
`ignoreUnknownKeys` on so an older process can read a newer document,
[Tables.kt:19](../server/src/main/kotlin/com/jmussel/chessgame/server/db/Tables.kt:19))
are a forward-compatibility decision worth copying verbatim.

**The command service shape.** A `sealed interface CommandResult` with one case
per refusal reason and the canonical state attached to each; a service that owns
the transaction; routes that only parse, dispatch, and render
([GameCommandService.kt:19](../server/src/main/kotlin/com/jmussel/chessgame/server/game/GameCommandService.kt:19),
[GameRoutes.kt](../server/src/main/kotlin/com/jmussel/chessgame/server/game/GameRoutes.kt)).
Extract the shape, not a base class — see below.

**`RealtimeHub` itself, verbatim.** It is already game-agnostic. When the second
game arrives it should be *moved*, not reimplemented, and `RealtimeMessage`'s
`gameId` generalised to whatever both games' state is keyed by.

**Everything in the account and social layer, unchanged.** Users, usernames,
friendships, `lastSeenAt`, the Supabase verifier, the auth plugin. These are
already a platform; they need extracting into a module only when a second
*application* exists, not merely a second ruleset. If the deck-builder ships as
another mode of the same app, they move nowhere at all.

## Abstractions that should remain concrete

**Do not build a generic `Game`/`Move`/`Rules` interface for `game-core`.**
`ARCHITECTURE.md` §5 and §32 already forbid it and this review confirms it was
right. The evidence is `ChessRules`' actual signatures: `canUndo(game, side)`,
`availableDrawClaims(state, declaredMove)`, `claimDraw(game, claim)`,
`resign(game, side)`. A generic engine interface would have to be either so wide
it is a union of two rulesets' vocabularies, or so narrow (`apply(state,
action): state`) that every caller immediately casts back to the concrete type —
and the callers *are* concrete: `GameCommandService.makeMove` asks
`ChessRules.isLegal` and nothing else could stand in for it. With one
implementation, an interface would be a description of chess with the word
"chess" removed.

**Do not generalise the `moves` table.** A move history for chess and an action
log for a deck-builder are different enough (an action may target a card, a
player, a zone, or nothing; a turn may contain many) that a shared table becomes
a column-per-concept graveyard. Two tables, two writers, one *pattern*.

**Do not extract a command base class or a `Command<T>` hierarchy.**
`ARCHITECTURE.md` §8 already says concrete command types are simpler and that has
held: `MakeMoveRequest`, `UndoMoveRequest`, `ResignRequest`, `ClaimDrawRequest`
are four small `@Serializable` data classes with no common supertype and no
ceremony, and the four service methods share a validation *order* rather than
code. Their differences are real — `resign` deliberately skips the turn check
because giving up is not a move, and `undoMove` skips it for the same reason.
A shared base would have had to carve out both.

**Do not extract `SeriesService` or `GameCommandService` into generic services
now.** They are the right *shapes* and the wrong time. `SeriesService`'s two
chess lines are a constructor call; when a second ruleset exists, that becomes a
parameter, and that is a five-line change made with two implementations in view.
Doing it now means inventing the parameter's type from one example.

**Do not move `game-core` to Kotlin Multiplatform.** No non-JVM consumer exists.
`ARCHITECTURE.md` §2 and §32 already say so; nothing in seventeen milestones
argued against it.

**Do not split the monorepo or introduce services.** The module boundary that
mattered (`game-core` importing nothing) was enforced by dependency rules and
review, not by deployment topology. Nothing about the beta suggested otherwise.

## What chess did not prove

The most useful section for the deck-builder, because these are the places where
a proven platform concept may quietly not apply. Chess is an unusually easy game:
two players, complete information, no randomness inside a game, strict
alternation, one action per turn, and a tiny state.

**Hidden information.** Both players are sent the entire position. `GameView`
carries the whole board to both sides, and the Android client rebuilds the game
from the move list to preview legal moves. **Neither is possible with a hand of
cards.** The consequences are concrete: a per-viewer projection of state becomes
mandatory rather than a nicety; `GameView.of(stored, viewer, opponent)` already
takes a viewer, which is the right seam, but it currently projects only
*perspective*, not *visibility*; and client-side pre-validation
(`ARCHITECTURE.md` §7, `§11.2`) largely stops working, which raises the
server-computed capability list from convenient to load-bearing.

**Randomness inside a game.** The only random thing chess does is the opening
coin toss for colours, and it happens once, outside game state
([SeriesService.kt:175](../server/src/main/kotlin/com/jmussel/chessgame/server/series/SeriesService.kt:175)).
A deck-builder shuffles. Nothing in this codebase has an opinion about seeded
randomness, deterministic replay from a seed, or where a seed is stored, and the
version model does not by itself make shuffling safe.

**Undo.** `D016`'s undo works because the previous position is stored whole with
each move (`moves.position_before`, `D029`) and because nothing was revealed by
the move being taken back. Undo in a game with hidden information leaks — the
mover has *seen* what they drew. The likely outcome is that the deck-builder has
no undo, and `PRODUCT.md`'s undo rules should be read as chess's, not the
platform's.

**Rewriting history on every write.** `save` deletes and rewrites the whole move
history each time
([GameRepository.kt:213](../server/src/main/kotlin/com/jmussel/chessgame/server/db/GameRepository.kt:213)),
which is correct, simple, and cheap for a hundred plies of chess. It is `O(game
length)` per action. This is a deliberate concrete choice that should be
revisited, not inherited, for a game with many actions per turn.

**Turn structure.** `NotYourTurn` is a hard refusal, and one command produces
exactly one version. Simultaneous choices, reactions out of turn, and a turn
composed of several actions are all outside what was built. The version model
still applies — it is about *which state you decided against* — but "one accepted
command, one version" may need to become "one accepted command, one version,
within a turn that spans several".

**Scale.** One beta game between two people on a Render Free instance. Nothing
here has been shown at load, with many concurrent games, or across more than one
server process — and the process-local `RealtimeHub` is a known blocker for the
last of those.

## What changes now

Nothing in the code. This is a review, and `D044` records the decision that
follows from it: extract when there are two implementations to compare, not
before. `ARCHITECTURE.md` §31's expectation list has been replaced with a
pointer here, because it was a prediction and this is a measurement.
