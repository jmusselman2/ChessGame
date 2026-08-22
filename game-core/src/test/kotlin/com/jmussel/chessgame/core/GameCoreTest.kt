package com.jmussel.chessgame.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GameCoreTest {
    @Test
    fun exposesProjectName() {
        assertEquals("ChessGame", GameCore.NAME)
    }
}
