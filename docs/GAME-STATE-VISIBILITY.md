# Game-State Visibility — the `M19.10` Analysis

**Written:** 2026-09-16, against `claude-autopilot` at `68d503b`
**Backlog task:** [`M19.10`](BACKLOG.md#m1910--per-viewer-state-projection-and-the-gameid-version-identity)
**Status:** nonbinding analysis. Its binding conclusions are
[`D069`](DECISIONS.md#d069--state-leaves-the-server-only-through-a-per-viewer-projection-built-as-an-allowlist)
(the projection boundary and what never crosses it) and
[`D070`](DECISIONS.md#d070--a-game-payloads-identity-is-gameid-version-viewer-and-version-orders-state-without-naming-a-payload)
(what identifies a payload once projection is per viewer). **Where this document and
those decisions differ, the decisions govern.** Nothing here is implemented beyond what
chess already does.

This document is below `docs/DECISIONS.md`, `PRODUCT.md`, `ARCHITECTURE.md`, `MVP.md` and
`BACKLOG.md` in precedence, like `PLATFORM-REVIEW.md` and `UNDO-STORAGE.md` (`D062`). It
consolidates the projection checklist that `PLATFORM-REVIEW.md` kept under *Randomness is
materialised*, which now points here.

It is about **game state**: what a game's canonical state and history reveal, to whom, and
how that is kept true. Authentication, logging policy (`M16.5`), and infrastructure security
stay in their own documents.

## Method

1. **An audit of the code as it stands** at `68d503b`: every route's response type, every
   realtime message, what the Android client stores and how it orders what it receives, and
   what persistence holds that no response carries.
2. **The deck-builder's settled design**, from `D048`–`D056`, `D061`, and
   `PLATFORM-REVIEW.md`'s design notes, read for what each makes hidden.
3. For each surface, the question: *what does this reveal to a viewer who is not entitled to
   it, once state is not the same for every viewer?*

## Assumptions

- **The deck-builder has no permanently hidden information** (`D056`): every drawn card is
  eventually revealed in play, and a player's hand is public during their own turn. Its
  hidden state is transient — deck order, other players' hands outside their turn, and any
  card whose position a viewer does not know.
- **Legality never depends on hidden contents** (`D054`), so a client needs no hidden state
  to pre-validate.
- **No answer to `M20.1`.** Nothing here depends on where rules code lives; every conclusion
  is about what the authoritative server sends.
- **Spectators are out of MVP scope** (`F20` in `FUTURE.md`), but the model must not assume a fixed cap on
  observers (`PLATFORM-REVIEW.md` *Still open*).

## What leaves the server today

| Surface | Carries | To whom |
|---|---|---|
| `GET /games/{id}`, and the state attached to a refused command (`CommandRejection.game`) | `GameView`: the whole board, the whole move list, last move, side to move, check, result, `canUndo`, claimable draws, `seriesActive` | a seated participant only (`NotAParticipant` → `403`) |
| `GET /dashboard` | `DashboardEntry`: game id, **version**, your side, side to move, move number, `seriesActive` | the caller, for series they sit at |
| `GET /history` | `SeriesHistoryEntry`: finished games' result, reason, move count — no moves or positions | the caller, for series they sat at |
| `POST /series`, `POST /series/{id}/leave` | `SeriesSummary`: ids, status, opponent | the caller |
| realtime `game-updated` | `{type, gameId, version}` — no state | every user participant of that game |
| server log | ids, expected version, outcome (`commandLogLine`) | operators |

**Never sent, by construction:** `GameStateDocument` (the stored `state`), `moves.position_before`
(the undo record, `D029`), `game_events` payloads, `game_series.seat_rotation`, and
`non_user_participants.state`. Every route responds with a type from `server/api`, each
built field by field from stored data (`GameView.of`, `DashboardEntry.of`, …). No route
serializes a persistence type, so a field added to storage does not reach a client unless a
DTO is changed to carry it.

**The Android client** keeps no game state on disk. It holds one viewer's payloads in memory,
installs a game view forwards only by `version` (`D059` rule 2), orders dashboard reads by
issue order (`D059` rule 3), and treats a realtime message's version as a hint, never as
state (`D022`).

## Findings

### 1. Per-viewer projection and the `GameView.of` seam

`GameView.of(stored, viewer, opponent)` already takes the viewer, and every game payload goes
through it — including the state attached to a refusal, which is easy to forget and would
otherwise be a second, unprojected path. That is the right seam.

It projects **perspective, not visibility.** It decides `yourSide`, `yourTurn`, `canUndo`
and the claimable draws for the viewer, then copies the whole board and move list, because in
chess both players may see all of it. Four things about it stop holding for a hidden-
information ruleset:

- **It is two-player shaped.** It requires the viewer to be White or Black and takes one
  `opponent`. A table of up to four, a scripted participant (`D051`), and a spectator are
  all outside it.
- **Its output type is chess's.** A board and a move list. A deck-builder's view is a
  different document, so "the projection" becomes one function per ruleset, not a flag on
  this one. Where that function lives is `M20.1`'s question.
- **It is a copy with nothing to omit.** Visibility projection is an *allowlist over
  canonical state per viewer*: each zone is either copied, reduced to a count, or omitted,
  according to who is looking. Chess's answer is "copy everything" for every field.
- **Its input is the full stored game, and must stay so.** Projection needs canonical state
  to decide what to strip. The boundary is its output, not its input.

The property worth keeping from the current code is the one that makes all of this safe:
**responses are built as allowlists, never as a serialized state document with fields
removed.** A denylist fails open when storage gains a field; an allowlist fails closed.

### 2. Hidden-state exposure

For the deck-builder, the hidden zones are:

- **deck order**, for every deck;
- **card identity wherever the viewer does not know the position** — another player's hand
  outside their turn, face-down cards, the contents of any pile whose order is private;
- **the randomness secret**: the seed, and anything that, combined with a public deck
  composition, reconstructs order (seed plus counter).

What stays public: counts (hand size, deck size — `D054` depends on them), revealed cards,
every zone the ruleset shows face up, and the active player's own hand during their turn.

Deck order is the most damaging leak on this list. A leaked hand shows one turn; a leaked
deck order or seed shows the rest of the game (`PLATFORM-REVIEW.md`).

### 3. Deck order, unknown-position identity, and the seed

These three are **confirmed stripped today**, and structurally rather than by care:

- none exists in chess, and
- no route can send stored state wholesale, so when they are added to canonical state they
  reach a client only if a projection copies them.

What has to be true when they are added:

- The projection for each viewer emits a deck as a **count**, never a list.
- A card whose position the viewer does not know is emitted as **absent or anonymous**, never
  as an identity with a hidden flag. "The app chooses not to display it" is not a boundary
  (`D054`, *Alternatives*).
- The seed and any counter live only in server state and audit rows. They are never in a
  projection, a refusal, a realtime message, or a log line.
- **Undo snapshots are canonical state** (`D061`), hidden zones included, so they are
  server-only. An undo response carries the *projected* restored state, never the snapshot.

### 4. Viewer-specific payload identity — `(gameId, version)`

`version` counts accepted mutations of one game (`D021`). While every viewer receives the same
document, `(gameId, version)` identifies a payload. Once projection is per viewer, two
viewers at the same version hold different payloads, so **`(gameId, version)` identifies a
state, not a payload.** A payload is identified by `(gameId, version, viewer)`.

What that means for each thing keyed on it today:

- **The version guard on commands (`D021`)** is about canonical state and is unaffected: a
  command is decided against a version, whoever decided it.
- **Forwards-only installation (`D059` rule 2)** compares versions *within one client*, which
  only ever holds one viewer's payloads. It stays correct. It would break only if a client
  held payloads for two viewers — a spectator switching seats, or a device shared across
  accounts — so the viewer belongs in the comparison the moment that is possible.
- **Realtime messages** carry `(gameId, version)` and no state. They say "something changed",
  which is the same fact for every viewer, so they need no viewer. They must never start to
  carry state — a push with a projected payload would need per-recipient projection.
- **Dashboard `version`** is display and ordering data for the caller's own view, like the
  game view's.
- **Nothing caches rendered views** today: no server-side response cache, no HTTP caching, no
  client persistence. Any that is added must key on the viewer, including the `Vary`
  behaviour of any HTTP cache in front of the server — an authenticated response without the
  viewer in its cache key would serve one player's hand to another.

### 5. Caching, dedup, and "seen this update"

These follow from finding 4:

- **Dedup** of an incoming update by `(gameId, version)` is safe only for state-free
  messages. Dedup of payloads needs the viewer.
- **"Seen this update"** (unread markers, notifications) is per user by nature. Keying it on
  `(gameId, version, userId)` is the same rule, and it is also the right grain for a
  notification that names what changed, since what changed differs by viewer.
- **A client may keep ordering by version** within one viewer's session. That is `D059`, and
  it is the reason no client change is needed now.

### 6. History visibility

- **The player-facing history view (`D056`)** is safe to show every participant *for this
  ruleset*, because it reports only events that occurred and nothing is permanently hidden.
  Raw `game_events` rows stay server-only: event payloads will record what was drawn before
  it was revealed.
- **Chess today** exposes a finished game's whole move list through `GET /games/{id}`, and
  history lists results only. Both are complete-information surfaces.
- **Undo snapshots** (`D061`) and `moves.position_before` are server-only. In chess the
  latter is harmless because both players could see it. In a hidden-information game it *is*
  the secret, so the rule is that the undo record is never projected, whatever the ruleset.
- A ruleset **with** permanent secrets would need the history view projected per viewer too
  (`D056`). Out of scope.

### 7. Spectators

A spectator is a viewer with no seat. For projection that means:

- visibility is decided by the viewer's **role** — seated participant, eliminated or
  resigned participant, spectator — not by a seat index or a colour, and not by assuming
  every viewer is a player;
- a spectator of a hidden-information game sees at most what *no* seat hides: counts and
  public zones. Showing a spectator every hand is a different product (an omniscient
  observer), and would make any collusion between a spectator and a player a full leak;
- there is no cap on viewers in the model. Realtime delivery is to "who is watching", not to
  "the two players";
- `GameView.of`'s "the viewer is White or Black" requirement, and the participant check in
  `GameCommandService.load`, are where a spectator is refused today, correctly for MVP.

## How each is validated and tested

| Rule | Validation |
|---|---|
| Nothing leaves except through a projection | Responses are `server/api` types built field by field. A test per game-state route asserts the serialized JSON has **exactly** the expected keys, so an added field fails a test rather than shipping silently. |
| Hidden zones never cross | For each hidden zone, a **two-viewer test** plays a state where the zone differs by viewer and asserts the other viewer's payload contains neither the list nor any identity from it — searched in the raw JSON text, not the decoded DTO, so an unmapped field still fails. |
| Refusals are projected too | The same assertions against the `game` attached to a `409`/`422` refusal. |
| The seed never ships | A test with a known seed asserts it (and the counter) appear in no response body, realtime frame, or captured log line. |
| Undo returns projection, not snapshot | An undo test for a hidden zone asserts the response equals the projection of the restored state for that viewer. |
| Realtime carries no state | A test asserts a pushed frame's keys are exactly `type`, `gameId`, `version`. |
| Payload identity includes viewer | Any cache or dedup introduced gets a test with two viewers at one version receiving different payloads. |
| Spectators see only public zones | When spectators exist: a spectator-view test beside each two-viewer test. |

Chess has no hidden zone, so the two-viewer and seed tests have nothing to test yet. They
belong to the first hidden-information ruleset's projection, alongside the code that makes
them meaningful. `GameViewTest.aStrangerIsNotToldAnythingAboutTheGame` already covers the
participant check.

## Projection checklist

Moved here from `PLATFORM-REVIEW.md`, with the additions above.

- **deck order** — never leaves the server; a deck is a count in every projection;
- **card identity where the viewer does not know the position** — absent or anonymous, never
  a flagged identity;
- **the seed and counter** — never leave the server, including logs;
- **undo snapshots** (`D061`) and `position_before` — server-only; undo answers with a
  projection;
- **raw `game_events`** — server-only; the derived history view (`D056`) is safe in a
  no-permanent-secrets ruleset and needs per-viewer redaction otherwise;
- **refusal-attached state** — projected exactly like a read;
- **realtime** — identity and version only, never state;
- **payload identity** — `(gameId, version, viewer)`; `(gameId, version)` names a state;
- **viewers** — decided by role, uncapped, spectators see only what no seat hides.
