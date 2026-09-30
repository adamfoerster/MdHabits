package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.platform.DesktopAppSettings
import com.adamfoerster.mdhabits.platform.DesktopVaultFileSystem
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopVaultFileSystemTest {
    private val fallback = Files.createTempDirectory("mdhabits-fallback").toFile()
    private val chosen = Files.createTempDirectory("mdhabits-chosen").toFile()
    private val prefs = Preferences.userRoot().node("mdhabits-test-${UUID.randomUUID()}")
    private val settings = DesktopAppSettings(prefs)
    private val fs = DesktopVaultFileSystem(settings, fallback)

    @AfterTest
    fun cleanUp() {
        fallback.deleteRecursively()
        chosen.deleteRecursively()
        prefs.removeNode()
    }

    @Test
    fun writesToFallbackUntilAVaultIsChosen() = runTest {
        fs.write("weeks", "a.md", "hello")
        assertEquals("hello", File(fallback, "weeks/a.md").readText())

        settings.vaultRef = chosen.absolutePath
        fs.write("weeks", "b.md", "world")
        assertEquals("world", File(chosen, "weeks/b.md").readText())
        assertEquals(listOf("b.md"), fs.list("weeks"))
    }

    @Test
    fun writeTruncatesAndDeleteRemoves() = runTest {
        fs.write("d", "n.md", "long content")
        fs.write("d", "n.md", "short")
        assertEquals("short", fs.read("d", "n.md"))
        fs.delete("d", "n.md")
        assertNull(fs.read("d", "n.md"))
    }

    @Test
    fun listOnlyReturnsMarkdownAndMissingFolderIsEmpty() = runTest {
        fs.write("d", "a.md", "x")
        fs.write("d", "b.txt", "x")
        assertEquals(listOf("a.md"), fs.list("d"))
        assertEquals(emptyList(), fs.list("nope"))
    }

    @Test
    fun readInAndListInUseTheGivenFolder() = runTest {
        File(chosen, "Prayer").mkdirs()
        File(chosen, "Prayer/2026.md").writeText("log")
        assertEquals(listOf("2026.md"), fs.listIn(chosen.absolutePath, "Prayer"))
        assertEquals("log", fs.readIn(chosen.absolutePath, "Prayer", "2026.md"))
        assertNull(fs.readIn(chosen.absolutePath, "Prayer", "missing.md"))
    }

    @Test
    fun settingsRoundTripAndClearNullable() {
        settings.languageTag = "pt-BR"
        settings.onboardingComplete = true
        assertEquals("pt-BR", settings.languageTag)
        settings.languageTag = null
        assertNull(settings.languageTag)
        assertEquals(true, settings.onboardingComplete)
    }
}
