package com.adamfoerster.mdhabits.core

import kotlin.random.Random

/** Generates a reasonably-unique id with a domain [prefix], e.g. "L-1a2b3c4d". */
fun newId(prefix: String): String {
    val suffix = buildString {
        repeat(8) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
    }
    return "$prefix-$suffix"
}

private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
