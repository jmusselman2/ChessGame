package com.jmussel.chessgame.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
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

    companion object {
        /** `localhost` on the machine running the emulator. */
        const val EMULATOR_LOOPBACK: String = "http://10.0.2.2:8080"
    }
}

/** Raised when the Chess server refuses a request. */
class ChessApiException(
    val status: Int,
    override val message: String,
) : RuntimeException(message)

/** A player as the server describes them. */
@Serializable
data class UserSummaryDto(
    val userId: String,
    val username: String,
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
    /** The caller's active series, newest first, as the server orders them. */
    suspend fun dashboard(): List<DashboardEntryDto> = get("/dashboard")

    /** Everyone the caller is friends with (`D009`). */
    suspend fun friends(): List<UserSummaryDto> = get("/friends")

    /** The games the caller has finished, in the series they belong to, newest series first. */
    suspend fun history(): List<SeriesHistoryDto> = get("/history")

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

    private suspend inline fun <reified T> read(
        path: String,
        request: () -> HttpResponse,
    ): T {
        val response = request()

        if (!response.status.isSuccess()) {
            throw ChessApiException(response.status.value, "Chess server refused $path: ${response.status}")
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
