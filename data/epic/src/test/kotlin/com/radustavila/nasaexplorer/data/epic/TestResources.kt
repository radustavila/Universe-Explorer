package com.radustavila.nasaexplorer.data.epic

/** Recorded EPIC responses (captured from epic.gsfc.nasa.gov on 2026-10-19) under `src/test/resources/epic`. */
internal object RecordedResponses {
    /** `/api/natural`: the 13 photos of 2026-10-16. */
    val NATURAL_LATEST: String get() = read("natural_latest.json")

    /** The first entries of `/api/natural/all`, newest 2026-10-16. */
    val NATURAL_ALL: String get() = read("natural_all.json")

    /** The first entries of `/api/enhanced/all`, including a repeated day as the live listing has. */
    val ENHANCED_ALL: String get() = read("enhanced_all.json")

    private fun read(name: String): String =
        requireNotNull(RecordedResponses::class.java.classLoader?.getResource("epic/$name")) {
            "Missing test resource epic/$name"
        }.readText()
}
