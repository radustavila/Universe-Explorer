package com.radustavila.nasaexplorer.core.database.di

import android.content.Context
import androidx.room.Room
import com.radustavila.nasaexplorer.core.database.NasaDatabase
import com.radustavila.nasaexplorer.core.database.dao.ApodDao
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
        Room.databaseBuilder(context, NasaDatabase::class.java, NasaDatabase.NAME).build()

    @Provides
    fun provideApodDao(database: NasaDatabase): ApodDao = database.apodDao()
}
