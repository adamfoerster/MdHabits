package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.core.releasenotes.ReleaseNotes
import com.adamfoerster.mdhabits.core.releasenotes.ReleaseNotesTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseNotesTest {

    private val latest = ReleaseNotes.all.first().version

    private fun tracker(settings: FakeAppSettings, version: String = latest) =
        ReleaseNotesTracker(settings, FakeAppInfo(version = version))

    @Test
    fun everyNoteHasTextInAllThreeLanguagesAndUniqueVersions() {
        assertEquals(ReleaseNotes.all.size, ReleaseNotes.all.map { it.version }.toSet().size)
        ReleaseNotes.all.forEach { note ->
            assertTrue(note.features.isNotEmpty() || note.fixes.isNotEmpty(), "${note.version} is empty")
            (note.features + note.fixes).forEach {
                assertTrue(it.en.isNotBlank() && it.pt.isNotBlank() && it.es.isNotBlank(), note.version)
            }
        }
    }

    @Test
    fun popsUpOnceAfterAnUpdate() {
        val settings = FakeAppSettings().apply {
            onboardingComplete = true
            lastSeenReleaseNotesVersion = "0.0.1"
        }
        val tracker = tracker(settings)

        assertNotNull(tracker.pending())
        tracker.markSeen()

        assertNull(tracker.pending())
        assertEquals(latest, settings.lastSeenReleaseNotesVersion)
    }

    @Test
    fun staysUntilDismissed() {
        val settings = FakeAppSettings().apply { onboardingComplete = true }
        val tracker = tracker(settings)

        assertNotNull(tracker.pending())
        assertNotNull(tracker.pending())
    }

    @Test
    fun freshInstallSkipsThePopup() {
        val settings = FakeAppSettings()

        assertNull(tracker(settings).pending())
        assertEquals(latest, settings.lastSeenReleaseNotesVersion)
    }

    @Test
    fun versionWithoutANoteIsMarkedSeenSilently() {
        val settings = FakeAppSettings().apply { onboardingComplete = true }

        assertNull(tracker(settings, version = "99.0.0").pending())
        assertEquals("99.0.0", settings.lastSeenReleaseNotesVersion)
    }

    @Test
    fun unknownVersionNeverPopsUp() {
        val settings = FakeAppSettings().apply { onboardingComplete = true }

        assertNull(tracker(settings, version = "").pending())
        assertNull(settings.lastSeenReleaseNotesVersion)
    }
}
