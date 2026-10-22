package com.radustavila.nasaexplorer.data.news.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Downloads a feed by its full URL: the five feeds live on three hosts (www.nasa.gov,
 * science.nasa.gov, www.youtube.com), each with its own rate budget, so the base URL is only a
 * placeholder Retrofit requires.
 *
 * The body is XML for [FeedParser]; Retrofit reads it into memory (the largest feed is ~260 KB).
 */
interface NewsFeedService {
    @GET
    suspend fun feed(@Url url: String): ResponseBody
}
