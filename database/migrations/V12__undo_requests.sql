-- Undo requests (M21.19, D091).
--
-- Once the opponent has replied, a player may ask them to undo that move and the
-- reply. The request, its last answer, and the last accepted change to the game
-- live on the game row, beside the rules state, never inside it: chess-core and
-- the state document are unchanged.
--
--   undo_requested_by          the player whose undo request is pending, if any.
--                              Any other accepted change clears it, so a pending
--                              request is always answered at the current version.
--   undo_answer, undo_answer_* the last answer (or crossing) and the two moves it
--                              was about, kept until the next accepted change so
--                              each player's note shows until then.
--   last_change_*              what the last accepted command was, who sent it,
--                              the version it was applied to, and the move an undo
--                              took back. Read only to recognise a request and an
--                              undo that crossed (D091); the audit trail is not.
--
-- Every column is nullable with no default, so adding them rewrites no row, and
-- a game in progress reads as having no request, no answer and no recorded last
-- change. A crossing against a change made before this migration is refused as
-- stale, which is the behaviour every command had until now.

alter table games
    add column undo_requested_by          uuid references users (id),
    add column undo_answer                text,
    add column undo_answer_requester      uuid references users (id),
    add column undo_answer_requester_move text,
    add column undo_answer_reply_move     text,
    add column last_change_kind           text,
    add column last_change_actor          uuid references users (id),
    add column last_change_version        bigint,
    add column last_change_move           text,

    -- A finished game never has a request pending (D017-D019).
    add constraint games_undo_request_in_progress
        check (undo_requested_by is null or status = 'IN_PROGRESS'),
    -- Asking clears the last answer, so the two are never there together.
    add constraint games_undo_request_or_answer
        check (undo_requested_by is null or undo_answer is null),
    add constraint games_undo_answer
        check (undo_answer is null
            or undo_answer in ('ACCEPTED', 'DECLINED', 'CROSSED_REQUEST_FIRST', 'CROSSED_UNDO_FIRST')),
    add constraint games_undo_answer_complete
        check ((undo_answer is null) = (undo_answer_requester is null)
            and (undo_answer is null) = (undo_answer_requester_move is null)
            and (undo_answer is null) = (undo_answer_reply_move is null)),
    add constraint games_last_change_kind
        check (last_change_kind is null
            or last_change_kind in ('MOVE', 'UNDO', 'UNDO_REQUEST', 'UNDO_ACCEPT', 'UNDO_DECLINE',
                                    'DRAW_CLAIM', 'RESIGNATION')),
    add constraint games_last_change_complete
        check ((last_change_kind is null) = (last_change_actor is null)
            and (last_change_kind is null) = (last_change_version is null)),
    -- Only an undo names the move it took back.
    add constraint games_last_change_move
        check (last_change_move is null or last_change_kind = 'UNDO');
