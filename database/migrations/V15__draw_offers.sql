-- Draw offers by agreement (M22.3, D097).
--
-- Either player may offer a draw in an unfinished game; the other accepts or
-- declines. An offer and an undo request may be pending together, and a change
-- to one request does not disturb the other. Any change to the board, the
-- active history or the result clears both.
--
--   draw_offered_by     the player whose draw offer is pending, if any.
--   draw_offer_blocked  a draw offer was declined, and no ordinary move has been
--                       accepted since: no new offer may be made until one is.
--                       Undo and Redo do not lift it.
--   draw_offered_version     the version the pending draw offer produced, and
--   undo_requested_version   the version the pending undo request produced. An
--                            answer must be decided at or after its request: one
--                            sent from earlier never saw it.
--   play_version        the version the last gameplay change (a move, Undo, Redo,
--                       an accepted request, a resignation, a claim or an agreed
--                       draw) produced. A request-only change leaves it alone, so
--                       an answer sent from any version at or after it still
--                       answers the same position (D097).
--
-- Existing games start with play_version at their current version, and a
-- pending undo request is taken to have been made at it. Until the next
-- gameplay change, an answer is accepted only at that version, which is the
-- rule every answer had until now.

alter table games
    add column draw_offered_by        uuid references users (id),
    add column draw_offer_blocked     boolean not null default false,
    add column draw_offered_version   bigint,
    add column undo_requested_version bigint,
    add column play_version           bigint;

update games
   set play_version = version,
       undo_requested_version = case when undo_requested_by is null then null else version end;

alter table games
    alter column play_version set not null,
    alter column play_version set default 0,

    -- A finished game has no offer pending (D017-D019).
    add constraint games_draw_offer_in_progress
        check (draw_offered_by is null or status = 'IN_PROGRESS'),
    add constraint games_play_version
        check (play_version >= 0 and play_version <= version),
    add constraint games_draw_offered_version
        check ((draw_offered_by is null) = (draw_offered_version is null)),
    add constraint games_undo_requested_version
        check ((undo_requested_by is null) = (undo_requested_version is null));

alter table games
    drop constraint games_last_change_kind,
    add constraint games_last_change_kind
        check (last_change_kind is null
            or last_change_kind in ('MOVE', 'UNDO', 'UNDO_REQUEST', 'UNDO_ACCEPT', 'UNDO_DECLINE',
                                    'DRAW_CLAIM', 'RESIGNATION', 'REDO',
                                    'DRAW_OFFER', 'DRAW_ACCEPT', 'DRAW_DECLINE'));
