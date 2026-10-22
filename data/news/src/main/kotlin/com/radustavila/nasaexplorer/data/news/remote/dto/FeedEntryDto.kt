package com.radustavila.nasaexplorer.data.news.remote.dto

/**
 * One `<item>` of an RSS 2.0 feed or `<entry>` of an Atom feed, as raw text: the parser keeps what
 * it finds and judges nothing, so a malformed entry is dropped during mapping instead of failing the
 * whole feed. Every field is optional for the same reason.
 */
data class FeedEntryDto(
    val title: String? = null,
    /** RSS `<link>` text, or Atom `<link rel="alternate" href>`. */
    val link: String? = null,
    /** RSS `<pubDate>` (RFC 1123) or Atom `<published>` (ISO-8601). */
    val published: String? = null,
    /** RSS `<description>`: plain text or HTML. */
    val description: String? = null,
    /** RSS `<content:encoded>`: the whole article as HTML, read for its first picture. */
    val contentHtml: String? = null,
    /** RSS `<enclosure url>` (the Image of the Day's full-size picture). */
    val enclosureUrl: String? = null,
    val enclosureType: String? = null,
    /** YouTube `<yt:videoId>`. */
    val videoId: String? = null,
    /** Media RSS `<media:thumbnail url>`. */
    val thumbnailUrl: String? = null,
    /** Media RSS `<media:description>`: a video's description, plain text. */
    val mediaDescription: String? = null,
)
