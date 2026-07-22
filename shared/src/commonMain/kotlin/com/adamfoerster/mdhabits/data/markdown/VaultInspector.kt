package com.adamfoerster.mdhabits.data.markdown

import com.adamfoerster.mdhabits.storage.VaultFileSystem
import com.adamfoerster.mdhabits.storage.VaultSelection

/**
 * Peeks into a picked-but-not-yet-active folder, before the selection is persisted as the vault.
 * Onboarding uses this to recognize an existing vault (any decodable `theme/<year>.md` note) and
 * skip the setup steps, so a returning user's notes are never re-seeded.
 */
class VaultInspector(private val vault: VaultFileSystem) {

    suspend fun hasAnnualTheme(selection: VaultSelection): Boolean =
        vault.listIn(selection.ref, THEME_DIR).any { name ->
            vault.readIn(selection.ref, THEME_DIR, name)
                ?.let(MarkdownCodecs::decodeTheme) != null
        }

    private companion object {
        /** Same folder [MarkdownThemeRepository] persists themes into. */
        const val THEME_DIR = "theme"
    }
}
