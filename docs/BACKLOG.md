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
| `M23.1` | Captured pieces beside the board           | `M22.7`, `M22.11`       | —      | `DONE`    |
| `M23.2` | Move history follows the current position  | `M22.7`, `M22.10`, `M23.1` | —   | `DONE`    |
| `M23.3` | Complete board accessibility               | `M22.6`, `M22.7`, `M22.11` | `F40` | `TODO` |
| `M23.4` | Dead-position detection by search          | None                    | `F11`  | `TODO`    |
| `M23.5` | User settings, opened from the username    | Initial contents chosen | `F35`  | `BLOCKED` |
| `M23.6` | Custom themes                              | None                    | `F8`   | `BLOCKED` |
| `M23.7` | Optional sound and haptic feedback         | `M23.5`                 | `F42`  | `BLOCKED` |
| `M23.8` | Elaborate animations                       | None                    | `F9`   | `BLOCKED` |
| `M23.9` | Online Review has one Back                 | `M22.10`                | —      | `DONE`    |
| `M23.10` | Leaving says what the leave did           | `M22.1`                 | —      | `DONE`    |
| `M23.11` | An unchanged game keeps what its screen said | `M21.15`              | —      | `DONE`    |
| `M23.12` | Play the next game replaces the finished one | `M22.1`             | —      | `DONE`    |
| `M23.13` | The dashboard is current after playing     | `M21.20`                | —      | `DONE`    |
| `M23.14` | Review's step buttons stay put             | `M22.10`, `M23.2`       | —      | `DONE`    |
| `M23.15` | Every promotion prompt offers Cancel       | `D041`                  | —      | `DONE`    |
| `M23.16` | Finding your own name says it is you       | `D009`                  | —      | `DONE`    |

---

## M23.1 — Captured pieces beside the board

**Status:** DONE

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
- When a player has captured nothing, show no text where the pieces would be: the row
  keeps its player and colour label, its tint and its height, and only its
  accessibility summary says "No captures" (amended 2026-10-18, `D104`; this replaced
  the original criterion to show "No captures").
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
  advantage, an empty row's label and summary, and glyph styling;
- Compose tests at representative Pixel 7 portrait, Kindle Fire portrait and
  Pixel-class landscape dimensions;
- Compose coverage for Black orientation, pass-and-play orientation,
  accessibility, existing board feedback, one-column placement and two-pane
  board-size preservation;
- the complete documented `.\gradlew.bat build` gate and `git diff --check`;
- physical verification on the attached Pixel 7 and Kindle when available,
  installing the newly built APK with `adb install -r` and without uninstalling.

### Completion Note — 2026-10-17

**Rules (`chess-core`).** `ChessRules.capturedPiece(record)` reads the captured `Piece`
from the record's own pre-move position: the piece on the destination, or the pawn an
en passant capture took beside it. It neither replays nor mutates. A promoted piece is
captured as what it became. `ChessRules.materialBalance(state)` is White's live non-king
material minus Black's, in exact quarter points, from the new `Material` (36, 20, 13, 12,
4; king 0).

**Server.** `MoveView.captured` is an optional `CapturedPieceView(side, type)`, read
from each recorded move. The game view also carries an optional
`whiteMaterialBalanceQuarterPoints` from the current state. Both are additive: no
column, no migration, and nothing new in `GameState`. Both viewers get identical facts.
Review's moves carry the field too, but review draws nothing new.

**App.**

- `CapturedPieces` builds the two rows:
  - locally, from active `MoveRecord`s and the current `GameState` (`forGame`);
  - online, from the server's projection alone (`OnlineGame.capturedRowsFor`). It never
    replays, parses SAN or reads the board. A missing balance shows no `+N`, and moves
    without `captured` show no captures.
- Labels follow `D104`:
  - online: "Alex · Black" and "You · White";
  - computer: "Computer (Medium) · Black" and "You · White";
  - pass-and-play: "Black" and "White".
- The top row belongs to the side drawn at the top of the board. Pass-and-play keeps
  Black above and White below throughout.
- Pieces are ordered queen, rook, bishop, knight, pawn.
- `+N` is on the leader's row only. Whole values print bare (`+1`, `+9`). Other values
  print two decimals (`+0.25`, `+0.50`, `+9.25`), so `.00` alone is dropped, as the
  criterion reads. `+0` never appears.
- `CapturedPiecesRow` is 40 dp tall whether empty or not. It sits on a
  0.6-alpha `surfaceVariant` tint with 6 dp corners, and has no border or click.
- Glyphs are `BoardRendering.glyphFor` in `PieceStyle.fill`, the board's colours, which
  `ChessBoard` now shares. Off the board each glyph also gets a thin outline in the other
  side's colour. Without it, white pieces vanish on the light theme's tint and black ones
  on the dark theme's.
- Repeats stay individual, slightly overlapped, while they fit. Otherwise they compact to
  a glyph and `×N`. If even that is too wide, they scale down rather than clip.
- The label takes at most 42% of the row and ellipsizes. Each row exposes one summary to
  a screen reader, for example "Alex, White. Captured pawn. Ahead by 1 point."

**Layout.**

- `GameLayout` takes `captures` and a `header` (the online heading, or the computer's
  name and colour).
- In one column, the rows sit 4 dp above and below the board, at its width.
  `GameLayoutSpec.forWindow(..., captureRows = true)` takes their 88 dp from the height
  only. Width-decided boards keep their size: Pixel 7 portrait 411 dp, Fire HD 8 portrait
  569 dp. Height-decided boards shrink by 88 dp. Short windows use the existing
  whole-screen scroll.
- In two panes, the rows follow Back and the header in the panel, before the controls.
  The board size is unchanged for every listed window (`GameLayoutSpecTest`).
- Review passes no rows and is unchanged.

**Two-pane fit (owner's decision, recorded under `D104`).** In short landscape windows
the rows push the panel's last controls below the fold. Measured on the Fire HD 8 with
forced windows:

| Window | Height | Overflow, worst panel |
| ------ | ------ | --------------------- |
| Stock Pixel 7 landscape, 3-button | 383 dp | 6 dp |
| Stock Pixel 7 landscape, gesture | 359 dp | 24 dp |
| 640×360 phone | 360 dp | 58 dp |

The owner's Pixel 7 (969×457), the Fire HD 8 and tablets still show every control. The
owner chose to follow `D104`: the panel scrolls to its last controls in short windows.
Six device tests required every two-pane window to fit without scrolling, and they were
updated to the new rule:

- `GameLayoutUiTest.withTwoPanesEveryControlIsInViewWithoutScrolling…`;
- `OnlineGameLayoutUiTest.aGameInProgressShowsItsControlsWithTwoPanes…`;
- `RedoUiTest`'s two fullest-panel tests;
- `UndoRequestUiTest.jordansPanelAfterAnAnswer…`;
- `DrawOfferUiTest.anUndoRequestAndADrawOffer…`.

The last two failed only on the second run. All six now use `assertPanelShows`, which:

- keeps the strict no-scroll check on `ROOMY_TWO_PANE_VIEWPORTS`;
- requires the shorter windows to scroll the panel to every control;
- requires the board not to move.

Panels that still fit everywhere keep their original strict tests: Alex's undo-request
panel, the computer's Redo panel and the promotion prompt.

**Tests.**

- `chess-core`:
  - `CapturedPieceTest` (10) covers ordinary, piece and en passant captures, a captured
    promoted queen, a capturing promotion, non-captures including castling, no
    mutation, Undo, a two-ply Undo with Redo, and a matching continuation.
  - `MaterialBalanceTest` (6) covers the scale, the start position, bishop against
    knight (±1 quarter point), captures, before and after promotion and after the
    promoted queen is taken, where captured totals would be wrong, and Undo with Redo.
- Server, against PostgreSQL:
  - `CapturedPieceProjectionTest` (5) covers both viewers, promotion (balances 21, 73
    and 37), Undo, Redo and the optional wire shape.
  - `SanProjectionTest`'s expected `Qxf7#` now carries its captured pawn.
- App, JVM:
  - `CapturedPiecesTest` (17) covers player association for all three modes, sorting,
    counted groups, the fit, an empty row, leader-only and exact `+N`, a missing
    balance, promotion, Undo and Redo, summaries and glyph styling.
  - `OnlineCapturedPiecesTest` (7) covers both perspectives, the same facts for both
    players, the server's balance, older payloads and decoding.
  - `GameLayoutSpecTest` (+2) covers unchanged two-pane boards and one-column room for
    the rows.
- Device: `CapturedPiecesUiTest` (10) runs at Pixel 7 portrait and landscape, Fire HD 8
  portrait and the owner's Pixel 7 windows. It covers:
  - pass-and-play on both turns, online as Black and as White, and the computer game;
  - one-column placement and width;
  - two-pane board size, and the rows' order between the heading and the controls;
  - existing square feedback unchanged, with nothing overlapping the board or status;
  - no click action, and one summary per row;
  - 40 dp at font scales 1.0, 1.3 and 2.0;
  - light and dark theme pixels (needs API 26).

**Verification.**

- `.\gradlew.bat build`: green.
- `:server:test` with `TEST_DATABASE_URL`, `--rerun-tasks`: 734 tests, 0 failures.
- `:chess-core:test`: 448 tests, 0 failures.
- Fire HD 8 (API 22): 16 Compose UI classes, 84 tests. One run passed 81. The 3 that
  failed passed after the fix, in a rerun of their six classes (43 tests). The pixel
  theme check skips itself there.
- `ChessGame-Light` emulator (API 36): the same 84 tests, 81 in one run and the
  same 3 passing after the fix in the 43-test rerun. The theme pixel check passes there.
- The device and test APKs were signed with the beta key through AGP's injected-signing
  properties, so `adb install -r` replaced the installed beta in place.
- Physical: the signed release `0.1.7-m23.1` (versionCode 9) was installed with
  `adb install -r` over the Fire HD 8's `0.1.6`, without uninstalling. In portrait
  pass-and-play:
  - Black's row is above the board and White's below, both "No captures" at first;
  - 1…e5 2.dxe5 d6 3.exd6 put two black pawns and `+2` on White's row;
  - 3…Bxd6 put a white pawn on Black's row and made it `+1`;
  - Undo removed that capture (`+2`), and Redo restored it.
- The Pixel 7 was not attached, so it has no physical check; its windows are covered by
  the forced-size device tests.
- Online rows on the beta show captures and `+N` only once the server with this change
  is deployed. An older server's games show empty rows and no `+N`.

### Amendment — 2026-10-18: no "No captures" text

At the project owner's request (`D104`, amended), an empty row no longer draws "No
captures". The completion evidence above, from 2026-10-17, still shows the text; this
amendment supersedes it.

- **Code.** `CapturedPieceGlyphs` draws nothing for an empty row. The label, tint and
  40 dp height are unchanged, so no layout measurement moves. `CapturedRow.summary`
  still ends "No captures." for a screen reader, and `NO_CAPTURES` stays for it alone.
- **Tests.**
  - JVM: `CapturedPiecesTest.anEmptyRowKeepsItsLabelAndSaysNoCapturesOnlyInItsSummary`
    replaces `anEmptyRowSaysNoCaptures`.
  - Device: `CapturedPiecesUiTest.anEmptyRowKeepsItsLabelHeightAndSummaryButShowsNoText`
    covers pass-and-play, online and computer screens in one-column and two-pane windows
    at font scales 1.0, 1.3 and 2.0. It checks there is no "No captures" text node, the
    summary still says it, the row is 40 dp, and the board is the same size.
  - Device: `anEmptyRowIsPlainTintBesideItsLabel` checks the pixels past the label are
    plain tint in both themes. It needs API 26, so the Fire HD 8 skips it. The semantic
    check alone cannot prove the text is gone, because the row hides its children from
    accessibility.
- **Verification.** Only the narrowest checks were run, as asked; the full build gate was
  not run.
  - `CapturedPiecesTest`: 17 of 17 pass.
  - `ktlintCheck` passes, and the device tests compile.
  - `CapturedPiecesUiTest`, `GameLayoutUiTest` and `OnlineGameLayoutUiTest` (25 tests)
    pass on the Fire HD 8 (API 22, pixel checks skipped) and on the Pixel 7 (API 36,
    pixel checks run). Both devices were awake and unlocked throughout.
  - Physical: the signed release `0.1.8-empty-rows` (versionCode 11) was installed with
    `adb install -r`, never uninstalling, over a beta-key debug build (versionCode 10) on
    both devices. The Pixel 7's display settings were not touched.
  - On the Fire HD 8, undoing Black's only capture left Black's row showing just
    "Black", at the same height, with the board where it was. Redo restored it.
  - The online game against the deployed beta server showed both rows and `+0.25`
    (bishop against knight).
  - On the Pixel 7 (dark theme), the pass-and-play, computer and online rows were
    checked. Every game there had captures on both sides, so its empty-row check is the
    pixel test.

---

## M23.2 — Move history follows the current position

**Status:** DONE

**Depends on:** M22.7, M22.10, M23.1

### Objective

Make the move history in Review and every live game clearly identify the move
corresponding to the displayed position, automatically keep that move in sight and
scroll independently from the board, status and available actions.

### Acceptance Criteria

#### Review behavior

- Apply the behavior to both local completed-game review and online completed-game
  review.
- At review ply zero, show no move entries and no current-move highlight.
- At every later review ply, show only the prefix of the game's move history through
  that ply.
- Never display moves that occur after the position currently shown on the board.
  Future moves must also be absent from the accessibility semantics tree.
- If the current ply is White's move, show that numbered row with only White's move
  populated. Do not reveal Black's following move until the review advances to it.
- Highlight exactly the SAN move that produced the displayed position. Highlight the
  individual White or Black move, not an entire paired row when only one move is
  current.
- Start, Previous, Next and End remain the only controls required to change the
  reviewed position. Scrolling or tapping the move list does not seek to another
  position.
- Stepping to another ply automatically scrolls the list enough to show the newly
  highlighted move and its move number.
- Selecting Start clears the list and resets its scroll position appropriately.
- Opening a review at its final position initially shows and highlights the final
  move.

#### Live-game behavior

- Apply the behavior consistently to online, pass-and-play and computer games,
  including a finished position still displayed on its normal game screen.
- The current move in a live game is the last move in active history.
- Highlight that move clearly. A game with no active moves has no highlighted move.
- After an accepted local move, computer move or online move, automatically scroll
  the move list so the newly highlighted move is fully visible.
- An opponent's move received through refresh or realtime receives the same highlight
  and automatic scrolling behavior.
- Undo removes moves no longer in active history, highlights the new final active move
  when one remains and keeps it visible.
- Redo and matching continuation restore the corresponding move, highlight it and
  keep it visible.
- A refresh or unrelated recomposition that does not change the current move must not
  repeatedly force the player back to the end after they manually scroll to earlier
  moves.
- Opening a nonempty live game initially places its current highlighted move in sight.

#### Independent scrolling and fixed actions

- The move list has its own bounded vertical scrolling region in both one-column and
  two-pane layouts.
- Scrolling the move list must not move the board, Back, player/game identification,
  status, current-position wording, captured-piece summary, review navigation or
  gameplay action buttons.
- In a live game, actions such as Undo, Redo, draw actions, Resign, New game and other
  currently applicable controls remain available while the player scrolls anywhere
  in the move history.
- In Review, Start, Previous, Next and End remain available while the player scrolls
  anywhere in the visible history.
- Do not satisfy this requirement by placing the controls and move history in one
  shared scrolling container and merely making the controls reachable after scrolling
  back.
- Preserve `D073`'s practical board-size rules and `M23.1`'s captured-piece placement.
  The move-history change must not shrink the existing two-pane board.
- On an extremely short window or large font scale where all fixed content cannot
  physically fit simultaneously, preserve access without clipping through a
  separately bounded overflow strategy. Scrolling the move history must still never
  change the position of that separate controls region.
- Nothing scrolls horizontally, and long SAN content remains readable without
  obscuring controls.

#### Highlight presentation and accessibility

- Use a clear, theme-appropriate current-move treatment that is easy to distinguish
  in both light and dark themes.
- Do not rely on colour alone. Combine the background or indicator with suitable
  emphasis and accessibility state or description.
- Maintain sufficient contrast and do not make a display-only move look like a
  tappable button.
- Preserve numbered White/Black pairing and SAN from `M22.7`.
- Exactly one move is exposed as current whenever at least one applicable move is
  visible.
- The highlight in Review corresponds to the displayed review ply. The highlight in
  a live game corresponds to the final active ply.
- The move list and highlight remain understandable with TalkBack and large text.

#### Planned state and architecture contract

- Use a shared typed Android presentation model for move-history rows or entries
  rather than trying to infer the current move from rendered strings.
- The model must preserve at least the ply index, move number, side, SAN and whether
  that individual move is current.
- Review derives its visible entries from the existing review projection using only
  the prefix through the selected ply.
- Live local and computer games derive their entries from active `MoveRecord`s.
- Live online games continue using the authoritative SAN move projection already
  supplied by the server.
- Do not replay moves, parse SAN, infer history from the current board or introduce a
  second review engine.
- Do not add database columns, migrations, canonical `GameState` fields or duplicate
  persisted move-list state.
- Do not change the online review or live-game wire contract merely to implement
  Android presentation and scrolling when the existing typed projections contain the
  required facts.
- Scroll position and highlight presentation are UI state, not canonical game state.

#### Required implementation verification

When this task is selected for implementation, it requires:

- model tests for review prefixes at ply zero, White-only half-moves, complete move
  pairs and the final ply;
- model tests proving exactly the correct move is current in Review and in live local,
  computer and online games;
- tests covering move, opponent refresh/realtime move, Undo, Redo and matching
  continuation;
- Compose tests proving move-history scrolling does not move or hide Review controls
  or live-game actions;
- Compose tests proving manual scrolling is retained across unrelated recomposition
  but a genuinely new current move scrolls into view;
- Compose tests for initial display, Start, Previous, Next and End;
- accessibility coverage for current-move semantics, absence of future review moves
  and sufficient non-colour indication;
- representative Pixel 7 portrait and landscape, Kindle Fire portrait and landscape,
  narrow-phone and short-landscape layouts;
- coverage with `M23.1` captured-piece rows present, confirming that fixed content and
  existing two-pane board dimensions are preserved;
- physical verification on the attached Pixel 7 and Kindle when available, installing
  the newly built APK with `adb install -r` and without uninstalling;
- the complete documented `.\gradlew.bat build` gate;
- `git diff --check`.

### Completion Note — 2026-10-21

**Model.** `MoveHistory` (`MoveHistoryRow`, `MoveHistoryEntry`: ply, move number, side,
SAN, current) is the one typed presentation. `GameControls.moveHistory` builds it from
active `MoveRecord`s for pass-and-play and the computer, `OnlineGame.moveHistoryFor` from
the server's `MoveView` SAN, and `GameReview.history` from Review's projected moves
through its ply (`MoveHistory.review`). Nothing replays, parses SAN or reads the board.
Live games make the last active move current. Review shows nothing at ply 0, and a
White move alone until Black's reply is reached. No server, wire, database or SQLDelight
change.

**List.** `MoveHistoryList` draws numbered rows in its own vertical scroll. The current
move is bold, on a rounded `primaryContainer` pill, and its row carries the state
description "Current move: <SAN>". Nothing in the list is clickable. A change of current
move brings its row into view. A recomposition or refresh with the same current move
keeps the player's scroll. The list pads its own top and bottom so the pill is never
clipped. Measured on the Pixel 7, text on the pill is 6.2:1 in light and 6.0:1 in dark.
The pill is 1.5:1 and 2.5:1 against the page, and the move is bold as well.

**Layout (`D105`).** `GameLayout` takes a `MoveHistory` instead of a move-list slot. In
every mode it places the list below a fixed region (`FixedThenHistory`):

- the controls take the height they need;
- a nonempty list keeps one row, 4 dp below them (`oneMoveRowHeight`: line height plus
  the pill's padding, 22 dp online and 30 dp elsewhere at font scale 1.0);
- the controls scroll in their own region only when they and that row cannot both fit;
- an empty list is not drawn.

Board sizes and captured-row placement are unchanged.

**Fixes this run.**

- *Six Fire HD 8 failures.* The first layout reserved 56 dp for the list. That pushed
  Resign and the claim buttons out of view in two-pane panels that had fitted. Probed on
  the Fire HD 8 with forced windows: Alex's undo-request panel ends Resign 401 dp down
  the owner's Pixel 7 panel, which then showed the controls only to 385 dp. The reserve
  became one row and the gap 4 dp. Jordan's answered-request panel then fits the owner's
  Pixel 7 with 2 dp to spare, and every checked panel fits the Fire HD 8 and the tablet.
- *`PieceDragUiTest`'s short-window swipe.* The test's swipe starts on e2, 15 px below the
  forced window. A build of the previous commit, run the same way, scrolled; this one did
  not. The empty list's zero-height scroller sat at the window's edge, and Compose's
  near-miss touch routing gave it the swipe. An empty list is no longer drawn at all,
  which restores the scroll.
- *`GameEndUiTest`'s after-game buttons* sat below the stock Pixel 7 landscape panel's
  controls region. The test now scrolls that region to each button, as the other
  short-window tests do.
- *Found in two-phone play:* when an undo request appeared above the Pixel 7's list, the
  list got shorter but kept its scroll offset, so the current move fell out of view. The
  list now stays at its end when its height changes, if it was showing its end. A list
  the player scrolled back keeps its place. The new device test
  `aListShowingTheCurrentMoveKeepsShowingItWhenRequestsShortenIt` failed on the Fire HD 8
  with the fix disabled and passes with it.

**Owner pre-authorization used (two-pane fit).** Where a window cannot show every
control and a usable list at once, the controls scroll in their own region. This is
recorded under `D105` (2026-10-21). Measured on the Fire HD 8 with forced windows:

- The shorter windows (stock Pixel 7 landscape, 359–383 dp; a 640×360 phone) scroll to
  their last controls. Resign ends 344–399 dp down panels that show 318–350 dp.
- On the owner's Pixel 7, with an undo request and a draw offer both waiting, Resign ends
  487 dp down a 457 dp window. It was never wholly in view there.
- The Undo-plus-two-claims panel, which no device test checks, ends Resign at 442 dp. It
  fitted before `M23.2`; it is now 20 dp into the controls' scroll.

`assertPanelShows` now requires a control in a roomy window to be wholly in view, not
partly. Moved to it:

- `RedoUiTest.theComputerRedoFitsWithTwoPanes`;
- `UndoRequestUiTest.alexsPanelFitsWithTwoPanesWithoutScrollingWhereThereIsRoom`
  (renamed from `…WithoutScrolling`);
- `ActionExplanationUiTest.onlineClaimsNameTheirRuleSayTheyAreNotOffersAndAskNobodyAnything`.

`DrawOfferUiTest.anUndoRequestAndADrawOffer…` no longer counts the owner's Pixel 7 as
roomy. The Pixel 7 (969×457), the Fire HD 8 landscape (962×553) and the tablet keep every
other checked control wholly in view without scrolling.

**Tests.**

- JVM: `MoveHistoryTest` (12), `OnlineMoveHistoryTest` (5) and `ComputerMoveHistoryTest`
  (1). They cover Review prefixes at ply 0, White-only, pairs and the final ply; exactly
  one current move; local, computer and online moves; refresh; Undo, Redo, matching
  continuation and takeback.
- `.\gradlew.bat build`: green. chess-app 818, chess-core 448, chess-ai 20, server 734.
  The server's database tests are no-ops without `TEST_DATABASE_URL`; nothing in the
  server changed.
- Device, `MoveHistoryUiTest` (8). It runs at Pixel 7 and Fire HD 8 portrait and
  landscape, a narrow phone and a short landscape window, at font scales 1.0 and 2.0. It
  covers:
  - Review's prefix, Start, Previous, Next and End, and future moves absent from
    semantics;
  - scrolling the list moving nothing else;
  - a manual scroll kept, and a new current move scrolled into view;
  - the list shortened by requests;
  - one bold current move named to a screen reader, and none in a new game.
- Full instrumented suite on the final code: every class except `LocalGameRotationTest`
  and `EngineTimingDeviceTest`, installed with `adb install -r` and run with
  `am instrument`.
  - Pixel 7 (API 36): 102 of 102.
  - Fire HD 8 (API 22): 102 of 102. Two of them are `CapturedPiecesUiTest`'s API 26
    pixel checks, which skip.
  - Per class, on both: ActionExplanation 5, BoardCoordinates 3, CapturedPieces 12,
    GameEnd 4, GameLayout 9, LocalDrawClaim 3, M5Independent 1, M5LocalAdversarial 2,
    MoveFeedback 6, MoveHistory 8, PieceDrag 7, Redo 8, DrawOffer 5,
    OnlineGameConnection 7, OnlineGameLayout 4, UndoRequest 7, PastLocalGames 2,
    Profile 4, OnlineReview 3, LocalGameStoreDevice 1, Example 1.
  - An earlier run, before the list fix: 101 of 101 on both.
- `EngineTimingDeviceTest` passed on both.
  - Pixel 7: Very Easy 72–256 ms, Easy 523–1501, Medium 328–1104, Hard 3000–3005.
  - Fire HD 8: Very Easy 239–795 ms, Easy 1512–1531, Medium 2014–2035, Hard 3010–3042.
- `LocalGameRotationTest`, run last, passed on both.

**Two-phone play.** Release builds 0.1.10 and 0.1.11 on the beta server, with the Pixel
7 as `test1234` and the Fire HD 8 as `test2345` in their existing series. Every move,
Undo, Redo, request, answer and offer reached the other phone by realtime within about
4 s with no refresh, and the receiver's list highlighted the new move in view. Checked:

- Undo of an unanswered move, and Redo;
- an undo request declined; another accepted; the same move then replayed by hand, a
  matching continuation, with no Redo of the opponent's reply;
- a draw offer declined;
- captures, check, and promotion to a queen (`11. hxg8=Q+`), with captured rows and
  `+13.75` mirrored on both;
- a list scrolled back to move 1 left the board, the captured rows and the buttons on the
  same pixels, and came back on the next incoming move;
- the game ended by Black's resignation, and the rematch started with colours swapped on
  both;
- Review from the end screen and from History on both: Start empties the list; Next
  shows `1. d4` alone, then `2. Nc3` without Black's reply; End highlights `c5`, the last
  move.

Landscape on both: the game and Review survived rotation, every control and the current
move stayed in view, the list scrolled with nothing else moving, and moves played in
landscape round-tripped. Pass-and-play and the computer were checked on both phones in
both orientations: highlight, auto-scroll, captured rows, Undo or takeback, and Redo
restoring the computer's recorded reply without a search. The Pixel 7 was checked in
light and dark mode; the Fire HD 8 has no night mode.

**Device settings.** Rotation, stay-awake and the Pixel 7's night mode were changed under
the owner's pre-authorization. All were restored to their recorded values: Pixel 7
accelerometer rotation 1, user rotation 1, stay-on 0, night yes; Fire HD 8 1, 0, 0.
Display size and density were not touched. Release 0.1.11 (`versionCode` 18) is left
installed on both. `LocalGameRotationTest` replaced both phones' accounts with throwaway
usernames.

**Not verified.** TalkBack on a physical device; the device tests cover the semantics.

**Outside this task, reported only.**

- Online Review shows two Back buttons: `ShellChromeContent.hasChrome` does not exclude
  `Destination.OnlineReview` (since `M22.10`).
- Review's turn line reads "WHITE to move".
- On the Fire HD 8 in portrait, the one-column controls do not all fit below the 569 dp
  board, so a request's Accept and Decline are reached by scrolling the controls region,
  which `D073` allows. The list's one row takes 22 dp of that space; whether Accept was
  in view before `M23.2` was not measured.

---

## M23.3 — Complete board accessibility

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

## M23.4 — Dead-position detection by search

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

## M23.5 — User settings, opened from the username

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
  implementation. Scheduled candidates include custom themes (`M23.6`) and sound
  or haptic controls (`M23.7`); future candidates still include username changes
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
`M23.6` and `M23.7` are a coordinated personalization group with this task, but
their presence in the same milestone does not silently resolve the choice.

---

## M23.6 — Custom themes

**Status:** BLOCKED

**Depends on:** None. Coordinate its selection UI with `M23.5` if themes are
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

## M23.7 — Optional sound and haptic feedback

**Status:** BLOCKED

**Depends on:** M23.5 for the full sound and haptic controls. Haptics alone need
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
  live in the settings surface from `M23.5`; their state and persistence are
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

## M23.8 — Elaborate animations

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

---

## M23.9 — Online Review has one Back

**Status:** DONE

**Depends on:** M22.10

**Source:** found during `M23.2`'s two-phone verification (2026-10-21) and scheduled by
the project owner on 2026-10-22, who asked for it to be fixed.

### Objective

Show exactly one Back on the online Review screen, as on every other game screen
(`D073`).

### Acceptance Criteria

- Online Review, opened from a finished game's screen or from History, shows one Back:
  its own, in the loading, unavailable and shown states. The app's top row is not drawn
  above it.
- That Back still returns to the screen Review was opened from.
- Every other screen keeps or omits the top row as before.
- A JVM test covers the top-row rule for online Review, and a device test counts one
  Back on online Review drawn inside the app. Verification includes the documented
  `.\gradlew.bat build` gate and `git diff --check`.

### Completion Note — 2026-10-22

**Cause.** `ShellChromeContent.hasChrome` leaves out the top row on the game screens
because they draw their own Back (`D073`). `M22.10` added `Destination.OnlineReview`
without adding it to that list, so online Review showed the top row's Back above its
own. Local Review (`PastLocalGame`) was already in the list.

**Fix.** `Destination.OnlineReview` is now one of the screens without the top row.
`GameReviewScreen` draws its own Back in every state, so the screen is never left
without one.

**Tests.**

- `ShellChromeContentTest.theGameScreensHaveNoTopRowBecauseTheyDrawTheirOwnBack` now
  includes online Review.
- New device test `OnlineReviewUiTest.theOnlineReviewInTheAppHasOneBackWhileLoadingAndOnceShown`
  draws the whole `ChessApp` on online Review and counts one Back while loading and once
  the review is shown. With the fix reverted it failed on the Fire HD 8 (two Backs).
- `OnlineReviewUiTest` (4), `PastLocalGamesUiTest` (2), `GameEndUiTest` (4) and
  `OnlineGameConnectionUiTest` (7): 17 of 17 on the Pixel 7 and on the Fire HD 8,
  installed with `adb install -r`.
- `.\gradlew.bat build`: green.

**On the devices.** Release 0.1.12 (`versionCode` 21) was installed on both phones. The
app data was cleared and `test1234` and `test2345` claimed again, because
`M23.2`'s `LocalGameRotationTest` had replaced both accounts. On both, the finished game's
Review from History shows one Back, and it returns to the finished game.

**Left as is.** Review's turn line reads "WHITE to move", as pass-and-play and the
computer game do (`GameControls.statusFor`, pinned by `GameControlsTest`). Changing it
would change every local game screen's wording, so it is not part of this task.

---

## M23.10 — Leaving says what the leave did

**Status:** DONE

**Depends on:** M22.1

**Source:** finding F3 of the project owner's adversarial UI test of 2026-10-22 (release
0.1.13-adv, built from `ecb8826`), scheduled the same day.

### Objective

After Leave series, the online game says what the server's leave actually did to the
game, not what the game looked like when the player tapped (`D095`).

### Background

When both players confirm Leave series about a second apart, the first leave resigns the
started game in the second player's favour, and the second leave is accepted
idempotently (`D095`). The second player's screen then showed "You left the series and
resigned this game." under "You won by resignation": `ChessAppViewModel.leaveSeries`
worded its message from the game as decided at the tap, which was unfinished and
started. Canonical state was right; only the message was wrong. Reproduced twice on the
Pixel 7, confirming on the Fire HD 8 about a second before it.

### Acceptance Criteria

- The message after a leave is worded from the game as read back after it. It says the
  player resigned only when that game ended by the player's own resignation after the
  tap. A game the opponent's resignation ended, a game already over, and a game that
  could not be read back say only that the series was left.
- A game the server deleted still closes its screen and says it was removed.
- Unit tests cover each wording, and a view-model test covers the race: the opponent's
  leave resigned the game before this player's leave was answered. Verification
  includes the documented `.\gradlew.bat build` gate and `git diff --check`.

### Completion Note — 2026-10-23

**Cause.** `ChessAppViewModel.leaveSeries` worded its message with
`OnlineGame.leftSeriesMessage(decidedAt)`, the game as it was when the player tapped. When
the opponent's leave had already resigned the game in this player's favour, the second leave
(accepted idempotently, `D095`) still said "You left the series and resigned this game."
The line came from `M22.1`.

**Fix.** `leftSeriesMessage(decidedAt, now)` words the message from the game read back after
the leave. It says the player resigned only when a game unfinished at the tap now ends in
that player's resignation (terminated by resignation, and not won by them). A game the
opponent's resignation ended, a game already over, and a game that could not be read back
(the screen keeps the game it had) say "You left the series. No more games with <opponent>
will start." A deleted game's wording moved to its own `leftSeriesRemovedMessage`, unchanged.

**Tests.**

- `OnlineGameTest`: the resigned wording, plus
  `leavingAfterTheOtherPlayerLeftFirstDoesNotSayYouResigned`,
  `aResignationThatWasAlreadyThereIsNotThisLeave`,
  `aGameThatCouldNotBeReadBackClaimsNoResignation` and
  `aGameThatEndedAnotherWayBeforeTheLeaveClaimsNoResignation`.
- `ChessAppTest.leavingJustAfterTheOtherPlayerLeftDoesNotSayYouResigned`: the stub server
  answers the read-back with a game Alex resigned; the screen says "You won by resignation",
  `SeriesOver`, and the plain leave message.
- `.\gradlew.bat build`: green for this commit alone, in a separate worktree, with the server
  tests run against PostgreSQL.

---

## M23.11 — An unchanged game keeps what its screen said

**Status:** DONE

**Depends on:** M21.15

**Source:** findings F1 and F2 of the project owner's adversarial UI test of 2026-10-22
(release 0.1.13-adv, built from `ecb8826`), proposed for fixing the same day.

### Objective

An answer that brings back the version already on the online game's screen leaves the
screen's message and its after-game offer as they were.

### Background

`ChessAppViewModel.show` rebuilt `OnlineGameState.Ready` from every answer at or above
the version on screen, with the answer's own message (none, for a read) and no
after-game state unless the game had just ended. A read at the same version therefore
erased what the screen had just said:

- **F1.** A refused command's message, such as "The game moved on. This is where it is
  now.", was shown for about 125 ms and then wiped by the realtime reload of the version
  the refusal had already brought. Caught on video in 2 of 4 races.
- **F2.** The next-game offer `followSeries` found ("Your next game … has started" and
  Play the next game) was wiped by any later read of the finished game: after a
  both-resign race, a both-leave race, a normal threefold claim, and deterministically by
  pressing Home and returning (`D075`'s reload).

### Acceptance Criteria

- An answer at the version on screen keeps the screen's message unless it brings its own
  (a command's refusal), and always keeps its after-game state. Only a newer version
  replaces them; a game that has just ended still asks what follows (`D014`).
- An older answer still never rolls the board back and still reports its message
  (`M14-02`), and a refresh still settles only its own sync state (`M21.15`).
- Opening a game starts its screen afresh: a message or offer from an earlier visit is
  not shown again.
- View-model tests cover: a refusal followed by a same-version realtime reload keeps the
  message; a next-game offer survives a same-version realtime reload and a return to the
  app. Verification includes the documented `.\gradlew.bat build` gate and
  `git diff --check`.

### Completion Note — 2026-10-22

**Cause.** `ChessAppViewModel.show` built a fresh `OnlineGameState.Ready` from every
answer that was not older than the screen, taking the message from the answer and
setting the after-game state only when the game had just ended. A read has no message
and a finished game read again has not just ended, so a read at the unchanged version
erased both. The code predates `M20.3`'s rename.

**Fix.** `show` now carries the screen's message and after-game state into an answer at
the version already shown. A command's refusal still replaces the message with its own,
a newer version still replaces both, and a game that has just ended still starts
`followSeries`. The older-answer branch (`M14-02`) and the sync handling (`M21.15`) are
unchanged. `openOnlineGame` clears the message and offer of a game seen before, so a new
visit does not repeat what an earlier one was told.

**Tests.** New `UnchangedGameAnswerTest`, against a stateful stub server built with
`mockEngineOn`:

- `aRefusalsMessageSurvivesTheReloadOfTheVersionItBrought` (F1): a `STALE_VERSION`
  refusal, then the realtime reload of the same version, keeps "The game moved on. This is
  where it is now."
- `theNextGameOfferSurvivesAReloadAndComingBackToTheApp` (F2): after a resignation finds
  the next game, a same-version realtime reload and then `onBackground`/`onForeground`
  (`D075`) both keep `AfterGame.NextGame`.
- `aNewerVersionReplacesTheMessage` and
  `openingTheGameAgainDoesNotRepeatWhatAnEarlierVisitWasTold` pin the edges.

With the fix reverted, the F1 and F2 tests fail and the other two pass. Existing
`StaleResponseOrderingTest` (`M14-02`) and `NetworkInterruptionTest` (`M21.15`) pass
unchanged. `.\gradlew.bat build`: green for this commit alone, in a separate worktree, with the
server tests run against PostgreSQL.

---

## M23.12 — Play the next game replaces the finished one

**Status:** DONE

**Depends on:** M22.1

**Source:** finding F8 of the project owner's adversarial UI test of 2026-10-22 (release
0.1.13-adv, built from `ecb8826`), reproduced twice on the Pixel 7 and scheduled the same
day. The behaviour predates `M23`.

### Objective

Back from a game opened with Play the next game leads to the screen the finished game was
opened from, in one press, however many next games were played in a row (`D106`).

### Background

`ChessAppViewModel.openNextGame` opened the next game in front of the finished game's
destination. `back()` then returned to that destination without reading it again, so the
screen kept drawing the next game's state under the finished game's destination: Back
seemed to do nothing, and after a chain of next games the player needed one Back per game
(three, once). Commands from that screen acted on the game drawn, so no wrong canonical
state resulted; the defect was navigation and what the screen claimed.

### Acceptance Criteria

- Play the next game puts the next game in place of the finished game on the back stack
  (`D106`). Every other way into a game still opens it in front of the current screen.
- One Back from the next game shows the screen the finished game was opened from, and
  Back to the dashboard still reads it again (`M21.20`).
- JVM tests cover the stack, including a chain of next games, and view-model tests cover
  what one Back shows after Play the next game. Verification includes the documented
  `.\gradlew.bat build` gate and `git diff --check`.

### Completion Note — 2026-10-23

**Cause.** `openNextGame` opened the next game with `open`, in front of the finished game's
destination. `back()` then popped to that destination without reading it, so the screen
kept drawing the next game's state under the finished game's destination.

**Fix (`D106`).** `AppNavigation.replace` puts a destination in place of the current one,
and `openNextGame` uses it before reading the next game. Back from the next game is now one
press to wherever the finished game was opened from; Back to the dashboard still reads it
again (`M21.20`). Every other way into a game still uses `open`. `PRODUCT.md` and
`AGENTS.md` say so.

**Tests.**

- `AppNavigationTest`: `theNextGameTakesTheFinishedGamesPlace` and
  `aChainOfNextGamesIsOneBackFromWhereTheFirstWasOpened`.
- `ChessAppTest`: `theNextGameTakesTheFinishedGamesPlaceSoOneBackLeavesIt` (stack, one
  Back, dashboard read again, no game read on the way back) and
  `backFromTheNextGameReturnsToTheScreenTheFinishedGameWasOpenedFrom` (Friends).
- `.\gradlew.bat build`: green for this commit alone, in a separate worktree.

---

## M23.13 — The dashboard is current after playing

**Status:** DONE

**Depends on:** M21.20

**Source:** finding F6 of the project owner's adversarial UI test of 2026-10-22 (release
0.1.13-adv, built from `ecb8826`), reproduced twice on the Fire HD 8 and scheduled by the
owner on 2026-10-23. The behaviour predates `M23`.

### Objective

After playing online, the dashboard lists every game under the turn the server reports
whenever the player arrives back on it, however they got there (`M21.20`).

### Background

`ChessAppViewModel.back` read the dashboard again only when Back went straight from an
online game to the dashboard. Dashboard → Friends → Play → a move → Back → Back reached
the dashboard through Friends, so it was not read again and still listed the game under
YOUR TURN with the old move number, until a realtime event or a return to the app.

### Acceptance Criteria

- Leaving an online game marks the dashboard as possibly behind. Arriving on the dashboard
  by Back while it is marked reads it again, once, from any screen in between: Friends, a
  group, History or another.
- Back to the dashboard from a screen when no online game was left since reads nothing.
- JVM tests cover the Friends path, the no-game path and a later return. Verification
  includes the documented `.\gradlew.bat build` gate and `git diff --check`.

### Completion Note — 2026-10-23

**Fix.** `back()` sets `dashboardBehindPlay` whenever it leaves an online game and reads
the dashboard when it arrives there while that is set, clearing it. Straight from a game
this is `M21.20`'s read, unchanged; through Friends, a group or History it is the read that
was missing. Other ways onto the dashboard (startup, `returnToDashboard`, coming back to the
app) already read it.

**Tests.** `TurnSectionsRefreshTest`:
`aGameOpenedThroughFriendsIsUnderTheirTurnWhenBackReachesTheDashboard` (fails with the old
condition), `backFromAScreenWithNoGamePlayedDoesNotReadTheDashboardAgain` and
`theDashboardIsReadOnceOnArrivalNotOnEveryLaterReturn`; the six earlier tests pass unchanged.
`.\gradlew.bat build`: green.

---

## M23.14 — Review's step buttons stay put

**Status:** DONE

**Depends on:** M22.10, M23.2

**Source:** finding F7 of the project owner's adversarial UI test of 2026-10-22 (release
0.1.13-adv, built from `ecb8826`), scheduled by the owner on 2026-10-23. The behaviour
predates `M23`.

### Objective

Start, Previous, Next and End stay exactly where they are while the player steps through a
review, in every window and text size (`D073`, `D100`, `D105`).

### Background

Review drew its title, then its status, then the position line, then the step buttons. The
status is the ending's larger headline at the last ply and a one-line turn line elsewhere,
so the button row moved by about 7 dp on the Pixel 7 whenever the review stepped onto or
off the last ply, and by more where the ending wraps. A thumb resting on Previous landed
somewhere else.

### Acceptance Criteria

- The four buttons have the same bounds at ply 0, a middle ply, the ply before the end and
  the end, in one-column, two-pane and short windows, at font scales 1.0 and 2.0.
- Nothing else in Review changes: the same title, status, position line, board and move
  history (`D105`), and the same behaviour of each button.
- A Compose test checks the bounds; it fails with the old order. Verification includes the
  documented `.\gradlew.bat build` gate and `git diff --check`.

### Completion Note — 2026-10-23

**Fix.** `GameReviewScreen` draws the step buttons straight after the title, which never
changes, and before the status and position lines, whose height does. The row's position
now depends only on what is above it, so it cannot move while stepping.

**Tests.** New device test `ReviewControlsUiTest.theStepButtonsDoNotMoveBetweenTheStartAMiddlePlyAndTheEnd`
at Pixel 7 and Fire HD 8 portrait, a narrow phone, the owner's Pixel 7 and the Fire HD 8 in
landscape, and a short split-screen window, each at font scales 1.0 and 2.0. It passes on the
Fire HD 8, and with the old order it fails there ("Pixel 7 stock portrait (411×914) at 1.0,
ply 0: the step buttons did not move", a 6 px shift in the forced window).
`.\gradlew.bat build`: green.

---

## M23.15 — Every promotion prompt offers Cancel

**Status:** DONE

**Depends on:** D041

**Source:** finding F4 of the project owner's adversarial UI test of 2026-10-22 (release
0.1.13-adv, built from `ecb8826`), scheduled by the owner on 2026-10-23, who chose the online
prompt's Cancel for every screen (`D107`).

### Objective

Pass-and-play and the game against the computer offer the same Cancel in the promotion
question as the online game, so backing out is visible everywhere.

### Background

The online prompt offered Queen, Rook, Bishop, Knight and Cancel. The pass-and-play and
computer prompts offered only the four pieces; the only way out was tapping the board, which
nothing on screen said. `D041` and `PRODUCT.md` did not say which was intended.

### Acceptance Criteria

- Every game screen's promotion question offers Cancel (`D107`). Cancel takes the pawn back
  with nothing played and nothing saved; locally, tapping the board still backs out too.
- One shared prompt serves the three screens.
- Device tests cancel the prompt on each screen. Verification includes the documented
  `.\gradlew.bat build` gate and `git diff --check`.

### Completion Note — 2026-10-23

**Fix (`D107`).** `PromotionPrompt` in `ui/board/LocalGameScreen.kt` takes `onCancel` and
draws Cancel after the four pieces, with "Promote to" in `bodyMedium` as online had it.
Pass-and-play cancels through `BoardInteraction.cancelPromotion` and `play`; the computer
through `ComputerGameActions.onUpdate`; the online screen now uses the same composable, and
its private copy is gone. A cancelled prompt changes no `ChessGame`, so
`LocalGameChange.between` saves nothing. `PRODUCT.md` says so.

**Tests.** New device test `PromotionCancelUiTest` (3): pass-and-play and the computer cancel
with the game unchanged and the prompt gone, and the online prompt still cancels. They pass
on the Fire HD 8; the old local and computer prompts have no Cancel to tap.
`.\gradlew.bat build`: green.

---

## M23.16 — Finding your own name says it is you

**Status:** DONE

**Depends on:** D009

**Source:** finding F5 of the project owner's adversarial UI test of 2026-10-22 (release
0.1.13-adv, built from `ecb8826`), scheduled by the owner on 2026-10-23.

### Objective

Looking up your own username on the Friends screen says it is you instead of offering an
Add friend that the server will refuse.

### Background

Friends → your own username (any case) → Find showed "Found <you>." with Add friend; tapping
it showed the server's "You cannot add yourself". No harm was done, but the screen offered
something it knew could not work.

### Acceptance Criteria

- A lookup whose answer is the signed-in player shows "<username> is you." and no Add
  friend. Any other lookup is unchanged, and no server change is made.
- A view-model test covers a case-variant of the player's own name. Verification includes
  the documented `.\gradlew.bat build` gate and `git diff --check`.

### Completion Note — 2026-10-23

**Fix.** `ChessAppViewModel.findUser` compares the server's answer with `currentUser` by user
id, so any spelling or case the server resolves to the player counts. For the player, it sets
no found user and the message `Friends.thatIsYou` ("Jordan is you."), which the Friends screen
already shows where it shows "No such user".

**Tests.** `ChessAppTest.lookingUpYourOwnNameSaysItIsYouAndOffersNoAdd`: the stub server now
answers the player's own name, in any case, with the player's id and spelling, as the real one
does; the lookup of "  jORDAN " leaves nothing to add and says "Jordan is you." Without the
fix the found user is set and the test fails. `.\gradlew.bat build`: green.
