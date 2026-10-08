package com.adamfoerster.mdhabits.data.markdown

import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.DailyHealth
import com.adamfoerster.mdhabits.domain.model.HealthComparison
import com.adamfoerster.mdhabits.domain.model.HealthGoal
import com.adamfoerster.mdhabits.domain.model.HealthMetric
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

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
                task.habitSince?.let { put("habit_since", JsonPrimitive(it.toString())) }
                task.healthGoal?.let { put("health_goal", encodeHealthGoal(it)) }
                task.doneOn?.let { put("done_on", JsonPrimitive(it.toString())) }
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
            habitSince = doc.string("habit_since")?.toLocalDateOrNull(),
            healthGoal = (doc.fields["health_goal"] as? JsonObject)?.let { decodeHealthGoal(it) },
            doneOn = doc.string("done_on")?.toLocalDateOrNull(),
        )
    }

    private fun encodeHealthGoal(goal: HealthGoal): JsonElement = buildJsonObject {
        put("metric", JsonPrimitive(goal.metric.name))
        put("comparison", JsonPrimitive(goal.comparison.name))
        put("target", goal.target.toCompactJson())
    }

    /** A goal with an unknown metric/comparison or a non-positive target is dropped, never fatal. */
    private fun decodeHealthGoal(obj: JsonObject): HealthGoal? {
        val metric = obj.primitiveOrNull("metric")?.content
            ?.let { name -> HealthMetric.entries.find { it.name == name } } ?: return null
        val comparison = obj.primitiveOrNull("comparison")?.content
            ?.let { name -> HealthComparison.entries.find { it.name == name } } ?: return null
        val target = obj.primitiveOrNull("target")?.doubleOrNull?.takeIf { it > 0 } ?: return null
        return HealthGoal(metric, comparison, target)
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

    // ---- Week note (`weeks/<weekId>.md`) ----
    // The frontmatter holds the week's points checkpoints and the review fields; the body holds the
    // review journal followed by three line-per-entry sections — `## Health`, `## Instances`, and
    // `## Ledger` — that link to the notes they refer to, so the week reads well in Obsidian.
    //
    //     ---
    //     week: 2026-W41
    //     closed: false
    //     pointsAtWeekStart: 1234
    //     ---
    //
    //     <journal>
    //
    //     ## Health
    //
    //     - 2026-10-08 | steps: 57 | weight_kg: 75.9
    //
    //     ## Instances
    //
    //     - [x] 2026-10-06 | false | [[MdHabits/tasks/t-1|Orar]] | ["2026-10-06"]
    //
    //     ## Ledger
    //
    //     - 2026-10-06T12:03:59.987044Z | TASK | [[MdHabits/tasks/t-1|Orar]] | 5 | L-942j9ubo
    //     - 2026-10-06T12:04:00.835433Z | HABIT_MISS | [[MdHabits/tasks/t-1|Orar]]@2026-10-05 | -5 | L-nnon76ag
    //
    // Once a week is `closed` (with its `pointsAtWeekEnd`), only the frontmatter is decoded: the body
    // is history, read in full only on demand ([decodeWeekNote] with `includeClosedBody`).

    private const val HEALTH_HEADING = "## Health"
    private const val INSTANCES_HEADING = "## Instances"
    private const val LEDGER_HEADING = "## Ledger"
    private val SECTION_HEADINGS = setOf(HEALTH_HEADING, INSTANCES_HEADING, LEDGER_HEADING)

    /**
     * [linkRoot] is the vault folder's path as Obsidian sees it (e.g. `MdHabits`), prefixed to the
     * links so they resolve from anywhere in the Obsidian vault; blank links relative to the folder.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun encodeWeekNote(note: WeekNote, linkRoot: String? = null): String {
        val links = NoteLinks(linkRoot)
        return Frontmatter.render(
            MarkdownDoc(
                fields = buildMap {
                    // Plain YAML scalars, so Obsidian shows a text, a checkbox, and numbers.
                    put("week", JsonUnquotedLiteral(note.weekId))
                    put("closed", JsonPrimitive(note.closed))
                    note.pointsAtWeekStart?.let { put("pointsAtWeekStart", JsonPrimitive(it)) }
                    if (note.closed) note.pointsAtWeekEnd?.let { put("pointsAtWeekEnd", JsonPrimitive(it)) }
                    note.review?.let { review ->
                        put("ratings", JsonObject(review.objectiveRatings.mapValues { JsonPrimitive(it.value) }))
                        put("planned", review.plannedTaskIds.toJsonArray())
                        review.submittedAt?.let { put("submittedAt", JsonPrimitive(it.toString())) }
                    }
                },
                body = listOfNotNull(
                    note.review?.journal?.trim()?.takeIf { it.isNotEmpty() },
                    note.health.takeIf { it.isNotEmpty() }?.let { days ->
                        section(HEALTH_HEADING, days.sortedBy { it.date }.map(::encodeHealthLine))
                    },
                    note.instances.takeIf { it.isNotEmpty() }?.let { instances ->
                        section(INSTANCES_HEADING, instances.map { encodeInstanceLine(it, links) })
                    },
                    note.events.takeIf { it.isNotEmpty() }?.let { events ->
                        section(LEDGER_HEADING, events.map { encodeLedgerLine(it, links) })
                    },
                ).joinToString("\n\n"),
            ),
        )
    }

    /**
     * Decodes a week note. A closed note (`closed: true` with a `pointsAtWeekEnd`) yields only its
     * frontmatter — checkpoints and review fields — unless [includeClosedBody] asks for the history.
     * Lines of a section that don't parse are skipped, never fatal.
     */
    fun decodeWeekNote(text: String, includeClosedBody: Boolean = false): WeekNote? {
        val doc = Frontmatter.parse(text)
        val weekId = doc.string("week") ?: return null
        val pointsAtWeekEnd = doc.int("pointsAtWeekEnd")
        // A note marked closed without its end checkpoint is treated as open, so it gets closed again.
        val closed = doc.boolean("closed") == true && pointsAtWeekEnd != null
        val pointsAtWeekStart = doc.int("pointsAtWeekStart")
        val readBody = !closed || includeClosedBody

        val sections = if (readBody) splitSections(doc.body) else emptyMap()
        val journal = sections[null].orEmpty().joinToString("\n").trim()
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
        return WeekNote(
            weekId = weekId,
            instances = sections[INSTANCES_HEADING].orEmpty().mapNotNull { decodeInstanceLine(it, weekId) },
            review = review,
            events = sections[LEDGER_HEADING].orEmpty().mapNotNull { decodeLedgerLine(it, weekId) },
            health = sections[HEALTH_HEADING].orEmpty().mapNotNull(::decodeHealthLine),
            closed = closed,
            pointsAtWeekStart = pointsAtWeekStart,
            pointsAtWeekEnd = pointsAtWeekEnd.takeIf { closed },
        )
    }

    private fun section(heading: String, lines: List<String>): String =
        "$heading\n\n" + lines.joinToString("\n")

    /** Body lines per section heading; the lines before the first heading (the journal) under null. */
    private fun splitSections(body: String): Map<String?, List<String>> {
        val result = mutableMapOf<String?, MutableList<String>>()
        var current: String? = null
        body.lines().forEach { line ->
            val heading = line.trim()
            if (heading in SECTION_HEADINGS) {
                current = heading
            } else {
                result.getOrPut(current) { mutableListOf() } += line
            }
        }
        return result
    }

    /** `- <date> | steps: <n> | sleep_min: <n> | weight_kg: <kg>`; unrecorded values are left out. */
    private fun encodeHealthLine(day: DailyHealth): String = buildList {
        add(day.date.toString())
        day.steps?.let { add("steps: $it") }
        day.sleepMinutes?.let { add("sleep_min: $it") }
        day.weightKg?.let { add("weight_kg: ${it.toCompactJson()}") }
    }.joinToString(" | ", prefix = "- ")

    private fun decodeHealthLine(line: String): DailyHealth? {
        val parts = line.listItem()?.split("|")?.map { it.trim() } ?: return null
        val date = parts.first().toLocalDateOrNull() ?: return null
        val values = parts.drop(1).associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        return DailyHealth(
            date = date,
            steps = values["steps"]?.toLongOrNull(),
            sleepMinutes = values["sleep_min"]?.toLongOrNull(),
            weightKg = values["weight_kg"]?.toDoubleOrNull(),
        )
    }

    /** `- [x] <completedOn> | <planned> | [[<root>/tasks/<taskId>|<title>]] | <completed days as JSON>`. */
    private fun encodeInstanceLine(instance: TaskInstance, links: NoteLinks): String {
        val box = if (instance.completed) "[x]" else "[ ]"
        val completedOn = instance.completedOn?.let { "$it " }.orEmpty()
        val dates = instance.completedDates.map { it.toString() }.toJsonArray()
        val link = links.link(TASKS_DIR, instance.taskId, alias = instance.taskTitle.takeIf { it.isNotBlank() })
        return "- $box $completedOn| ${instance.planned} | $link | $dates"
    }

    private fun decodeInstanceLine(line: String, weekId: String): TaskInstance? {
        val match = CHECKBOX.matchEntire(line.trim()) ?: return null
        val completed = match.groupValues[1].isNotBlank()
        val rest = match.groupValues[2]
        // The link may hold pipes of its own, so the columns around it are taken from both ends.
        val first = rest.indexOf('|').takeIf { it >= 0 } ?: return null
        val second = rest.indexOf('|', first + 1).takeIf { it >= 0 } ?: return null
        val last = rest.lastIndexOf('|').takeIf { it > second } ?: return null
        val completedOn = rest.substring(0, first).trim().toLocalDateOrNull()
        val (taskId, title) = NoteLinks.parse(rest.substring(second + 1, last).trim()) ?: return null
        val dates = runCatching {
            markdownJson.parseToJsonElement(rest.substring(last + 1).trim()).jsonArray
                .mapNotNull { it.jsonPrimitive.content.toLocalDateOrNull() }
        }.getOrDefault(emptyList())
        return TaskInstance(
            taskId = taskId,
            weekId = weekId,
            planned = rest.substring(first + 1, second).trim().toBooleanStrictOrNull() ?: false,
            completed = completed,
            completedOn = completedOn,
            completedDates = dates.ifEmpty { listOfNotNull(completedOn.takeIf { completed }) },
            taskTitle = title,
        )
    }

    /**
     * `- <timestamp> | <source> | <reference> | <delta> | <eventId>`. The reference links the note
     * the entry is about, aliased with its name; a missed habit day appends `@<date>` after the link.
     */
    private fun encodeLedgerLine(event: PointsEvent, links: NoteLinks): String {
        val (id, suffix) = when (event.source) {
            PointsSource.HABIT_MISS -> event.refId.substringBefore('@') to
                event.refId.substringAfter('@', "").let { if (it.isEmpty()) "" else "@$it" }
            else -> event.refId to ""
        }
        val dir = when (event.source) {
            PointsSource.TASK, PointsSource.HABIT_MISS -> TASKS_DIR
            PointsSource.PENALTY -> "penalties"
            PointsSource.REWARD -> "rewards"
            // Objectives live inside the year's theme note.
            PointsSource.OBJECTIVE -> "theme"
            PointsSource.ADJUSTMENT -> null
        }
        val reference = if (dir == null) {
            event.refId
        } else {
            // An objective has no note of its own: it links into its theme note, its id as the anchor.
            val target = if (event.source == PointsSource.OBJECTIVE) "${event.weekId.substringBefore("-W")}#$id" else id
            links.link(dir, target, alias = event.label) + suffix
        }
        return "- ${event.timestamp} | ${event.source.name} | $reference | ${event.delta} | ${event.id}"
    }

    private fun decodeLedgerLine(line: String, weekId: String): PointsEvent? {
        val parts = line.listItem()?.split(" | ") ?: return null
        if (parts.size < 5) return null
        // The reference (third column) may itself contain " | " in a name: take the rest from the ends.
        val reference = parts.subList(2, parts.size - 2).joinToString(" | ").trim()
        val (refId, label) = if (reference.startsWith("[[")) {
            val end = reference.indexOf("]]").takeIf { it >= 0 } ?: return null
            val (id, name) = NoteLinks.parse(reference.substring(0, end + 2)) ?: return null
            (id + reference.substring(end + 2).trim()) to name
        } else {
            reference to ""
        }
        return PointsEvent(
            id = parts.last().trim(),
            timestamp = parts[0].trim().toInstantOrNull() ?: return null,
            weekId = weekId,
            source = PointsSource.entries.find { it.name == parts[1].trim() } ?: return null,
            refId = refId,
            label = label,
            delta = parts[parts.size - 2].trim().toIntOrNull() ?: return null,
        )
    }

    /** The text of a `- ` list item, or null for any other line. */
    private fun String.listItem(): String? = trim().takeIf { it.startsWith("- ") }?.removePrefix("- ")

    /** `- [x] rest` / `- [ ] rest` (also `[ x ]`, as typed by hand). */
    private val CHECKBOX = Regex("""-\s*\[\s*([xX]?)\s*]\s*(.*)""")

    private const val TASKS_DIR = "tasks"

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
            body = events.joinToString("\n") { encodeLegacyLedgerLine(it) },
        ),
    )

    fun decodeLedgerWeek(text: String): List<PointsEvent>? {
        val doc = Frontmatter.parse(text)
        val weekId = doc.string("week") ?: return null
        return doc.body.lines().mapNotNull { decodeLegacyLedgerLine(it, weekId) }
    }

    /** `- <timestamp> | <source> | <refId> | <delta> | <eventId> | <label as JSON string>`. */
    private fun encodeLegacyLedgerLine(event: PointsEvent): String {
        val label = JsonPrimitive(event.label)
        return "- ${event.timestamp} | ${event.source.name} | ${event.refId} | " +
            "${event.delta} | ${event.id} | $label"
    }

    private fun decodeLegacyLedgerLine(line: String, weekId: String): PointsEvent? {
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

private fun JsonObject.primitiveOrNull(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

/** Whole numbers are written without a trailing `.0` (`8000`, not `8000.0`), for Obsidian. */
private fun Double.toCompactJson(): JsonPrimitive =
    if (this % 1.0 == 0.0 && kotlin.math.abs(this) < 1e15) JsonPrimitive(toLong()) else JsonPrimitive(this)

/** Weekday frontmatter values, indexed by isoDayNumber − 1, kept human-readable for Obsidian. */
private val DAY_NAMES = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

private fun DayOfWeek.encoded(): String = DAY_NAMES[isoDayNumber - 1]

private fun String.toDayOfWeekOrNull(): DayOfWeek? =
    DAY_NAMES.indexOf(lowercase()).takeIf { it >= 0 }?.let { DayOfWeek(it + 1) }

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

/**
 * Obsidian wikilinks between notes of the vault. [root] is the vault folder's path inside the
 * Obsidian vault (e.g. `MdHabits`); Obsidian resolves `[[MdHabits/tasks/t-1]]` from any note.
 */
internal class NoteLinks(root: String?) {
    private val prefix = root?.trim()?.trim('/')?.takeIf { it.isNotEmpty() }?.let { "$it/" }.orEmpty()

    fun link(dir: String, name: String, alias: String?): String {
        val target = "$prefix$dir/$name"
        // "]]" would end the link early; a line break would end the list item.
        val safe = alias?.replace("]]", "] ]")?.replace('\n', ' ')
        return if (safe.isNullOrEmpty()) "[[$target]]" else "[[$target|$safe]]"
    }

    companion object {
        /**
         * (id, name) of `[[<path>|<name>]]`: the id is the linked note's file name — or the anchor,
         * for an objective linked into its theme note (`theme/2026#<objectiveId>`) — and the alias
         * is the name.
         */
        fun parse(link: String): Pair<String, String>? {
            if (!link.startsWith("[[") || !link.endsWith("]]")) return null
            val inner = link.removePrefix("[[").removeSuffix("]]")
            val path = inner.substringBefore('|')
            val name = if ('|' in inner) inner.substringAfter('|') else ""
            val file = path.substringAfterLast('/')
            val id = (if ('#' in file) file.substringAfter('#') else file.removeSuffix(".md")).trim()
            return (id to name).takeIf { id.isNotEmpty() }
        }
    }
}
