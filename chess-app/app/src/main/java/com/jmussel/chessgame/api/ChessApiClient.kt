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
 * developer running `.\gradlew.bat :server:run` needs. A beta build is given the deployed
 * HTTPS endpoint through build configuration instead (`M15.4`).
 *
 * [allowCleartext] is the build's cleartext allowance, and it exists so a misconfigured
 * beta fails immediately and legibly. Everything this app sends carries a session token, so
 * only a debug build may use plain HTTP, and only to a development server (`D033`). A
 * release build whose address is cleartext would otherwise install, launch, and fail every
 * request for a reason nothing on screen explains — which is exactly what forgetting
 * `-PchessServerUrl` would produce.
 */
data class ChessServerConfig(
    val baseUrl: String = EMULATOR_LOOPBACK,
    val allowCleartext: Boolean = true,
) {
    init {
        require(baseUrl.isNotBlank()) { "The server needs an address" }
        require(allowCleartext || isSecure) {
            "This build may not use a cleartext server address, but was given \"$baseUrl\". " +
                "Build the beta with -PchessServerUrl=https://… (see docs/DEVELOPMENT.md); plain HTTP is " +
                "for a debug build against a development server only."
        }
    }

    /** Whether traffic to this server is encrypted, which a non-debug build requires. */
    val isSecure: Boolean
        get() = baseUrl.startsWith(HTTPS, ignoreCase = true)

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
            isSecure -> url(path).replaceFirst(HTTPS, "wss://")
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
 * [explanation] is what the server said about it, when it said anything — "A username can
 * be at most 24 characters" is worth showing a player, and the app has no business rewriting
 * it. [message] is for a log; it names the request as well.
 */
class ChessApiException(
    val status: Int,
    val explanation: String,
    override val message: String,
) : RuntimeException(message)

/** One person on the "All users" page, and whether the caller is already their friend (`D071`). */
@Serializable
data class ListedUserDto(
    val userId: String,
    val username: String,
    val friend: Boolean,
)

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
    /**
     * The requests pending in the game other than a move, undo request first and then draw offer
     * (`D091`, `D097`). Required, with no default: a server that leaves it out is incompatible,
     * not one with nothing pending. One waiting on the player lists the game as theirs to act
     * on; [yourTurn] still means only "your move".
     */
    val pendingRequests: List<PendingRequestDto>,
) {
    /** Whether the game needs this player: their move, or a request for them to answer. */
    val waitingOnYou: Boolean
        get() = yourTurn || pendingRequests.any { it.waitingOnYou }
}

/** A request pending in a game, as a dashboard line names it (`D091`, `D097`). */
@Serializable
data class PendingRequestDto(
    /** [UNDO] for an undo request, [DRAW] for a draw offer. */
    val kind: String,
    /** `true` when the player must answer it, `false` when they sent it. */
    val waitingOnYou: Boolean,
) {
    companion object {
        const val UNDO: String = "UNDO"
        const val DRAW: String = "DRAW"
    }
}

/** A draw offer pending in a game (`D097`): only who made it. */
@Serializable
data class DrawOfferDto(
    val fromYou: Boolean,
)

/** An undo request pending in a game (`D091`): who sent it, and the two moves it would undo. */
@Serializable
data class UndoRequestDto(
    val fromYou: Boolean,
    val requesterMove: MoveDto,
    val replyMove: MoveDto,
)

/**
 * What the player is told about the last undo request until the game next changes (`D091`):
 * `ACCEPTED`, `DECLINED`, `UNDONE_TOO` or `BOTH_UNDONE`, and the two moves it was about.
 */
@Serializable
data class UndoNoteDto(
    val kind: String,
    val requesterMove: MoveDto,
    val replyMove: MoveDto,
)

/**
 * A friend as `/friends` lists them: who they are, and the player's lifetime chess record
 * against them (`D096`). [record] is required, so a server that leaves it out is incompatible.
 */
@Serializable
data class FriendDto(
    val userId: String,
    val username: String,
    val record: RecordDto,
) {
    /** The friend as every other screen names a person. */
    val user: UserSummaryDto
        get() = UserSummaryDto(userId = userId, username = username)
}

/** A lifetime chess record from the player's side (`D096`), counted by the server. */
@Serializable
data class RecordDto(
    val wins: Int,
    val losses: Int,
    val draws: Int,
)

/**
 * Another player's profile, as the server lets this player see it (`D099`): name, the UTC
 * month and year they joined, the player's record against them and the groups the two share.
 */
@Serializable
data class ProfileDto(
    val userId: String,
    val username: String,
    val joinedYear: Int,
    val joinedMonth: Int,
    val record: RecordDto,
    val sharedGroups: List<SharedGroupDto>,
)

/** A group the player and a profile's subject are both in. */
@Serializable
data class SharedGroupDto(
    val groupId: String,
    val name: String,
)

/**
 * A finished game, ply by ply, as the server projects it for review (`D100`, `D101`): each
 * move with the position it was played from, and the final position and result. Nothing here
 * is replayed on the device.
 */
@Serializable
data class CompletedGameReviewDto(
    val gameId: String,
    val version: Long,
    val plies: List<ReviewPlyDto>,
    val finalPosition: ReviewPositionDto,
    val result: String,
    val terminationReason: String,
)

/** One ply of a review: the move, and the position it was played from. */
@Serializable
data class ReviewPlyDto(
    val move: MoveDto,
    val positionBefore: ReviewPositionDto,
)

/** A reviewed position: the board rows, whose move it is, and whether they are in check. */
@Serializable
data class ReviewPositionDto(
    val board: List<String>,
    val sideToMove: String,
    val inCheck: Boolean,
)

/** A group the caller is in, as the server lists it (`D049`, `D076`). */
@Serializable
data class GroupSummaryDto(
    val groupId: String,
    val name: String,
    val createdBy: String,
    val memberCount: Int,
)

/** A series as the server describes it to one of its two players. */
@Serializable
data class SeriesSummaryDto(
    val seriesId: String,
    val opponent: UserSummaryDto,
    val status: String,
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

/** A command that only needs the version it was decided against. */
@Serializable
data class VersionedRequestDto(
    val expectedVersion: Long,
)

/** A draw claim: the version it was decided against, and which rule is being claimed. */
@Serializable
data class ClaimDrawRequestDto(
    val expectedVersion: Long,
    val claim: String,
)

/** One move, in the pieces needed to draw it. */
@Serializable
data class MoveDto(
    val from: String,
    val to: String,
    val promotion: String? = null,
    /**
     * The move in standard algebraic notation, from the server (`D101`). Required: a move the
     * server sends without it is an incompatible response, never shown as coordinates.
     */
    val san: String,
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
    /** The active move history, oldest first, each with its coordinates and SAN (`D101`). */
    val moves: List<MoveDto> = emptyList(),
    val lastMove: MoveDto? = null,
    val moveNumber: Int = 1,
    val halfmoveClock: Int = 0,
    val result: String? = null,
    val terminationReason: String? = null,
    val availableDrawClaims: List<String> = emptyList(),
    val canUndo: Boolean = false,
    /** `false` once a player has left the series, which ended this game too (`D052`, `D095`). */
    val seriesActive: Boolean = true,
    /**
     * Whether a move has ever been accepted in this game, even if every move has since been
     * undone (`D095`). Leaving the series resigns a started game and deletes one that has not.
     */
    val hasStarted: Boolean = false,
    /** Whether the player may send an undo request now (`D091`). */
    val canRequestUndo: Boolean = false,
    /** Whether the player may Redo now (`D092`); the server keeps the path itself. */
    val canRedo: Boolean = false,
    /** The undo request pending in this game, whichever player sent it. */
    val undoRequest: UndoRequestDto? = null,
    /** What the player is told about the last undo request. */
    val undoNote: UndoNoteDto? = null,
    /** Whether the player may offer a draw now (`D097`). */
    val canOfferDraw: Boolean = false,
    /** The draw offer pending in this game, whichever player made it. */
    val drawOffer: DrawOfferDto? = null,
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
     * Claims [username] for this installation, returning the name as the server stored it.
     *
     * A name nobody has becomes the caller's. A name somebody has makes this installation
     * that user (`D082`), so the caller's user id may change: ask [me] afterwards. A name is
     * picked once and never changed (`docs/PRODUCT.md`), and whether it is allowed is the
     * server's decision, not the app's (`D007`) — so an invalid name, or a different name
     * for an installation that already has one, comes back as a refusal carrying the
     * server's explanation.
     */
    suspend fun claimUsername(username: String): String = post("/username", username)

    /** The caller's active series, newest first, as the server orders them. */
    suspend fun dashboard(): List<DashboardEntryDto> = get("/dashboard")

    /** Everyone the caller is friends with (`D009`). */
    suspend fun friends(): List<FriendDto> = get("/friends")

    /** The finished game [gameId], ply by ply, for review (`D100`). Refused for a game in progress. */
    suspend fun review(gameId: String): CompletedGameReviewDto = get("/games/$gameId/review")

    /**
     * [userId]'s profile, by immutable id (`D099`). A `404` means the two are not friends or in a
     * group together now; it is raised like any refusal.
     */
    suspend fun profile(userId: String): ProfileDto = get("/profiles/$userId")

    /**
     * The player with exactly this [username], matched however it is capitalised.
     *
     * There is no search and no partial match: friends are added by knowing the name
     * (`D009`), so a name that belongs to nobody is a refusal rather than an empty list.
     */
    suspend fun lookUpUser(username: String): UserSummaryDto = get("/users/$username")

    /**
     * Every user but the caller, for the "All users" testing aid (`D071`): people the caller
     * could add first, then their friends, each most recently seen first. At most 200.
     */
    suspend fun allUsers(): List<ListedUserDto> = get("/users")

    /**
     * Becomes friends with [username], which is mutual immediately (`D009`).
     *
     * Returns the name as the server stored it. Adding yourself, adding someone who is
     * already a friend, and adding a name that belongs to nobody are refusals carrying the
     * server's explanation.
     */
    suspend fun addFriend(username: String): String = post("/friends", username)

    /**
     * Stops being friends with [username], and returns the server's sentence about it.
     *
     * Only the friends list changes: every series and game with them carries on (`D053`).
     */
    suspend fun removeFriend(username: String): String = delete("/friends/$username")

    /** The groups the caller is in, oldest first (`D076`). */
    suspend fun groups(): List<GroupSummaryDto> = get(GROUPS)

    /**
     * Creates a group called [name] with the caller as its one member, and returns it.
     *
     * A name the server will not take is a refusal carrying its explanation.
     */
    suspend fun createGroup(name: String): GroupSummaryDto = post(GROUPS, name)

    /**
     * Everyone in [groupId] now, the caller included, oldest member first.
     *
     * A group the caller is not in answers as though it did not exist (`M19.2`).
     */
    suspend fun groupMembers(groupId: String): List<UserSummaryDto> = get("$GROUPS/$groupId/members")

    /**
     * Adds [username], one of the caller's own friends, to [groupId], and returns the name
     * as the server stored it. Membership is immediate (`D049`). Someone who is not a friend
     * is a refusal that says so.
     */
    suspend fun addGroupMember(
        groupId: String,
        username: String,
    ): String = post("$GROUPS/$groupId/members", username)

    /** Leaves [groupId], and returns the server's sentence about it. No game is touched (`D049`). */
    suspend fun leaveGroup(groupId: String): String = delete("$GROUPS/$groupId/members/me")

    /**
     * Takes back the caller's latest move, and returns the game as it stands after it.
     *
     * Whether there is anything to take back is the server's answer (`D016`); the version
     * makes the command unique in the same way a move's does, so a retry after the undo
     * landed is refused as stale rather than taking back a second move.
     */
    suspend fun undoMove(
        gameId: String,
        expectedVersion: Long,
    ): GameViewDto = command("/games/$gameId/undo", VersionedRequestDto(expectedVersion))

    /**
     * Restores the operation the caller's latest Undo took back (`D092`), and returns the game
     * as it stands after it. The version makes it unique, so a retry after it landed is
     * refused as stale rather than restoring a second operation.
     */
    suspend fun redo(
        gameId: String,
        expectedVersion: Long,
    ): GameViewDto = command("/games/$gameId/redo", VersionedRequestDto(expectedVersion))

    /**
     * Asks the opponent to undo the caller's move and their reply to it (`D091`), and returns
     * the game as it stands after the request.
     */
    suspend fun requestUndo(
        gameId: String,
        expectedVersion: Long,
    ): GameViewDto = command("/games/$gameId/undo-request", VersionedRequestDto(expectedVersion))

    /** Accepts the undo request waiting on the caller: both moves are undone (`D091`). */
    suspend fun acceptUndo(
        gameId: String,
        expectedVersion: Long,
    ): GameViewDto = command("/games/$gameId/undo-request/accept", VersionedRequestDto(expectedVersion))

    /** Declines the undo request waiting on the caller (`D091`). */
    suspend fun declineUndo(
        gameId: String,
        expectedVersion: Long,
    ): GameViewDto = command("/games/$gameId/undo-request/decline", VersionedRequestDto(expectedVersion))

    /** Offers the opponent a draw (`D097`); whose turn it is does not matter. */
    suspend fun offerDraw(
        gameId: String,
        expectedVersion: Long,
    ): GameViewDto = command("/games/$gameId/draw-offer", VersionedRequestDto(expectedVersion))

    /** Accepts the draw offer waiting on the caller: the game ends as a draw by agreement (`D097`). */
    suspend fun acceptDraw(
        gameId: String,
        expectedVersion: Long,
    ): GameViewDto = command("/games/$gameId/draw-offer/accept", VersionedRequestDto(expectedVersion))

    /** Declines the draw offer waiting on the caller (`D097`). */
    suspend fun declineDraw(
        gameId: String,
        expectedVersion: Long,
    ): GameViewDto = command("/games/$gameId/draw-offer/decline", VersionedRequestDto(expectedVersion))

    /**
     * Claims the draw [claim] allows, and returns the game as it stands after it.
     *
     * Whether the position allows it is the server's answer (`D019`); the app offers only
     * what the canonical state listed and never decides that a game is drawn.
     */
    suspend fun claimDraw(
        gameId: String,
        expectedVersion: Long,
        claim: String,
    ): GameViewDto = command("/games/$gameId/draw-claims", ClaimDrawRequestDto(expectedVersion, claim))

    /**
     * Gives up the game, and returns it as it stands after that.
     *
     * Final once accepted (`D018`), which is why the screen asks first. The version makes
     * the command unique, so a retry after the resignation landed is refused as a finished
     * game rather than resigning twice.
     */
    suspend fun resign(
        gameId: String,
        expectedVersion: Long,
    ): GameViewDto = command("/games/$gameId/resignation", VersionedRequestDto(expectedVersion))

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
     * "Play with this friend": the pair's one series (`D095`), opened if they have it and started,
     * with its first game, if they do not.
     */
    suspend fun openSeries(username: String): SeriesSummaryDto = post(SERIES, username)

    /**
     * Leaves [seriesId], which ends it and its current game for both players (`D095`), and
     * returns it as it now is. A game a move was played in is the leaver's resignation; a game
     * nobody moved in is deleted, and the series then names no current game. Leaving a series
     * already ended is not refused.
     */
    suspend fun leaveSeries(seriesId: String): SeriesSummaryDto = post("$SERIES/$seriesId/leave", "")

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
        private const val SERIES = "/series"
        private const val GROUPS = "/groups"

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
