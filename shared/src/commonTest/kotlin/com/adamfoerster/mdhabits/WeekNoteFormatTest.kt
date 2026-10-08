package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.markdown.MarkdownCodecs
import com.adamfoerster.mdhabits.data.markdown.MarkdownPointsLedgerRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownTaskRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeekStore
import com.adamfoerster.mdhabits.data.markdown.WeekNote
import com.adamfoerster.mdhabits.domain.model.DailyHealth
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.TaskInstance
import com.adamfoerster.mdhabits.domain.model.WeeklyReview
import com.adamfoerster.mdhabits.domain.model.displayLabel
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The week note's text format: frontmatter checkpoints plus `## Health/Instances/Ledger` sections. */
class WeekNoteFormatTest {

    private val monday = LocalDate(2026, 10, 5)
    private val tuesday = LocalDate(2026, 10, 6)

    private fun event(id: String, source: PointsSource, refId: String, label: String, delta: Int) =
        PointsEvent(id, Instant.parse("2026-10-06T12:03:59.987044Z"), "2026-W41", source, refId, label, delta)

    @Test
    fun encodesTheAgreedLayout() {
        val note = WeekNote(
            weekId = "2026-W41",
            instances = listOf(
                TaskInstance("t-1", "2026-W41", completed = true, completedOn = tuesday, completedDates = listOf(tuesday), taskTitle = "Orar"),
                TaskInstance("t-2", "2026-W41", planned = true, taskTitle = "Correr"),
            ),
            events = listOf(
                event("L-1", PointsSource.TASK, "t-1", "Orar", 5),
                event("L-2", PointsSource.HABIT_MISS, "t-1@2026-10-05", "Orar", -5),
            ),
            health = listOf(DailyHealth(LocalDate(2026, 10, 8), steps = 57, weightKg = 75.9)),
            pointsAtWeekStart = 1234,
        )

        assertEquals(
            """
            ---
            week: 2026-W41
            closed: false
            pointsAtWeekStart: 1234
            ---

            ## Health

            - 2026-10-08 | steps: 57 | weight_kg: 75.9

            ## Instances

            - [x] 2026-10-06 | false | [[MdHabits/tasks/t-1|Orar]] | ["2026-10-06"]
            - [ ] | true | [[MdHabits/tasks/t-2|Correr]] | []

            ## Ledger

            - 2026-10-06T12:03:59.987044Z | TASK | [[MdHabits/tasks/t-1|Orar]] | 5 | L-1
            - 2026-10-06T12:03:59.987044Z | HABIT_MISS | [[MdHabits/tasks/t-1|Orar]]@2026-10-05 | -5 | L-2

            """.trimIndent(),
            MarkdownCodecs.encodeWeekNote(note, linkRoot = "MdHabits"),
        )
        assertEquals(note, MarkdownCodecs.decodeWeekNote(MarkdownCodecs.encodeWeekNote(note, "MdHabits")))
    }

    @Test
    fun readsTheHandWrittenExample() {
        // The example as typed in Obsidian: quoted numbers, a spaced checkbox, an odd timestamp.
        val text = """
            ---
            week: 2026-W41
            closed: "true"
            pointsAtWeekStart: "1234"
            pointsAtWeekEnd: 2345
            ---

            ## Health

            - 2026-10-08 | steps: 57 | weight_kg: 75.9

            ## Instances

            - [ x ] 2026-10-06T00:00:00:000000Z | false | [[MdHabits/tasks/t-f8sif2fp|Orar]] | ["2026-10-06"]

            ## Ledger

            - 2026-10-06T12:03:59.987044Z | TASK | [[MdHabits/tasks/t-f8sif2fp|Orar]] | 5 | L-942j9ubo
            - 2026-10-06T12:04:00.835433Z | HABIT_MISS | [[MdHabits/tasks/t-f8sif2fp|Orar]]@2026-10-05 | -5 | L-nnon76ag
        """.trimIndent()

        // Closed: only the frontmatter is processed.
        val closed = MarkdownCodecs.decodeWeekNote(text)!!
        assertTrue(closed.closed)
        assertEquals(1234, closed.pointsAtWeekStart)
        assertEquals(2345, closed.pointsAtWeekEnd)
        assertTrue(closed.instances.isEmpty() && closed.events.isEmpty() && closed.health.isEmpty())

        val full = MarkdownCodecs.decodeWeekNote(text, includeClosedBody = true)!!
        val instance = full.instances.single()
        assertEquals("t-f8sif2fp", instance.taskId)
        assertEquals("Orar", instance.taskTitle)
        assertTrue(instance.completed)
        assertEquals(listOf(tuesday), instance.completedDates)
        assertEquals(listOf("t-f8sif2fp", "t-f8sif2fp@2026-10-05"), full.events.map { it.refId })
        assertEquals(listOf(5, -5), full.events.map { it.delta })
        assertEquals("Orar (2026-10-05)", full.events.last().displayLabel)
        assertEquals(75.9, full.health.single().weightKg)
    }

    @Test
    fun roundTripsJournalReviewAndAwkwardNames() {
        val note = WeekNote(
            weekId = "2026-W41",
            review = WeeklyReview(
                "2026-W41",
                journal = "Semana boa.\n\nCom dois parágrafos.",
                objectiveRatings = mapOf("obj-1" to 4),
                plannedTaskIds = listOf("t-1"),
                submittedAt = Instant.parse("2026-10-05T21:10:00Z"),
            ),
            instances = listOf(TaskInstance("t-1", "2026-W41", planned = true, taskTitle = "Ler | estudar")),
            events = listOf(
                event("L-1", PointsSource.PENALTY, "p-1", "Furar a dieta | doce", -10),
                event("L-2", PointsSource.REWARD, "r-1", "1h games", -30),
                event("L-3", PointsSource.OBJECTIVE, "obj-1", "Mudar de casa", 1000),
            ),
            pointsAtWeekStart = 0,
        )
        val text = MarkdownCodecs.encodeWeekNote(note, linkRoot = "MdHabits")

        assertTrue("[[MdHabits/penalties/p-1|Furar a dieta | doce]]" in text, text)
        assertTrue("[[MdHabits/theme/2026#obj-1|Mudar de casa]]" in text, text)
        assertEquals(note, MarkdownCodecs.decodeWeekNote(text))
    }

    @Test
    fun withoutALinkRootLinksAreRelativeToTheFolder() {
        val note = WeekNote("2026-W41", instances = listOf(TaskInstance("t-1", "2026-W41", taskTitle = "Orar")))
        assertTrue("[[tasks/t-1|Orar]]" in MarkdownCodecs.encodeWeekNote(note))
    }

    @Test
    fun aNoteMarkedClosedWithoutItsEndCheckpointIsReadAsOpen() {
        val text = MarkdownCodecs.encodeWeekNote(
            WeekNote("2026-W41", events = listOf(event("L-1", PointsSource.TASK, "t-1", "Orar", 5))),
        ).replace("closed: false", "closed: true")

        val note = MarkdownCodecs.decodeWeekNote(text)!!
        assertFalse(note.closed)
        assertEquals(1, note.events.size)
    }
}

/** The points checkpoints the week store keeps settled, and what closing a week freezes. */
class WeekClosingTest {

    private val vault = FakeVaultFileSystem()

    // 2026-10-08 is a Thursday in W41: W39 (ended Sunday 2026-09-27) is more than 7 days past, W40 isn't.
    private val today = "2026-10-08T12:00:00Z"

    private fun store(iso: String = today) = MarkdownWeekStore(vault, fixedWeekCalculator(iso))

    private fun event(weekId: String, delta: Int, id: String = "L-$weekId-$delta") =
        PointsEvent(id, Instant.parse("2026-09-01T10:00:00Z"), weekId, PointsSource.TASK, "t-1", "Orar", delta)

    private fun seed(vararg notes: WeekNote) =
        notes.forEach { vault.externalWrite("weeks", "${it.weekId}.md", MarkdownCodecs.encodeWeekNote(it)) }

    @Test
    fun loadingSettlesStartsAndClosesWeeksMoreThanAWeekPastTheirEnd() = runTest {
        seed(
            WeekNote("2026-W38", events = listOf(event("2026-W38", 10))),
            WeekNote("2026-W39", events = listOf(event("2026-W39", 5))),
            WeekNote("2026-W40", events = listOf(event("2026-W40", -3))),
            WeekNote("2026-W41", events = listOf(event("2026-W41", 20))),
        )

        val notes = store().snapshot()

        assertEquals(listOf(true, true, false, false), notes.values.sortedBy { it.weekId }.map { it.closed })
        assertEquals(15, notes.getValue("2026-W39").pointsAtWeekEnd)
        assertEquals(15, notes.getValue("2026-W40").pointsAtWeekStart)
        assertEquals(12, notes.getValue("2026-W41").pointsAtWeekStart)
        assertNull(notes.getValue("2026-W41").pointsAtWeekEnd)
        // Written back: the files carry the checkpoints for the next device / launch.
        assertTrue("closed: true" in vault.files.getValue("weeks/2026-W39.md"))
        assertTrue("pointsAtWeekEnd: 15" in vault.files.getValue("weeks/2026-W39.md"))
        assertTrue("pointsAtWeekStart: 12" in vault.files.getValue("weeks/2026-W41.md"))
        assertEquals(32, MarkdownPointsLedgerRepository(store()).currentBalance())
    }

    @Test
    fun aClosedWeekCountsThroughItsCheckpointNotItsBody() = runTest {
        seed(
            WeekNote("2026-W39", events = listOf(event("2026-W39", 5)), closed = true, pointsAtWeekStart = 0, pointsAtWeekEnd = 100),
            WeekNote("2026-W41", events = listOf(event("2026-W41", 20))),
        )

        val notes = store().snapshot()

        assertTrue(notes.getValue("2026-W39").events.isEmpty())
        assertEquals(100, notes.getValue("2026-W41").pointsAtWeekStart)
        assertEquals(120, MarkdownPointsLedgerRepository(store()).currentBalance())
    }

    @Test
    fun aClosedWeekIsNeverWrittenAgain() = runTest {
        seed(WeekNote("2026-W39", events = listOf(event("2026-W39", 5)), closed = true, pointsAtWeekStart = 0, pointsAtWeekEnd = 5))
        val before = vault.files.getValue("weeks/2026-W39.md")
        val ledger = MarkdownPointsLedgerRepository(store())

        ledger.append(event("2026-W39", 50, id = "L-late"))

        assertEquals(before, vault.files.getValue("weeks/2026-W39.md"))
        assertEquals(5, ledger.currentBalance())
        assertTrue(ledger.isWeekClosed("2026-W39"))
    }

    @Test
    fun aChangeToAnOpenWeekMovesTheFollowingWeeksStart() = runTest {
        seed(
            WeekNote("2026-W40", events = listOf(event("2026-W40", 10))),
            WeekNote("2026-W41", events = listOf(event("2026-W41", 1))),
        )
        val ledger = MarkdownPointsLedgerRepository(store())
        ledger.currentBalance()

        ledger.append(event("2026-W40", 7, id = "L-more"))

        assertTrue("pointsAtWeekStart: 17" in vault.files.getValue("weeks/2026-W41.md"))
    }

    @Test
    fun aWeekClosesWhileTheAppIsOpenOnTheNextPoll() = runTest {
        seed(WeekNote("2026-W40", events = listOf(event("2026-W40", 10))))
        val vaultStore = store()
        assertFalse(vaultStore.isClosed("2026-W40"))

        // The same day, nothing changed: polling writes nothing.
        val written = vault.files.getValue("weeks/2026-W40.md")
        vaultStore.refreshFromVault()
        assertEquals(written, vault.files.getValue("weeks/2026-W40.md"))

        // A store whose clock moved past the closing day closes it on its next refresh.
        val later = store("2026-10-13T12:00:00Z")
        later.ensureLoaded()
        assertTrue(later.isClosed("2026-W40"))
        assertTrue("pointsAtWeekEnd: 10" in vault.files.getValue("weeks/2026-W40.md"))
    }

    @Test
    fun completedAdhocTasksStayDoneAfterTheirWeekCloses() = runTest {
        val open = store("2026-09-24T12:00:00Z") // W39 still open
        val tasks = MarkdownTaskRepository(vault, open)
        val ledger = MarkdownPointsLedgerRepository(open)
        val chore = Task("t-chore", "Trocar a lâmpada", 30, recurrence = Recurrence.ADHOC)
        tasks.upsert(chore)

        CompleteTaskUseCase(tasks, ledger, fixedClock("2026-09-24T12:00:00Z")).invoke(chore, "2026-W39", true)
        assertEquals(LocalDate(2026, 9, 24), tasks.getTask("t-chore")?.doneOn)

        // Weeks later W39 is closed and its instances are no longer loaded…
        val reopened = MarkdownTaskRepository(vault, store())
        assertTrue(MarkdownWeekStore(vault, fixedWeekCalculator(today)).isClosed("2026-W39"))
        // …but the task itself remembers it is done.
        assertTrue("t-chore" in reopened.observeCompletedTaskIds().first())
    }

    @Test
    fun theMissedHabitSweepLeavesClosedWeeksAlone() = runTest {
        // W39 closed with no instances loaded: charging its "missed" days would be wrong.
        seed(WeekNote("2026-W39", closed = true, pointsAtWeekStart = 0, pointsAtWeekEnd = 0))
        val weekStore = store()
        val tasks = MarkdownTaskRepository(vault, weekStore)
        val ledger = MarkdownPointsLedgerRepository(weekStore)
        tasks.upsert(Task("t-h", "Orar", 5, recurrence = Recurrence.HABIT, habitSince = LocalDate(2026, 9, 1)))

        habitSweep(tasks, ledger, today).invoke()

        assertTrue(ledger.eventsForWeek("2026-W39").isEmpty())
        assertEquals(0, ledger.weeklyReport("2026-W39").penalties.size)
        // The open weeks were swept as usual.
        assertTrue(ledger.eventsForWeek("2026-W40").any { it.source == PointsSource.HABIT_MISS })
    }
}
