package com.jmussel.chessgame.ui.review

/**
 * The review of a finished online game (`D100`, `M22.10`): which game, the review once the server
 * has projected it, or why there is none.
 */
data class OnlineReviewState(
    val gameId: String? = null,
    val review: GameReview? = null,
    /** Why there is no review to show, when there is none. */
    val unavailable: String? = null,
) {
    companion object {
        /** What review says when the server could not project it; coordinates are never shown instead. */
        const val UNAVAILABLE: String = "Review is not available for this game."
    }
}
