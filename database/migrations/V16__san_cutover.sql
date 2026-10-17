-- The SAN cutover (M22.7, D101).
--
-- Game views now carry every move as a structured view with required SAN, and
-- undo requests and their notes name their moves the same way, derived from the
-- recorded positions. The beta takes this as one contract change with no
-- compatibility period, so whatever was in flight under the old coordinate
-- wording is dropped before the new views are served:
--
--   * every pending undo request (and the version it was made at),
--   * every last-answer note,
--   * the recorded last change, which only recognised a request and an undo that
--     crossed (D091). A crossing against a change from before this migration is
--     refused as stale, as one from before V12 was.
--
-- Nothing about the position, the move history, the Redo path, a draw offer or a
-- result is touched, and no version moves: these columns are request metadata.

update games
   set undo_requested_by          = null,
       undo_requested_version     = null,
       undo_answer                = null,
       undo_answer_requester      = null,
       undo_answer_requester_move = null,
       undo_answer_reply_move     = null,
       last_change_kind           = null,
       last_change_actor          = null,
       last_change_version        = null,
       last_change_move           = null;
