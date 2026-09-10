-- Groups: standing invite-eligibility pools (D049, M19.2).
--
-- A group's only function is invite-eligibility. Members of a group may be
-- invited to each other's tables (D048) even when they are not friends with one
-- another. A group has no presence in game state, series identity, the
-- dashboard, or any game rule, and nothing here references games or series.
--
-- Membership mirrors friendship (D009): any member may create a group they
-- belong to, any member may add one of their own friends, membership takes
-- effect immediately with no accept step, and any member may leave
-- unilaterally. Leaving revokes future eligibility only; it touches no table,
-- series, or game.
--
-- Two of those rules cannot be a column constraint and are enforced by
-- GroupRepository inside one transaction, because each needs a row this table
-- cannot see:
--
--   * "member-of to create" -- the creator becomes the first member in the same
--     transaction that inserts the group, so a group with no members never
--     exists to be observed. A check constraint cannot read the other table,
--     and a deferred trigger would enforce the same invariant less legibly.
--   * "the added user is a friend of the adder" -- needs `friendships`, which is
--     also the table that can change the answer a moment later. It is checked
--     where the friendship is read (D046 makes the same argument for series
--     creation: the invite gate is the check, not the schema).
--
-- What the schema does enforce is everything structural: one membership row per
-- person per group, real users, a named group, and a creator that exists.

create table groups (
    id         uuid        primary key default gen_random_uuid(),
    name       text        not null,
    created_by uuid        not null references users (id),
    created_at timestamptz not null default now(),

    constraint groups_name_length check (char_length(btrim(name)) between 1 and 48),
    constraint groups_name_trimmed check (name = btrim(name))
);

create index groups_created_by on groups (created_by);

-- One row per person per group, so leaving and being added again revives the row
-- rather than accumulating history rows -- the same shape as `friendships`,
-- for the same reason: "are they in it right now" must be one row lookup.
--
-- added_by records who put them there, and may be the member themselves: that is
-- exactly the creator's own first membership.
create table group_members (
    group_id  uuid        not null references groups (id) on delete cascade,
    user_id   uuid        not null references users (id),
    added_by  uuid        not null references users (id),
    joined_at timestamptz not null default now(),
    left_at   timestamptz,

    primary key (group_id, user_id)
);

-- "Which groups is this person in right now", which is half of every
-- invite-eligibility question, so the partial index carries the predicate.
create index group_members_current on group_members (user_id, group_id) where left_at is null;
