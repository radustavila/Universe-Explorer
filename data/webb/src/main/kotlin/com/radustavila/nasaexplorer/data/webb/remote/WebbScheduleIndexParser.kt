package com.radustavila.nasaexplorer.data.webb.remote

import okhttp3.HttpUrl

/**
 * Finds the weekly schedule files on STScI's observing schedules page. File names cannot be
 * predicted (recent ones are `YYYYMMDD_report_YYYYMMDD.txt`, the first years' an OP package id such
 * as `2219105f02_report_20220710.txt`), so the page's links are read in page order, which is newest
 * first, and resolved against the page's own URL.
 */
object WebbScheduleIndexParser {

    fun scheduleLinks(html: String, pageUrl: HttpUrl): List<HttpUrl> = LINK.findAll(html)
        .mapNotNull { pageUrl.resolve(it.groupValues[1].replace("&amp;", "&")) }
        .distinct()
        .toList()

    private val LINK = Regex(
        """href\s*=\s*["']([^"']*/observing-schedules/_documents/[^"']+\.txt)["']""",
        RegexOption.IGNORE_CASE,
    )
}
