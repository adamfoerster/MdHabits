package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.markdown.Frontmatter
import com.adamfoerster.mdhabits.data.markdown.MarkdownCodecs
import com.adamfoerster.mdhabits.data.markdown.MarkdownDoc
import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.Objective
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.PersonalValue
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Reward
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.data.markdown.WeekNote
import com.adamfoerster.mdhabits.domain.model.TaskInstance
import com.adamfoerster.mdhabits.domain.model.WeeklyReview
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrontmatterTest {

    @Test
    fun roundTripsFieldsAndBody() {
        val doc = MarkdownDoc(
            fields = linkedMapOf(
                "id" to JsonPrimitive("t-1"),
                "title" to JsonPrimitive("Meditar: 10 min \"por dia\""),
                "points" to JsonPrimitive(5),
                "active" to JsonPrimitive(true),
            ),
            body = "Linha um.\n\nLinha dois.",
        )
        val parsed = Frontmatter.parse(Frontmatter.render(doc))
        assertEquals("t-1", (parsed.fields["id"] as JsonPrimitive).content)
        assertEquals("Meditar: 10 min \"por dia\"", (parsed.fields["title"] as JsonPrimitive).content)
        assertEquals("5", (parsed.fields["points"] as JsonPrimitive).content)
        assertEquals("true", (parsed.fields["active"] as JsonPrimitive).content)
        assertEquals("Linha um.\n\nLinha dois.", parsed.body)
    }

    @Test
    fun rendersValidYamlFrontmatterFences() {
        val text = Frontmatter.render(MarkdownDoc(linkedMapOf("id" to JsonPrimitive("x")), "body"))
        assertTrue(text.startsWith("---\n"))
        assertTrue(text.contains("\n---\n"))
        assertTrue(text.contains("id: \"x\""))
    }

    @Test
    fun acceptsHandEditedUnquotedValues() {
        val parsed = Frontmatter.parse("---\nname: Meditation\ncost: 12\n---\n\nHello")
        assertEquals("Meditation", (parsed.fields["name"] as JsonPrimitive).content)
        assertEquals("12", (parsed.fields["cost"] as JsonPrimitive).content)
        assertEquals("Hello", parsed.body)
    }

    @Test
    fun textWithoutFrontmatterBecomesBody() {
        val parsed = Frontmatter.parse("just some note")
        assertTrue(parsed.fields.isEmpty())
        assertEquals("just some note", parsed.body)
    }
}

class MarkdownCodecsTest {

    @Test
    fun valueRoundTrip() {
        val value = PersonalValue("v-1", "Moderação", "Fazer menos, melhor.")
        assertEquals(value, MarkdownCodecs.decodeValue(MarkdownCodecs.encodeValue(value)))
    }

    @Test
    fun penaltyAndRewardRoundTrip() {
        val penalty = Penalty("p-1", "Dormir tarde", 10, "Depois das 23h.")
        val reward = Reward("r-1", "Jantar fora", 50)
        assertEquals(penalty, MarkdownCodecs.decodePenalty(MarkdownCodecs.encodePenalty(penalty)))
        assertEquals(reward, MarkdownCodecs.decodeReward(MarkdownCodecs.encodeReward(reward)))
    }

    @Test
    fun taskRoundTrip() {
        val task = Task(
            id = "t-1",
            title = "Ler 30 min",
            points = 5,
            recurrence = Recurrence.ADHOC,
            linkedValueIds = listOf("v-1", "v-2"),
            linkedObjectiveIds = listOf("obj-1"),
            active = false,
        )
        assertEquals(task, MarkdownCodecs.decodeTask(MarkdownCodecs.encodeTask(task)))
    }

    @Test
    fun themeRoundTripIncludingObjectives() {
        val theme = AnnualTheme(
            id = "theme-1",
            year = 2026,
            name = "Ano da Saúde",
            description = "Corpo são,\nmente sã.",
            objectives = listOf(
                Objective("obj-1", "Correr 10km", 100),
                Objective("obj-2", "Meditar 100x", 80, achieved = true, achievedOn = LocalDate(2026, 6, 1)),
            ),
        )
        assertEquals(theme, MarkdownCodecs.decodeTheme(MarkdownCodecs.encodeTheme(theme)))
    }

    @Test
    fun weekInstancesRoundTrip() {
        val note = WeekNote(
            weekId = "2026-W27",
            instances = listOf(
                TaskInstance("t-1", "2026-W27", planned = true),
                TaskInstance("t-2", "2026-W27", planned = true, completed = true, completedOn = LocalDate(2026, 7, 1)),
            ),
        )
        assertEquals(note, MarkdownCodecs.decodeWeekNote(MarkdownCodecs.encodeWeekNote(note)))
    }

    @Test
    fun reviewRoundTripDraftAndSubmitted() {
        val draft = WeeklyReview("2026-W27", journal = "Semana boa.", objectiveRatings = mapOf("obj-1" to 4))
        val submitted = draft.copy(
            plannedTaskIds = listOf("t-1", "t-2"),
            submittedAt = Instant.parse("2026-07-05T18:00:00Z"),
        )
        assertEquals(draft, MarkdownCodecs.decodeReview(MarkdownCodecs.encodeReview(draft)))
        assertEquals(submitted, MarkdownCodecs.decodeReview(MarkdownCodecs.encodeReview(submitted)))
    }

    @Test
    fun ledgerRoundTripPreservesOrderAndTrickyLabels() {
        val events = listOf(
            PointsEvent(
                "L-1", Instant.parse("2026-07-01T10:00:00Z"), "2026-W27",
                PointsSource.TASK, "t-1", "Concluída: Ler | \"clássicos\"", 5,
            ),
            PointsEvent(
                "L-2", Instant.parse("2026-07-02T11:00:00Z"), "2026-W27",
                PointsSource.REWARD, "r-1", "Resgate: Jantar fora", -50,
            ),
        )
        val decoded = MarkdownCodecs.decodeLedgerWeek(MarkdownCodecs.encodeLedgerWeek("2026-W27", events))
        assertEquals(events, decoded)
    }

    @Test
    fun decodersReturnNullForGarbage() {
        assertNull(MarkdownCodecs.decodeTask("nonsense"))
        assertNull(MarkdownCodecs.decodeTheme("---\nname: \"no id\"\n---"))
        assertNull(MarkdownCodecs.decodeReview("# just a heading"))
    }
}
