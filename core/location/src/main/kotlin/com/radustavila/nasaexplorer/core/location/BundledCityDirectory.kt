package com.radustavila.nasaexplorer.core.location

import com.radustavila.nasaexplorer.core.domain.location.CityDirectory
import com.radustavila.nasaexplorer.core.model.City
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

/**
 * Searches [BUNDLED_CITIES] by name or country, ignoring case and diacritics ("iasi" finds Iași),
 * names that start with the query first.
 */
class BundledCityDirectory @Inject constructor() : CityDirectory {

    private val index: List<Pair<City, String>> = BUNDLED_CITIES.map { city -> city to city.name.searchKey() }

    override suspend fun search(query: String, limit: Int): List<City> {
        val key = query.searchKey()
        if (key.isEmpty()) return emptyList()
        val byName = index.filter { (_, name) -> name.contains(key) }
            .sortedWith(compareBy<Pair<City, String>> { (_, name) -> !name.startsWith(key) }.thenBy { it.second })
            .map { it.first }
        val byCountry = BUNDLED_CITIES.filter { it.country.searchKey().startsWith(key) && it !in byName }
        return (byName + byCountry).take(limit)
    }

    private fun String.searchKey(): String =
        Normalizer.normalize(trim(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)

    private companion object {
        val COMBINING_MARKS = Regex("\\p{Mn}+")
    }
}
