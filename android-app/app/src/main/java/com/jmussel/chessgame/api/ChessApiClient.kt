package com.jmussel.chessgame.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Where the Chess server lives.
 *
 * The default is the host machine as seen from an Android emulator, which is what a
 * developer running `.\gradlew.bat :server:run` needs. The beta endpoint is configured in
 * `M15.4`.
 */
data class ChessServerConfig(
    val baseUrl: String = EMULATOR_LOOPBACK,
) {
    init {
        require(baseUrl.isNotBlank()) { "The server needs an address" }
    }

    /** [path] against this server, with no doubled slash however the base was written. */
    fun url(path: String): String = "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"

    /**
     * [path] as a WebSocket address on the same server.
     *
     * `https` becomes `wss` and `http` becomes `ws`, so the socket is exactly as protected
     * as the rest of the traffic: a beta or release build reaches an HTTPS server and gets
     * an encrypted socket, and only a development build can have either in the clear
     * (`D033`).
     */
    fun webSocketUrl(path: String): String =
        when {
            baseUrl.startsWith(HTTPS, ignoreCase = true) -> url(path).replaceFirst(HTTPS, "wss://")
            baseUrl.startsWith(HTTP, ignoreCase = true) -> url(path).replaceFirst(HTTP, "ws://")
            else -> url(path)
        }

    companion object {
        /** `localhost` on the machine running the emulator. */
        const val EMULATOR_LOOPBACK: String = "http://10.0.2.2:8080"

        private const val HTTP = "http://"
        private const val HTTPS = "https://"
    }
}

/**
 * Raised when the Chess server refuses a request.
 *
 * [explanation] is what the server said about it, when it said anything — "That username is
 * taken" is worth showing a player, and the app has no business rewriting it. [message] is
 * for a log; it names the request as well.
 */
class ChessApiException(
    val status: Int,
    val explanation: String,
    override val message: String,
) : RuntimeException(message)

/** A player as the server describes them. */
@Serializable
data class UserSummaryDto(
    val userId: String,
    val username: String,
)

/**
 * The caller, as the server describes them to themselves.
 *
 * [username] is `null` until it is claimed, and that is the whole question the app asks on
 * startup: a returning player goes to the dashboard, a new one to onboarding.
 */
@Serializable
data class CurrentUserDto(
    val userId: String,
    val username: String? = null,
)

/**
 * One line of the dashboard: an active series, the game it is at, and whose move it is.
 *
 * Every field about the game is nullable because a series can exist without one; the app
 * shows what the server actually said rather than inventing a default.
 */
@Serializable
data class DashboardEntryDto(
    val seriesId: String,
    val opponent: UserSummaryDto,
    val gameId: String? = null,
    val version: Long? = null,
    val yourSide: String? = null,
    val sideToMove: String? = null,
    val moveNumber: Int? = null,
    val yourTurn: Boolean = false,
    val closeAfterCurrentGame: Boolean = false,
)

/** A series as the server describes it to one of its two players. */
@Serializable
data class SeriesSummaryDto(
    val seriesId: String,
    val opponent: UserSummaryDto,
    val status: String,
    val closeAfterCurrentGame: Boolean = false,
    val currentGameId: String? = null,
)

/** What a client asks the server to play: intent, never a board state. */
@Serializable
data class MakeMoveRequestDto(
    val expectedVersion: Long,
    val from: String,
    val to: String,
    val promotion: String? = null,
)

/** One move, in the pieces needed to draw it. */
@Serializable
data class MoveDto(
    val from: String,
    val to: String,
    val promotion: String? = null,
)

/**
 * A game as the server tells one of its two players about it.
 *
 * This is the whole of what the game screen draws: the server owns the position, whose move
 * it is, and how it ended (`D004`). Everything needed is here, so a screen rebuilt after the
 * process was recreated shows the same thing as one opened from the dashboard.
 */
@Serializable
data class GameViewDto(
    val gameId: String,
    val seriesId: String,
    val opponent: UserSummaryDto,
    val version: Long,
    val yourSide: String,
    val sideToMove: String,
    val yourTurn: Boolean = false,
    val inCheck: Boolean = false,
    /** Eight rows, rank 8 first, FEN-style letters with `.` for an empty square. */
    val board: List<String> = emptyList(),
    val moves: List<String> = emptyList(),
    val lastMove: MoveDto? = null,
    val moveNumber: Int = 1,
    val halfmoveClock: Int = 0,
    val result: String? = null,
    val terminationReason: String? = null,
    val availableDrawClaims: List<String> = emptyList(),
    val canUndo: Boolean = false,
) {
    /** Whether the game is over, which is the server's word and never worked out here. */
    val isOver: Boolean
        get() = result != null
}

/**
 * A command the server refused, and the canonical state it sent back with the refusal.
 *
 * [game] is the game as the server has it right now, so a client that is behind — a retry
 * whose first reply was lost, above all — can correct itself from the refusal instead of
 * asking again (`D021`).
 */
@Serializable
data class CommandRejectionDto(
    val reason: String? = null,
    val message: String = "",
    val game: GameViewDto? = null,
)

/** Raised when the Chess server refuses a command, carrying what it said about it. */
class ChessCommandRefusedException(
    val status: Int,
    val rejection: CommandRejectionDto,
) : RuntimeException(rejection.message) {
    /** The canonical state the refusal carried, when it carried one. */
    val game: GameViewDto?
        get() = rejection.game

    /** `STALE_VERSION`, `ILLEGAL_MOVE`, and so on, as the server named it. */
    val reason: String?
        get() = rejection.reason
}

/** One finished game, as history lists it. */
@Serializable
data class FinishedGameDto(
    val gameId: String,
    val sequenceNumber: Int,
    val yourSide: String,
    val result: String? = null,
    val terminationReason: String? = null,
    val moveNumber: Int = 0,
    val endedAt: String? = null,
)

/** One series a player took part in, with the games in it that are over. */
@Serializable
data class SeriesHistoryDto(
    val seriesId: String,
    val opponent: UserSummaryDto,
    val status: String,
    val closedAt: String? = null,
    val games: List<FinishedGameDto> = emptyList(),
)

/**
 * The app's connection to the Chess server, which is authoritative for everything about a
 * game (`D004`).
 *
 * Every call carries the anonymous session's access token, supplied per request rather
 * than captured, so a refresh between calls is picked up without rebuilding the client
 * (`D006`).
 */
class ChessApiClient(
    private val config: ChessServerConfig,
    private val httpClient: HttpClient,
    private val accessToken: suspend () -> String,
) {
    /** Who the caller is, and whether they have claimed a username yet. */
    suspend fun me(): CurrentUserDto = get("/me")

    /**
     * Claims [username] for the caller, returning the name as the server stored it.
     *
     * A name is picked once and never changed (`docs/PRODUCT.md`), and whether it is
     * available is the database's decision, not the app's (`D007`) — so an invalid or taken
     * name comes back as a refusal carrying the server's explanation.
     */
    suspend fun claimUsername(username: String): String = post("/username", username)

    /** The caller's active series, newest first, as the server orders them. */
    suspend fun dashboard(): List<DashboardEntryDto> = get("/dashboard")

    /** Everyone the caller is friends with (`D009`). */
    suspend fun friends(): List<UserSummaryDto> = get("/friends")

    /**
     * The player with exactly this [username], matched however it is capitalised.
     *
     * There is no search and no partial match: friends are added by knowing the name
     * (`D009`), so a name that belongs to nobody is a refusal rather than an empty list.
     */
    suspend fun lookUpUser(username: String): UserSummaryDto = get("/users/$username")

    /**
     * Becomes friends with [username], which is mutual immediately (`D009`).
     *
     * Returns the name as the server stored it. Adding yourself, adding someone who is
     * already a friend, and adding a name that belongs to nobody are refusals carrying the
     * server's explanation.
     */
    suspend fun addFriend(username: String): String = post("/friends", username)

    /**
     * Stops being friends with [username], and says what that did.
     *
     * The game under way is not cancelled: it finishes and the series closes after it
     * (`D013`), which is what the returned sentence says.
     */
    suspend fun removeFriend(username: String): String = delete("/friends/$username")

    /** The games the caller has finished, in the series they belong to, newest series first. */
    suspend fun history(): List<SeriesHistoryDto> = get("/history")

    /**
     * One game, as the server has it now.
     *
     * The whole screen is drawn from this: the app never keeps a canonical position of its
     * own (`D004`). A game the caller is not playing is a refusal, not an empty board.
     */
    suspend fun game(gameId: String): GameViewDto = get("/games/$gameId")

    /**
     * Plays a move, and returns the game as it stands after it.
     *
     * [expectedVersion] is the version the move was decided against, which is what makes
     * the command unique: a retry of a move that was already applied arrives as a stale
     * one and is refused with the canonical state attached, so the same move cannot be
     * played twice (`D021`).
     */
    suspend fun makeMove(
        gameId: String,
        expectedVersion: Long,
        from: String,
        to: String,
        promotion: String? = null,
    ): GameViewDto =
        command(
            path = "/games/$gameId/moves",
            body =
                MakeMoveRequestDto(
                    expectedVersion = expectedVersion,
                    from = from,
                    to = to,
                    promotion = promotion,
                ),
        )

    /**
     * The active series with [username], opening the existing one or starting it.
     *
     * "Play with this friend" is one action, and which of the two it turns out to be is
     * the server's business, not the app's (`D011`).
     */
    suspend fun openSeries(username: String): SeriesSummaryDto = post("/series", username)

    private suspend inline fun <reified T> get(path: String): T =
        read(path) {
            httpClient.get(config.url(path)) { header(HttpHeaders.Authorization, "Bearer ${accessToken()}") }
        }

    private suspend inline fun <reified T> post(
        path: String,
        body: String,
    ): T =
        read(path) {
            httpClient.post(config.url(path)) {
                header(HttpHeaders.Authorization, "Bearer ${accessToken()}")
                setBody(body)
            }
        }

    /**
     * Sends a command and reads the game it produced.
     *
     * A refusal carries the server's reason and, whenever there is a game to show, the
     * canonical state — so it is raised as [ChessCommandRefusedException] rather than a
     * plain refusal, and the caller can take the attached state and carry on.
     */
    private suspend inline fun <reified B : Any> command(
        path: String,
        body: B,
    ): GameViewDto {
        val response =
            httpClient.post(config.url(path)) {
                header(HttpHeaders.Authorization, "Bearer ${accessToken()}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }

        if (response.status.isSuccess()) return response.body()

        val text = response.bodyAsText()
        val rejection = runCatching { Json.decodeFromString<CommandRejectionDto>(text) }.getOrNull()

        if (rejection != null) throw ChessCommandRefusedException(response.status.value, rejection)

        throw ChessApiException(
            status = response.status.value,
            explanation = text.trim(),
            message = "Chess server refused $path: ${response.status}",
        )
    }

    private suspend inline fun <reified T> delete(path: String): T =
        read(path) {
            httpClient.delete(config.url(path)) { header(HttpHeaders.Authorization, "Bearer ${accessToken()}") }
        }

    private suspend inline fun <reified T> read(
        path: String,
        request: () -> HttpResponse,
    ): T {
        val response = request()

        if (!response.status.isSuccess()) {
            val explanation = response.bodyAsText().trim()

            throw ChessApiException(
                status = response.status.value,
                explanation = explanation,
                message = "Chess server refused $path: ${response.status}",
            )
        }

        return response.body()
    }

    companion object {
        /** Lenient so a newer server can add fields without breaking an older app. */
        val Json: Json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }

        /** An [HttpClient] configured for the Chess server's JSON API. */
        fun defaultHttpClient(): HttpClient =
            HttpClient {
                install(ContentNegotiation) { json(Json) }
            }
    }
}
