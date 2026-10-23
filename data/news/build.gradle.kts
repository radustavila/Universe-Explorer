plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
    id("nasaexplorer.android.room")
}

android {
    namespace = "com.radustavila.nasaexplorer.data.news"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Public RSS/Atom feeds, not documented APIs: no key, checked 2026-10-22.
        buildConfigField("String", "NASA_STORIES_FEED_URL", "\"https://www.nasa.gov/feed/\"")
        buildConfigField("String", "NASA_NEWS_RELEASES_FEED_URL", "\"https://www.nasa.gov/news-release/feed/\"")
        buildConfigField("String", "NASA_SCIENCE_FEED_URL", "\"https://science.nasa.gov/feed/\"")
        buildConfigField("String", "NASA_IMAGE_OF_THE_DAY_FEED_URL", "\"https://www.nasa.gov/feeds/iotd-feed/\"")
        buildConfigField(
            "String",
            "NASA_YOUTUBE_FEED_URL",
            "\"https://www.youtube.com/feeds/videos.xml?channel_id=UCLA_DiR1FfKNvjuUpBHmylQ\"",
        )
        // NASA's podcast feeds (F21): nasa.gov redirects each to Megaphone, which hosts the audio.
        buildConfigField(
            "String",
            "PODCAST_HOUSTON_FEED_URL",
            "\"https://www.nasa.gov/feeds/podcasts/houston-we-have-a-podcast\"",
        )
        buildConfigField(
            "String",
            "PODCAST_CURIOUS_UNIVERSE_FEED_URL",
            "\"https://www.nasa.gov/feeds/podcasts/curious-universe\"",
        )
        buildConfigField(
            "String",
            "PODCAST_SMALL_STEPS_FEED_URL",
            "\"https://www.nasa.gov/feeds/podcasts/small-steps-giant-leaps\"",
        )
    }
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.cache)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.androidx.work.testing)
}
