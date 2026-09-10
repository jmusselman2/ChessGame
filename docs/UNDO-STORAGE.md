# Undo Storage Sizing — the `M19.9` Spike

**Measured:** 2026-09-10, against `claude-autopilot` at `81e0c8d`
**Discharges:** `D055`'s explicit obligation to cost the persistence approach
before `M19` implements undo
**Status:** analysis and recommendations. **Nothing here is implemented, and
nothing here is an accepted decision yet** — the undo task decides, and records
what it decides. This document is deliberately below `docs/DECISIONS.md`,
`PRODUCT.md`, `ARCHITECTURE.md`, `MVP.md` and `BACKLOG.md` in precedence, for the
same reason `PLATFORM-REVIEW.md` is: it describes and recommends, it does not
bind.

It also assumes **no** answer to `M19.1`. Every number below is about a state
document and a write pattern, not about where code lives, so the analysis holds
under any of `M19.1`'s four layouts.

## What `D055` bought, and what it cost

`D055` set undo depth to **unlimited within a shuffle boundary**: any number of
actions may be taken back, as far as the last shuffle, and a shuffle is a hard,
global, permanent barrier. It then said the quiet part out loud — that this needs
full prior-state snapshots for every action since that barrier, that the span can
be arbitrarily long because *a deck that never exhausts is never barred*, and
that **the cost had been accepted without being measured.**

This is that measurement.

## Method

Two probes, run against the real serializers and the real
`save(wholeGame)` write pattern, then discarded rather than left in the suite —
they print numbers, they assert nothing, and a size assertion would be a brittle
test of a model that is about to change. Both are reproducible from the
parameters given here.

**Chess (real data).** A 96-ply game played by choosing uniformly among
`ChessRules.legalMoves` from `Random(19)`, serializing `GameStateDocument`
through the production `StorageJson` at every ply, and accumulating what
`save(wholeGame)` would write.

**Deck-builder (modelled).** A representative `state` document built to the shape
`D048`/`D051` imply — per seat a deck, hand, discard and play area plus counters;
a shared ten-pile market; a trash; a turn record with `shuffleBarrierSeq` — at
2, 3, 4 and 5 participants, in two card representations. Plain JSON with no
encoding tricks, because that is the model being costed.

The deck-builder state shape **does not exist yet**, so those figures are a model
and not a measurement. They are stated as such throughout; the chess figures are
real.

## Chess, as built (96 plies)

| | bytes |
|---|---:|
| `state` blob, opening position | 307 |
| `state` blob, peak | 1,040 |
| `state` blob, mean | 451 |
| `state` blob with `repetitions` emptied | 262 |
| `moves` table, final size | 43,363 |
| **`moves` bytes actually written across the game** | **1,844,774** |
| Write amplification vs. an append-only log | **42×** |

Two things are worth pulling out.

**`repetitions` is the only part of the chess blob that grows,** and it is
self-limiting: `positionCounts` is discarded on an irreversible move, so it is
bounded by the halfmove clock rather than by game length. That is why the peak
(1,040 B) sits in a long quiet middlegame and the final position is back down to
425 B. Worst case is a full 150-ply quiet stretch before the seventy-five-move
rule fires, at roughly 90 B per entry — about 13 KB. Bounded, and small.

**The 42× is already real.** `save` deletes and re-inserts the entire move
history on every write, because it is handed the *resulting game* and cannot tell
a push from a pop (`PLATFORM-REVIEW`, *Rewriting history on every write*). At 96
plies that is 1.8 MB written to store 43 KB. This is invisible in chess and is
accepted there — 1.8 MB per game is nothing, and `D044` says not to generalise
chess before there is a second ruleset to compare. It is the mechanism, not the
magnitude, that does not survive.

## Deck-builder, modelled

### Representative retained-snapshot size

| participants | compact (ids) | verbose (embedded definitions) |
|---:|---:|---:|
| 2 | 1,512 B | 7,565 B |
| 3 | 1,828 B | 9,148 B |
| 4 | 2,144 B | 10,742 B |
| 5 | 2,460 B | 12,331 B |

Growth is linear and gentle: about **+320 B per participant** compact, **+1.6 KB
verbose**. The five-participant row is the `D048` ceiling (four humans plus the
scripted enemy, `D051`).

The compact/verbose gap is the single largest and cheapest lever in this
document: **a card instance that is an id resolved against a static catalogue is
five times smaller than one carrying its own definition.** Nothing is lost — the
catalogue is static data the server already has — and every figure below assumes
the compact form.

### Realistic action counts

Deck-builders are many-actions-per-turn games, which is exactly the property
chess did not exercise (`PLATFORM-REVIEW`, *What chess did not prove*).

| | actions |
|---|---:|
| One turn (play a hand, buy, resolve reactions) | 8–15 |
| A four-player game, ~20 turns each | ~800–1,000 |
| **Between shuffles — the number that matters** | **~10–30** |

The retention span is *not* the game length, because the barrier is a shuffle by
**any** participant (`D055`). With four players each reshuffling roughly every
other turn, a barrier lands every couple of turns overall — so the realistic
`actions-since-shuffle` is tens, not hundreds.

The tail is what `D055` flagged: a deck-manipulation build that never exhausts is
never barred. The pathological case is a whole game in one span, ~960 actions.
Both are costed below.

### Retained bytes, and bytes written

At five participants, 2,460 B per snapshot:

| actions since shuffle | snapshots retained | written under `save(wholeGame)` | that action's own write |
|---:|---:|---:|---:|
| 10 | 24.6 KB | 135 KB | 24.6 KB |
| 30 *(typical)* | 73.8 KB | **1.14 MB** | 73.8 KB |
| 60 | 148 KB | 4.5 MB | 148 KB |
| 120 | 295 KB | 17.9 MB | 295 KB |
| 960 *(pathological)* | 2.36 MB | **1.13 GB** | **2.36 MB** |

**Retention is affordable. The write pattern is not.**

2.36 MB of retained snapshots for a pathological game is a non-problem —
`D055`'s accepted cost is, on the numbers, fine. What is not fine is the middle
column: `save(wholeGame)`'s O(N) rewrite per action makes the total quadratic, so
the *typical* 30-action span already writes 1.14 MB, and the pathological one
writes **over a gigabyte to store two megabytes**. The last row's final column is
the clearest way to say it: at 960 actions, playing one card writes 2.36 MB.

## Recommendations

### 1. Snapshot form — **delta, with a full checkpoint at each barrier**

Measured, at the five-participant size:

| form | chess (real) | deck-builder (modelled) |
|---|---:|---:|
| Whole, uncompressed | 1× | 1× |
| Whole, compressed per row | **1.95×** | 2.6× |
| Whole, sequence compressed together | 18.5× | (upper bound only — see below) |
| Delta between consecutive snapshots | **~5.5×** | **~250×** |

**Per-row compression is not worth having.** 1.95× on real chess data: these
payloads are a few hundred bytes to a few kilobytes, and gzip cannot warm a
dictionary on that. It adds an encode/decode step and a debugging obstacle for
roughly half the bytes.

**Sequence compression is worth 18.5× on real chess data** and gets it entirely
from *inter-row* similarity — consecutive positions are nearly identical. (The
modelled deck-builder figure is far higher and is not quoted, because synthetic
snapshots built from a seeded generator are more self-similar than real play.
The chess number is the trustworthy one.) But capturing that redundancy by
compressing the run as one object fights both operations undo needs: appending
one action, and truncating to a sequence number. It would mean rewriting the
compressed run per action — the O(N²) problem again, in a different coat.

**A delta captures the same redundancy structurally, and stays row-addressable.**
The measurement that settles it: **one action changes 2 characters of a 2,460-byte
document** in the counter case, and roughly ten bytes when a card moves between
zones — **0.4% to 1% of the blob.** Chess is less extreme and still 5.5×
(mean 83 characters differ out of ~450).

So the recommendation is: **store the action and what is needed to invert it, not
the prior state**, plus one full snapshot at each shuffle boundary — which is
free, because `undoBarrierSeq` is already the retention floor and the barrier is
already a write. Any earlier state within the span is reached by replaying from
that checkpoint, which is sound here because the ruleset is deterministic and the
randomness is materialised (`PLATFORM-REVIEW`, *Randomness is materialised*) —
a replay cannot reshuffle differently.

This is a real departure from chess, and it should be named as one. `D029` keeps
`position_before` per move precisely so an undo is exact without replay. That is
right for chess: 96 snapshots at 451 B is 43 KB, replay buys nothing, and storing
the position is simpler than storing an inverse. It stops being right when one
action changes 1% of a document and there may be a thousand of them.

### 2. Whole-state vs. partial — **write in parts: per seat, plus a shared row**

One action touches one or two seats' zones, sometimes a market pile, and the turn
record. The current model rewrites everything.

At five participants, a per-seat split makes the per-action write **about 320 B
of seat state plus the turn record instead of 2,460 B — roughly 8×** — and the
saving grows with participant count, because the untouched seats are what is
being skipped.

Two constraints to carry, neither of which blocks it:

- **Atomicity is not lost.** `save` already writes the game row, the history and
  the audit event in one transaction; N seat rows in that same transaction is not
  new risk.
- **The version guard must stay on one row.** `D021`'s
  `update ... where id = ? and version = ?` is what settles a race, and it needs
  a single row to settle it on. Keep the version on the game/table row and let
  the seat rows hang off it, guarded by the same transaction. Splitting the
  version across seats would break the one platform concept `M18.1` found most
  portable.

Combined with delta snapshots, this removes the quadratic term entirely: per
action, one seat row rewritten and one small delta appended.

### 3. `append` / `truncateTo` — **confirmed, and it is the prerequisite, not a nicety**

`PLATFORM-REVIEW` already argued the signature has to change. The measurement
makes it concrete: `save(wholeGame)` is handed the resulting game and cannot tell
a push from a pop, so its only correct option is to snapshot the whole stack —
`deleteWhere` then re-insert every record. That is the 42× amplification measured
in chess and the 1.13 GB modelled for a pathological deck-builder game.

Neither operation undo needs can be expressed by it:

- **`append(action, …)`** — add one action at sequence N+1. Under
  `save(wholeGame)` this is a full rewrite.
- **`truncateTo(seq)`** — discard everything after `seq`. Under
  `save(wholeGame)` the caller must reconstruct the whole truncated game and hand
  it back, so a rewind of one action costs a rewrite of the whole span.

With them, per-action cost is O(1) and a rewind is O(actions rewound).

Two things the signature should carry:

- **`truncateTo` must refuse to cross `undoBarrierSeq`.** The barrier is
  permanent with or without agreement (`D055`); making that a precondition of the
  storage operation means no rules bug can reach past it.
- **Pruning belongs at the barrier.** Snapshots and deltas before
  `undoBarrierSeq` may be deleted, in the same transaction that records the
  shuffle. That is what keeps retention at the "typical" row of the table above
  rather than the game-length one.

## Summary

| question | answer |
|---|---|
| Representative snapshot | **2.5 KB** at 5 participants, compact ids; 12 KB if definitions are embedded |
| Realistic action count | 8–15 per turn; **10–30 between shuffles**; ~960 pathological |
| Retained bytes | 74 KB typical, 2.4 MB pathological — **affordable** |
| Per-action write, as built | 74 KB typical, **2.4 MB** pathological — **not affordable** |
| Snapshot form | **Delta + a full checkpoint at each barrier.** Per-row compression buys 1.95× and is not worth it |
| State persistence | **In parts, per seat plus shared** — ~8× off the per-action write at 5 participants |
| `append` / `truncateTo` | **Confirmed prerequisite.** `save(wholeGame)` cannot express a bounded rewind; it is the whole source of the 42× |
| Card representation | **Ids against a static catalogue** — 5× smaller, costs nothing |

`D055`'s accepted retention cost survives measurement. Its *mechanism* does not,
and the fix was already named — the command signature — which this confirms and
sizes.

Chess is left alone. 1.8 MB per game is not a problem, and changing it before a
second ruleset exists is what `D044` forbids.
