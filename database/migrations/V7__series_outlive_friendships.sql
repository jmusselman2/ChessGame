-- Removing a friend no longer closes a series (D053, M19.5).
--
-- D013 made removing a friend mark the pair's active series to close after its
-- current game: the game finished, no rematch followed, and the series closed.
-- close_after_current_game was that mark. D053 superseded D013: removing a
-- friend affects the friends list only, a series persists independently of the
-- friend graph, and a series ends only when a participant explicitly leaves it
-- (D052, M19.8).
--
-- M19.5 removes the code that wrote and read the mark, so the column is dead and
-- is dropped here, beside that change. (D053 placed this drop in the migration
-- that removed the one-active-series index; M19.4 left it for this one, because
-- the code still depended on it then.)
--
-- A series that was marked before this migration is simply no longer marked: it
-- plays on, with its automatic rematch, which is what D053 says it should do.

alter table game_series
    drop column close_after_current_game;
