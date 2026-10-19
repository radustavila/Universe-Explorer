package com.radustavila.nasaexplorer.data.launches

/**
 * `GET /2.3.0/launches/upcoming/?limit=5&mode=detailed&hide_recent_previous=true`, recorded from
 * lldev.thespacedevs.com on 2026-10-19 and trimmed of nested fields the app never reads.
 */
internal object LaunchFixtures {
    val UPCOMING_DETAILED: String = readResource("ll2/upcoming_detailed.json")

    private fun readResource(path: String): String =
        checkNotNull(LaunchFixtures::class.java.classLoader?.getResource(path)) { "Missing $path" }
            .readText()
}
