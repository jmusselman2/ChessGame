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
| …of which mention `game-core` at all | 7 | 1,856 |
| …of which are *shaped* by chess | 5 | ~1,444 |

The seven are `api/ApiTypes.kt`, `db/GameRepository.kt`, `db/GameStateDocument.kt`,
`game/GameCommandService.kt`, `game/GameRoutes.kt`, `series/SeriesService.kt`,
and `Application.kt`. Two of those barely count: `Application.kt` uses
`GameCore.NAME` for a banner, and `SeriesService` touches chess at exactly two
call sites, both `ChessGame.newGame()` ([SeriesService.kt:139](../server/src/main/kotlin/com/jmussel/chessgame/server/series/SeriesService.kt:139),
[:166](../server/src/main/kotlin/com/jmussel/chessgame/server/series/SeriesService.kt:166)).

So **25 of the server's 32 files never mention chess at all** — authentication,
identity, usernames, friendships, series lifecycle, dashboard, history, realtime,
persistence plumbing, logging — which is 2,401 of its 4,257 lines. That is the strongest single piece of
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
information played one action at a time. The *mechanism* turned out to carry into
the next game and only the lock predicate is chess's own; see *What chess did not
prove*, where that is worked out.

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
Supabase appears in code in exactly two places: `auth/`, and the composition root
in `Application.kt`, which reads `SUPABASE_URL` and builds the verifier it
installs. Three other files name it only in comments. Swapping the provider
touches one column, one verifier, and that wiring. `D043` sharpened the rule that makes this safe — an
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

**Undo.** The mechanism carries; the lock predicate does not.

`D016` is a stack with no bookkeeping. Each `MoveRecord` stores the move together
with the *entire* position it was played from (`moves.position_before`, `D029`);
undo pops the top and restores that position wholesale; and eligibility is a pure
function of the stack's top — `undoableSide` is `lastMover` unless the game is
over. Nothing is remembered anywhere, because in chess **"your move is
unanswered" and "your move is on top of the stack" are the same statement**: turns
strictly alternate, so an answered move is not the top one. That identity is why
`D016`'s "the previous move becomes undoable again once the opponent takes their
reply back" costs no code at all.

What does not carry is that chess's lock is **entirely retrospective**. Whether a
move is undoable depends only on what happened *after* it, which is what makes it
derivable from the stack top, and what lets it flip back. A game with hidden
information adds a second, **intrinsic** class of lock: an action that moved
information from hidden to known — for anyone, the actor included — cannot be
taken back by the actor alone, whatever follows, and unlike the retrospective
lock it never reopens on its own. Drawing a card and playing one face up are the
same case in this respect; popping the stack does not un-know either of them.

An intrinsic lock cannot be derived from being on top of the stack, so it has to
be carried by the record. That is one field, not a redesign: each history entry
records whether its action revealed anything, and eligibility becomes *you made
the top action, the game is running, and that action revealed nothing*. Undo
therefore survives into the deck-builder for every action that leaks nothing —
rearranging a hand, selecting without committing, a purchase that reveals no card
— which is most of what a player actually wants back.

**Both locks are on unilateral undo only, and that is the whole of what `D016`
governs** — its title is *Takebacks Are Unilateral Until Opponent Responds*. A
locked action is not physically irreversible; it is one the player may no longer
reverse *by themselves*. Undo by mutual agreement is a separate authority level
that chess never needed and the deck-builder should keep available, and the
project owner has said the system must be built so a consented undo can reach
past a lock even though the asking-for-permission flow is not being built yet.
The consequence is a storage one and is taken up under *Design notes* below: a
rewind that may cross a reveal cannot rely on the reveal to bound how much prior
state is kept.

One consequence for the projection work: `positionBefore` is safe to store and to
hand out in chess because both players may see it anyway. In a hidden-information
game that snapshot *is* the secrets, so the undo record must stay server-side and
never be projected to a viewer.

`PRODUCT.md`'s undo rules are still chess's own — "once the opponent moves, the
prior move is locked" is the retrospective predicate and nothing more.

**Rewriting history on every write.** `save` deletes and rewrites the whole move
history each time
([GameRepository.kt:213](../server/src/main/kotlin/com/jmussel/chessgame/server/db/GameRepository.kt:213)),
which is correct, simple, and cheap for a hundred plies of chess. It is `O(game
length)` per action. This is a deliberate concrete choice that should be
revisited, not inherited, for a game with many actions per turn.

**Turn structure.** In chess a turn is a move: `NotYourTurn` is a hard refusal
against a single scalar `sideToMove`, and playing a move both changes the state
and passes the turn, because those are the same event. A turn composed of several
actions, and reactions taken outside one's own turn, are outside what was built.

The deck-builder's shape is known, and it is a mild one. **Ending a turn becomes
its own command**, which the deck-builder raises from a button and chess issues
implicitly after every move — so chess stays exactly as it behaves today while
the platform gains the concept. Turn ownership can stay a scalar; what separates
is *whose turn it is* from *who may act right now*.

Reactions are narrow and prompted rather than open priority — "when an opponent
deals damage to you, you may discard this card to prevent up to 3" — so they need
a **pending decision** in game state naming the one player who owes an answer and
the choice offered, not a general priority system. `NotYourTurn` generalises to
*you are not the player this state is waiting on*.

There are no simultaneous secret choices in the planned game, which matters more
than it sounds: those were the one case that genuinely broke the version model,
because two concurrent legal commands would contend for a single version and the
loser would be refused as stale despite being valid. Without them, **"one accepted
command, one version" holds unchanged.** Several versions per turn is the
intended behaviour and not a cost to be optimised away — each action is worth
watching, and the opponent seeing them arrive one at a time is the point.

Turn order also stops being a two-sided alternation: it rotates across up to four
human participants and a scripted enemy that takes turns of its own. The
sequencing consequences are under *Design notes*; how those participants are
seated in the first place is separate work.

**Scale.** One beta game between two people on a Render Free instance. Nothing
here has been shown at load, with many concurrent games, or across more than one
server process — and the process-local `RealtimeHub` is a known blocker for the
last of those.

## Design notes for the deck-builder

Everything above is a measurement. This section is not: it is design input
settled with the project owner across two sessions (2026-09-08 and 2026-09-09),
for a system that does not exist yet. When the deck-builder starts, whatever
survives contact with it is confirmed or revised then.

The 2026-09-09 session produced decisions rather than notes: **`D048`–`D056`**
cover seating and continuity (the *table* replacing the friend pair), groups,
seat rotation, non-user participants, resignation and series exit for N players,
the chess product rules that change as a result, client-side legality, the undo
horizon, and the player-visible history view. Those decisions are authoritative;
the subsections below summarise them and keep the reasoning that did not fit a
decision record. The 2026-09-08 material (undo mechanism, shuffle barrier,
randomness, turn structure, the public active hand) remains as notes — it is
reflected in `D044` and in `PRODUCT`/`ARCHITECTURE` pointers but was not
re-recorded as its own decisions.

### The action history should be a stack in storage, as it already is in the domain

`ChessGame.history` is already a stack — push on a move, `dropLast(1)` on undo.
The O(N²) cost is entirely in persistence, and its root cause is a signature:

```kotlin
fun save(id: Uuid, expectedVersion: Long, game: ChessGame, auditEvent: String?): Long
```

`save` is handed *the resulting game*, never what happened to it. It cannot tell
a push from a pop, so its only correct option is to snapshot the whole stack —
`deleteWhere` then re-insert every record
([GameRepository.kt:213](../server/src/main/kotlin/com/jmussel/chessgame/server/db/GameRepository.kt:213)).
It does O(N) work to record an O(1) operation. For a hundred plies of ~1KB that
is invisible and buys a real guarantee: stored history always exactly equals
`game.history`, with no incremental-diff bug possible.

The fix is to make the command say what it did — `append(action)` /
`truncateTo(seq)` rather than "here is the new whole game". Push becomes one
insert, undo becomes `delete where game_id = ? and seq > ?`, and nothing else
about the model changes.

### Prior state is kept to a turn horizon, not to a reveal

The expensive column is not the action, it is `position_before`: a full state
snapshot per row, which exists only to make undo exact.

An earlier draft of this review proposed bounding that storage with the reveal
lock — a revealed action can never be undone, so it needs no snapshot. **That is
wrong, and it is wrong because of consented undo.** Both locks restrain the actor
alone; two players who agree may rewind past a reveal, so a reveal cannot be
treated as the point beyond which prior state is discardable.

Two horizons work, and the tighter of the two applies.

The turn boundary, which the deck-builder has anyway now that ending a turn is a
command:

- a snapshot per action **within the open turn**, bounded by actions-per-turn,
- a checkpoint per **closed turn**, bounded by turns,
- per-action snapshots pruned as their turn closes.

And the shuffle, which is a **hard barrier**: nothing before the most recent
shuffle is ever needed again, so it prunes unconditionally. Consented undo inside
the turn restores an action snapshot; to an earlier turn, that turn's checkpoint;
and past a shuffle, never.

**A shuffle can never be undone — by anyone, with or without agreement (project
owner, 2026-09-08).** The reason is not storage, which is only the dividend. It is
that undo across a shuffle has no correct implementation:

- **restore the RNG position** and redoing reproduces the order the players have
  already seen, so the restored state claims hidden information that is not
  hidden;
- **do not restore it** and the shuffle re-rolls, which is peek-and-reroll —
  see a bad shuffle, rewind, take a different route to a different one.

Every other undo restores state *and* knowledge symmetrically, because nothing
was learned that the restoration does not account for. A shuffle is the single
action where that symmetry breaks: it destroys position knowledge globally, and
no restoration puts it back. Barring it is the only clean resolution, not a
convenience.

The barrier is **global** — one ordered action log, one version counter, one
barrier — and **inclusive of the action that triggered the shuffle**, since a
reshuffle raised inside a draw would otherwise unwind with that draw. One integer
on the game row records it, and eligibility gains `seq > undoBarrierSeq`.

**Nothing here re-executes rules.** Restoring a stored snapshot is safe under any
future rules change; reconstructing state by replaying actions is not, which is
the subject of the next note.

### Turn structure and sequencing

A turn is several actions followed by an explicit end, so **`EndTurn` becomes its
own command**. The deck-builder raises it from a button; chess issues it
implicitly after every move, because there a move and the end of the turn are the
same event. Chess therefore behaves exactly as it does today while the platform
gains the concept.

**One accepted command, one version, one push — per action, deliberately.**
Several versions in a turn is the intent rather than a cost to optimise away:
each action is worth watching, and the opponent seeing them arrive one at a time
is the point. This holds only because the planned game has no simultaneous secret
choices; those were the single case that would have broken the version model, by
making two concurrent legal commands contend for one version so the loser was
refused as stale despite being valid.

**Turn order rotates across participants, and one of them is not a person.** Up
to four humans, plus a scripted enemy deck that takes turns of its own. The
enemy must resolve server-side through the same command path as everyone else —
any other route and canonical state, audit, and the clients diverge — and it
should bump the version per action like any other participant, so both the audit
trail and the players see what it did, one step at a time. It also closes the
retrospective undo lock exactly as an opponent's move does in chess: the enemy
acting *is* another participant having acted.

*(How four players are seated — what replaces the friendship pair as the unit of
continuity — is now decided: `D048` (the table), `D049` (groups), `D050` (seat
rotation), `D051` (non-user participants), `D052` (resignation and series exit
for N players). See the summary below.)*

**Reactions are prompted, not open priority.** "When an opponent deals damage to
you, you may discard this card to prevent up to 3" is a bounded question put to
one participant at a known point, not a general right to act out of turn. That
needs a **pending decision** in game state naming the participant who owes an
answer and the choice offered — not a priority system. `NotYourTurn` then
generalises to *you are not the participant this state is waiting on*, which
covers the turn holder and an owed reaction with one rule.

### The active player's hand is public, which makes most of a turn undoable

A product decision with a large technical dividend: the player whose turn it is
has their hand revealed to everyone, and when the turn passes the next player's
hand is revealed in its place.

It deletes a case rather than solving it. "Put a known card on top of your deck,
then draw it, and now a card in your hand is known to your opponent" stops being
awkward — during your turn *all* of your hand is known, and outside your turn
none of it is being acted on. Visibility no longer varies card by card within a
hand; it varies by whose turn it is.

The dividend is undo. **Almost every in-turn action reveals nothing**, because
the zone it operates on is already public: playing a card, choosing a target,
arranging, buying with visible resources. What still reveals is a short list —
drawing from your own deck when the top card is not known, and any effect that
exposes a card from the market deck. So a player may take back anything up to the
point where they pull new information out of a hidden zone, which is a generous
and easily explained rule, and lands very close to what `D016` reaches for in
chess from the opposite direction.

Two consequences worth keeping in view. A draw that triggers a reshuffle always
reveals — the deck was empty, so no top card could have been known — so it is
locked twice over, by the reveal and by the shuffle barrier. And consented undo
remains outside the rest of this: two players who agree may rewind past a
*reveal*, and no mechanism can make that information-safe, because it is an
agreement to disregard something already seen rather than a technical guarantee.
That is acceptable between friends, and it is deliberately where the line sits —
consent buys a takeback, never a re-roll.

**`Reshuffle` and `Draw` are separate entries in the action log** (project owner,
2026-09-08), so the barrier lands on the shuffle alone and the draw stays
independently undoable. That keeps the misclicked draw on an empty deck
recoverable, which is the friendly-undo property the barrier would otherwise have
taken away as a side effect. It is safe for the reason it is useful: the player
has seen the top card either way, so taking the draw back gains them nothing.

The split has to be real in the log. A reshuffle recorded as a side effect inside
the draw's own record unwinds with it, which is the whole thing this avoids. Three
consequences follow:

- **Two transitions, two versions, one transaction.** One tap produces both, and
  `D021` counts every accepted mutation, so the version moves by two. They commit
  together — a crash between them must not leave a deck reshuffled for a draw that
  never happened — and one realtime push carrying the final version is enough,
  since clients reload rather than apply. Committing two related transitions
  atomically has precedent: `GameCommandService.applied` finalizes a game and
  creates its rematch in one transaction. Bumping a version twice does **not**.
  `GameRepository.save` computes `expectedVersion + 1` internally and writes it
  once, so a single call cannot express two transitions; that signature has to
  change alongside the append/truncate one above.
- **The log must tolerate entries nobody chose.** A reshuffle is forced by the
  rules, not selected by the player. Its actor is still the deck's owner, but
  "who acted" and "who chose" stop being the same field. The scripted enemy needs
  exactly the same separation, so this is one concept, not two.
- **The draw's stored prior state is the post-reshuffle state**, which is what
  makes undoing it land on a deck that is already shuffled and stays shuffled.

### Randomness is materialised, counter-based, recorded, and never given to the client

Five parts, and the first is what makes the rest easy.

**Shuffle into state; never derive a draw on demand.** The deck is an ordered
list in canonical state. A shuffle reorders it once, and drawing pops from it.
This is simply the real-life model — once the deck is shuffled the order is
fixed — and it is what makes *peek-and-reroll* structurally impossible rather
than merely defended against. A design that derived each draw lazily from
`(seed, counter)` at draw time would let a player see what they drew, undo,
act differently so the counter advanced differently, and pull a different card.
Materialising the order removes the attack: undoing a later action does not
re-shuffle, because the order was never going to be recomputed.

Shuffles happen twice in a game's life — at setup, and on deck exhaustion, when
the discard pile is shuffled back in. Nothing else reorders a deck.

**The seed is audit, not mechanism.** Materialising the order was the first half
of this; barring undo across a shuffle is the second, and together they retire the
question. A shuffle is never undone, so it is never redone, so no shuffle ever has
to be reproduced — and the RNG stops being part of the state machine's
reversibility contract altogether. Shuffle with anything; record the resulting
order in state and in the action row. Keeping a seed is still worth it for
debugging and for settling a dispute about what a game actually did, but nothing
depends on it, and it should not be mistaken for something that does.

**Record the outcome as well.** The action row should say which cards were
actually drawn, not merely imply it from the seed. It is redundant on purpose:
the audit trail becomes readable, and divergence becomes detectable instead of
silent.

**Never reconstruct canonical state by replaying from the seed.** Seed replay is
deterministic only while the rules code is unchanged. Deploy a fix to a card's
effect and every in-flight game replays into a different game than the players
actually played — silently, with no error anywhere. Chess never risks this
because it never replays: `state` is canonical and history serves undo and
display. **Keep that property.** Seed and counter are for generating randomness
reproducibly during forward execution, and for debugging; they are not a
state-recovery mechanism. This is also why the turn-horizon scheme above stores
snapshots rather than replaying forward from checkpoints.

**Neither the seed nor the deck order may reach a client.** Materialising the
shuffle moves the secret: canonical state now literally contains the order of
every deck, so the per-viewer projection has to strip deck order — and the
identity of any card whose position is not known to that viewer — before the
state leaves the server. The seed must not ship either, since seed plus counter
plus a known deck composition reconstructs the same thing. Chess has no analogue
for either; there is nothing in a chess position that both players may not see.
This is the single most important thing on the projection checklist, because
unlike a leaked hand it would hand over the entire future of the game.

Projection checklist so far, for this ruleset:

- **deck order** — never leaves the server;
- **card identity where the viewer does not know the position** — stripped
  before state leaves the server;
- **the seed** — never leaves the server;
- **the player-visible history view** (`D056`) — safe to expose here, because
  this ruleset has no permanently hidden information and the view reports only
  events that occurred; a ruleset *with* permanent secrets would need per-viewer
  redaction of that view.

### Seating, continuity, and participants (2026-09-09 — see `D048`–`D053`)

The **table** — a creator-assembled set of 2–4 participants — replaces the
friend pair as the unit of continuity, and a series is keyed to a table's exact
participant set (`D048`). Table size is game-defined (chess: 2; deck-builder MVP:
2–4). **Groups** are a standing invite-eligibility pool with no role in game
state (`D049`); membership mirrors friendship — immediate, unilateral leave.
Invite-eligibility runs through the table creator only and propagates
transitively through a group.

**Seat rotation** (`D050`) generalises chess's colour alternation: a *cycle* is N
games with a fixed base order that rotates by one each game, so every seat is
first once and last once per cycle; for N ≥ 3 the next cycle's base order may not
be a rotation of the immediately preceding one. Chess is the N = 2 case,
unchanged. The **scripted enemy** (`D051`) is a non-`users` participant kind, is
excluded from rotation, and always takes the last turn; a future AI player is a
separate kind that rotates like a human.

**Resignation and series exit** (`D052`) separate: resigning ends only that
game's participation; the resigner is then asked whether to continue at the
table, and declining cancels the table's auto-rematch and offers the remainder a
fresh table. Series exit is an explicit action, not a friend-graph side effect.

Two chess product rules change as a result (`D053`): one active series per friend
pair is **removed** (drop index `game_series_one_active_per_pair`, migration V3),
and unfriending **no longer closes a series** (drop
`game_series.close_after_current_game`). `D014` is kept as `D050`'s N = 2 case.

### Client legality, undo horizon, history view (2026-09-09 — see `D054`–`D056`)

- **The deck-builder client computes legality locally**, in full, like chess
  (`D054`) — sound only because legality never depends on hidden *contents*,
  just on the player's own hand, public state, and opponent hand *counts*. Any
  future card that breaks that assumption forces a per-action rethink.
- **Undo reaches back to the last shuffle with no depth cap** (`D055`). The
  accepted cost — retained full snapshots for an unbounded span, times a
  whole-blob rewrite per action, times up to five participants' zones — was
  never sized, and `M19` carries an explicit costing task before undo is built.
- **Players get a readable history view** (`D056`) derived from `game_events`,
  with no per-viewer redaction, because this ruleset has no permanent secrets.
  The "audit never leaves the server" rule becomes: raw rows and canonical state
  are server-only, a derived replay-equivalent view is a legitimate surface.

### Still open

Named here so they are not lost; none is decided.

- **Repository / module structure for the deck-builder** (`M19.1`). Same repo,
  same server, separate module — or a new project. Most of the seating design
  assumes shared concepts, which leans toward same-repo, but it is not decided.
  This gates the shape of most other `M19` work.
- **Performance under deck-builder load.** Three uncosted things: the
  `loadForUpdate` row lock held across rule resolution; `inSeries`'s N+1
  (test-only today); and the whole-`state`-blob read-and-rewrite per action,
  which now also carries `D055`'s snapshot retention. The blob cost is the one
  to size first.
- **`(gameId, version)` stops being a state identity** once per-viewer
  projection means two viewers at the same version get different payloads.
  Anything keyed on it — caching, dedup, "seen this update" — needs the viewer
  in the key. A consequence of an already-settled decision; needs a recorded
  decision, not another interview. Carried as an `M19` task.
- **Target concurrency / load** — deliberately not guessed; revisit with real
  usage data. New `users` columns `last_login_at` / `last_action_at` are a first
  step toward having something to look at.
- **Spectators** — out of MVP scope, but the projection model should not assume a
  fixed cap on who can observe a game.

## What changes now

Nothing in the code. This is a review, and `D044` records the decision that
follows from it: extract when there are two implementations to compare, not
before. `ARCHITECTURE.md` §31's expectation list has been replaced with a
pointer here, because it was a prediction and this is a measurement.
