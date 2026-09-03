package com.jmussel.chessgame.server.realtime

import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import io.ktor.websocket.close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A game one player started reaching the other player's open connection.
 *
 * "Play with this friend" is the one change to a player's games that the *other* player
 * did nothing to cause, so it is the one they cannot discover by looking at what they
 * asked for. Moves already announce themselves ([GameUpdateBroadcastTest]); without this
 * the opponent's dashboard says they have no game until their next app start, and when the
 * colour toss (`D014`) made them White it is their own move they are not being shown.
 *
 * Like every push, this one carries no state: it names the game, and the client reloads
 * over HTTPS (`D022`).
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class NewGameBroadcastTest {
    private val tokens = TestTokens()

    @Test
    fun theOpponentHearsAboutAGameTheyDidNotStart() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            becomeFriends(jordan, alex)
            val waiting = alex.connect()

            val gameId = requireNotNull(jordan.openSeries("Alex").currentGameId)

            val update = waiting.nextMessage()

            assertEquals(RealtimeMessage.GAME_UPDATED, update.type)
            assertEquals(gameId, update.gameId)
            assertEquals(0, update.version, "a game nobody has moved in yet")

            waiting.close()
        }
    }

    @Test
    fun theGameAnnouncedIsOneTheOpponentCanOpen() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            becomeFriends(jordan, alex)
            val waiting = alex.connect()

            jordan.openSeries("Alex")

            // The point of the message is that acting on it works: the opponent reloads
            // and finds the game, rather than being told about something they cannot see.
            val announced = requireNotNull(waiting.nextMessage().gameId)

            assertEquals(announced, alex.readGame(announced).gameId)
            assertEquals(
                listOf(announced),
                alex.dashboard().mapNotNull { it.gameId },
                "the dashboard the client reloads now lists the new game",
            )

            waiting.close()
        }
    }

    @Test
    fun theStarterIsToldTooSoTheirOtherDevicesKeepUp() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            becomeFriends(jordan, alex)
            val jordansOtherDevice = jordan.connect()

            val gameId = requireNotNull(jordan.openSeries("Alex").currentGameId)

            val update = jordansOtherDevice.nextMessage()

            assertEquals(RealtimeMessage.GAME_UPDATED, update.type)
            assertEquals(gameId, update.gameId)

            jordansOtherDevice.close()
        }
    }

    @Test
    fun openingASeriesThatAlreadyHasAGameAnnouncesNothing() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            becomeFriends(jordan, alex)
            jordan.openSeries("Alex")

            val waiting = alex.connect()

            // Tapping "Play" again opens the game already under way and creates nothing
            // (`M16.4`), so there is no news to carry. Announcing here would be harmless
            // but untrue, and a message per tap is a message per tap.
            jordan.openSeries("Alex")
            assertNull(waiting.nextMessageOrNull())

            // Including when the tap comes from the other side of the same series.
            alex.openSeries("Jordan")
            assertNull(waiting.nextMessageOrNull())

            waiting.close()
        }
    }

    @Test
    fun someoneElsesNewGameIsNotAnnouncedToOnlookers() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            becomeFriends(jordan, alex)
            val onlooker = PlayerClient(this, tokens, "auth-onlooker")
            onlooker.claimUsername("Onlooker")
            val watching = onlooker.connect()

            jordan.openSeries("Alex")

            assertNull(watching.nextMessageOrNull(), "only the two players hear about their game")

            watching.close()
        }
    }

    @Test
    fun theGameIsStartedWhenNobodyIsListening() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            becomeFriends(jordan, alex)

            // Neither player has a socket open. Starting the game still works: realtime
            // delivery is a convenience, not part of opening a series (`D022`).
            val gameId = requireNotNull(jordan.openSeries("Alex").currentGameId)

            assertEquals(gameId, alex.readGame(gameId).gameId)
        }
    }

    /** Both sign in and become friends, stopping short of starting a game. */
    private suspend fun becomeFriends(
        jordan: PlayerClient,
        alex: PlayerClient,
    ) {
        jordan.claimUsername("Jordan")
        alex.claimUsername("Alex")
        jordan.addFriend("Alex")
    }
}
