-- The friends lookup gets its missing index, and two redundant indexes go (M17.12).
--
-- FriendshipRepository.friendsOf matches user_a_id = ? OR user_b_id = ?. The
-- primary key (user_a_id, user_b_id) serves the first half only, so the second
-- was a sequential scan of friendships. This index serves it, and it is also the
-- index the user_b_id foreign key lacked.
--
-- moves_game_id (game_id) and games_series_id (series_id) each repeat the leading
-- column of a unique index, moves_game_ply (game_id, ply) and
-- games_series_sequence (series_id, sequence_number), which serve every lookup
-- they could. Each cost a write on every insert and saved no read.
--
-- Found by the database review of 2026-09-24. No row is changed.

create index friendships_user_b_id on friendships (user_b_id);

drop index moves_game_id;

drop index games_series_id;
