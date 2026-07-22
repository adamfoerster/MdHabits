package com.adamfoerster.mdhabits.storage

/** A folder the user chose to store the Markdown vault in. */
data class VaultSelection(val displayName: String, val ref: String)

/**
 * Launches the OS folder picker and persists access. Implemented per platform (Android SAF tree
 * picker; iOS document picker + security-scoped bookmark). Returns null if the user cancels.
 */
interface VaultPicker {
    suspend fun pickVault(): VaultSelection?
}
