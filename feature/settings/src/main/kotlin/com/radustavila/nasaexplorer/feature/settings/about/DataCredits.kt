package com.radustavila.nasaexplorer.feature.settings.about

import androidx.annotation.StringRes
import com.radustavila.nasaexplorer.feature.settings.R

/** One source the app's data, imagery or computations come from. */
internal data class DataCredit(
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val url: String,
)

/** Every remote source and bundled engine the app relies on, in the order a user meets them. */
internal val DATA_CREDITS: List<DataCredit> = listOf(
    DataCredit(R.string.credit_apod, R.string.credit_apod_description, "https://apod.nasa.gov/apod/"),
    DataCredit(R.string.credit_epic, R.string.credit_epic_description, "https://epic.gsfc.nasa.gov/"),
    DataCredit(R.string.credit_gibs, R.string.credit_gibs_description, "https://nasa-gibs.github.io/gibs-api-docs/"),
    DataCredit(R.string.credit_eonet, R.string.credit_eonet_description, "https://eonet.gsfc.nasa.gov/"),
    DataCredit(R.string.credit_nasa_images, R.string.credit_nasa_images_description, "https://images.nasa.gov/"),
    DataCredit(R.string.credit_exoplanets, R.string.credit_exoplanets_description, "https://exoplanetarchive.ipac.caltech.edu/"),
    DataCredit(R.string.credit_swpc, R.string.credit_swpc_description, "https://www.swpc.noaa.gov/"),
    DataCredit(R.string.credit_donki, R.string.credit_donki_description, "https://kauai.ccmc.gsfc.nasa.gov/DONKI/"),
    DataCredit(R.string.credit_jpl, R.string.credit_jpl_description, "https://cneos.jpl.nasa.gov/"),
    DataCredit(R.string.credit_dsn, R.string.credit_dsn_description, "https://eyes.nasa.gov/dsn/dsn.html"),
    DataCredit(R.string.credit_horizons, R.string.credit_horizons_description, "https://ssd.jpl.nasa.gov/horizons/"),
    DataCredit(R.string.credit_mars, R.string.credit_mars_description, "https://mars.nasa.gov/"),
    DataCredit(R.string.credit_celestrak, R.string.credit_celestrak_description, "https://celestrak.org/"),
    DataCredit(R.string.credit_met_norway, R.string.credit_met_norway_description, "https://api.met.no/"),
    DataCredit(R.string.credit_wheretheiss, R.string.credit_wheretheiss_description, "https://wheretheiss.at/"),
    DataCredit(R.string.credit_launch_library, R.string.credit_launch_library_description, "https://thespacedevs.com/llapi"),
    DataCredit(R.string.credit_nasa_news, R.string.credit_nasa_news_description, "https://www.nasa.gov/news/"),
    DataCredit(R.string.credit_nasa_youtube, R.string.credit_nasa_youtube_description, "https://www.youtube.com/@NASA"),
    DataCredit(R.string.credit_astronomy_engine, R.string.credit_astronomy_engine_description, "https://github.com/cosinekitty/astronomy"),
    DataCredit(R.string.credit_hyg, R.string.credit_hyg_description, "https://codeberg.org/astronexus/hyg"),
    DataCredit(
        R.string.credit_d3_celestial,
        R.string.credit_d3_celestial_description,
        "https://github.com/ofrohn/d3-celestial",
    ),
    DataCredit(R.string.credit_maplibre, R.string.credit_maplibre_description, "https://maplibre.org/"),
)
