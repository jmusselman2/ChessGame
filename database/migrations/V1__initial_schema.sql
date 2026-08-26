-- Initial schema for the Chess MVP.
--
-- Shapes here follow docs/ARCHITECTURE.md and the accepted decisions:
--   D007  usernames are globally unique, case-insensitively
--   D011  at most one ACTIVE series per friend pair
--   D012  a series is ACTIVE or CLOSED
--   D013  removing a friend closes the series after the current game
--   D020  canonical current state + active move history + append-only audit
--   D021  every accepted mutation increments the game version
--   D029  an undo restores the position recorded with the move

-- Users -----------------------------------------------------------------

-- auth_subject is the Supabase auth subject; user_id is the immutable internal
-- identity everything else references. A user exists before choosing a
-- username, so username is nullable until it is claimed.
create table users (
    id                  uuid        primary key default gen_random_uuid(),
    auth_subject        text        not null unique,
    username            text,
    username_normalized text        unique,
    last_seen_at        timestamptz,
    created_at          timestamptz not null default now(),

    -- Either both username columns are set or neither is.
    constraint users_username_pair
        check ((username is null) = (username_normalized is null)),
    -- The normalized form is the lowercase one; the database is the final
    -- race-safe authority on uniqueness (D007).
    constraint users_username_normalized_is_lowercase
        check (username_normalized is null or username_normalized = lower(username)),
    constraint users_username_length
        check (username is null or char_length(username) between 3 and 24),
    constraint users_username_characters
        check (username is null or username ~ '^[A-Za-z0-9_-]+$')
);

-- Friendships -----------------------------------------------------------

-- One row per pair, always stored with the lower id first, so a reversed
-- duplicate cannot be written. Friendship is mutual immediately (D009).
-- Removal deactivates the row rather than deleting it, preserving history
-- (D013).
create table friendships (
    user_a_id  uuid        not null references users (id),
    user_b_id  uuid        not null references users (id),
    created_at timestamptz not null default now(),
    removed_at timestamptz,

    primary key (user_a_id, user_b_id),
    constraint friendships_ordered_pair check (user_a_id < user_b_id)
);

-- Game series -----------------------------------------------------------

create table game_series (
    id                        uuid        primary key default gen_random_uuid(),
    user_a_id                 uuid        not null references users (id),
    user_b_id                 uuid        not null references users (id),
    status                    text        not null default 'ACTIVE',
    close_after_current_game  boolean     not null default false,
    current_game_id           uuid,
    created_at                timestamptz not null default now(),
    closed_at                 timestamptz,

    constraint game_series_ordered_pair check (user_a_id < user_b_id),
    constraint game_series_status check (status in ('ACTIVE', 'CLOSED')),
    constraint game_series_closed_at check ((status = 'CLOSED') = (closed_at is not null))
);

-- At most one ACTIVE series per friend pair (D011). Closed series stay for
-- history, so the constraint is partial.
create unique index game_series_one_active_per_pair
    on game_series (user_a_id, user_b_id)
    where status = 'ACTIVE';

-- Games -----------------------------------------------------------------

-- state holds the canonical position (board, side to move, castling rights, en
-- passant target, draw-rule state). version increments on every accepted
-- mutation (D021).
create table games (
    id                 uuid        primary key default gen_random_uuid(),
    series_id          uuid        not null references game_series (id),
    sequence_number    integer     not null,
    white_user_id      uuid        not null references users (id),
    black_user_id      uuid        not null references users (id),
    status             text        not null default 'IN_PROGRESS',
    version            bigint      not null default 0,
    side_to_move       text        not null default 'WHITE',
    state              jsonb       not null,
    result             text,
    termination_reason text,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    ended_at           timestamptz,

    constraint games_distinct_players check (white_user_id <> black_user_id),
    constraint games_sequence_number check (sequence_number >= 1),
    constraint games_version check (version >= 0),
    constraint games_status check (status in ('IN_PROGRESS', 'COMPLETE')),
    constraint games_side_to_move check (side_to_move in ('WHITE', 'BLACK')),
    constraint games_result check (result is null or result in ('WHITE_WINS', 'BLACK_WINS', 'DRAW')),
    -- A finished game has a result and an end time; a running one has neither.
    constraint games_result_matches_status
        check ((status = 'COMPLETE') = (result is not null)),
    constraint games_ended_at check ((status = 'COMPLETE') = (ended_at is not null)),
    constraint games_termination_reason
        check ((result is null) = (termination_reason is null)),

    -- Games within a series are numbered from 1 (D014/D015 rematches).
    constraint games_series_sequence unique (series_id, sequence_number)
);

create index games_series_id on games (series_id);
create index games_white_user_id on games (white_user_id);
create index games_black_user_id on games (black_user_id);

-- The series points at its current game; the game points back at its series.
-- The reference is added after both tables exist.
alter table game_series
    add constraint game_series_current_game_id
    foreign key (current_game_id) references games (id);

-- Moves -----------------------------------------------------------------

-- The active move history. position_before is the complete position the move
-- was played from, which is what makes an undo exact (D029).
create table moves (
    id              uuid        primary key default gen_random_uuid(),
    game_id         uuid        not null references games (id) on delete cascade,
    ply             integer     not null,
    side            text        not null,
    from_square     text        not null,
    to_square       text        not null,
    promotion       text,
    position_before jsonb       not null,
    created_at      timestamptz not null default now(),

    constraint moves_ply check (ply >= 1),
    constraint moves_side check (side in ('WHITE', 'BLACK')),
    constraint moves_from_square check (from_square ~ '^[a-h][1-8]$'),
    constraint moves_to_square check (to_square ~ '^[a-h][1-8]$'),
    constraint moves_squares_differ check (from_square <> to_square),
    -- Promotion is always an explicit choice, never a king or a pawn (D019 UI
    -- rule: no automatic queen).
    constraint moves_promotion check (promotion is null or promotion in ('QUEEN', 'ROOK', 'BISHOP', 'KNIGHT')),
    constraint moves_game_ply unique (game_id, ply)
);

create index moves_game_id on moves (game_id);

-- Game events -----------------------------------------------------------

-- Append-only audit of meaningful changes (D020). Not event sourcing: loading a
-- game never replays these. An event may belong to a game, to a series, or to
-- neither (a friendship change), so both references are optional.
create table game_events (
    id         bigint      generated always as identity primary key,
    game_id    uuid        references games (id) on delete cascade,
    series_id  uuid        references game_series (id),
    actor_id   uuid        references users (id),
    type       text        not null,
    payload    jsonb       not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index game_events_game_id on game_events (game_id, id);
create index game_events_series_id on game_events (series_id, id);
