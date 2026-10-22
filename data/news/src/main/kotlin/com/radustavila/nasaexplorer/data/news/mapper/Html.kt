package com.radustavila.nasaexplorer.data.news.mapper

/**
 * Just enough HTML handling for feed text: WordPress writes descriptions and articles as HTML, and
 * the app shows plain text and picks pictures out of the markup. Regular expressions suffice for
 * WordPress's regular output; nothing here has to understand arbitrary HTML.
 */
internal object Html {

    /** Tags removed, block ends turned into spaces, entities decoded, whitespace collapsed. */
    fun toPlainText(html: String): String =
        decodeEntities(
            html.replace(SCRIPT_OR_STYLE, " ")
                .replace(TAG, " "),
        )
            .replace(WHITESPACE, " ")
            .trim()

    /** `&amp;`, `&#8217;`, `&#x2019;` and the named entities NASA's feeds use, decoded. */
    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return ENTITY.replace(text) { match ->
            val name = match.groupValues[1]
            val decoded = when {
                name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(HEX)?.toCodePointString()
                name.startsWith("#") -> name.drop(1).toIntOrNull()?.toCodePointString()
                else -> NAMED[name]
            }
            decoded ?: match.value
        }
    }

    /** The value of [attribute] in a single tag's markup, entities decoded. */
    fun attribute(tag: String, attribute: String): String? =
        Regex("""\s$attribute\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(tag)
            ?.groupValues?.get(1)
            ?.let(::decodeEntities)

    private fun Int.toCodePointString(): String? =
        if (Character.isValidCodePoint(this) && this != 0) String(Character.toChars(this)) else null

    private const val HEX = 16

    private val SCRIPT_OR_STYLE = Regex(
        """<(script|style)\b.*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val TAG = Regex("""<[^>]*>""")
    private val WHITESPACE = Regex("""\s+""")
    private val ENTITY = Regex("""&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);""")

    private val NAMED = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "hellip" to "…",
        "rsquo" to "’",
        "lsquo" to "‘",
        "rdquo" to "”",
        "ldquo" to "“",
        "ndash" to "–",
        "mdash" to "—",
        "deg" to "°",
        "copy" to "©",
    )
}
