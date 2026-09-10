package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.markdown.MarkdownCodecs
import com.adamfoerster.mdhabits.data.markdown.MarkdownPenaltyRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownPointsLedgerRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownTaskRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownThemeRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownValueRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeekStore
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeeklyReviewRepository
import com.adamfoerster.mdhabits.data.markdown.WeekNote
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.TaskInstance
import com.adamfoerster.mdhabits.domain.model.WeeklyReview
import com.adamfoerster.mdhabits.domain.usecase.ApplyPenaltyUseCase
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.ui.screens.home.HomeViewModel
import com.adamfoerster.mdhabits.ui.screens.review.ReviewViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun event(id: String, weekId: String, delta: Int) = PointsEvent(
    id, Instant.parse("2026-07-02T12:00:00Z"), weekId, PointsSource.TASK, "t-1", "Concluída: Ler", delta,
)

class WeekNoteCodecTest {

    @Test
    fun fullWeekNoteRoundTrips() {
        val note = WeekNote(
            weekId = "2026-W27",
            instances = listOf(
                TaskInstance("t-1", "2026-W27", planned = true, completed = true, completedOn = LocalDate(2026, 7, 1)),
                TaskInstance("t-2", "2026-W27", planned = true),
            ),
            review = WeeklyReview(
                weekId = "2026-W27",
                journal = "Semana boa.\n\nDormi melhor | e li mais.",
                objectiveRatings = mapOf("obj-1" to 4),
                plannedTaskIds = listOf("t-1"),
                submittedAt = Instant.parse("2026-07-05T18:00:00Z"),
            ),
            events = listOf(event("L-1", "2026-W27", 5), event("L-2", "2026-W27", -3)),
        )
        assertEquals(note, MarkdownCodecs.decodeWeekNote(MarkdownCodecs.encodeWeekNote(note)))
    }

    @Test
    fun ledgerOnlyNoteHasNoReview() {
        val note = WeekNote(weekId = "2026-W27", events = listOf(event("L-1", "2026-W27", 5)))
        val decoded = MarkdownCodecs.decodeWeekNote(MarkdownCodecs.encodeWeekNote(note))!!
        assertNull(decoded.review)
        assertEquals(note, decoded)
    }
}

class WeekStoreMigrationTest {

    @Test
    fun legacyLedgerAndReviewNotesFoldIntoTheWeekNote() = runTest {
        val vault = FakeVaultFileSystem()
        // A pre-0.7.0 vault: instances in weeks/, plus separate ledger/ and reviews/ notes.
        vault.files["weeks/2026-W26.md"] = MarkdownCodecs.encodeWeekNote(
            WeekNote("2026-W26", instances = listOf(TaskInstance("t-1", "2026-W26", planned = true))),
        )
        vault.files["ledger/2026-W26.md"] =
            MarkdownCodecs.encodeLedgerWeek("2026-W26", listOf(event("L-1", "2026-W26", 5)))
        vault.files["reviews/2026-W26.md"] = MarkdownCodecs.encodeReview(
            WeeklyReview("2026-W26", journal = "Semana boa.", submittedAt = Instant.parse("2026-06-28T18:00:00Z")),
        )

        val store = MarkdownWeekStore(vault)
        val note = store.snapshot().getValue("2026-W26")

        assertEquals(listOf("L-1"), note.events.map { it.id })
        assertEquals("Semana boa.", note.review?.journal)
        assertEquals(listOf("t-1"), note.instances.map { it.taskId })
        // The consolidated note replaced the legacy files on disk.
        assertFalse("ledger/2026-W26.md" in vault.files)
        assertFalse("reviews/2026-W26.md" in vault.files)
        val onDisk = MarkdownCodecs.decodeWeekNote(vault.files.getValue("weeks/2026-W26.md"))!!
        assertEquals(note, onDisk)

        // The migrated data survives a relaunch and feeds the derived balance.
        assertEquals(5, MarkdownPointsLedgerRepository(MarkdownWeekStore(vault)).currentBalance())
        assertEquals(
            "2026-W26",
            MarkdownWeeklyReviewRepository(MarkdownWeekStore(vault)).lastSubmittedWeekId(),
        )
    }

    @Test
    fun dataAlreadyInTheWeekNoteWinsOverLegacyFiles() = runTest {
        val vault = FakeVaultFileSystem()
        vault.files["weeks/2026-W26.md"] = MarkdownCodecs.encodeWeekNote(
            WeekNote(
                "2026-W26",
                review = WeeklyReview("2026-W26", journal = "Consolidated journal"),
                events = listOf(event("L-1", "2026-W26", 5)),
            ),
        )
        // Stale legacy copies: the same event id and an older review.
        vault.files["ledger/2026-W26.md"] = MarkdownCodecs.encodeLedgerWeek(
            "2026-W26", listOf(event("L-1", "2026-W26", 5), event("L-2", "2026-W26", 2)),
        )
        vault.files["reviews/2026-W26.md"] =
            MarkdownCodecs.encodeReview(WeeklyReview("2026-W26", journal = "Old journal"))

        val note = MarkdownWeekStore(vault).snapshot().getValue("2026-W26")

        assertEquals(listOf("L-1", "L-2"), note.events.map { it.id }) // L-1 not duplicated
        assertEquals(7, note.events.sumOf { it.delta })
        assertEquals("Consolidated journal", note.review?.journal)
    }
}

/** The fixed test instant 2026-07-02 is in ISO week 2026-W27; the previous week is 2026-W26. */
class WeekFileReviewFlowTest : MainDispatcherTest() {

    private val iso = "2026-07-02T12:00:00Z"

    @Test
    fun homeShowsReviewCtaOnlyWhileTheWeekFileIsMissing() = runTest {
        val vault = FakeVaultFileSystem()
        val store = MarkdownWeekStore(vault)
        val tasks = MarkdownTaskRepository(vault, store)
        val ledger = MarkdownPointsLedgerRepository(store)
        val task = Task("t-1", "Meditate", 5)
        tasks.upsert(task)

        val vm = HomeViewModel(
            taskRepository = tasks,
            themeRepository = MarkdownThemeRepository(vault),
            ledgerRepository = ledger,
            penaltyRepository = MarkdownPenaltyRepository(vault),
            valueRepository = MarkdownValueRepository(vault),
            completeTask = CompleteTaskUseCase(tasks, ledger, fixedClock(iso), TimeZone.UTC),
            applyPenalty = ApplyPenaltyUseCase(ledger, fixedClock(iso)),
            weekCalculator = fixedWeekCalculator(iso),
            syncMdPrayer = disabledMdPrayerSync(fixedWeekCalculator(iso)),
        )
        keepHot(vm.state)

        assertFalse("weeks/2026-W27.md" in vault.files)
        assertTrue(vm.state.value.showReviewCta)

        // Anything that creates the week note hides the call to action.
        vm.onToggleComplete(task, completed = true)
        assertTrue("weeks/2026-W27.md" in vault.files)
        assertFalse(vm.state.value.showReviewCta)
    }

    @Test
    fun submittingTheReviewUpdatesThePreviousWeekFileAndCreatesTheNewOne() = runTest {
        val vault = FakeVaultFileSystem()
        val store = MarkdownWeekStore(vault)
        val tasks = MarkdownTaskRepository(vault, store)
        tasks.upsert(Task("t-1", "Meditate", 5))

        val vm = ReviewViewModel(
            tasks,
            MarkdownPointsLedgerRepository(store),
            MarkdownWeeklyReviewRepository(store),
            MarkdownThemeRepository(vault),
            fixedWeekCalculator(iso),
            fixedClock(iso),
        )
        keepHot(vm.state)
        vm.setJournal("Great week.")
        vm.submit(onDone = {})

        // The journal landed in the previous week's file…
        assertEquals("2026-W26", vm.reviewWeekId)
        val previous = MarkdownCodecs.decodeWeekNote(vault.files.getValue("weeks/2026-W26.md"))!!
        assertEquals("Great week.", previous.review?.journal)

        // …and the new week's file was created with the committed tasks.
        assertEquals("2026-W27", vm.planWeekId)
        val started = MarkdownCodecs.decodeWeekNote(vault.files.getValue("weeks/2026-W27.md"))!!
        assertEquals(listOf("t-1"), started.instances.filter { it.planned }.map { it.taskId })
        assertTrue(tasks.observeWeekStarted("2026-W27").first())
    }
}
