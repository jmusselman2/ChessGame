# Chess MVP — Implementation Backlog

## Status Values

Use exactly one:

```text
TODO
IN PROGRESS
BLOCKED
DONE
```

Do not use Markdown checkboxes as a second status system.

Work in milestone order unless a dependency explicitly permits otherwise.

Each task must satisfy its acceptance criteria and verification requirements before being marked `DONE`.

## How the Backlog Is Organised

This file holds the backlog's rules, its index, and every active milestone in full.
A milestone stays active even when all of its currently scheduled tasks are `DONE`,
until the owner explicitly closes it. Owner-closed milestones are archived under
`docs/backlog/`, grouped by phase, one file per group, named by its range with
two-digit milestone numbers so the files sort in order (`M01-M05.md`, `M21.md`). Task `M19.9` is in
`docs/backlog/M18-M20.md` (`D093`). Archived milestones keep their own
`# M<n> — Title` headings. This file and the archives share `BACKLOG`'s rank in
`CLAUDE.md`'s precedence list (`D103`).

- **Completing a task:** mark it `DONE` and add its completion note here.
- **Adding a milestone:** add it to this file, after the last active one, and its
  row to the index as `Active`.
- **Closing and archiving a milestone:** completing its current tasks does not close
  it. Leave its full text here and its index row `Active` until the owner explicitly
  authorizes closing that named milestone. Every task must then be `DONE`; in that
  owner-authorized change, move it unchanged into a new archive file in
  `docs/backlog/`, named by its range (`M23.md`, or `M23-M24.md` when several closed
  milestones go together), and point its index row there as `Done`. Archive files
  are never added to or renamed, so links into them keep working. Fix relative links
  in the moved text: the archive sits one directory deeper (`D103`).
- The backlog holds only scheduled work. Work that is not scheduled lives in
  `docs/FUTURE.md` (`D072`).

## Milestone Index

| Milestone | Title                                                       | Where                                       | State  |
| --------- | ----------------------------------------------------------- | ------------------------------------------- | ------ |
| `M1`      | Repository and Build Bootstrap                              | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M2`      | Chess Domain Model                                          | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M3`      | Chess Legal Move Engine                                     | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M4`      | Undo Semantics                                              | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M5`      | Local Android Chess                                         | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M6`      | Server + PostgreSQL Foundation                              | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M7`      | Identity and Username                                       | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M8`      | Friends                                                     | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M9`      | Game Series                                                 | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M10`     | Authoritative Game Command API                              | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M11`     | Authoritative Undo                                          | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M12`     | Realtime                                                    | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M13`     | Game End and Automatic Rematch                              | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M14`     | Android Multiplayer Client, Dashboard, and History          | [`backlog/M14-M17.md`](backlog/M14-M17.md)  | Done   |
| `M15`     | Beta Deployment Environment                                 | [`backlog/M14-M17.md`](backlog/M14-M17.md)  | Done   |
| `M16`     | Hardening                                                   | [`backlog/M14-M17.md`](backlog/M14-M17.md)  | Done   |
| `M17`     | Friend Beta                                                 | [`backlog/M14-M17.md`](backlog/M14-M17.md)  | Done   |
| `M18`     | Post-Chess Architecture Review                              | [`backlog/M18-M20.md`](backlog/M18-M20.md)  | Done   |
| `M19`     | Deck-Builder Platform Generalization                        | [`backlog/M18-M20.md`](backlog/M18-M20.md)  | Done   |
| `M20`     | Multi-Game Rules Architecture                               | [`backlog/M18-M20.md`](backlog/M18-M20.md)  | Done   |
| `M21`     | AI Opponent, Local Game Persistence, and Game-Screen Polish | [`backlog/M21.md`](backlog/M21.md)          | Done   |
| `M22`     | Play and Social Improvements                                | [`backlog/M22.md`](backlog/M22.md)          | Done   |
| `M23`     | Chess Experience and Personalization                         | [below](#m23--chess-experience-and-personalization) | Active |

The archives, by phase: `M01-M05.md` local chess; `M06-M13.md` the server and online
play; `M14-M17.md` the multiplayer client through the friend beta; `M18-M20.md` the
platform review and generalization; `M21.md` the AI opponent and local games;
`M22.md` play and social improvements.

## Task Selection Order (Autonomous Mode)

When running the continuous autonomous workflow (`docs/AUTONOMOUS-DEVELOPMENT.md`),
select the next task deterministically:

1. Consider only the milestones in this file (the index marks them `Active`),
   and in them only tasks with `Status: TODO`. Archived milestones have been
   explicitly closed by the owner. An active milestone with no `TODO` contributes
   no candidate but does not block tasks in later active milestones (`D103`).
2. Exclude any task whose `Depends on` tasks are not all `DONE`.
3. Exclude any task marked `BLOCKED` and any task that would require a
   documented stop condition.
4. Of the remainder, pick the one with the lowest milestone number, then the
   lowest task number.
5. Mark it `IN PROGRESS` before editing code.
6. Mark it `DONE` only after its required **local** verification passes
   (implementation + acceptance criteria + `./gradlew build`).

### `DONE` is not the same as being allowed to start the next task

- `DONE` = the task's implementation and local acceptance criteria passed.
- The autonomous workflow may **not** select or begin the next task until the
  pushed `claude-autopilot` commit has passed its **required GitHub Actions
  run**. A green remote CI run is the branch-level progression gate; `DONE` is
  the task-level implementation gate. A task can legitimately be `DONE` while
  the branch is still waiting on CI. A commit that changes Markdown files only
  does not wait for its run (`D094`).
- For a task whose acceptance criteria require GitHub Actions itself to run
  successfully (for example `M1.7`), do **not** mark it `DONE` until that CI run
  has actually succeeded — the successful run is part of that task's acceptance
  criteria, not only the progression gate.
- If remote CI cannot be verified (GitHub CLI unavailable, unauthenticated, or
  no repo access), stop per the Stop Conditions in
  `docs/AUTONOMOUS-DEVELOPMENT.md`. Do not silently skip remote CI.
- Claude pushes `claude-autopilot` only. The owner brings `main` and
  `codex-autopilot` up to date by hand (`D094`).

Milestone boundaries are **not** stopping points. After a task is `DONE` and its
pushed commit's required CI is green, immediately select the next eligible `TODO`,
including one in a later active milestone. Do not archive the completed milestone
unless the owner explicitly closes it (`D103`).

A single milestone-level task with no sub-tasks (for example `M2.1` inside a
milestone that only has `M2.1`, `M2.2`) is selected the same way.

# M23 — Chess Experience and Personalization

Post-M22 refinements to the in-game chess experience, its accessibility and its
player-facing personalization. On 2026-10-17 the project owner scheduled six
former future-work items here: `F40`, `F11`, `F35`, `F8`, `F42` and `F9`. Those
IDs are retired and are not reused (`D072`). This milestone remains `Active`,
including after all of its currently scheduled tasks are `DONE`, until the owner
explicitly closes and authorizes archiving it (`D103`).

| Task    | Title                                      | Depends on              | Former | Status    |
| ------- | ------------------------------------------ | ----------------------- | ------ | --------- |
| `M23.1` | Captured pieces beside the board           | `M22.7`, `M22.11`       | —      | `TODO`    |
| `M23.2` | Complete board accessibility               | `M22.6`, `M22.7`, `M22.11` | `F40` | `TODO` |
| `M23.3` | Dead-position detection by search          | None                    | `F11`  | `TODO`    |
| `M23.4` | User settings, opened from the username    | Initial contents chosen | `F35`  | `BLOCKED` |
| `M23.5` | Custom themes                              | None                    | `F8`   | `BLOCKED` |
| `M23.6` | Optional sound and haptic feedback         | `M23.4`                 | `F42`  | `BLOCKED` |
| `M23.7` | Elaborate animations                       | None                    | `F9`   | `BLOCKED` |

---

## M23.1 — Captured pieces beside the board

**Status:** TODO

**Depends on:** M22.7, M22.11

### Objective

Show a compact, accurate summary of the pieces captured by each side on every live
chess game screen, without unnecessarily reducing the board or changing gameplay.

### Acceptance Criteria

#### Meaning and placement

- Show captured pieces in online, pass-and-play, and computer games, including a
  finished position that remains visible on its normal game screen.
- Completed-game review is outside this task. Do not expand review merely to add
  captured-piece displays.
- Each row shows pieces captured **by** its named player, not pieces missing from
  that player's army. For example, Black's row contains the White pieces Black
  captured.
- Associate each row with its player and colour:
  - online: opponent username or "You", plus White or Black;
  - computer: "Computer (<level>)" or "You", plus White or Black;
  - pass-and-play: White or Black.
- Placement follows board orientation, not the current turn:
  - the side shown at the top of the board owns the upper row;
  - the side shown at the bottom owns the lower row;
  - pass-and-play therefore keeps Black above and White below throughout the game.
- In one-column layouts, place slim display-only rows immediately above and below
  the board.
- In two-pane layouts, preserve the board's existing maximum size on the left. Put
  the two captured-piece rows near the top of the right panel, after game/player
  identification and before interactive controls.

#### Presentation

- Keep each row approximately 36–44 dp tall and the same height whether empty or
  populated.
- Use subtle theme-appropriate background tinting and spacing consistent with the
  existing app.
- Do not use cards, heavy borders, large headings, decorative clutter or styling
  that resembles a control.
- Reuse the exact chess-piece glyph artwork and White/Black piece styling used by
  `ChessBoard`.
- Sort captured pieces as queen, rook, bishop, knight, pawn.
- At wider widths, show repeated individual piece glyphs.
- At narrow widths, consolidate repetitions as a glyph followed by `×2`, `×3`, and
  so on. Nothing may be clipped or scroll horizontally.
- When a player has captured nothing, show "No captures" while preserving row
  height.
- Calculate the current position's material balance by summing each side's live
  non-king pieces using queen=9, rook=5, bishop=3.25, knight=3 and pawn=1. This is
  the project owner's chosen scale; the bishop's value is deliberately higher than
  the conventional 3-point simplification. Do not calculate the balance by
  subtracting the two captured-piece totals: promotions make that result wrong.
  Show `+N` only on the leading player's row. Quarter-point advantages are shown
  exactly (for example, `+0.25` or `+1.25`); whole values omit `.00`, and `+0` is
  never shown.
- The rows are non-interactive and expose concise, useful accessibility summaries
  rather than meaningless individual glyph names.
- Captured-piece displays do not obscure board coordinates, pieces, last-move
  highlighting, check indication, legal-move dots or rings, prompts, status,
  controls or move history.
- Preserve the board's maximum practical size. One-column layout calculations
  account for the rows and may use the existing whole-screen scrolling behaviour
  when necessary. Existing two-pane board dimensions remain unchanged.
- Support both light and dark themes with sufficient contrast.

#### Correctness and architecture

- Derive captures from active recorded move history, never by subtracting the
  current board from the standard starting inventory. Inventory subtraction is
  incorrect after promotion.
- Add a pure query through `ChessRules` that identifies the captured piece from a
  `MoveRecord`. It handles ordinary captures, en passant, capture of a promoted
  piece and non-capturing moves without replaying the move or mutating game state.
- Add a separate pure query through `ChessRules` that calculates signed material
  balance from a `GameState` by summing the live non-king pieces. Represent it in
  exact quarter-pawn units—queen=36, rook=20, bishop=13, knight=12 and pawn=4—rather
  than floating point. It handles promoted pieces at their current value and returns
  zero for equal material.
- Local and computer games derive captured pieces from their active `MoveRecord`s.
- Local and computer games derive material balance from their current `GameState`.
- The authoritative server projects exact captured-piece information for online
  games from recorded `MoveRecord`s. Android does not replay online history, parse
  SAN to discover captures or infer captures from the current board.
- The authoritative server also projects the current material balance for online
  games as optional `whiteMaterialBalanceQuarterPoints`: White's live material total
  minus Black's, in quarter-pawn units. Android uses its sign and magnitude only to
  place and format `+N`; it does not calculate online material locally. If an older
  payload omits the field, Android omits `+N` rather than treating the missing value
  as zero.
- Both online players receive identical capture facts. Only player labels and row
  placement change with perspective. They also receive the same material balance.
- Undo removes captures no longer present in active history. Redo and matching
  continuation restore captures from recorded history.
- Do not add database columns or duplicate captured-piece lists or material balance
  in canonical `GameState`. Both are derived.
- In every game mode, each derived captured-piece fact preserves both the captured
  piece's side and type. The additive, typed online projection transmits both, with
  appropriate defaults so existing clients tolerate the server addition.

#### Required implementation verification

When this task is selected for implementation, it requires:

- `chess-core` tests covering ordinary capture, en passant, capture of a promoted
  piece, non-capture, Undo and Redo, plus material balance before promotion, after
  promotion and after the promoted piece is captured, and the exact quarter-point
  difference between a bishop and a knight;
- server projection tests covering both viewer perspectives, promotion, Undo and
  Redo;
- Android model tests for player association, sorting, compact counts, material
  advantage, "No captures" and glyph styling;
- Compose tests at representative Pixel 7 portrait, Kindle Fire portrait and
  Pixel-class landscape dimensions;
- Compose coverage for Black orientation, pass-and-play orientation,
  accessibility, existing board feedback, one-column placement and two-pane
  board-size preservation;
- the complete documented `.\gradlew.bat build` gate and `git diff --check`;
- physical verification on the attached Pixel 7 and Kindle when available,
  installing the newly built APK with `adb install -r` and without uninstalling.

---

## M23.2 — Complete board accessibility

**Status:** TODO

**Depends on:** M22.6, M22.7, M22.11

**Formerly:** `F40`. Source: the project owner's 2026-10-14 usability review.
Value: low while no tester is known to use TalkBack, but high for a player who
does. Effort: small for spoken square descriptions and medium for the complete
pass scheduled here.

### Objective

Make every chess board understandable and operable with TalkBack and large text,
without changing chess rules, board interaction or the visible feedback that
other players use.

### Acceptance Criteria

- Every board square exposes its true coordinate and its occupant, if any, in a
  concise spoken description. White and Black pieces are named correctly rather
  than inheriting the misleading Unicode glyph name.
- Existing spoken state for selection and board feedback remains available. Last
  move, check, selection and legal destinations are distinguishable without
  relying on colour alone.
- TalkBack focus follows a predictable square order for White orientation, Black
  orientation and face-to-face pass-and-play. Pass-and-play's upside-down piece
  drawing does not change what is spoken.
- `M22.11`'s visual edge labels remain outside the semantics tree; the square's
  own description supplies its coordinate.
- Board controls, move history and prompts remain reachable and understandable at
  large font scales. The board retains the layout and interaction rules already
  decided by `D073`, `D087` and `M22.12`.
- Host-side and Compose tests cover descriptions, focus order, both orientations,
  empty and occupied squares, selection, legal destinations, last move and check.
  Acceptance also includes TalkBack and large-text checks on a physical Android
  device, the documented `.\gradlew.bat build` gate and `git diff --check`.

The earlier open question was whether to schedule only the small spoken-square
slice first. The owner has instead scheduled the complete pass as this task; no
separate partial task is created.

---

## M23.3 — Dead-position detection by search

**Status:** TODO

**Depends on:** None

**Formerly:** `F11`. Source: `D038`. Value: low. Effort: medium.

### Objective

Extend `chess-core`'s automatic dead-position result beyond the exact material and
colour-complex cases already covered, using search to recognise positions in
which no legal sequence can end in checkmate.

### Acceptance Criteria

- `ChessRules.terminalResult` recognises a dead position when checkmate is
  impossible after every legal continuation, and does not declare a draw while
  any legal continuation can still reach checkmate.
- The existing exact insufficient-material cases remain correct, including any
  number of bishops confined to one colour complex once no pawn, rook or queen
  remains (`D038`).
- Checkmate, insufficient material, fivefold repetition, the seventy-five-move
  rule and stalemate retain their documented precedence. The stored termination
  reason remains the existing insufficient-material reason; no new wire or
  persistence representation is introduced merely for the search.
- The implementation stays entirely in pure `chess-core`; Android and the server
  receive the result through their existing use of `ChessRules`.
- Rules tests cover search-positive and search-negative positions, promoted
  material and the existing material shortcuts. Verification includes
  `:chess-core:test`, the documented `.\gradlew.bat build` gate and
  `git diff --check`.

**Open decisions:** none. The search design is an implementation choice inside
`chess-core` and must be measured well enough not to make ordinary move handling
unusable.

---

## M23.4 — User settings, opened from the username

**Status:** BLOCKED

**Depends on:** the project owner choosing at least one initial setting.

**Formerly:** `F35`. Source: `PRODUCT.md`. Value and effort are not estimated
until the initial contents are chosen.

### Objective

Turn the username at the right-hand end of the dashboard's top row into the entry
point for the player's settings, while keeping screens whose top row contains
Back unchanged.

### Acceptance Criteria

- The settings screen's initial contents are decided and recorded before
  implementation. Scheduled candidates include custom themes (`M23.5`) and sound
  or haptic controls (`M23.6`); future candidates still include username changes
  (`F5`) and approval settings (`F14`). Scheduling this screen does not decide
  that those are its complete contents.
- Once the screen has real contents, tapping the dashboard username opens it and
  Back returns to the dashboard. Until then, the username remains the existing
  plain, non-tappable text from `M17.3`.
- A long username remains one line and cannot displace the dashboard navigation.
- The chosen settings have explicit persistence, default, accessibility and test
  requirements in this task or in the task that supplies each setting. No empty
  placeholder screen is shipped merely to satisfy the navigation requirement.
- Verification for the implemented scope includes host-side state/navigation
  tests, Compose tests, the documented `.\gradlew.bat build` gate and
  `git diff --check`.

### Blocking decision

The project owner has not chosen the screen's initial contents. That product
decision determines this task's value, effort and complete acceptance criteria.
`M23.5` and `M23.6` are a coordinated personalization group with this task, but
their presence in the same milestone does not silently resolve the choice.

---

## M23.5 — Custom themes

**Status:** BLOCKED

**Depends on:** None. Coordinate its selection UI with `M23.4` if themes are
chosen as settings content.

**Formerly:** `F8`. Source: `PRODUCT.md` (*Deferred Features*). Value: low.
Effort: small–medium; Compose only, with no server work.

### Objective

Let the player choose supported visual theming without changing gameplay or
introducing server-owned presentation state.

### Acceptance Criteria

- The project owner first decides whether the feature themes the board, the chess
  pieces, the whole app, or a stated combination of them.
- The chosen scope applies consistently anywhere that surface appears, including
  online, pass-and-play, computer play and review where applicable.
- Every supported theme preserves readable pieces, coordinates, captured-piece
  displays, board feedback, controls and accessibility descriptions. Theme choice
  changes presentation only.
- Theme selection and persistence are covered by host-side and Compose tests.
  Verification includes the documented `.\gradlew.bat build` gate and
  `git diff --check`.

### Blocking decision

The themeable surface is not decided: board, pieces, whole app, or some
combination. Choosing it materially changes the scope and player-facing behavior,
so implementation does not begin until the owner decides it.

---

## M23.6 — Optional sound and haptic feedback

**Status:** BLOCKED

**Depends on:** M23.4 for the full sound and haptic controls. Haptics alone need
not wait when they follow Android's own touch-feedback setting.

**Formerly:** `F42`. Source: the project owner's 2026-10-14 usability review.
Value: low–medium, principally so a player hears an opponent's move arrive while
the app is open. Effort: small–medium.

### Objective

Add optional sound and haptic feedback at the player-visible moments the owner
chooses, with explicit controls and platform-respecting defaults.

### Acceptance Criteria

- The project owner decides which moments receive feedback: a move, a capture,
  check, game end, an opponent's move arriving, or a stated subset.
- The project owner decides whether sound is on by default. Full on/off controls
  live in the settings surface from `M23.4`; their state and persistence are
  tested.
- Haptics use Compose's platform feedback and respect Android's touch-feedback
  setting. Sound and haptic feedback never determine whether an action occurs.
- Every shipped sound asset has a recorded licence that permits distribution with
  the app. No unlicensed placeholder becomes a release asset.
- Tests cover each chosen trigger, disabled controls, defaults and duplicate-event
  protection where the same player-visible event can arrive through refresh or
  realtime. Verification includes the documented `.\gradlew.bat build` gate,
  `git diff --check` and physical-device checks for the feedback that hardware
  produces.

### Blocking decisions

The feedback moments and the sound default are not decided. Those choices define
the feature's behavior and test matrix, so implementation does not begin until
the owner resolves them.

---

## M23.7 — Elaborate animations

**Status:** BLOCKED

**Depends on:** None.

**Formerly:** `F9`. Source: `PRODUCT.md` (*Deferred Features*). Value: low.
Effort: medium; Compose only, with no server work.

### Objective

Add the selected presentation animations without changing chess rules, command
semantics, navigation results or authoritative state.

### Acceptance Criteria

- The project owner first decides which moments animate: moves, captures, check,
  game end, or a stated subset.
- Animations are local presentation state only. They do not delay, repeat or
  suppress a command, and canonical state never depends on animation completion.
- Tap, long-press drag, scrolling, prompts, Undo, Redo, refresh and realtime
  updates retain their documented behavior while an animation is running or is
  interrupted.
- Host-side and Compose tests cover every selected moment and interruption by a
  newer authoritative state. Verification includes the documented
  `.\gradlew.bat build` gate and `git diff --check`.

### Blocking decision

The moments that animate are not decided. That product choice determines the
feature's scope and must be made before implementation begins.
