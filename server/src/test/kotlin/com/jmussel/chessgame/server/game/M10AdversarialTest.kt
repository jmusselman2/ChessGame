@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.user.Username
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/** Independent M10 probe for atomic canonical reads during a concurrent command. */
class M10AdversarialTest {
    @Test
    fun aRefreshCannotMixAnOldGameRowWithNewMoveHistory() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            val white = users.resolveBySubject("m10-white").id
            val black = users.resolveBySubject("m10-black").id
            users.claimUsername(white, Username.of("White"))
            users.claimUsername(black, Username.of("Black"))

            val series = GameSeriesRepository(database).openOrCreate(white, black).series
            val games = GameRepository(database)
            val initial = ChessGame.newGame()
            val gameId =
                games.create(
                    seriesId = series.id,
                    sequenceNumber = 1,
                    whiteUserId = white,
                    blackUserId = black,
                    game = initial,
                )
            val played = ChessRules.applyMove(initial, Move(Square.parse("e2"), Square.parse("e4")))

            val historyReadReached = CountDownLatch(1)
            val releaseHistoryRead = CountDownLatch(1)
            val pausedDatabase =
                Databases.connect(PausingHistoryDataSource(dataSource, historyReadReached, releaseHistoryRead))
            val reader = GameRepository(pausedDatabase)
            val pool = Executors.newSingleThreadExecutor()

            try {
                val reading = pool.submit(Callable { reader.load(gameId) })
                assertTrue(historyReadReached.await(10, TimeUnit.SECONDS), "refresh never reached its history read")

                games.save(gameId, expectedVersion = 0, game = played)
                releaseHistoryRead.countDown()

                val refreshed = assertNotNull(reading.get(10, TimeUnit.SECONDS))
                assertEquals(1, refreshed.version, "a refresh must not return the row from before the committed move")
                assertEquals(played.state, refreshed.game.state)
                assertEquals(played.history, refreshed.game.history)
            } finally {
                releaseHistoryRead.countDown()
                pool.shutdownNow()
            }
        }
    }

    /** Pauses only the evaluator reader's first `moves` query, after its game-row query. */
    private class PausingHistoryDataSource(
        private val delegate: DataSource,
        private val reached: CountDownLatch,
        private val release: CountDownLatch,
    ) : DataSource by delegate {
        private val paused = AtomicBoolean()

        override fun getConnection(): Connection = wrap(delegate.connection)

        override fun getConnection(
            username: String,
            password: String,
        ): Connection = wrap(delegate.getConnection(username, password))

        private fun wrap(connection: Connection): Connection =
            Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
            ) { _, method, arguments ->
                val args = arguments.orEmpty()
                val result = invoke(connection, method, args)
                if (
                    method.name == "prepareStatement" &&
                    args.firstOrNull() is String &&
                    (args.first() as String).uppercase().contains("FROM MOVES")
                ) {
                    wrap(result as PreparedStatement)
                } else {
                    result
                }
            } as Connection

        private fun wrap(statement: PreparedStatement): PreparedStatement =
            Proxy.newProxyInstance(
                PreparedStatement::class.java.classLoader,
                arrayOf(PreparedStatement::class.java),
            ) { _, method, arguments ->
                if (method.name == "executeQuery" && paused.compareAndSet(false, true)) {
                    reached.countDown()
                    assertTrue(release.await(10, TimeUnit.SECONDS), "evaluator did not release the history read")
                }
                invoke(statement, method, arguments.orEmpty())
            } as PreparedStatement

        private fun invoke(
            target: Any,
            method: java.lang.reflect.Method,
            arguments: Array<out Any?>,
        ): Any? =
            try {
                method.invoke(target, *arguments)
            } catch (failure: InvocationTargetException) {
                throw failure.targetException
            }
    }
}
