package com.radustavila.nasaexplorer.core.database.di

import android.content.Context
import androidx.room.Room
import com.radustavila.nasaexplorer.core.database.NasaDatabase
import com.radustavila.nasaexplorer.core.database.dao.ApodDao
import com.radustavila.nasaexplorer.core.database.dao.EpicDao
import com.radustavila.nasaexplorer.core.database.dao.FavoriteDao
import com.radustavila.nasaexplorer.core.database.migration.NasaDatabaseMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideNasaDatabase(@ApplicationContext context: Context): NasaDatabase =
        Room.databaseBuilder(context, NasaDatabase::class.java, NasaDatabase.NAME)
            // No destructive fallback: the cache is the offline experience, and favourites only
            // exist here.
            .addMigrations(*NasaDatabaseMigrations.ALL)
            .build()

    @Provides
    fun provideApodDao(database: NasaDatabase): ApodDao = database.apodDao()

    @Provides
    fun provideFavoriteDao(database: NasaDatabase): FavoriteDao = database.favoriteDao()
    fun provideEpicDao(database: NasaDatabase): EpicDao = database.epicDao()
}
