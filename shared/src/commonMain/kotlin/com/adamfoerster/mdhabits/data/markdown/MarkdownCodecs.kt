package com.adamfoerster.mdhabits.data.markdown

import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.Objective
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.PersonalValue
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Reward
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.TaskInstance
import com.adamfoerster.mdhabits.domain.model.WeeklyReview
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Codecs between domain models and Markdown notes. Every entity is one note whose frontmatter
 * holds the structured fields and whose body holds the free text (description / journal), so the
 * vault stays pleasant to read and edit in Obsidian. Decoders return null for notes they can't
 * make sense of; callers skip those files instead of failing the whole load.
 */
object MarkdownCodecs {

    // ---- Personal values, penalties, rewards (name + optional description body) ----

    fun encodeValue(value: PersonalValue): String = Frontmatter.render(
        MarkdownDoc(
            fields = linkedMapOf(
                "id" to JsonPrimitive(value.id),
                "name" to JsonPrimitive(value.name),
            ),
            body = value.description,
        ),
    )

    fun decodeValue(text: String): PersonalValue? {
        val doc = Frontmatter.parse(text)
        return PersonalValue(
            id = doc.string("id") ?: return null,
            name = doc.string("name") ?: return null,
            description = doc.body,
        )
    }

    fun encodePenalty(penalty: Penalty): String = Frontmatter.render(
        MarkdownDoc(
            fields = linkedMapOf(
                "id" to JsonPrimitive(penalty.id),
                "name" to JsonPrimitive(penalty.name),
                "cost" to JsonPrimitive(penalty.pointCost),
            ),
            body = penalty.description,
        ),
    )

    fun decodePenalty(text: String): Penalty? {
        val doc = Frontmatter.parse(text)
        return Penalty(
            id = doc.string("id") ?: return null,
            name = doc.string("name") ?: return null,
            pointCost = doc.int("cost") ?: return null,
            description = doc.body,
        )
    }

    fun encodeReward(reward: Reward): String = Frontmatter.render(
        MarkdownDoc(
            fields = linkedMapOf(
                "id" to JsonPrimitive(reward.id),
                "name" to JsonPrimitive(reward.name),
                "cost" to JsonPrimitive(reward.pointCost),
            ),
            body = reward.description,
        ),
    )

    fun decodeReward(text: String): Reward? {
        val doc = Frontmatter.parse(text)
        return Reward(
            id = doc.string("id") ?: return null,
            name = doc.string("name") ?: return null,
            pointCost = doc.int("cost") ?: return null,
            description = doc.body,
        )
    }

    // ---- Tasks ----

    fun encodeTask(task: Task): String = Frontmatter.render(
        MarkdownDoc(
            fields = buildMap {
                put("id", JsonPrimitive(task.id))
                put("title", JsonPrimitive(task.title))
                put("points", JsonPrimitive(task.points))
                put("recurrence", JsonPrimitive(task.recurrence.name))
                if (task.daysOfWeek.isNotEmpty()) {
                    put("days", task.daysOfWeek.map { it.encoded() }.toJsonArray())
                }
                put("values", task.linkedValueIds.toJsonArray())
                put("objectives", task.linkedObjectiveIds.toJsonArray())
                put("active", JsonPrimitive(task.active))
            },
        ),
    )

    fun decodeTask(text: String): Task? {
        val doc = Frontmatter.parse(text)
        return Task(
            id = doc.string("id") ?: return null,
            title = doc.string("title") ?: return null,
            points = doc.int("points") ?: return null,
            recurrence = doc.string("recurrence")?.let { decodeRecurrence(it) } ?: Recurrence.WEEKLY,
            daysOfWeek = doc.stringList("days").mapNotNull { it.toDayOfWeekOrNull() },
            linkedValueIds = doc.stringList("values"),
            linkedObjectiveIds = doc.stringList("objectives"),
            active = doc.boolean("active") ?: true,
        )
    }

    private fun decodeRecurrence(name: String): Recurrence? = when (name) {
        // Notes written before 0.5.0 used ONCE for what is now the ad-hoc frequency.
        "ONCE" -> Recurrence.ADHOC
        else -> Recurrence.entries.find { it.name == name }
    }

    // ---- Annual theme (objectives embedded as a JSON array of objects) ----

    @Serializable
    private data class ObjectiveDto(
        val id: String,
        val title: String,
        val points: Int,
        val achieved: Boolean = false,
        val achievedOn: String? = null,
    )

    fun encodeTheme(theme: AnnualTheme): String {
        val objectives = theme.objectives.map {
            ObjectiveDto(it.id, it.title, it.points, it.achieved, it.achievedOn?.toString())
        }
        return Frontmatter.render(
            MarkdownDoc(
                fields = linkedMapOf(
                    "id" to JsonPrimitive(theme.id),
                    "year" to JsonPrimitive(theme.year),
                    "name" to JsonPrimitive(theme.name),
                    "objectives" to markdownJson.encodeToJsonElement(
                        ListSerializer(ObjectiveDto.serializer()), objectives,
                    ),
                ),
                body = theme.description,
            ),
        )
    }

    fun decodeTheme(text: String): AnnualTheme? {
        val doc = Frontmatter.parse(text)
        val objectives = doc.fields["objectives"]?.let { element ->
            runCatching {
                markdownJson.decodeFromJsonElement(ListSerializer(ObjectiveDto.serializer()), element)
            }.getOrNull()
        }.orEmpty().map {
            Objective(it.id, it.title, it.points, it.achieved, it.achievedOn?.toLocalDateOrNull())
        }
        return AnnualTheme(
            id = doc.string("id") ?: return null,
            year = doc.int("year") ?: return null,
            name = doc.string("name") ?: return null,
            description = doc.body,
            objectives = objectives,
        )
    }

    // ---- Consolidated week note (`weeks/<weekId>.md`) ----
    // Task instances, the weekly review, and the points ledger of a week share one note:
    // instances/ratings/planned/submittedAt live in the frontmatter, the journal is the body,
    // and the ledger events are bullet lines under a `## Ledger` heading at the end of the body.

    @Serializable
    private data class InstanceDto(
        val taskId: String,
        val planned: Boolean = false,
        val completed: Boolean = false,
        val completedOn: String? = null,
    )

    private const val LEDGER_HEADING = "## Ledger"

    fun encodeWeekNote(note: WeekNote): String = Frontmatter.render(
        MarkdownDoc(
            fields = buildMap {
                put("week", JsonPrimitive(note.weekId))
                if (note.instances.isNotEmpty()) {
                    val dtos = note.instances.map {
                        InstanceDto(it.taskId, it.planned, it.completed, it.completedOn?.toString())
                    }
                    put(
                        "instances",
                        markdownJson.encodeToJsonElement(ListSerializer(InstanceDto.serializer()), dtos),
                    )
                }
                note.review?.let { review ->
                    put("ratings", JsonObject(review.objectiveRatings.mapValues { JsonPrimitive(it.value) }))
                    put("planned", review.plannedTaskIds.toJsonArray())
                    review.submittedAt?.let { put("submittedAt", JsonPrimitive(it.toString())) }
                }
            },
            body = buildString {
                val journal = note.review?.journal?.trim().orEmpty()
                if (journal.isNotEmpty()) append(journal)
                if (note.events.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    appendLine(LEDGER_HEADING)
                    append(note.events.joinToString("\n") { encodeLedgerLine(it) })
                }
            },
        ),
    )

    fun decodeWeekNote(text: String): WeekNote? {
        val doc = Frontmatter.parse(text)
        val weekId = doc.string("week") ?: return null
        val lines = doc.body.lines()
        val headingAt = lines.indexOfFirst { it.trim() == LEDGER_HEADING }
        val journal = (if (headingAt >= 0) lines.take(headingAt) else lines).joinToString("\n").trim()
        val events = if (headingAt >= 0) {
            lines.drop(headingAt + 1).mapNotNull { decodeLedgerLine(it, weekId) }
        } else {
            emptyList()
        }
        val hasReview = journal.isNotEmpty() ||
            listOf("ratings", "planned", "submittedAt").any { it in doc.fields }
        val review = if (hasReview) {
            WeeklyReview(
                weekId = weekId,
                journal = journal,
                objectiveRatings = decodeRatings(doc),
                plannedTaskIds = doc.stringList("planned"),
                submittedAt = doc.string("submittedAt")?.toInstantOrNull(),
            )
        } else {
            null
        }
        return WeekNote(weekId, decodeInstances(doc, weekId), review, events)
    }

    private fun decodeInstances(doc: MarkdownDoc, weekId: String): List<TaskInstance> {
        val element = doc.fields["instances"] ?: return emptyList()
        return runCatching {
            markdownJson.decodeFromJsonElement(ListSerializer(InstanceDto.serializer()), element)
        }.getOrNull().orEmpty().map {
            TaskInstance(it.taskId, weekId, it.planned, it.completed, it.completedOn?.toLocalDateOrNull())
        }
    }

    private fun decodeRatings(doc: MarkdownDoc): Map<String, Int> =
        (doc.fields["ratings"] as? JsonObject)?.mapNotNull { (key, value) ->
            runCatching { key to value.jsonPrimitive.content.toInt() }.getOrNull()
        }?.toMap().orEmpty()

    // ---- Legacy pre-0.7.0 formats (`reviews/<weekId>.md` and `ledger/<weekId>.md`) ----
    // Kept so MarkdownWeekStore can fold old vaults into the consolidated week notes; the
    // encoders only produce legacy fixtures in tests.

    fun encodeReview(review: WeeklyReview): String = Frontmatter.render(
        MarkdownDoc(
            fields = buildMap<String, JsonElement> {
                put("week", JsonPrimitive(review.weekId))
                put(
                    "ratings",
                    JsonObject(review.objectiveRatings.mapValues { JsonPrimitive(it.value) }),
                )
                put("planned", review.plannedTaskIds.toJsonArray())
                review.submittedAt?.let { put("submittedAt", JsonPrimitive(it.toString())) }
            },
            body = review.journal,
        ),
    )

    fun decodeReview(text: String): WeeklyReview? {
        val doc = Frontmatter.parse(text)
        return WeeklyReview(
            weekId = doc.string("week") ?: return null,
            journal = doc.body,
            objectiveRatings = decodeRatings(doc),
            plannedTaskIds = doc.stringList("planned"),
            submittedAt = doc.string("submittedAt")?.toInstantOrNull(),
        )
    }

    fun encodeLedgerWeek(weekId: String, events: List<PointsEvent>): String = Frontmatter.render(
        MarkdownDoc(
            fields = linkedMapOf("week" to JsonPrimitive(weekId)),
            body = events.joinToString("\n") { encodeLedgerLine(it) },
        ),
    )

    fun decodeLedgerWeek(text: String): List<PointsEvent>? {
        val doc = Frontmatter.parse(text)
        val weekId = doc.string("week") ?: return null
        return doc.body.lines().mapNotNull { decodeLedgerLine(it, weekId) }
    }

    /** `- <timestamp> | <source> | <refId> | <delta> | <eventId> | <label as JSON string>`. */
    private fun encodeLedgerLine(event: PointsEvent): String {
        val label = JsonPrimitive(event.label)
        return "- ${event.timestamp} | ${event.source.name} | ${event.refId} | " +
            "${event.delta} | ${event.id} | $label"
    }

    private fun decodeLedgerLine(line: String, weekId: String): PointsEvent? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("- ")) return null
        // limit=6 keeps any " | " inside the JSON-encoded label intact.
        val parts = trimmed.removePrefix("- ").split(" | ", limit = 6)
        if (parts.size != 6) return null
        return PointsEvent(
            id = parts[4],
            timestamp = parts[0].toInstantOrNull() ?: return null,
            weekId = weekId,
            source = PointsSource.entries.find { it.name == parts[1] } ?: return null,
            refId = parts[2],
            label = runCatching {
                markdownJson.parseToJsonElement(parts[5]).jsonPrimitive.content
            }.getOrNull() ?: return null,
            delta = parts[3].toIntOrNull() ?: return null,
        )
    }
}

private fun List<String>.toJsonArray(): JsonElement =
    kotlinx.serialization.json.JsonArray(map { JsonPrimitive(it) })

/** Weekday frontmatter values, indexed by isoDayNumber − 1, kept human-readable for Obsidian. */
private val DAY_NAMES = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

private fun DayOfWeek.encoded(): String = DAY_NAMES[isoDayNumber - 1]

private fun String.toDayOfWeekOrNull(): DayOfWeek? =
    DAY_NAMES.indexOf(lowercase()).takeIf { it >= 0 }?.let { DayOfWeek(it + 1) }

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()
