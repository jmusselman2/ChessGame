-- Engagement timestamps on users (M19.11).
--
-- Three timestamps that answer three different questions, none of which the
-- other two can answer:
--
--   created_at     when this account first existed.
--   last_seen_at   the throttled "recently around" marker (D010). Written at
--                  most once every five minutes per user from ANY authenticated
--                  request, so it is accurate to within the throttle and is
--                  deliberately not exact. It answers "is this person active
--                  these days", which is what a social feature needs, and it is
--                  explicitly not a heartbeat.
--   last_login_at  when a session last started -- the app restoring or creating
--                  its anonymous session and asking who it belongs to (GET /me).
--                  Unthrottled and exact, because a session start is a discrete,
--                  infrequent event and an approximate one would be useless.
--   last_action_at when this user last had a command ACCEPTED -- a move, an
--                  undo, a resignation, a claimed draw. Unthrottled and exact,
--                  and written in the same transaction as the command, so it can
--                  never claim an action the database did not take.
--
-- Why not one column: last_seen_at cannot distinguish opening the app from
-- playing a move, and it rounds both to five minutes. "Came back but has not
-- played" and "played" are the two things engagement questions actually turn on,
-- and they are the two things it cannot tell apart.
--
-- Nullable with no backfill. An account that has not logged in or acted since
-- this migration has no answer, and null is the honest one; a default of now()
-- or of created_at would invent activity that did not happen.
--
-- Neither column is exposed through the API (ARCHITECTURE.md 14). No response
-- shape changes.

alter table users
    add column last_login_at timestamptz;

alter table users
    add column last_action_at timestamptz;
