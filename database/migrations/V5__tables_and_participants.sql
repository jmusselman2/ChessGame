-- Tables and participants replace the friend pair (D048, D063, M19.3).
--
-- Until now a series was keyed by an ordered pair (game_series.user_a_id /
-- user_b_id) and a game named its players as white_user_id / black_user_id. The
-- pair was a database artifact, not a design principle: what recurs is a
-- specific set of people who sat down together (D048). This migration makes that
-- set the key.
--
--   game_types          which games exist, and how many participants each seats.
--   tables              one row per exact participant set, per game type.
--   table_participants  who sits at a table.
--   game_participants   who took which seat in one game. For chess, seat 0 is
--                       White and seat 1 is Black: seat order is turn order.
--
-- Player counts belong to the game type, not the platform (D048, D063). Chess is
-- registered here as exactly 2 and is the only game type. Nothing in this schema
-- caps a table or a game at 2: a game type seating 2-4 is a new game_types row,
-- not a schema change. No such type is registered (D044, D063).
--
-- Exact-set identity. participant_set is the canonical form of a table's
-- participants -- each participant written as KIND:ref, sorted, comma-joined --
-- and it is unique per game type, so the same people asking twice find the same
-- table, and a different set is a different table and therefore a different
-- series. Like the group rules in V3, two things here cannot be column
-- constraints, because each needs rows another table owns, and TableRepository
-- enforces them inside the transaction that inserts the table:
--
--   * participant_set agrees with the table's table_participants rows;
--   * the number of participants lies within the game type's range.
--
-- Participant kind. Every participant today is a user. The kind column exists
-- because D051 requires a participant to be discriminated by kind rather than
-- always be a users row; M19.7 widens the allowed kinds. Until then the check
-- admits USER only, and a USER participant always names a user.
--
-- Forward-only, and nothing is lost. Every existing pair gets one CHESS table
-- with the lower id in seat 0 (the order the pair was already stored in); every
-- series points at its pair's table; every game's White and Black become seats 0
-- and 1. Series ids, game ids, moves, and audit events are untouched.
--
-- One active series per pair (D011) is still the rule until M19.4 removes it.
-- The partial unique index that enforced it on the pair columns is rebuilt on
-- table_id, which for a chess table is the same pair.

-- Game types -------------------------------------------------------------

create table game_types (
    id               text    primary key,
    min_participants integer not null,
    max_participants integer not null,

    constraint game_types_id_format check (id ~ '^[A-Z][A-Z0-9_]*$'),
    -- A table always has at least two participants in total (D048); a future
    -- solo mode is one human plus a non-user opponent, which is still two.
    constraint game_types_min_participants check (min_participants >= 2),
    constraint game_types_participant_range check (max_participants >= min_participants)
);

insert into game_types (id, min_participants, max_participants)
values ('CHESS', 2, 2);

-- Tables -----------------------------------------------------------------

create table tables (
    id              uuid        primary key default gen_random_uuid(),
    game_type       text        not null references game_types (id),
    participant_set text        not null,
    created_at      timestamptz not null default now(),

    constraint tables_one_per_participant_set unique (game_type, participant_set)
);

create table table_participants (
    table_id   uuid    not null references tables (id),
    seat_index integer not null,
    kind       text    not null default 'USER',
    user_id    uuid    references users (id),

    primary key (table_id, seat_index),
    constraint table_participants_seat_index check (seat_index >= 0),
    constraint table_participants_kind check (kind in ('USER')),
    constraint table_participants_user_ref check ((kind = 'USER') = (user_id is not null)),
    -- Nobody sits at the same table twice.
    constraint table_participants_one_seat_per_user unique (table_id, user_id)
);

create index table_participants_user_id on table_participants (user_id);

-- Game participants ------------------------------------------------------

create table game_participants (
    game_id    uuid    not null references games (id) on delete cascade,
    seat_index integer not null,
    kind       text    not null default 'USER',
    user_id    uuid    references users (id),

    primary key (game_id, seat_index),
    constraint game_participants_seat_index check (seat_index >= 0),
    constraint game_participants_kind check (kind in ('USER')),
    constraint game_participants_user_ref check ((kind = 'USER') = (user_id is not null)),
    -- Replaces games_distinct_players: nobody plays against themselves.
    constraint game_participants_one_seat_per_user unique (game_id, user_id)
);

create index game_participants_user_id on game_participants (user_id);

-- Carrying the pairs over ------------------------------------------------

-- user_a_id < user_b_id is guaranteed by game_series_ordered_pair, and uuid
-- ordering is the ordering TableRepository sorts by, so this is exactly the key
-- the server would compute for the same two people.
insert into tables (game_type, participant_set, created_at)
select 'CHESS',
       'USER:' || user_a_id::text || ',USER:' || user_b_id::text,
       min(created_at)
  from game_series
 group by user_a_id, user_b_id;

alter table game_series
    add column table_id uuid references tables (id);

update game_series s
   set table_id = t.id
  from tables t
 where t.game_type = 'CHESS'
   and t.participant_set = 'USER:' || s.user_a_id::text || ',USER:' || s.user_b_id::text;

insert into table_participants (table_id, seat_index, kind, user_id)
select distinct table_id, 0, 'USER', user_a_id from game_series
union all
select distinct table_id, 1, 'USER', user_b_id from game_series;

insert into game_participants (game_id, seat_index, kind, user_id)
select id, 0, 'USER', white_user_id from games
union all
select id, 1, 'USER', black_user_id from games;

-- Retiring the pair columns ----------------------------------------------

alter table game_series
    alter column table_id set not null;

drop index game_series_one_active_per_pair;

alter table game_series
    drop constraint game_series_ordered_pair,
    drop column user_a_id,
    drop column user_b_id;

create index game_series_table_id on game_series (table_id);

-- At most one ACTIVE series per table (D011, until M19.4).
create unique index game_series_one_active_per_table
    on game_series (table_id)
    where status = 'ACTIVE';

drop index games_white_user_id;
drop index games_black_user_id;

alter table games
    drop constraint games_distinct_players,
    drop column white_user_id,
    drop column black_user_id;
