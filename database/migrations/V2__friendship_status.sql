-- Friendship status (D047).
--
-- A friendship becomes usable the moment it is made and there is no accept step
-- (D009), so every row MVP code writes is 'ACTIVE'. The column exists now so
-- that adding an approval flow later is additive (new values and new
-- transition logic) rather than a migration plus a retrofit of every read.
--
-- 'PENDING' and 'DECLINED' are reserved for that future flow and are
-- never written today. Removal keeps its own column: removed_at records that a
-- friendship ended and preserves the history D013 requires, which is a
-- different fact from whether the friendship was ever approved.

alter table friendships
    add column status text not null default 'ACTIVE';

alter table friendships
    add constraint friendships_status check (status in ('ACTIVE', 'PENDING', 'DECLINED'));
