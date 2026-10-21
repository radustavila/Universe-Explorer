package com.radustavila.nasaexplorer.core.database.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.search.readLocally
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Saved items of every source, by the title snapshot taken when they were saved, so a favourite is
 * found even after its source's cache has dropped it. Most recently saved first.
 */
class FavoritesSearchableSource @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) : SearchableSource {

    override val source = SearchSource.FAVORITES

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> = readLocally {
        val terms = SearchText.terms(query)
        favoritesRepository.observeFavorites().first()
            .asSequence()
            .filter { SearchText.matchesAll(terms, it.title) }
            .take(limit)
            .map { item ->
                SearchHit(
                    source = source,
                    id = "${item.source.key}/${item.itemId}",
                    title = item.title,
                    target = SearchTarget.Favorite(item),
                    date = item.itemId.takeIf { item.source == FavoriteSource.APOD }?.toLocalDateOrNull(),
                    imageUrl = item.imageUrl,
                )
            }
            .toList()
    }

    private fun String.toLocalDateOrNull(): LocalDate? = try {
        LocalDate.parse(this)
    } catch (_: DateTimeParseException) {
        null
    }
}
