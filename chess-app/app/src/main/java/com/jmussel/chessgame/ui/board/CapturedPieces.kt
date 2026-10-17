package com.jmussel.chessgame.ui.board

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Material
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side

/**
 * One captured-piece row beside a live board (`D104`, `M23.1`): the pieces [side] has captured
 * — the other side's pieces, not [side]'s missing ones — and how far ahead [side] is in material.
 *
 * [name] is who plays [side]: "You", the opponent's username or "Computer (Hard)", or `null` in
 * pass-and-play, where the colour alone says who it is. [captured] is already in display order,
 * and [advantageQuarterPoints] is set only on the leading side's row.
 */
data class CapturedRow(
    val side: Side,
    val name: String?,
    val captured: List<Piece>,
    val advantageQuarterPoints: Int?,
) {
    /** `"You · White"`, `"Alex · Black"`, or `"White"` in pass-and-play. */
    val label: String
        get() = name?.let { "$it · ${colourOf(side)}" } ?: colourOf(side)

    /** `"+1.25"` on the leading side's row, otherwise `null`: there is never a `+0`. */
    val advantageText: String?
        get() = advantageQuarterPoints?.let { "+" + CapturedPieces.pointsText(it) }

    /** The captured pieces with repeats counted, in display order: what a narrow row shows. */
    val groups: List<CapturedGroup>
        get() = captured.groupBy { it }.map { (piece, same) -> CapturedGroup(piece, same.size) }

    /** What a screen reader says for the whole row, instead of a glyph at a time. */
    val summary: String
        get() {
            val who = name?.let { "$it, ${colourOf(side)}" } ?: colourOf(side)
            val pieces =
                if (captured.isEmpty()) {
                    NO_CAPTURES
                } else {
                    "Captured " + groups.joinToString(", ") { group -> CapturedPieces.spoken(group) }
                }
            val ahead =
                advantageQuarterPoints?.let { " Ahead by ${CapturedPieces.pointsText(it)} ${if (it == 4) "point" else "points"}." } ?: ""
            return "$who. $pieces.$ahead"
        }

    companion object {
        /** What [summary] says for a player who has captured nothing; never drawn on the row. */
        const val NO_CAPTURES = "No captures"

        /** The name on the viewer's own row, online and against the computer. */
        const val YOU = "You"

        private fun colourOf(side: Side): String = if (side == Side.WHITE) "White" else "Black"
    }
}

/** [count] captured pieces the same as [piece]: shown as one glyph and `×count` when narrow. */
data class CapturedGroup(
    val piece: Piece,
    val count: Int,
) {
    /** `"×2"`, or nothing for a single piece. */
    val countText: String?
        get() = if (count > 1) "×$count" else null
}

/** Both rows of a board: [top] for the side drawn at the top of the board, [bottom] for the other. */
data class CapturedRows(
    val top: CapturedRow,
    val bottom: CapturedRow,
)

/** How a row's pieces fit the width it has: one glyph each, or counted, scaled down only if even that is too wide. */
data class CapturedFit(
    val compact: Boolean,
    val scale: Float,
)

/**
 * Captured pieces and material as the live game screens show them (`D104`).
 *
 * The facts come from `chess-core` locally and from the server online; nothing here works out a
 * capture or a balance on its own. This only sorts, counts, words and fits them.
 */
object CapturedPieces {
    /** The order captured pieces are shown in, most valuable first. */
    val ORDER: List<PieceType> = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN)

    /** A row's height, whether it holds pieces or only its label. */
    val ROW_HEIGHT: Dp = 40.dp

    /** The gap between a row and the board, or between the two rows in a panel. */
    val ROW_GAP: Dp = 4.dp

    /** What both rows take from a one-column screen's height, gaps included. */
    val ROWS_HEIGHT: Dp = (ROW_HEIGHT + ROW_GAP) * 2

    /** A captured piece's glyph, in dp like the board's, so the font scale does not change it. */
    val GLYPH: Dp = 24.dp

    /** How far along each individual glyph starts after the one before: they overlap a little. */
    val GLYPH_STEP: Dp = 17.dp

    /** The width of a `×N` count after a glyph; one digit, since nine is the most of one piece. */
    val COUNT_WIDTH: Dp = 18.dp

    /** The gap between counted groups. */
    val GROUP_GAP: Dp = 6.dp

    /** [pieces] in display order: queens, rooks, bishops, knights, then pawns. */
    fun sorted(pieces: List<Piece>): List<Piece> = pieces.sortedBy { ORDER.indexOf(it.type) }

    /** How far ahead [side] is, or `null` unless it leads; a missing balance shows nothing. */
    fun advantageFor(
        side: Side,
        whiteBalanceQuarterPoints: Int?,
    ): Int? {
        val balance = whiteBalanceQuarterPoints ?: return null
        val forSide = if (side == Side.WHITE) balance else -balance
        return forSide.takeIf { it > 0 }
    }

    /**
     * [quarterPoints] in pawns, exactly: `"9"` for a whole value, otherwise two decimals,
     * `"0.25"`, `"1.50"`, `"9.75"`. Never rounded: material is counted in quarter points.
     */
    fun pointsText(quarterPoints: Int): String {
        val whole = quarterPoints / Material.QUARTER_POINTS_PER_PAWN
        val quarters = quarterPoints % Material.QUARTER_POINTS_PER_PAWN
        return if (quarters == 0) "$whole" else "$whole.${quarters * 25}"
    }

    /** `"queen"`, `"2 pawns"`. */
    fun spoken(group: CapturedGroup): String {
        val name = BoardRendering.nameFor(group.piece.type).lowercase()
        return if (group.count == 1) name else "${group.count} ${name}s"
    }

    /**
     * Both rows, from every capture in the game ([captures], either side's) and White's material
     * minus Black's ([whiteBalanceQuarterPoints], `null` when unknown). [topSide] is the side drawn
     * at the top of the board, and [nameOf] who plays a side.
     */
    fun rows(
        captures: List<Piece>,
        whiteBalanceQuarterPoints: Int?,
        topSide: Side,
        nameOf: (Side) -> String? = { null },
    ): CapturedRows {
        fun row(side: Side) =
            CapturedRow(
                side = side,
                name = nameOf(side),
                captured = sorted(captures.filter { it.side == side.opposite }),
                advantageQuarterPoints = advantageFor(side, whiteBalanceQuarterPoints),
            )

        return CapturedRows(top = row(topSide), bottom = row(topSide.opposite))
    }

    /**
     * The rows for a game on this device, pass-and-play or against the computer: captures from
     * its active recorded moves and the balance from its current position, both through
     * `chess-core` (`D104`).
     */
    fun forGame(
        game: ChessGame,
        topSide: Side,
        nameOf: (Side) -> String? = { null },
    ): CapturedRows =
        rows(
            captures = game.history.mapNotNull(ChessRules::capturedPiece),
            whiteBalanceQuarterPoints = ChessRules.materialBalance(game.state),
            topSide = topSide,
            nameOf = nameOf,
        )

    /** How [row]'s pieces fit [width]: each glyph if they fit, else counted, else counted and smaller. */
    fun fit(
        row: CapturedRow,
        width: Dp,
    ): CapturedFit {
        if (row.captured.isEmpty() || individualWidth(row.captured.size) <= width) return CapturedFit(compact = false, scale = 1f)

        val counted = countedWidth(row.groups)
        return CapturedFit(compact = true, scale = if (counted <= width) 1f else width / counted)
    }

    /** The width [count] individual glyphs take. */
    fun individualWidth(count: Int): Dp = GLYPH_STEP * (count - 1) + GLYPH

    /** The width [groups] take counted: a glyph each, a `×N` after each repeat, and the gaps between. */
    fun countedWidth(groups: List<CapturedGroup>): Dp =
        groups.fold(GROUP_GAP * (groups.size - 1)) { width, group -> width + GLYPH + if (group.count > 1) COUNT_WIDTH else 0.dp }
}
