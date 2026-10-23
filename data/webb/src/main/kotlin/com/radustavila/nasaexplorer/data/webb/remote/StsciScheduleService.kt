package com.radustavila.nasaexplorer.data.webb.remote

import okhttp3.HttpUrl
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * STScI's website (www.stsci.edu, no key, no API): the JWST observing schedules page and the weekly
 * plain-text files it links to, both by full URL. Bodies are read whole into memory (the page is
 * ~250 KB, a week ~11–20 KB).
 */
interface StsciScheduleService {
    @GET
    suspend fun fetch(@Url url: String): ResponseBody
}

/** Where the index page lives; the app's is a `BuildConfig` field, tests point at a local server. */
data class WebbScheduleIndexUrl(val url: HttpUrl)
