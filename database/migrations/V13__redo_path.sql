-- Redo (M21.21, D092).
--
-- An accepted Undo no longer destroys what it took back: the game row keeps the
-- ordered Undo operations Redo can restore, each with the moves it removed, the
-- position each was played from, and the position after the last of them. Redo
-- restores those recorded states; it never replays a move through the rules.
--
--   redo_path  a JSON array of operations, the next one to restore last:
--              [{"records":[{"from":"g1","to":"f3","promotion":null,
--                            "positionBefore":{...}}, ...],
--                "stateAfter":{...}}, ...]
--              The position documents have the shape of games.state.
--
-- It is written in the same statement as the game's state, version and move
-- history, so the three never disagree. A move, a resignation or a claimed draw
-- writes it empty. It is server state only: no game view ever carries it (D069).
--
-- The column is added with a constant default, which rewrites no row, so every
-- game in progress starts with no Redo path.

alter table games
    add column redo_path jsonb not null default '[]'::jsonb,

    add constraint games_redo_path_array
        check (jsonb_typeof(redo_path) = 'array'),
    -- A finished game offers no Redo (D017-D019, D092).
    add constraint games_redo_path_in_progress
        check (status = 'IN_PROGRESS' or redo_path = '[]'::jsonb);

-- A Redo is an accepted change of its own, recorded as the game's last change.
alter table games
    drop constraint games_last_change_kind,
    add constraint games_last_change_kind
        check (last_change_kind is null
            or last_change_kind in ('MOVE', 'UNDO', 'UNDO_REQUEST', 'UNDO_ACCEPT', 'UNDO_DECLINE',
                                    'DRAW_CLAIM', 'RESIGNATION', 'REDO'));
