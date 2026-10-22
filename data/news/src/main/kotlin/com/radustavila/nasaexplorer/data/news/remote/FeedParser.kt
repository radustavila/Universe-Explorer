package com.radustavila.nasaexplorer.data.news.remote

import com.radustavila.nasaexplorer.data.news.remote.dto.FeedEntryDto
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Reads the entries of an RSS 2.0 or Atom feed: NASA's WordPress feeds are RSS with the `content`
 * and Media RSS extensions, YouTube's is Atom with its own `yt` namespace. One parser serves both,
 * because an entry is just a bag of the few elements the app reads.
 *
 * Elements are matched by namespace, not prefix. Unknown elements are walked into rather than
 * skipped, so fields inside a container (YouTube's `<media:group>`) are found; anything else is
 * ignored. Entries are returned as found: judging them is the mapper's job.
 */
internal object FeedParser {

    /**
     * @throws XmlPullParserException when the document is not well-formed (e.g. cut off). Nothing is
     * returned then, not even the entries before the fault: a broken answer must not replace a
     * cached feed with part of it.
     */
    fun parse(input: InputStream, charset: String? = null): List<FeedEntryDto> {
        val parser = newParser().apply { setInput(input, charset) }
        val entries = mutableListOf<FeedEntryDto>()
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.isEntryStart()) entries += parser.readEntry()
            event = parser.next()
        }
        return entries
    }

    private fun newParser(): XmlPullParser {
        val parser = XmlPullParserFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newPullParser()
        // Lets an HTML entity (`&nbsp;`) slip through as text instead of failing the whole feed;
        // the mapper decodes it. Parsers without the feature stay strict.
        runCatching { parser.setFeature(FEATURE_RELAXED, true) }
        return parser
    }

    private fun XmlPullParser.isEntryStart(): Boolean =
        name == "item" && namespace.isNullOrEmpty() || name == "entry" && namespace == ATOM

    /** Reads from an entry's start tag to its end tag. */
    private fun XmlPullParser.readEntry(): FeedEntryDto {
        val entryDepth = depth
        var entry = FeedEntryDto()
        var event = next()
        while (!(event == XmlPullParser.END_TAG && depth == entryDepth)) {
            if (event == XmlPullParser.END_DOCUMENT) throw XmlPullParserException("Feed ends inside an entry")
            if (event == XmlPullParser.START_TAG) entry = readField(entry)
            event = next()
        }
        return entry
    }

    /**
     * Fills in the field the current start tag holds, if it is one the app reads. Elements read as
     * text are consumed up to their end tag; any other is left open so its children are visited.
     */
    private fun XmlPullParser.readField(entry: FeedEntryDto): FeedEntryDto = when (namespace.orEmpty()) {
        "" -> readRssField(entry)
        ATOM -> readAtomField(entry)
        CONTENT -> if (name == "encoded") entry.copy(contentHtml = readText()) else entry
        YOUTUBE -> if (name == "videoId") entry.copy(videoId = readText()) else entry
        MEDIA -> readMediaField(entry)
        else -> entry
    }

    private fun XmlPullParser.readRssField(entry: FeedEntryDto): FeedEntryDto = when (name) {
        "title" -> entry.copy(title = readText())
        "link" -> entry.copy(link = readText())
        "pubDate" -> entry.copy(published = readText())
        "description" -> entry.copy(description = readText())
        "enclosure" -> entry.copy(
            enclosureUrl = getAttributeValue(null, "url"),
            enclosureType = getAttributeValue(null, "type"),
        )
        else -> entry
    }

    private fun XmlPullParser.readAtomField(entry: FeedEntryDto): FeedEntryDto = when (name) {
        "title" -> entry.copy(title = readText())
        "published" -> entry.copy(published = readText())
        "link" -> {
            val rel = getAttributeValue(null, "rel")
            if (rel == null || rel == "alternate") entry.copy(link = getAttributeValue(null, "href")) else entry
        }
        else -> entry
    }

    private fun XmlPullParser.readMediaField(entry: FeedEntryDto): FeedEntryDto = when (name) {
        "thumbnail" -> entry.copy(thumbnailUrl = entry.thumbnailUrl ?: getAttributeValue(null, "url"))
        "description" -> entry.copy(mediaDescription = readText())
        else -> entry
    }

    /**
     * The text of the current element, CDATA included, up to its end tag. Markup nested inside it
     * (an RSS description that is unescaped HTML) is skipped, its text kept.
     */
    private fun XmlPullParser.readText(): String {
        val elementDepth = depth
        val text = StringBuilder()
        var event = next()
        while (!(event == XmlPullParser.END_TAG && depth == elementDepth)) {
            when (event) {
                XmlPullParser.TEXT -> text.append(this.text)
                XmlPullParser.END_DOCUMENT -> throw XmlPullParserException("Feed ends inside an element")
            }
            event = next()
        }
        return text.toString()
    }

    private const val FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed"
    private const val ATOM = "http://www.w3.org/2005/Atom"
    private const val CONTENT = "http://purl.org/rss/1.0/modules/content/"
    private const val MEDIA = "http://search.yahoo.com/mrss/"
    private const val YOUTUBE = "http://www.youtube.com/xml/schemas/2015"
}
