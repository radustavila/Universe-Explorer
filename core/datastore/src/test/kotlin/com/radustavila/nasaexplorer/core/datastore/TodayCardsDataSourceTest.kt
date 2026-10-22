package com.radustavila.nasaexplorer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.radustavila.nasaexplorer.core.model.TodayCardId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TodayCardsDataSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var todayCards: TodayCardsDataSource

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { temporaryFolder.newFile("today_cards.preferences_pb") },
        )
        todayCards = TodayCardsDataSource(dataStore)
    }

    @Test
    fun `an untouched install shows the cards Today always had`() = runTest {
        assertEquals(TodayCardId.DEFAULT, todayCards.todayCards.first())
    }

    @Test
    fun `persists the order and the choice of cards`() = runTest {
        val picked = listOf(TodayCardId.ISS_NOW, TodayCardId.NEXT_LAUNCHES, TodayCardId.EARTH_TODAY)
        todayCards.setTodayCards(picked)

        assertEquals(picked, todayCards.todayCards.first())
    }

    @Test
    fun `hiding every card is kept, not mistaken for the default`() = runTest {
        todayCards.setTodayCards(emptyList())

        assertEquals(emptyList<TodayCardId>(), todayCards.todayCards.first())
    }

    @Test
    fun `unknown names are skipped and duplicates collapse`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("enabled_cards")] = "SPACE_WEATHER,MARS_ROVER,SPACE_WEATHER,EARTH_TODAY"
        }

        assertEquals(listOf(TodayCardId.SPACE_WEATHER, TodayCardId.EARTH_TODAY), todayCards.todayCards.first())
    }
}
