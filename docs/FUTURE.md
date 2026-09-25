# Future Work — Not in the MVP

**Status:** nonbinding. This document carries no precedence and defers to every
document in `CLAUDE.md`'s precedence list. **Where it and `docs/MVP.md` differ,
`MVP.md` governs** (`D072`).

## What this document is

`docs/MVP.md` (*Explicitly Not Required for MVP*) is the binding list of what is
not in the MVP. This document holds the same items, no more and no fewer, with
what is known about each: where it was decided, what the project owner still has
to decide, what it depends on, its value to players, and a rough size. Adding or
removing an item means changing both files.

Nothing here is scheduled. The backlog holds only scheduled work (`D072`), and the
autonomous loop never selects anything from this document. When the project owner
schedules an item, it becomes a task in `docs/BACKLOG.md` and is removed from
here.

These are the four things `D062` asks every standalone document to state:

- **Status:** nonbinding, as above.
- **Evidence and method:** the items are `MVP.md`'s list, which gathered every
  non-MVP item from `MVP.md`, `PRODUCT.md` and `DECISIONS.md` on 2026-09-23.
  Effort and notes come from the owner's triage on 2026-09-23, which checked
  the code as it stood at `c7a6fe1`.
- **Assumptions:** effort is relative: **Small (S)** is about one backlog task,
  **Medium (M)** a few tasks, **Large (L)** a milestone, **Extra large (XL)**
  several milestones. *At a Glance* uses the letters. It assumes the code as
  it is today, and nothing about `M20.1`'s answer.
- **Relationship to binding decisions:** `D072` (where non-MVP work lives),
  `D071` (the "All users" page), and the decision named as each item's source.
  A decision that later changes an item is reflected here in the same change.

**IDs are stable.** Each item keeps its `F` number while it is here. An item that
leaves keeps its ID in the git history, and the number is never given out again.

**Order.** The project owner's triage on 2026-09-23: value to players against
cost, with every item after the items it depends on. The value ratings in *At a
Glance* come from the same triage. `F1`–`F32` were numbered in this order that
day. Numbers are stable from then on: an item added later takes the next free
number and is placed by the same rule, and a later reordering moves entries and
leaves their numbers alone. After either, *At a Glance* and the tiers follow
this order, not the numbers. `MVP.md` lists the same items by topic.

## At a Glance

| ID    | Item                                        | Topic (`MVP.md`)      | Effort        | Value to players   |
| ----- | ------------------------------------------- | --------------------- | ------------- | ------------------ |
| `F1`  | Win/loss/draw record per friend             | Series and Statistics | M             | High               |
| `F2`  | Draw offers by agreement                    | Chess Play            | M             | High               |
| `F3`  | Drag-and-drop moves                         | Chess Play            | S–M           | Medium             |
| `F4`  | Turning automatic rematches off by hand     | Series and Statistics | S             | Medium             |
| `F5`  | Username changes                            | Identity and Accounts | M             | Medium             |
| `F6`  | Detailed profiles                           | Social                | M             | Medium             |
| `F7`  | AI opponent                                 | Chess Play            | L             | Medium–high        |
| `F8`  | Custom themes                               | Presentation          | S–M           | Low                |
| `F9`  | Elaborate animations                        | Presentation          | M             | Low                |
| `F10` | Clocks                                      | Chess Play            | L             | Low–medium         |
| `F11` | Dead-position detection by search           | Chess Play            | M             | Low                |
| `F12` | Remove the "All users" page                 | Identity and Accounts | S–M           | None (testing aid) |
| `F13` | Account recovery UI                         | Identity and Accounts | L             | High               |
| `F35` | User settings, opened from the username     | Identity and Accounts | not estimated | not estimated      |
| `F14` | Approval for friend requests, game invites  | Identity and Accounts | M             | Low                |
| `F15` | Blocking                                    | Social                | M             | Low now            |
| `F16` | Chat                                        | Social                | L             | Medium             |
| `F17` | Followers                                   | Social                | L             | Low                |
| `F18` | Social feeds                                | Social                | L             | Low                |
| `F19` | Contact syncing                             | Social                | L             | Low                |
| `F20` | Spectators                                  | Public Play           | L             | Low                |
| `F21` | Matchmaking                                 | Public Play           | L             | Medium             |
| `F22` | Ratings                                     | Series and Statistics | M             | Low                |
| `F23` | Public games                                | Public Play           | L             | Low–medium         |
| `F24` | Tournaments                                 | Public Play           | XL            | Low                |
| `F25` | Push notifications                          | Social                | L             | High               |
| `F26` | Deck-building systems                       | Deck-Builder Platform | not estimated | The long-term goal |
| `F27` | "Suggest friends" for table co-participants | Social                | not estimated | Medium             |
| `F28` | Deck-builder solo mode                      | Deck-Builder Platform | not estimated | Medium             |
| `F29` | AI players in the deck-builder modes        | Deck-Builder Platform | not estimated | Medium             |
| `F30` | Detailed and per-table statistics           | Series and Statistics | not estimated | Low–medium         |
| `F31` | Series revival                              | Series and Statistics | not estimated | Low                |
| `F32` | iOS or web clients                          | Other Clients         | not estimated | Not needed yet     |
| `F33` | Database backups                            | Operations            | S             | High (protection)  |
| `F34` | Lock down direct database access            | Operations            | S–M           | High (protection)  |

---

## 1. Low cost, high value

No dependency outside this tier. The most value to players for the cost.

### F1 — Win/loss/draw record per friend

- **Source:** `MVP.md`. Split from `F30` (detailed statistics) on 2026-09-23.
- **Value:** High
- **Effort:** Medium. Results are already stored; this is a query and a screen.
- **Depends on:** nothing.
- **Open decisions:** where the record shows (dashboard, friends list, series),
  and whether it counts one series or every game with that friend.

### F2 — Draw offers by agreement

- **Source:** `MVP.md`, `PRODUCT.md`, `D019`
- **Value:** High
- **Effort:** Medium. A new command and a pending-offer state.
- **Depends on:** nothing new.
- **Open decisions:** when an offer expires, and how an offer interacts with
  undo and the game version.

### F3 — Drag-and-drop moves

- **Source:** `PRODUCT.md`
- **Value:** Medium
- **Effort:** Small–medium. Compose only; legality already comes from
  `game-core`.
- **Depends on:** nothing.
- **Open decisions:** none.

### F4 — Turning automatic rematches off by hand

- **Source:** `PRODUCT.md`
- **Value:** Medium
- **Effort:** Small. Explicit series exit already exists (`M19.8`).
- **Depends on:** nothing.
- **Open decisions:** whether the switch lives in user settings (`F35`).

### F5 — Username changes

- **Source:** `MVP.md`, `PRODUCT.md`
- **Value:** Medium
- **Effort:** Medium
- **Depends on:** nothing new.
- **Open decisions:** what happens to the old name (`D008` keeps lost names
  reserved), and how to prevent impersonation. A security decision.

### F6 — Detailed profiles

- **Source:** `PRODUCT.md`
- **Value:** Medium
- **Effort:** Medium
- **Depends on:** `F1` (something worth showing).
- **Open decisions:** what a profile shows, and to whom.

## 2. Higher cost, or lower value

No unfinished dependency. Worth doing, but each costs more or does less for
players than tier 1.

### F7 — AI opponent

- **Source:** `MVP.md`
- **Value:** Medium–high
- **Effort:** Large
- **Depends on:** nothing new. Non-user participants exist (`M19.7`, `D051`).
- **Open decisions:** which engine, and where it runs. A server-side engine is a
  hosting cost.

### F8 — Custom themes

- **Source:** `PRODUCT.md` (*Deferred Features*)
- **Value:** Low
- **Effort:** Small–medium. Compose theming; no server work.
- **Depends on:** nothing.
- **Open decisions:** what can be themed: the board, the pieces, or the whole
  app.

### F9 — Elaborate animations

- **Source:** `PRODUCT.md` (*Deferred Features*)
- **Value:** Low
- **Effort:** Medium. Compose only; no server work.
- **Depends on:** nothing.
- **Open decisions:** which moments animate: moves, captures, check, or the end
  of a game.

### F10 — Clocks

- **Source:** `MVP.md`
- **Value:** Low–medium
- **Effort:** Large
- **Depends on:** a server-side job that ends a game on timeout.
- **Open decisions:** what a clock means in asynchronous play (days per move,
  not minutes), and what happens when it runs out. A product decision.

### F11 — Dead-position detection by search

- **Source:** `D038`
- **Value:** Low
- **Effort:** Medium
- **Depends on:** nothing.
- **Open decisions:** none. Low value: the colour-complex rule is exact for the
  material left once pawns, rooks and queens are gone.

### F12 — Remove the "All users" page, or restrict it to admins

- **Source:** `D071`, `M17.5`, `M17.6`
- **Value:** None (testing aid)
- **Effort:** Small to remove. Small–medium to restrict, plus the admin
  decision.
- **Depends on:** `M17.5`, `M17.6` (both done).
- **Open decisions:** remove it or restrict it to admins. No deadline.

`D071`'s "All users" page is a testing aid and part of the MVP. When the project
owner schedules this item, either delete the page or keep it for admins only.
The task's acceptance criteria:

- The project owner has chosen: remove, or restrict to admins.
- **Remove:** every place listed under *Removing it touches* in `M17.5`'s
  completion note is deleted or edited. `D071` is superseded, not deleted. An
  older APK opening the page sees "The list of users is not available." and
  nothing else changes.
- **Restrict:** `GET /users` refuses callers who are not admins. `/me` carries a
  field the Friends screen reads to show or hide "Browse all users". How someone
  becomes an admin is decided and recorded first, since it is a new security
  decision.
- `docs/MVP.md`, `PRODUCT.md` Friends and `ARCHITECTURE.md` §15 say what
  happened.
- Tests: server and Android tests for whichever path is chosen, and
  `.\gradlew.bat build` passes.

### F13 — Account recovery UI

- **Source:** `MVP.md`, `PRODUCT.md` (the architecture must still let an
  anonymous identity be upgraded later)
- **Value:** High
- **Effort:** Large. Linking a Supabase anonymous user to a recoverable
  identity.
- **Depends on:** nothing new.
- **Open decisions:** which recovery methods. A security decision.

### F35 — User settings, opened from the username

- **Source:** `PRODUCT.md`
- **Value:** not estimated. The screen's value depends on which settings it holds.
- **Effort:** not estimated until its initial contents are decided.
- **Depends on:** the feature or features chosen for its initial contents; possible
  contents already include `F4`, `F5`, `F8`, and `F14`.
- **Open decisions:** which settings it contains. Its entry point is decided:
  tapping the username at the right-hand end of the dashboard's top row.

## 3. Social

Lower priority while every player is a friend of a tester. `F15` (blocking)
gates the public play in tier 4.

### F14 — Approval setting for friend requests and game invites

- **Source:** `D047`, `D049`, `PRODUCT.md`
- **Value:** Low
- **Effort:** Medium. `friendships.status` (`D047`) already exists.
- **Depends on:** nothing. There is no equivalent provision for group or table
  membership (`D049`).
- **Open decisions:** whether it covers friend requests, game invites, or both.

### F15 — Blocking

- **Source:** `PRODUCT.md`
- **Value:** Low now
- **Effort:** Medium
- **Depends on:** nothing.
- **Open decisions:** what a block hides and prevents. A safety decision. It is
  a prerequisite for any public play (`F20`–`F24`).

### F16 — Chat

- **Source:** `MVP.md`, `PRODUCT.md`
- **Value:** Medium
- **Effort:** Large
- **Depends on:** nothing in the code.
- **Open decisions:** moderation and reporting.

### F17 — Followers

- **Source:** `PRODUCT.md`
- **Value:** Low
- **Effort:** Large
- **Depends on:** `F15`, `F6`.
- **Open decisions:** whether following needs consent.

### F18 — Social feeds

- **Source:** `PRODUCT.md`
- **Value:** Low
- **Effort:** Large
- **Depends on:** `F15`, `F17`.
- **Open decisions:** what a feed shows, and to whom.

### F19 — Contact syncing

- **Source:** `PRODUCT.md`
- **Value:** Low
- **Effort:** Large
- **Depends on:** `F15`.
- **Open decisions:** what leaves the phone. A privacy decision.

## 4. Public play, after blocking

Large to extra large. Each needs `F15`. Listed after what it depends on.

### F20 — Spectators

- **Source:** `docs/GAME-STATE-VISIBILITY.md`, `docs/PLATFORM-REVIEW.md`
- **Value:** Low
- **Effort:** Large. Per-viewer projection already decides visibility by role
  with no cap on viewers (`D069`).
- **Depends on:** `F15`.
- **Open decisions:** what a spectator sees, and who may watch.

### F21 — Matchmaking

- **Source:** `MVP.md`
- **Value:** Medium
- **Effort:** Large
- **Depends on:** `F15`.
- **Open decisions:** who is matched with whom.

### F22 — Ratings

- **Source:** `MVP.md`
- **Value:** Low
- **Effort:** Medium
- **Depends on:** `F21` or `F23`. Ratings mean little among friends only.
- **Open decisions:** the rating system.

### F23 — Public games

- **Source:** `MVP.md`
- **Value:** Low–medium
- **Effort:** Large
- **Depends on:** `F15`, `F20`.
- **Open decisions:** what "public" exposes.

### F24 — Tournaments

- **Source:** `MVP.md`
- **Value:** Low
- **Effort:** Extra large
- **Depends on:** `F21`, `F10`.
- **Open decisions:** format.

## 5. Push notifications

High value, but intentionally deferred for now due to the additional infrastructure and platform work required.

### F25 — Push notifications

- **Source:** `MVP.md`
- **Value:** High
- **Effort:** Large. No FCM or Firebase code exists: it needs a Firebase
  account, device-token storage and server-side sends.
- **Depends on:** nothing in the code.
- **Open decisions:** a new external account, and the security of device tokens.

## 6. Deck-builder, after `M20.1`

Waiting on `M20.1`'s sign-off. `F27` is marked higher priority than most non-MVP
work (`D049`), so it comes right after `F26`, which it needs.

### F26 — Deck-building systems

- **Source:** `MVP.md`. The long-term product goal; its platform groundwork is
  `M19` and `M20` in the backlog.
- **Value:** The long-term goal
- **Effort:** not estimated.
- **Depends on:** `M20.1`.
- **Open decisions:** `M20.1`.

### F27 — "Suggest friends" for table co-participants

- **Source:** `D049`, which marks it higher priority than most non-MVP work.
- **Value:** Medium
- **Effort:** not estimated. It matters only once tables of three or more exist.
- **Depends on:** `F26`.
- **Open decisions:** when the prompt appears.

### F28 — Deck-builder solo mode

- **Source:** `D048`. 1–4 humans against the enemy.
- **Value:** Medium
- **Effort:** not estimated.
- **Depends on:** `F26`.
- **Open decisions:** none recorded yet.

### F29 — AI players in the deck-builder modes

- **Source:** `D051`
- **Value:** Medium
- **Effort:** not estimated.
- **Depends on:** `F26`.
- **Open decisions:** none recorded yet.

### F30 — Detailed and per-table statistics

- **Source:** `MVP.md`, `D048`. Split from `F1` on 2026-09-23.
- **Value:** Low–medium
- **Effort:** not estimated. Per-table statistics need tables of three or more.
- **Depends on:** `F1`, so both share one statistics model; `F26` for per-table
  statistics.
- **Open decisions:** which statistics.

### F31 — Series revival

- **Source:** `D048`. The identical participant set resuming a prior series's
  identity for statistics.
- **Value:** Low
- **Effort:** not estimated.
- **Depends on:** `F1` and `F30`, so all three share one statistics model; the
  deck-builder, after `M20.1`.
- **Open decisions:** when a new series counts as a revival.

## 7. Other clients

Waiting on a concrete non-JVM client requirement.

### F32 — iOS or web clients

- **Source:** `D002`
- **Value:** Not needed yet
- **Effort:** not estimated. A separate client strategy.
- **Depends on:** a concrete non-JVM client requirement (`CLAUDE.md`: no Kotlin
  Multiplatform without one).
- **Open decisions:** the client strategy.

## 8. Operations

Added by the project owner on 2026-09-24 from the database review of that day,
at the bottom of the list. Neither changes what a player sees; both protect the
data players already have.

### F33 — Database backups

- **Source:** the 2026-09-24 database review. Supabase's free plan keeps no
  backups (`D035`, `docs/DEVELOPMENT.md`).
- **Value:** High (protection). Losing or corrupting the data would be permanent.
- **Effort:** Small. A nightly export, for example `pg_dump` from a scheduled
  GitHub Actions workflow.
- **Depends on:** nothing.
- **Open decisions:** storing a database secret in GitHub (a security decision),
  where the exports are kept and for how long, and whether a paid plan's backups
  are preferred instead.

### F34 — Lock down direct database access

- **Source:** the 2026-09-24 database review. Checked that day: row-level security
  is off on all 14 public tables, and the `anon` and `authenticated` roles hold
  `SELECT`, `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`, `REFERENCES` and `TRIGGER` on
  every one, `flyway_schema_history` included.
- **Value:** High (protection). The anon key ships inside the APK, so anyone who
  extracts it could read and change every table through Supabase's REST API. That
  API was answering `503` on 2026-09-24, which hides the problem but does not fix
  it. The app never uses it: Android goes through Ktor (`CLAUDE.md`).
- **Effort:** Small–medium. Revoke the `anon` and `authenticated` grants, change
  the default privileges so new tables do not get them, and enable row-level
  security. Optionally, give the server its own least-privilege database role
  instead of `postgres`.
- **Depends on:** nothing. The server connects as `postgres`, which neither change
  affects.
- **Open decisions:** it is a security change to the live database, so it needs a
  decision in `DECISIONS.md`; and whether the server gets its own role.
