-- Participants that are not users (D051, M19.7).
--
-- Only real human accounts get rows in users. A participant that is not a person
-- -- the scripted opponent that always takes the final turn, a computer player
-- standing in for a human -- is a distinct participant KIND, not a users row and
-- not a flag on a game. A users row for one would leak it into friends lists,
-- dashboards, last_seen_at, and username uniqueness: everything that treats a
-- users row as a person.
--
-- Kinds, and what the platform knows about each (and nothing more):
--
--   USER       a person; a users row. Rotates in seat order (D050).
--   COMPUTER   not a person, plays a seat the way a person would. Rotates.
--   SCRIPTED   not a person; never rotates and always takes the final turn.
--
-- What a non-user participant does in a game is its game type's rules, which do
-- not exist yet for anything but chess (D044, D063). This only gives it an
-- identity and somewhere to keep its per-instance state across turns and reloads
-- (D051): non_user_participants.state, a jsonb document the platform stores and
-- never interprets.
--
-- A participant row now names exactly one of users.id or
-- non_user_participants.id, chosen by kind, and a non-user seat's kind must be its
-- participant's kind (the composite reference). Each is unique per table and per game,
-- so nobody -- person or not -- takes two seats.
--
-- tables.participant_set already writes each participant as KIND:ref (V5), so a
-- table with a non-user participant is keyed the same way. A non-user participant
-- is one instance, so two tables never share one by accident.

create table non_user_participants (
    id         uuid        primary key default gen_random_uuid(),
    kind       text        not null,
    state      jsonb       not null default '{}'::jsonb,
    created_at timestamptz not null default now(),

    constraint non_user_participants_kind check (kind in ('COMPUTER', 'SCRIPTED')),
    -- The target of the (id, kind) references below, so a seat cannot claim a kind
    -- its participant does not have.
    constraint non_user_participants_id_kind unique (id, kind)
);

alter table table_participants
    drop constraint table_participants_kind,
    drop constraint table_participants_user_ref,
    add column non_user_participant_id uuid,
    add constraint table_participants_non_user foreign key (non_user_participant_id, kind)
        references non_user_participants (id, kind),
    add constraint table_participants_kind check (kind in ('USER', 'COMPUTER', 'SCRIPTED')),
    add constraint table_participants_ref check (
        (kind = 'USER') = (user_id is not null)
        and (kind <> 'USER') = (non_user_participant_id is not null)
    ),
    add constraint table_participants_one_seat_per_non_user unique (table_id, non_user_participant_id);

alter table game_participants
    drop constraint game_participants_kind,
    drop constraint game_participants_user_ref,
    add column non_user_participant_id uuid,
    add constraint game_participants_non_user foreign key (non_user_participant_id, kind)
        references non_user_participants (id, kind),
    add constraint game_participants_kind check (kind in ('USER', 'COMPUTER', 'SCRIPTED')),
    add constraint game_participants_ref check (
        (kind = 'USER') = (user_id is not null)
        and (kind <> 'USER') = (non_user_participant_id is not null)
    ),
    add constraint game_participants_one_seat_per_non_user unique (game_id, non_user_participant_id);
