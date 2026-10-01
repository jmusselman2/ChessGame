# ChessGame — Codebase Guide for Agents

The working map of this repository: what each part owns, how a request moves
through it, the commands that verify it, and the places it breaks. It is written
for any coding agent (Claude Code imports it from `CLAUDE.md`; Codex and others
read it directly) and for a new engineer.

**Status:** descriptive and nonbinding. It was written on 2026-10-01 against
`main` at `27abac0`. The rules live in `CLAUDE.md` and the documents it ranks:
`docs/DECISIONS.md` first, then `PRODUCT`, `ARCHITECTURE`, `MVP`, `BACKLOG`,
`DEVELOPMENT`. Where this file and those disagree, they win, and this file is the
one to fix. `docs/DEVELOPMENT.md` stays the authority for commands.

**Agents other than Claude Code:** read `CLAUDE.md` before changing anything. Its
product rules, architecture rules, branch procedure and stop conditions apply to
every agent. Independent evaluation follows `docs/INDEPENDENT-EVALUATION.md` and
`docs/CODEX_EVALUATION_STATE.md`, runs only on `codex-autopilot`, and changes no
production code.

## What exists today

Four Gradle modules (`settings.gradle.kts`):

- `chess-core` — pure Kotlin/JVM chess rules. No I/O and no dependencies outside
  the module.
- `chess-ai` — pure Kotlin/JVM: the computer opponent's `ChessEngine` and the
  project's `AlphaBetaEngine` (`D086`, `D088`). Depends on `chess-core` only; only
  `chess-app` may use it.
- `server` — Ktor + Exposed on PostgreSQL. Authoritative for every online game.
- `chess-app` — the Android app (Jetpack Compose); source under
  `chess-app/app/src/main/java/com/jmussel/chessgame/`.

Plus `database/migrations/` (Flyway `V1`–`V11`), `Dockerfile` and `render.yaml`
(the beta deploy), `scripts/` (beta verification), `evals/` (evaluator reports)
and `docs/`.

Pass-and-play is saved in the SQLDelight local-game store (`M21.1`, `M21.2`).
Games against the computer are `M21.6`–`M21.7`. `deck-core` and the Deck Builder app
are planned (`F26`), not built; `CLAUDE.md`'s rules about them bind the code that
will be written, and you will not find it yet.

## Architecture

### `chess-core` — the rules

- `core/chess/ChessRules.kt` is the only public entry point: `legalMoves`,
  `isLegal`, `applyMove`, `terminalResult`, `availableDrawClaims`, `claimDraw`,
  `resign`, `undoableSide`, `canUndo`, `undo`. The other files (`LegalMoves`,
  `PseudoLegalMoves`, `Attacks`, `Castling`, `EnPassant`, `Repetition`,
  `MoveCountDraws`, `InsufficientMaterial`) are its helpers.
- `GameState` is one immutable position: board, side to move, castling rights,
  en passant target, `DrawRuleState` (halfmove clock plus repetition counts) and
  `result`.
- `ChessGame` is a `GameState` plus history: `MoveRecord(move, positionBefore)`.
  Undo restores `positionBefore`; it never replays moves (`D061`).
- Every action is a query/transition pair (`isLegal`/`applyMove`,
  `canClaimDraw`/`claimDraw`, `canUndo`/`undo`). A transition `require`s its
  query and throws `IllegalArgumentException` when called without it.

### `server` — the authority

Under `server/src/main/kotlin/com/jmussel/chessgame/server/`:

- `Application.kt` wires everything. With `DATABASE_URL` and `SUPABASE_URL` set it
  migrates the database and serves the API; without them it serves `/health`
  alone.
- `auth/` verifies the Supabase JWT (`SupabaseTokenVerifier`, via
  `SUPABASE_JWKS_URL`) and resolves it to a `users.id` through
  `user_auth_subjects`. Everything except `/health` sits behind it.
- `game/` — `GameRoutes` (HTTP) and `GameCommandService` (validate with
  `chess-core`, save, bump the version).
- `series/` — `SeriesService` creates series and starts automatic rematches.
- `user/`, `friends/`, `groups/`, `history/`, `dashboard/` — the social and
  listing endpoints.
- `realtime/` — `RealtimeHub` (in-memory map of open sockets per user) and
  `/ws`.
- `api/ApiTypes.kt` — the wire DTOs, including the per-viewer `GameViewDto`.
- `db/` — Exposed repositories. `Tables.kt` mirrors the migrations, which are the
  real schema. `GameStateDocument` is the `jsonb` shape a `GameState` is stored
  in.

Endpoints: `/health`, `/me`, `/username`, `/users`, `/friends`, `/groups`,
`/series`, `/series/{id}/leave`, `/games/{id}` and its
`moves|undo|resignation|draw-claims`, `/dashboard`, `/history`, `/ws`.

### `chess-app` — the client

- `MainActivity.kt` creates `ChessAppViewModel` and hands its state and callbacks
  to `app/ChessApp.kt`, which picks the screen with a `when` on the current
  `Destination` (`navigation/Destination.kt`, a back stack).
- `app/ChessAppViewModel.kt` (about 1,650 lines) holds nearly all client state:
  navigation, startup, the online game, the local game, friends, groups,
  dashboard, history, and the realtime loop.
- `app/AppStartup.kt` — sign in, wait out a cold server, ask `/me`.
- `auth/` — anonymous Supabase session. `api/ChessApiClient.kt` — HTTPS with an
  overall per-request time limit (`D074`). `api/ChessRealtimeClient.kt` — the
  WebSocket.
- `ui/board/` is shared by the local and the online game: `BoardInteraction`
  (what a tap means), `GameControls` (undo, claims, resign, status, move list),
  `ChessBoard`, `BoardRendering`, `GameLayout`. `LocalGameScreen` is
  pass-and-play.
- `ui/game/OnlineGame.kt` and `OnlineGameScreen.kt` — the online game.
- `ui/localhistory/` — Past local games: the list, and `LocalGameReview`, a
  read-only ply-by-ply review built from the stored positions.
- `local/` — the device-authoritative store for local games (`D084`, `D085`).
  `LocalGameStore` over the SQLDelight `LocalGameDatabase` generated from
  `src/main/sqldelight` (`local_games`, `local_moves`); positions as JSON in
  `LocalStateDocument`; `openLocalGameStore(context)` opens `local_games.db`, which
  the backup rules exclude. `LocalGameSession` keeps the store in step with the game
  on screen, and `LocalGameChange` says what a screen update did to the game.

### Outside the code

- Supabase supplies authentication and the hosted PostgreSQL, nothing else. The
  app never reads or writes game tables directly.
- Render builds `Dockerfile` (server only, `-PserverOnly`) and auto-deploys every
  commit to `main` as the beta. The beta shares the `ChessGame Dev` Supabase
  project (`D035`).
- CI (`.github/workflows/ci.yml`) runs `./gradlew build --continue` with a
  PostgreSQL service on pushes to `main` and `claude-autopilot`, and on pull
  requests to `main`. It does not run on `codex-autopilot`.

## Key flows

### Startup and identity

Local games do not wait for any of this: the startup screen offers them throughout
(`M21.4`).

1. `AppStartup.run` restores or creates the anonymous Supabase session, retrying
   while the free Render instance wakes (`withServerWake`).
2. It calls `GET /me` with the bearer token. The server verifies the token and
   maps its subject to a user through `user_auth_subjects`.
3. No username yet means onboarding: `POST /username`. A new name creates the
   user; an existing name attaches this installation to that user with no
   verification (`D082`, prototype-only).

### An online move

1. `OnlineGame.replayOf` replays the server's move list through `chess-core` to
   preview legal destinations. This is pre-validation only.
2. The app POSTs `/games/{id}/moves` with the move and the `expectedVersion` it
   last saw.
3. `GameRoutes` → `GameCommandService.makeMove`, in one transaction: load the
   game, check participant, turn and version, check `ChessRules.isLegal`, apply,
   `GameRepository.save` with the expected version, write the audit event.
4. Rejections are typed `CommandResult`s: `StaleVersion`, `NotYourTurn`,
   `GameOver`, `NothingToUndo` → 409; `IllegalMove`, `NoSuchClaim` → 422.
   Not a participant → 403.
5. After the commit, `RealtimeHub` announces the new version to both players'
   sockets.

Undo, resignation and draw claims follow the same path through their own
endpoints.

### Realtime updates

The socket message carries only `gameId` and `version`. The client then fetches
the game over HTTPS (`D022`). The socket is a hint, not a source of truth; a
dropped socket is reconnected and the screen refreshed.

### Game end and rematch

A move or claim that ends a game calls `SeriesService.settleAfter` inside the
same transaction. If the series is still active, the next game is created with
colours swapped (`D015`, `D050`), so no client can see a finished game without
its successor. The series row is locked, so retries and races start one rematch
at most.

### A local game (pass-and-play)

Entirely on the device: no network, and the device's own database, never the
server's (`D084`).

1. The top bar's "Local game" calls `ChessAppViewModel.open`, which shows a loading
   state while `LocalGameSession.resume` reads the unfinished pass-and-play game from
   `LocalGameStore`, or starts a new one.
2. A tap goes `ChessBoard` → `BoardInteraction.onSquareTapped` →
   `ChessRules.applyMove`. A promotion or a draw-entitling move stops first to
   ask the player (`D041`).
3. `LocalGameScreen` passes the new state to `ChessAppViewModel.updateLocalGame`,
   and Compose redraws. `LocalGameChange.between` compares the old and new
   `ChessGame`; a move, a takeback or a result is saved, in order, off the main
   thread. A selection change saves nothing.
4. New game asks first if the game is unfinished, then `startNewLocalGame` deletes
   it and starts another.

The board is drawn face to face and never turns: White at the bottom, Black's
pieces upside down for the player opposite (`D087`). The game survives rotation
(`D073`), Back and process death. Finished games are listed under History → "Past
local games" and reviewed read-only (`ChessAppViewModel.openPastLocalGames`,
`openPastLocalGame`).

## Commands

`docs/DEVELOPMENT.md` is authoritative and has the Windows forms. Any JDK 17 or
newer starts the wrapper; Gradle provisions JDK 25 for its daemon and JDK 24 for
`chess-core` and `server`. The Android SDK must be findable through `ANDROID_HOME`
or a git-ignored `local.properties` (`sdk.dir=...`), or `build` fails with "SDK
location not found".

```bash
./gradlew build                     # the one full gate: ktlint, all tests, Android lint, APKs, server dist
./gradlew build --continue          # what CI runs: report every failure, not the first
./gradlew check                     # build without packaging
./gradlew ktlintCheck               # formatting; ktlintFormat fixes it (review the diff)
./gradlew :chess-core:test          # rules tests — start here for any rules change
./gradlew :chess-ai:test            # the engine (about a minute)
./gradlew :chess-app:testDebugUnitTest
./gradlew :server:test              # database tests are no-ops without TEST_DATABASE_URL

# server tests against a real database
docker compose up -d                # PostgreSQL 18 on localhost:55432
TEST_DATABASE_URL=postgresql://chessgame:chessgame@localhost:55432/chessgame_test \
  ./gradlew :server:test --rerun-tasks

./gradlew :server:run               # http://localhost:8080/health; needs DATABASE_URL and SUPABASE_URL for the API
./gradlew -PserverOnly=true :server:installDist   # what the Docker image runs; no Android SDK
```

`-PserverOnly=true` (or `CHESSGAME_SERVER_ONLY=true`) leaves the Android module
out, for machines without the SDK. A green `./gradlew build` is required
before a backlog task is `DONE`, and `git diff --check` must be clean before every
commit.

## Sharp edges

### Rules and `chess-core`

- **Client and server run different `chess-core` versions.** `main` deploys to
  the server at once; testers' APKs lag. A rule change is a client–server
  contract change. An older app whose replay fails gets `null` from
  `replayOf` and loses its move preview.
- **Stored games are not re-derived.** The server stores `GameState` as JSON and
  a full `positionBefore` per move. Changing `Repetition.keyOf`'s string format
  silently breaks the threefold and fivefold counts of every in-progress game. A
  new `GameState` field needs a defaulted field in `GameStateDocument`. A stored
  `result` is never recomputed.
- **Keep query and transition in step.** The server asks the query first and maps
  "no" to a 409/422. A new `require` in a transition without the matching
  condition in its query becomes a 500 inside a database transaction.
- **`terminalResult`'s order is product behaviour:** checkmate, insufficient
  material, fivefold, 75-move, stalemate. The reason is persisted and shown in
  history.
- **Use the `ChessGame` overloads** for anything that must stay undoable. The
  `GameState` `applyMove` records no history.
- **Do not turn undo into replay** (`D061`).
- **Legal moves are regenerated often:** about half a dozen full generations per
  tap, and `terminalResult` alone does two. That is harmless for people; `M21`'s
  engine should call `ChessRules` directly and keep any speed-up inside it.
- **Keep `chess-core` free of** Android, Ktor, serialization and database code.
  Wire mapping belongs in a server DTO, as `GameStateDocument` shows.

### Server and database

- **Migrations run on server startup against whatever database it is given**,
  including the beta. Migrations are forward-only and must be safe on live data.
  Never edit an applied `V*` file; add the next one.
- **`Migrations.reset` drops the schema.** It is guarded to loopback hosts
  (`DisposableDatabase`), and every server test run calls it. Never point
  `TEST_DATABASE_URL` at Supabase.
- **`/health` returns 200 even in health-only mode.** Read the body: a deploy with
  missing environment variables reports healthy while serving nothing.
- **Realtime is in-memory in one process.** A second server instance would miss
  announcements for sockets connected to the other.
- **Optimistic concurrency:** every accepted mutation bumps `version`, and a save
  with a stale `expectedVersion` is refused. A new command must go through
  `GameRepository.save` with the version, not around it.
- **Identity is claim-by-username** with no verification (`D082`). Don't build
  anything that treats a username as proof of who is asking.

### Client

- **`ChessAppViewModel` is the god object.** Read it before adding state, and
  prefer a focused class it owns over another 100 lines inside it.
- **Local games are reachable before startup succeeds** (`D084`, `M21.4`): the
  startup screen offers them. So startup can finish while the player is in a local
  screen; `arriveAt` then rebases the navigation stack rather than restarting it.
  Anything new that navigates on startup's result must do the same.
- **The local-game store's writes are intent-level and strict.** `recordMove` takes
  the stored game plus exactly one move, `recordResult` a result with no new move,
  `takeBack` a number of plies; anything else, or a finished game, is refused. Its
  calls block: run them off the main thread.
- **Two SQLDelight versions on purpose.** Plugin and runtime 2.4.0 (AGP 9), Android
  driver 2.2.1 (minSdk 22). Don't "align" them; `docs/DEVELOPMENT.md` explains. Keep
  the SQL within Android 5.1's SQLite 3.8, and change the schema only through a new
  `.sqm` migration plus a new `databases/N.db` snapshot.
- **`updateLocalGame` gets a whole screen state, not an intent.** It saves only what
  `LocalGameChange.between` finds changed in the `ChessGame`. A new local action must
  produce a game that is the old one plus a move, minus moves, or with a result; any
  other change to the game throws.
- **Release builds forbid cleartext.** The debug build allows `10.0.2.2` and
  `localhost` only (`D033`). An emulator reaches the local server at
  `http://10.0.2.2:8080`.

### Build, tests and process

- **A green `:server:test` without `TEST_DATABASE_URL` proves nothing.**
  Database tests report *passed* without running, and Gradle may report the task
  `UP-TO-DATE` even after you set the variable. Use `--rerun-tasks`; CI always
  sets the variable.
- **Rules correctness belongs in `chess-core` tests**, not Android tests.
- **Branches have owners:**
  - `claude-autopilot` — implementation.
  - `codex-autopilot` — the evaluator's checkpoints.
  - `main` — linear, deploys the beta.

  Fast-forward only; never merge, rebase or force-push `main` or
  `codex-autopilot`. The full procedure is in `CLAUDE.md`.
- **The docs are big and binding.** `docs/BACKLOG.md` is about 7,000 lines and
  `docs/DECISIONS.md` about 6,000. Search by task id (`M21.1`) or decision
  number (`D061`) rather than reading top to bottom, and check whether a decision
  has been superseded before relying on it.
