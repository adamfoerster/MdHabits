package com.adamfoerster.mdhabits.core.i18n

import com.adamfoerster.mdhabits.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The device language tag (BCP-47), e.g. "pt-BR". */
expect fun systemLanguageTag(): String

/**
 * Holds the effective UI language. Starts from the user's saved choice, falling back to the system
 * language, and finally to English. Changing it here recomposes the app and persists the choice.
 */
class LocaleController(private val settings: AppSettings) {
    private val _lang = MutableStateFlow(resolve(settings.languageTag))
    val lang: StateFlow<Lang> = _lang.asStateFlow()

    /** null follows the system language. */
    fun setLanguage(tag: String?) {
        settings.languageTag = tag
        _lang.value = resolve(tag)
    }

    private fun resolve(savedTag: String?): Lang =
        langFromTag(savedTag ?: systemLanguageTag())
}
