package com.radustavila.nasaexplorer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.radustavila.nasaexplorer.core.datastore.di.TodayCardsStore
import com.radustavila.nasaexplorer.core.domain.repository.TodayCardsRepository
import com.radustavila.nasaexplorer.core.model.TodayCardId
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The Today cards the user picked, as their names joined by commas in one key. No key means the
 * user never customized Today ([TodayCardId.DEFAULT]); an empty value means every card is hidden.
 * Names this version does not know (a card dropped since, or added by a newer version restored from
 * a backup) are skipped.
 */
@Singleton
class TodayCardsDataSource @Inject constructor(
    @TodayCardsStore private val dataStore: DataStore<Preferences>,
) : TodayCardsRepository {

    override val todayCards: Flow<List<TodayCardId>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> preferences[ENABLED_CARDS]?.let(::decode) ?: TodayCardId.DEFAULT }
        .distinctUntilChanged()

    override suspend fun setTodayCards(cards: List<TodayCardId>) {
        try {
            dataStore.edit { preferences ->
                preferences[ENABLED_CARDS] = cards.distinct().joinToString(SEPARATOR) { it.name }
            }
        } catch (_: IOException) {
            // Today keeps its previous cards; the user can save again.
        }
    }

    private fun decode(value: String): List<TodayCardId> = value
        .split(SEPARATOR)
        .mapNotNull { name -> TodayCardId.entries.firstOrNull { it.name == name } }
        .distinct()

    private companion object {
        val ENABLED_CARDS = stringPreferencesKey("enabled_cards")
        const val SEPARATOR = ","
    }
}
