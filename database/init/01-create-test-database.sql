-- Runs once, when the local PostgreSQL data volume is first created.
--
-- The test database is separate from the development one so integration tests
-- can be reset without touching whatever a developer is doing by hand.
CREATE DATABASE chessgame_test OWNER chessgame;
