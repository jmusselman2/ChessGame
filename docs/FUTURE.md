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
cost, with every item after the items it depends on. The value ratings in
*At a Glance* come from the same triage. `F` numbers were assigned in
this order that day. They are stable from then on, so a later reordering moves
entries and leaves their numbers alone. `MVP.md` lists the same items by topic.

## At a Glance

| ID | Item | Topic (`MVP.md`) | Effort | Value to players |
|---|---|---|---|---|
| `F1` | Win/loss/draw record per friend | Series and Statistics | M | High |
| `F2` | Draw offers by agreement | Chess Play | M | High |
| `F3` | Drag-and-drop moves | Chess Play | S–M | Medium |
| `F4` | Turning automatic rematches off by hand | Series and Statistics | S | Medium |
| `F5` | Username changes | Identity and Accounts | M | Medium |
| `F6` | Detailed profiles | Social | M | Medium |
| `F7` | Account recovery UI | Identity and Accounts | L | High |
| `F8` | AI opponent | Chess Play | L | Medium–high |
| `F9` | Clocks | Chess Play | L | Low–medium |
| `F10` | Remove the "All users" page, or restrict it to admins | Identity and Accounts | S–M | None (testing aid) |
| `F11` | Dead-position detection by search | Chess Play | M | Low |
| `F12` | Approval setting for friend requests and game invites | Identity and Accounts | M | Low |
| `F13` | Blocking | Social | M | Low now |
| `F14` | Chat | Social | L | Medium |
| `F15` | Followers | Social | L | Low |
| `F16` | Social feeds | Social | L | Low |
| `F17` | Contact syncing | Social | L | Low |
| `F18` | Spectators | Public Play | L | Low |
| `F19` | Matchmaking | Public Play | L | Medium |
| `F20` | Ratings | Series and Statistics | M | Low |
| `F21` | Public games | Public Play | L | Low–medium |
| `F22` | Tournaments | Public Play | XL | Low |
| `F23` | Push notifications | Social | L | Very low (owner's call) |
| `F24` | Deck-building systems | Deck-Builder Platform | not estimated | The long-term goal |
| `F25` | "Suggest friends" for table co-participants | Social | not estimated | Medium |
| `F26` | Deck-builder solo mode | Deck-Builder Platform | not estimated | Medium |
| `F27` | AI players in the deck-builder's normal modes | Deck-Builder Platform | not estimated | Medium |
| `F28` | Detailed and per-table statistics | Series and Statistics | not estimated | Low–medium |
| `F29` | Series revival | Series and Statistics | not estimated | Low |
| `F30` | iOS or web clients | Other Clients | not estimated | Not needed yet |

---

## 1. Low cost, high value

No dependency outside this tier. The most value to players for the cost.

### F1 — Win/loss/draw record per friend

- **Source:** `MVP.md`. Split from `F28` (detailed statistics) on 2026-09-23.
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
- **Open decisions:** where the switch lives, possibly user settings (`M17.4`).

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

### F7 — Account recovery UI

- **Source:** `MVP.md`, `PRODUCT.md` (the architecture must still let an
  anonymous identity be upgraded later)
- **Value:** High
- **Effort:** Large. Linking a Supabase anonymous user to a recoverable
  identity.
- **Depends on:** nothing new.
- **Open decisions:** which recovery methods. A security decision.

### F8 — AI opponent

- **Source:** `MVP.md`
- **Value:** Medium–high
- **Effort:** Large
- **Depends on:** nothing new. Non-user participants exist (`M19.7`, `D051`).
- **Open decisions:** which engine, and where it runs. A server-side engine is a
  hosting cost.

### F9 — Clocks

- **Source:** `MVP.md`
- **Value:** Low–medium
- **Effort:** Large
- **Depends on:** a server-side job that ends a game on timeout.
- **Open decisions:** what a clock means in asynchronous play (days per move,
  not minutes), and what happens when it runs out. A product decision.

### F10 — Remove the "All users" page, or restrict it to admins

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

### F11 — Dead-position detection by search

- **Source:** `D038`
- **Value:** Low
- **Effort:** Medium
- **Depends on:** nothing.
- **Open decisions:** none. Low value: the colour-complex rule is exact for the
  material left once pawns, rooks and queens are gone.

## 3. Social

Lower priority while every player is a friend of a tester. `F13` (blocking)
gates the public play in tier 4.

### F12 — Approval setting for friend requests and game invites

- **Source:** `D047`, `D049`, `PRODUCT.md`
- **Value:** Low
- **Effort:** Medium. `friendships.status` (`D047`) already exists.
- **Depends on:** nothing. There is no equivalent provision for group or table
  membership (`D049`).
- **Open decisions:** whether it covers friend requests, game invites, or both.

### F13 — Blocking

- **Source:** `PRODUCT.md`
- **Value:** Low now
- **Effort:** Medium
- **Depends on:** nothing.
- **Open decisions:** what a block hides and prevents. A safety decision. It is
  a prerequisite for any public play (`F18`–`F22`).

### F14 — Chat

- **Source:** `MVP.md`, `PRODUCT.md`
- **Value:** Medium
- **Effort:** Large
- **Depends on:** nothing in the code.
- **Open decisions:** moderation and reporting.

### F15 — Followers

- **Source:** `PRODUCT.md`
- **Value:** Low
- **Effort:** Large
- **Depends on:** `F13`, `F6`.
- **Open decisions:** whether following needs consent.

### F16 — Social feeds

- **Source:** `PRODUCT.md`
- **Value:** Low
- **Effort:** Large
- **Depends on:** `F13`, `F15`.
- **Open decisions:** what a feed shows, and to whom.

### F17 — Contact syncing

- **Source:** `PRODUCT.md`
- **Value:** Low
- **Effort:** Large
- **Depends on:** `F13`.
- **Open decisions:** what leaves the phone. A privacy decision.

## 4. Public play, after blocking

Large to extra large. Each needs `F13`. Listed after what it depends on.

### F18 — Spectators

- **Source:** `docs/GAME-STATE-VISIBILITY.md`, `docs/PLATFORM-REVIEW.md`
- **Value:** Low
- **Effort:** Large. Per-viewer projection already decides visibility by role
  with no cap on viewers (`D069`).
- **Depends on:** `F13`.
- **Open decisions:** what a spectator sees, and who may watch.

### F19 — Matchmaking

- **Source:** `MVP.md`
- **Value:** Medium
- **Effort:** Large
- **Depends on:** `F13`.
- **Open decisions:** who is matched with whom.

### F20 — Ratings

- **Source:** `MVP.md`
- **Value:** Low
- **Effort:** Medium
- **Depends on:** `F19` or `F21`. Ratings mean little among friends only.
- **Open decisions:** the rating system.

### F21 — Public games

- **Source:** `MVP.md`
- **Value:** Low–medium
- **Effort:** Large
- **Depends on:** `F13`, `F18`.
- **Open decisions:** what "public" exposes.

### F22 — Tournaments

- **Source:** `MVP.md`
- **Value:** Low
- **Effort:** Extra large
- **Depends on:** `F19`, `F9`.
- **Open decisions:** format.

## 5. Push notifications

Very low priority, by the project owner's call.

### F23 — Push notifications

- **Source:** `MVP.md`
- **Value:** Very low (owner's call)
- **Effort:** Large. No FCM or Firebase code exists: it needs a Firebase
  account, device-token storage and server-side sends.
- **Depends on:** nothing in the code.
- **Open decisions:** a new external account, and the security of device tokens.

## 6. Deck-builder, after `M20.1`

Waiting on `M20.1`'s sign-off. `F25` is marked higher priority than most non-MVP
work (`D049`), so it comes right after `F24`, which it needs.

### F24 — Deck-building systems

- **Source:** `MVP.md`. The long-term product goal; its platform groundwork is
  `M19` and `M20` in the backlog.
- **Value:** The long-term goal
- **Effort:** not estimated.
- **Depends on:** `M20.1`.
- **Open decisions:** `M20.1`.

### F25 — "Suggest friends" for table co-participants

- **Source:** `D049`, which marks it higher priority than most non-MVP work.
- **Value:** Medium
- **Effort:** not estimated. It matters only once tables of three or more exist.
- **Depends on:** `F24`.
- **Open decisions:** when the prompt appears.

### F26 — Deck-builder solo mode

- **Source:** `D048`. 1–4 humans against the enemy.
- **Value:** Medium
- **Effort:** not estimated.
- **Depends on:** `F24`.
- **Open decisions:** none recorded yet.

### F27 — AI players in the deck-builder's normal modes

- **Source:** `D051`
- **Value:** Medium
- **Effort:** not estimated.
- **Depends on:** `F24`.
- **Open decisions:** none recorded yet.

### F28 — Detailed and per-table statistics

- **Source:** `MVP.md`, `D048`. Split from `F1` on 2026-09-23.
- **Value:** Low–medium
- **Effort:** not estimated. Per-table statistics need tables of three or more.
- **Depends on:** `F1`, so both share one statistics model; `F24` for per-table
  statistics.
- **Open decisions:** which statistics.

### F29 — Series revival

- **Source:** `D048`. The identical participant set resuming a prior series's
  identity for statistics.
- **Value:** Low
- **Effort:** not estimated.
- **Depends on:** `F1` and `F28`, so all three share one statistics model; the
  deck-builder, after `M20.1`.
- **Open decisions:** when a new series counts as a revival.

## 7. Other clients

Waiting on a concrete non-JVM client requirement.

### F30 — iOS or web clients

- **Source:** `D002`
- **Value:** Not needed yet
- **Effort:** not estimated. A separate client strategy.
- **Depends on:** a concrete non-JVM client requirement (`CLAUDE.md`: no Kotlin
  Multiplatform without one).
- **Open decisions:** the client strategy.
