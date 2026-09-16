-- Seat rotation by cycle (D050, M19.6).
--
-- Turn order is decided by cycle rotation: a cycle is N consecutive games, N being
-- the number of rotating participants; the cycle's base order is rotated one seat
-- per game, and the next cycle's base order is drawn from the orders that are not
-- a rotation of the cycle just finished. Chess's colour alternation (D014) is the
-- N = 2 case.
--
-- Deciding the next game's order needs these facts, and this column holds them:
--
--   cycleLength        N
--   baseOrder          the current cycle's base order (participant refs)
--   gameInCycle        which game of the cycle was played last, from 0
--   previousBaseOrder  the previous cycle's base order, or null in the first cycle
--
-- It is kept per series, not per table. A table may hold several active series at
-- once (D053), and a table-wide cycle would interleave their games: a rematch in
-- one series could then keep the same colours because a game of another series
-- was started in between, which breaks D014 within a series. With one series at a
-- table the two are the same thing. D066 records this.
--
-- One jsonb document rather than four columns, because the four only mean anything
-- together and are always read and written together, like games.state.
--
-- Nullable with no backfill. A series from before this migration has none; its
-- next rematch starts a cycle whose base order is the seat order of the game that
-- just finished, which gives exactly the reversal D014 already promised.

alter table game_series
    add column seat_rotation jsonb;
