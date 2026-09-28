-- Many installations can authenticate as one user (M20.4, D082).
--
-- The anonymous Supabase subject is an installation's authentication principal,
-- not the user. users.auth_subject allowed exactly one subject per user, so it
-- moves to its own table, where a subject still maps to exactly one user
-- (the primary key) but a user may have any number of subjects.
--
-- Every existing subject is carried over to the user it already named, with the
-- time that user was created, and then the column goes, so there is one source
-- of truth. No user, and no user's id, changes.

create table user_auth_subjects (
    auth_subject text        primary key,
    user_id      uuid        not null references users (id),
    created_at   timestamptz not null default now()
);

-- A claim that attaches an installation re-points the subjects of the nameless
-- user it replaces, and deleting that user checks this foreign key.
create index user_auth_subjects_user_id on user_auth_subjects (user_id);

insert into user_auth_subjects (auth_subject, user_id, created_at)
select auth_subject, id, created_at
from users;

alter table users drop column auth_subject;
