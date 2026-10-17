-- One active chess series per pair, and leaving ends its current game (M22.1, D095).
--
-- D053 let a pair play several chess series at once. D095 supersedes that rule: a
-- chess table has at most one ACTIVE series, Play opens it, and Leave ends the
-- series and its current game together. This migration brings existing data to
-- that invariant and then has the database keep it.
--
-- 1. games.has_started records whether an ordinary move was ever accepted. Undo
--    and Redo never clear it, so a game whose moves were all undone is still a
--    started game: leaving it is a resignation, not a deletion. Existing rows are
--    backfilled from durable evidence of an accepted move -- an active move, a
--    retained Redo operation, or a move audit event. A row with none stays false.
--
-- 2. Every chess table with more than one ACTIVE series keeps the newest, by
--    created_at desc, id desc, and loses every older one with all its games, moves,
--    seats and audit events. The project owner chose deletion over preservation for
--    data that obstructs the invariant (D095). Closed series do not conflict.
--
-- 3. Under D068 a series could be closed while its current game was still being
--    played. D095 leaves no such state, so each one is ended as a Leave would end it
--    now: a started game becomes the leaver's resignation, recorded with the
--    PlayerResigned and GameEnded events a resignation writes; a game no move was
--    ever accepted in is deleted. A started game whose leaver cannot be named (no
--    SeriesLeft event) is deleted too, as obstructing data.
--
-- 4. A trigger refuses a second ACTIVE series at a chess table. It locks the table
--    row first, the same row SeriesService.play locks, so two transactions racing
--    to open a series are put in order rather than both passing the check. It is
--    scoped to chess: no rule is chosen here for another game type's tables (F26).

-- 1. Whether a game has ever had a move accepted -------------------------------

alter table games
    add column has_started boolean not null default false;

update games g
   set has_started = true
 where exists (select 1 from moves m where m.game_id = g.id)
    or g.redo_path <> '[]'::jsonb
    or exists (select 1
                 from game_events e
                where e.game_id = g.id
                  and e.type in ('MoveMade', 'MoveUndone', 'MoveRedone', 'UndoAccepted'));

-- 2. Older parallel chess series ------------------------------------------------

create temporary table v14_doomed_series on commit drop as
select ranked.id
  from (select s.id,
               row_number() over (partition by s.table_id order by s.created_at desc, s.id desc) as newness
          from game_series s
          join tables t on t.id = s.table_id
         where s.status = 'ACTIVE'
           and t.game_type = 'CHESS') ranked
 where ranked.newness > 1;

-- The series points at its current game and the game at its series, so the
-- pointer goes first. A game's moves, seats and audit events go with it (on
-- delete cascade); the series' own events have no cascade and go explicitly.
update game_series
   set current_game_id = null
 where id in (select id from v14_doomed_series);

delete from game_events
 where series_id in (select id from v14_doomed_series);

delete from games
 where series_id in (select id from v14_doomed_series);

delete from game_series
 where id in (select id from v14_doomed_series);

-- 3. Closed series whose current game is unfinished -----------------------------

create temporary table v14_unfinished_after_leave on commit drop as
select s.id   as series_id,
       g.id   as game_id,
       g.has_started,
       (select e.actor_id
          from game_events e
         where e.series_id = s.id
           and e.type = 'SeriesLeft'
           and e.actor_id is not null
         order by e.id desc
         limit 1) as leaver
  from game_series s
  join games g on g.id = s.current_game_id
 where s.status = 'CLOSED'
   and g.status = 'IN_PROGRESS';

alter table v14_unfinished_after_leave
    add column leaver_seat integer;

update v14_unfinished_after_leave u
   set leaver_seat = p.seat_index
  from game_participants p
 where p.game_id = u.game_id
   and p.user_id = u.leaver;

-- The leaver resigns a started game: the other player wins (seat 0 is White).
update games g
   set status                     = 'COMPLETE',
       result                     = case u.leaver_seat when 0 then 'BLACK_WINS' else 'WHITE_WINS' end,
       termination_reason         = 'RESIGNATION',
       state                      = jsonb_set(
                                        g.state,
                                        '{result}',
                                        jsonb_build_object(
                                            'outcome', case u.leaver_seat when 0 then 'BLACK_WINS' else 'WHITE_WINS' end,
                                            'reason', 'RESIGNATION')),
       version                    = g.version + 1,
       updated_at                 = now(),
       ended_at                   = now(),
       redo_path                  = '[]'::jsonb,
       undo_requested_by          = null,
       undo_answer                = null,
       undo_answer_requester      = null,
       undo_answer_requester_move = null,
       undo_answer_reply_move     = null,
       last_change_kind           = 'RESIGNATION',
       last_change_actor          = u.leaver,
       last_change_version        = g.version,
       last_change_move           = null
  from v14_unfinished_after_leave u
 where g.id = u.game_id
   and u.has_started
   and u.leaver_seat is not null;

insert into game_events (game_id, series_id, actor_id, type, payload)
select g.id, u.series_id, u.leaver, 'PlayerResigned',
       jsonb_build_object('version', g.version, 'leftSeries', true)
  from v14_unfinished_after_leave u
  join games g on g.id = u.game_id
 where u.has_started
   and u.leaver_seat is not null;

insert into game_events (game_id, series_id, actor_id, type, payload)
select g.id, u.series_id, u.leaver, 'GameEnded',
       jsonb_build_object('version', g.version, 'result', g.result, 'terminationReason', g.termination_reason)
  from v14_unfinished_after_leave u
  join games g on g.id = u.game_id
 where u.has_started
   and u.leaver_seat is not null;

-- Everything else is deleted without a result: never started, or no leaver to name.
create temporary table v14_doomed_games on commit drop as
select game_id, series_id
  from v14_unfinished_after_leave
 where not has_started
    or leaver_seat is null;

update game_series
   set current_game_id = null
 where id in (select series_id from v14_doomed_games);

delete from games
 where id in (select game_id from v14_doomed_games);

-- 4. At most one active chess series per table, from now on ---------------------

create function game_series_one_active_chess_series() returns trigger
    language plpgsql
as
$$
declare
    table_game_type text;
begin
    if new.status <> 'ACTIVE' then
        return new;
    end if;

    -- The table row is the lock every decision about its series takes, so a racing
    -- transaction waits here and then sees the series this one is writing.
    select game_type
      into table_game_type
      from tables
     where id = new.table_id
       for update;

    if table_game_type = 'CHESS' and exists (select 1
                                                from game_series
                                               where table_id = new.table_id
                                                 and status = 'ACTIVE'
                                                 and id <> new.id) then
        raise exception 'A chess table already has an active series'
            using errcode = 'unique_violation',
                  constraint = 'game_series_one_active_chess_series';
    end if;

    return new;
end;
$$;

create trigger game_series_one_active_chess_series
    before insert or update of status, table_id
    on game_series
    for each row
execute function game_series_one_active_chess_series();
