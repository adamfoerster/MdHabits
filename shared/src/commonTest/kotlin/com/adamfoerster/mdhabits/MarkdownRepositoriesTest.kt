package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.markdown.MarkdownPenaltyRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownPointsLedgerRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownRewardRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownTaskRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownThemeRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownValueRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeekStore
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeeklyReviewRepository
import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.Objective
import com.adamfoerster.mdhabits.domain.model.PersonalValue
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.WeeklyReview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The key behavior beyond the in-memory repos: every mutation lands in the vault as Markdown, and
 * a fresh repository over the same vault (i.e. the next app launch) reads the same state back.
 */
class MarkdownRepositoriesTest {

    private val vault = FakeVaultFileSystem()
    private val date = LocalDate(2026, 7, 2)

    // Fresh store per repository, so building a second repository simulates an app relaunch.
    private fun taskRepo() = MarkdownTaskRepository(vault, MarkdownWeekStore(vault))
    private fun ledgerRepo() = MarkdownPointsLedgerRepository(MarkdownWeekStore(vault))
    private fun reviewRepo() = MarkdownWeeklyReviewRepository(MarkdownWeekStore(vault))

    @Test
    fun valuesPersistAcrossRepositoryInstances() = runTest {
        val value = PersonalValue("v-1", "Moderação", "Fazer menos, melhor.")
        MarkdownValueRepository(vault).upsert(value)

        assertTrue("values/v-1.md" in vault.files)
        assertEquals(listOf(value), MarkdownValueRepository(vault).observeValues().first())
    }

    @Test
    fun deleteRemovesTheNote() = runTest {
        val repo = MarkdownValueRepository(vault)
        repo.upsert(PersonalValue("v-1", "Foco"))
        repo.delete("v-1")

        assertTrue(vault.files.isEmpty())
        assertEquals(emptyList(), MarkdownValueRepository(vault).observeValues().first())
    }

    @Test
    fun corruptNoteIsSkippedOnLoad() = runTest {
        MarkdownValueRepository(vault).upsert(PersonalValue("v-1", "Foco"))
        vault.files["values/broken.md"] = "not a valid note"

        assertEquals(listOf("Foco"), MarkdownValueRepository(vault).observeValues().first().map { it.name })
    }

    @Test
    fun penaltiesAndRewardsPersist() = runTest {
        MarkdownPenaltyRepository(vault).upsert(
            com.adamfoerster.mdhabits.domain.model.Penalty("p-1", "Dormir tarde", 10),
        )
        MarkdownRewardRepository(vault).upsert(
            com.adamfoerster.mdhabits.domain.model.Reward("r-1", "Jantar fora", 50),
        )

        assertEquals("Dormir tarde", MarkdownPenaltyRepository(vault).getPenalty("p-1")?.name)
        assertEquals(50, MarkdownRewardRepository(vault).getReward("r-1")?.pointCost)
    }

    @Test
    fun tasksAndWeekInstancesPersist() = runTest {
        val repo = taskRepo()
        repo.upsert(Task("t-1", "Ler", 5))
        repo.upsert(Task("t-2", "Correr", 8))
        repo.setPlanned("2026-W27", listOf("t-1", "t-2"))
        repo.setCompleted("t-1", "2026-W27", completed = true, on = date)

        assertTrue("weeks/2026-W27.md" in vault.files)
        val reloaded = taskRepo()
        assertEquals(setOf("t-1", "t-2"), reloaded.observeTasks().first().map { it.id }.toSet())
        val instances = reloaded.observeInstances("2026-W27").first()
        assertEquals(2, instances.size)
        val done = instances.first { it.taskId == "t-1" }
        assertTrue(done.planned && done.completed)
        assertEquals(date, done.completedOn)
    }

    @Test
    fun unplanningKeepsCompletedHistory() = runTest {
        val repo = taskRepo()
        repo.setPlanned("2026-W27", listOf("t-1", "t-2"))
        repo.setCompleted("t-1", "2026-W27", completed = true, on = date)
        repo.setPlanned("2026-W27", listOf("t-2"))

        val instances = taskRepo().observeInstances("2026-W27").first()
        val kept = instances.first { it.taskId == "t-1" }
        assertTrue(kept.completed)
        assertTrue(!kept.planned)
    }

    @Test
    fun inactiveTasksAreFilteredWhenActiveOnly() = runTest {
        val repo = taskRepo()
        repo.upsert(Task("t-1", "Ler", 5, active = false))
        repo.upsert(Task("t-2", "Correr", 8))

        assertEquals(listOf("t-2"), repo.observeTasks(activeOnly = true).first().map { it.id })
        assertEquals(2, repo.observeTasks(activeOnly = false).first().size)
    }

    @Test
    fun themeAndObjectiveAchievementPersist() = runTest {
        val theme = AnnualTheme(
            id = "theme-1", year = 2026, name = "Ano da Saúde",
            objectives = listOf(Objective("obj-1", "Correr 10km", 100)),
        )
        val repo = MarkdownThemeRepository(vault)
        repo.upsertTheme(theme)
        repo.setObjectiveAchieved("obj-1", achieved = true, on = date)

        val reloaded = MarkdownThemeRepository(vault).getTheme(2026)
        val objective = reloaded?.objectives?.single()
        assertEquals(true, objective?.achieved)
        assertEquals(date, objective?.achievedOn)

        repo.setObjectiveAchieved("obj-1", achieved = false, on = date)
        assertEquals(false, MarkdownThemeRepository(vault).getTheme(2026)?.objectives?.single()?.achieved)
    }

    @Test
    fun ledgerPersistsAcrossWeeksAndDerivesBalance() = runTest {
        val repo = ledgerRepo()
        repo.append(event("L-1", "2026-W26", PointsSource.TASK, "t-1", "Concluída: Ler", 5))
        repo.append(event("L-2", "2026-W27", PointsSource.TASK, "t-2", "Concluída: Correr", 8))
        repo.append(event("L-3", "2026-W27", PointsSource.REWARD, "r-1", "Resgate: Café", -3))

        assertTrue("weeks/2026-W26.md" in vault.files)
        assertTrue("weeks/2026-W27.md" in vault.files)

        val reloaded = ledgerRepo()
        assertEquals(10, reloaded.currentBalance())
        assertEquals(10, reloaded.observeBalance().first())
        assertEquals(listOf("L-2", "L-3"), reloaded.eventsForWeek("2026-W27").map { it.id })

        val report = reloaded.weeklyReport("2026-W27")
        assertEquals(8, report.earned)
        assertEquals(-3, report.spent)
        assertEquals(5, report.net)
        assertEquals(10, report.endingBalance)
        assertEquals(listOf("L-2"), report.completedTasks.map { it.id })
        assertEquals(listOf("L-3"), report.redemptions.map { it.id })
    }

    @Test
    fun reviewsPersistAndTrackLastSubmittedWeek() = runTest {
        val repo = reviewRepo()
        repo.upsert(WeeklyReview("2026-W26", journal = "Rascunho"))
        assertNull(repo.lastSubmittedWeekId())

        repo.upsert(
            WeeklyReview(
                weekId = "2026-W26",
                journal = "Semana boa.",
                objectiveRatings = mapOf("obj-1" to 4),
                plannedTaskIds = listOf("t-1"),
                submittedAt = Instant.parse("2026-06-28T18:00:00Z"),
            ),
        )

        val reloaded = reviewRepo()
        assertEquals("2026-W26", reloaded.lastSubmittedWeekId())
        val review = reloaded.getReview("2026-W26")
        assertEquals("Semana boa.", review?.journal)
        assertEquals(mapOf("obj-1" to 4), review?.objectiveRatings)
    }

    private fun event(
        id: String,
        weekId: String,
        source: PointsSource,
        refId: String,
        label: String,
        delta: Int,
    ) = PointsEvent(id, Instant.parse("2026-07-02T12:00:00Z"), weekId, source, refId, label, delta)
}
