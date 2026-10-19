package com.radustavila.nasaexplorer.core.model

import java.time.Instant

/**
 * An upcoming rocket launch, as listed by Launch Library 2.
 *
 * Launches are a deliberately small side section of the app: only what the next-launches card shows
 * is modelled here.
 */
data class Launch(
    /** Launch Library 2 identifier (a UUID). */
    val id: String,
    val name: String,
    /** "No earlier than": the current target time. It moves as the launch date firms up. */
    val net: Instant,
    val status: LaunchStatus,
    val rocket: String,
    val pad: String,
    val imageUrl: String? = null,
    /** First livestream listed for the launch, if any is announced yet. */
    val livestreamUrl: String? = null,
    /** Public web page about the launch, opened when there is no livestream. */
    val pageUrl: String,
) {
    /** Where tapping the launch leads: the livestream when there is one, the launch page otherwise. */
    val linkUrl: String get() = livestreamUrl ?: pageUrl
}

/** Launch Library 2 launch statuses, keyed by their stable numeric ids. */
enum class LaunchStatus(val id: Int) {
    GO(1),
    TBD(2),
    SUCCESS(3),
    FAILURE(4),
    HOLD(5),
    IN_FLIGHT(6),
    PARTIAL_FAILURE(7),
    TBC(8),
    UNKNOWN(-1),
    ;

    companion object {
        fun fromId(id: Int?): LaunchStatus = entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}
