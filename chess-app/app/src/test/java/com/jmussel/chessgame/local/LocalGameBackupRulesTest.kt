package com.jmussel.chessgame.local

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Local games stay on this installation (`D084`): the local-game database is excluded from
 * cloud backup and from device transfer, in the rules for every Android version.
 *
 * Android 11 and lower read `backup_rules.xml`; Android 12 and later read
 * `data_extraction_rules.xml`, which has a section for each. A missing exclusion in any of
 * the three would quietly copy local games somewhere else.
 */
class LocalGameBackupRulesTest {
    private val databaseFiles =
        listOf("", "-journal", "-wal", "-shm").map { LOCAL_GAME_DATABASE_NAME + it }.toSet()

    /** Unit tests run in the module directory. */
    private fun rules(name: String): Element =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/$name"))
            .documentElement

    private fun Element.excludedDatabaseFiles(): Set<String> {
        val excludes = getElementsByTagName("exclude")
        return (0 until excludes.length)
            .map { excludes.item(it) as Element }
            .filter { it.getAttribute("domain") == "database" }
            .map { it.getAttribute("path") }
            .toSet()
    }

    private fun Element.section(tag: String): Element = getElementsByTagName(tag).item(0) as Element

    @Test
    fun autoBackupBeforeAndroid12ExcludesTheDatabase() {
        assertEquals(databaseFiles, rules("backup_rules.xml").excludedDatabaseFiles())
    }

    @Test
    fun cloudBackupExcludesTheDatabase() {
        assertEquals(databaseFiles, rules("data_extraction_rules.xml").section("cloud-backup").excludedDatabaseFiles())
    }

    @Test
    fun deviceTransferExcludesTheDatabase() {
        assertEquals(databaseFiles, rules("data_extraction_rules.xml").section("device-transfer").excludedDatabaseFiles())
    }
}
