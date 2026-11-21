package com.adamfoerster.mdhabits.core.releasenotes

import com.adamfoerster.mdhabits.core.i18n.Lang
import com.adamfoerster.mdhabits.core.platform.AppInfo
import com.adamfoerster.mdhabits.core.settings.AppSettings

/** A text in the three supported languages. */
data class LocalizedText(val en: String, val pt: String, val es: String) {
    fun get(lang: Lang): String = when (lang) {
        Lang.EN -> en
        Lang.PT -> pt
        Lang.ES -> es
    }
}

/**
 * The short, user-facing notes of one release: new features and bug fixes only, no implementation
 * details. The long version lives in CHANGELOG.md.
 */
data class ReleaseNote(
    val version: String,
    val features: List<LocalizedText> = emptyList(),
    val fixes: List<LocalizedText> = emptyList(),
)

/**
 * Release notes shown in the "What's new" popup. Newest first. Every released version that changes
 * something the user can notice must have an entry here (see CLAUDE.md).
 *
 * The popup labels live here, not in `Strings`, which is at the JVM's 255-parameter limit.
 */
object ReleaseNotes {
    val title = LocalizedText("What's new", "Novidades", "Novedades")
    val subtitle = LocalizedText("Version", "Versão", "Versión")
    val featuresLabel = LocalizedText("New", "Novo", "Nuevo")
    val fixesLabel = LocalizedText("Fixes", "Correções", "Correcciones")
    val gotIt = LocalizedText("Got it", "Entendi", "Entendido")
    val settingsButton = LocalizedText("Release notes", "Notas da versão", "Notas de la versión")
    val empty = LocalizedText(
        "Nothing to show for this version.",
        "Nada a mostrar para esta versão.",
        "Nada que mostrar para esta versión.",
    )

    val all: List<ReleaseNote> = listOf(
        ReleaseNote(
            version = "0.30.0",
            features = listOf(
                LocalizedText(
                    "New monthly task: do it once a month, any day. It appears under \"This month\" and comes back on the first of the next month.",
                    "Nova tarefa mensal: faça uma vez por mês, em qualquer dia. Ela aparece em \"Este mês\" e volta no dia 1 do mês seguinte.",
                    "Nueva tarea mensual: hazla una vez al mes, cualquier día. Aparece en \"Este mes\" y vuelve el día 1 del mes siguiente.",
                ),
            ),
        ),
        ReleaseNote(
            version = "0.29.0",
            features = listOf(
                LocalizedText(
                    "Ad-hoc tasks can now have a due date: done by then, they earn their points; if the date passes, the points are deducted.",
                    "Tarefas avulsas agora podem ter um prazo: feitas até a data, ganham os pontos; se o prazo passar, os pontos são descontados.",
                    "Las tareas sueltas ahora pueden tener fecha límite: hechas a tiempo ganan sus puntos; si la fecha pasa, se descuentan.",
                ),
            ),
        ),
        ReleaseNote(
            version = "0.28.0",
            features = listOf(
                LocalizedText(
                    "The Android widget now has a \"+\" button that opens the app straight on the new task form.",
                    "O widget do Android agora tem um botão \"+\" que abre o app direto no formulário de nova tarefa.",
                    "El widget de Android ahora tiene un botón \"+\" que abre la app directamente en el formulario de nueva tarea.",
                ),
            ),
        ),
        ReleaseNote(
            version = "0.27.0",
            features = listOf(
                LocalizedText(
                    "Android home-screen widget: see today's and this week's tasks and check them off without opening the app.",
                    "Widget para a tela inicial do Android: veja as tarefas do dia e da semana e marque-as sem abrir o app.",
                    "Widget para la pantalla de inicio de Android: mira las tareas del día y de la semana y márcalas sin abrir la app.",
                ),
                LocalizedText(
                    "On Android 16 and newer, assistants like Gemini can add tasks to MdHabits for you.",
                    "No Android 16 ou mais recente, assistentes como o Gemini podem adicionar tarefas ao MdHabits para você.",
                    "En Android 16 o posterior, asistentes como Gemini pueden añadir tareas a MdHabits por ti.",
                ),
            ),
        ),
        ReleaseNote(
            version = "0.26.0",
            features = listOf(
                LocalizedText(
                    "Redesigned management screen: search your tasks, objectives, values, penalties and rewards, and see how many tasks each filter holds.",
                    "Tela de cadastros redesenhada: busque tarefas, objetivos, valores, penalidades e recompensas, e veja quantas tarefas cada filtro tem.",
                    "Pantalla de gestión rediseñada: busca tareas, objetivos, valores, penalizaciones y recompensas, y mira cuántas tareas tiene cada filtro.",
                ),
                LocalizedText(
                    "Objectives and values now show how many tasks are linked to them.",
                    "Objetivos e valores agora mostram quantas tarefas estão ligadas a eles.",
                    "Los objetivos y valores ahora muestran cuántas tareas tienen vinculadas.",
                ),
                LocalizedText(
                    "New tasks start as one-off (ad-hoc) by default.",
                    "Novas tarefas começam como avulsas por padrão.",
                    "Las tareas nuevas empiezan como sueltas por defecto.",
                ),
            ),
        ),
        ReleaseNote(
            version = "0.25.0",
            features = listOf(
                LocalizedText(
                    "Faster startup: the app only reloads the notes that changed since you last opened it.",
                    "Abertura mais rápida: o app só recarrega as notas que mudaram desde a última vez que você o abriu.",
                    "Inicio más rápido: la app solo vuelve a cargar las notas que cambiaron desde la última vez que la abriste.",
                ),
                LocalizedText(
                    "Your changes appear instantly and are saved to the notes in the background.",
                    "Suas alterações aparecem na hora e são salvas nas notas em segundo plano.",
                    "Tus cambios aparecen al instante y se guardan en las notas en segundo plano.",
                ),
            ),
        ),
        ReleaseNote(
            version = "0.24.0",
            features = listOf(
                LocalizedText(
                    "A \"What's new\" message now appears once after each update. You can read it again any time from Settings, next to the version.",
                    "Uma mensagem de \"Novidades\" aparece uma vez após cada atualização. Você pode relê-la quando quiser em Ajustes, ao lado da versão.",
                    "Un mensaje de \"Novedades\" aparece una vez tras cada actualización. Puedes volver a leerlo cuando quieras en Ajustes, junto a la versión.",
                ),
            ),
        ),
        ReleaseNote(
            version = "0.23.2",
            fixes = listOf(
                LocalizedText(
                    "A habit that is checked off now shows its points with just \"+\".",
                    "Um hábito marcado como feito agora mostra seus pontos só com \"+\".",
                    "Un hábito marcado como hecho ahora muestra sus puntos solo con \"+\".",
                ),
            ),
        ),
        ReleaseNote(
            version = "0.23.1",
            features = listOf(
                LocalizedText(
                    "Habit days you missed stand out in terracotta with a \"−\" showing the points they cost.",
                    "Dias de hábito perdidos aparecem em terracota com um \"−\" mostrando os pontos que custaram.",
                    "Los días de hábito perdidos se ven en terracota con un \"−\" que muestra los puntos que costaron.",
                ),
            ),
        ),
        ReleaseNote(
            version = "0.23.0",
            features = listOf(
                LocalizedText(
                    "Home now lists the rewards you redeemed today.",
                    "A tela inicial agora lista as recompensas resgatadas hoje.",
                    "Inicio ahora lista las recompensas canjeadas hoy.",
                ),
            ),
        ),
    )

    /** The note for [version], or null when that release has none. */
    fun forVersion(version: String): ReleaseNote? = all.firstOrNull { it.version == version }
}

/**
 * Decides when the "What's new" popup opens: once per installed version, never on a fresh install
 * (a new user has nothing to compare with), and only for versions that have a note.
 */
class ReleaseNotesTracker(
    private val settings: AppSettings,
    private val appInfo: AppInfo,
) {
    /** The note to pop up now, or null. Call [markSeen] when the user dismisses it. */
    fun pending(): ReleaseNote? {
        val current = appInfo.version
        if (current.isBlank()) return null
        val last = settings.lastSeenReleaseNotesVersion
        if (last == current) return null
        if (last == null && !settings.onboardingComplete) {
            settings.lastSeenReleaseNotesVersion = current
            return null
        }
        val note = ReleaseNotes.forVersion(current)
        if (note == null) {
            settings.lastSeenReleaseNotesVersion = current
            return null
        }
        return note
    }

    fun markSeen() {
        val current = appInfo.version
        if (current.isNotBlank()) settings.lastSeenReleaseNotesVersion = current
    }

    /** The note for the installed version, for the Settings button; null when there is none. */
    fun current(): ReleaseNote? = ReleaseNotes.forVersion(appInfo.version)
}
