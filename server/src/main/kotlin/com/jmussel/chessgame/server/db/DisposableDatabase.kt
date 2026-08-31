package com.jmussel.chessgame.server.db

import java.net.URI
import java.net.URISyntaxException

/** Raised when a destructive operation is aimed at a database that is not disposable. */
class NotADisposableDatabaseException(
    override val message: String,
) : RuntimeException(message)

/**
 * Which databases may be destroyed and re-created.
 *
 * `Migrations.reset` drops everything in the schema, and the server's test support calls
 * it on every run against whatever `TEST_DATABASE_URL` names. Since `D035` the beta shares
 * the `ChessGame Dev` Supabase project, so one mistaken environment variable would destroy
 * beta data that the Supabase Free plan cannot restore. This is the check that stands
 * between the two.
 *
 * The rule is deliberately narrow and positive: only a database on this machine's loopback
 * address counts as disposable — that is the `compose.yaml` container developers use and
 * the service container CI runs. Anything else is refused, including anything unparseable,
 * so a URL this cannot read fails closed rather than open.
 *
 * Pure and free of I/O, so every case is tested without a database.
 */
object DisposableDatabase {
    /**
     * The escape hatch, for the rare case where destroying a non-loopback database really
     * is wanted. It has to be set to [OVERRIDE_VALUE] exactly, so no truthy value anyone
     * already exports can switch it on by accident.
     */
    const val OVERRIDE_VARIABLE: String = "CHESSGAME_ALLOW_DESTRUCTIVE_RESET"

    /** The one value [OVERRIDE_VARIABLE] is accepted with. */
    const val OVERRIDE_VALUE: String = "i-know-this-destroys-data"

    /** The hosts a disposable database is allowed to live on. */
    private val DISPOSABLE_HOSTS = setOf("localhost", "127.0.0.1", "::1")

    /** Whether [jdbcUrl] names a database that may be dropped. */
    fun isDisposable(jdbcUrl: String): Boolean = hostOf(jdbcUrl) in DISPOSABLE_HOSTS

    /**
     * Passes when [jdbcUrl] may be destroyed, and throws
     * [NotADisposableDatabaseException] naming the host when it may not.
     *
     * [override] is read from the environment by default; a caller supplies it only in a
     * test.
     */
    fun requireDisposable(
        jdbcUrl: String,
        override: String? = System.getenv(OVERRIDE_VARIABLE),
    ) {
        if (isDisposable(jdbcUrl)) return
        if (override == OVERRIDE_VALUE) return

        val host = hostOf(jdbcUrl) ?: "an address this could not read"
        throw NotADisposableDatabaseException(
            "Refusing to destroy the database at $host: only a loopback database is disposable " +
                "(${DISPOSABLE_HOSTS.joinToString()}). Point TEST_DATABASE_URL at the local " +
                "compose.yaml container. If destroying this database really is intended, set " +
                "$OVERRIDE_VARIABLE=$OVERRIDE_VALUE.",
        )
    }

    /**
     * The host in [jdbcUrl], or `null` when there is not one to read.
     *
     * Accepts both the JDBC form the driver reports — `jdbc:postgresql://host:port/db` —
     * and the plain `postgresql://` form `.env.example` uses.
     */
    private fun hostOf(jdbcUrl: String): String? =
        try {
            URI(jdbcUrl.removePrefix("jdbc:")).host?.trim('[', ']')?.lowercase()
        } catch (_: URISyntaxException) {
            null
        }
}
