package com.radustavila.nasaexplorer.core.domain.search

import java.util.Locale

/**
 * The text rules every search source and the search screen share, so that what matches, how it
 * ranks and what is highlighted agree: a query is split into lowercase terms, and an item matches
 * when each term occurs somewhere in it, ignoring case.
 */
object SearchText {

    /** The query's words, lowercase and without punctuation: "Kepler-22 b" is `kepler`, `22`, `b`. */
    fun terms(query: String): List<String> =
        query.lowercase(Locale.ROOT).split(NON_WORD).filter { it.isNotEmpty() }.distinct()

    /** True when every term occurs in at least one of [texts]. */
    fun matchesAll(terms: List<String>, vararg texts: String?): Boolean =
        terms.isNotEmpty() && terms.all { term -> texts.any { it?.contains(term, ignoreCase = true) == true } }

    /** Ranges of [text] covered by any term, sorted and merged, for highlighting. */
    fun matchRanges(text: String, terms: List<String>): List<IntRange> {
        if (text.isEmpty() || terms.isEmpty()) return emptyList()
        val ranges = terms.flatMap { term ->
            generateSequence(text.indexOf(term, ignoreCase = true).takeIf { it >= 0 }) { previous ->
                text.indexOf(term, startIndex = previous + term.length, ignoreCase = true).takeIf { it >= 0 }
            }.map { start -> start until start + term.length }.toList()
        }.sortedBy { it.first }
        return ranges.fold(mutableListOf()) { merged, range ->
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
            merged
        }
    }

    /**
     * About [maxLength] characters of [text] around its first match, with ellipses where it was cut;
     * the start of the text when nothing in it matches. Null for a blank text.
     */
    fun snippet(text: String?, terms: List<String>, maxLength: Int = SNIPPET_LENGTH): String? {
        val clean = text?.replace(WHITESPACE, " ")?.trim()
        if (clean.isNullOrEmpty()) return null
        if (clean.length <= maxLength) return clean

        val firstMatch = matchRanges(clean, terms).firstOrNull()?.first ?: 0
        val start = (firstMatch - maxLength / CONTEXT_BEFORE_DIVISOR).coerceIn(0, clean.length - maxLength)
        val end = start + maxLength
        // Cut between words rather than through one, as long as the match stays inside.
        val from = if (start == 0) 0 else clean.indexOf(' ', start).let { if (it in start until firstMatch) it + 1 else start }
        val to = if (end == clean.length) end else clean.lastIndexOf(' ', end).let { if (it > firstMatch) it else end }
        return buildString {
            if (from > 0) append(ELLIPSIS)
            append(clean, from, to)
            if (to < clean.length) append(ELLIPSIS)
        }
    }

    /**
     * How well [title] answers [query], higher is better: the whole title, then its beginning, then
     * every term starting a word of it, then every term inside it, then some; a hit that matched
     * only outside its title ranks last.
     */
    fun score(title: String, query: String): Int {
        val terms = terms(query)
        if (terms.isEmpty()) return 0
        val titleTerms = terms(title)
        val normalizedTitle = titleTerms.joinToString(" ")
        val normalizedQuery = terms.joinToString(" ")
        return when {
            normalizedTitle == normalizedQuery -> SCORE_EXACT
            normalizedTitle.startsWith(normalizedQuery) -> SCORE_PREFIX
            terms.all { term -> titleTerms.any { it.startsWith(term) } } -> SCORE_WORD_PREFIX
            terms.all { title.contains(it, ignoreCase = true) } -> SCORE_CONTAINS
            terms.any { title.contains(it, ignoreCase = true) } -> SCORE_PARTIAL
            else -> SCORE_ELSEWHERE
        }
    }

    const val SCORE_EXACT = 100
    const val SCORE_PREFIX = 80
    const val SCORE_WORD_PREFIX = 60
    const val SCORE_CONTAINS = 40
    const val SCORE_PARTIAL = 20
    const val SCORE_ELSEWHERE = 10

    private const val SNIPPET_LENGTH = 140
    private const val CONTEXT_BEFORE_DIVISOR = 4
    private const val ELLIPSIS = "…"
    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val WHITESPACE = Regex("""\s+""")
}
