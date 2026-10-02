package com.jmussel.chessgame.ui

/**
 * What the app says while a safe read waits through a sleeping server (`D037`).
 *
 * One wording wherever it happens, at startup or on a game screen (`M21.15`), so a cold
 * start reads as the same ordinary wait every time and never as an error.
 */
object ServerWaiting {
    const val TITLE = "Waking the server…"
    const val DETAIL = "The server sleeps when nobody has played for a while. Waking it takes about a minute."
}
