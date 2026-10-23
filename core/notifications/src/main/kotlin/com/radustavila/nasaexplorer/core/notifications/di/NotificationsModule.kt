package com.radustavila.nasaexplorer.core.notifications.di

import com.radustavila.nasaexplorer.core.domain.announcement.AuroraAlertNotifier
import com.radustavila.nasaexplorer.core.domain.announcement.NewApodNotifier
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeAlertNotifier
import com.radustavila.nasaexplorer.core.domain.sky.IssPassNotifier
import com.radustavila.nasaexplorer.core.domain.sky.SatellitePassNotifier
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventNotifier
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingAlertNotifier
import com.radustavila.nasaexplorer.core.notifications.SystemTrayAuroraAlertNotifier
import com.radustavila.nasaexplorer.core.notifications.SystemTrayEarthquakeAlertNotifier
import com.radustavila.nasaexplorer.core.notifications.SystemTrayIssPassNotifier
import com.radustavila.nasaexplorer.core.notifications.SystemTrayNewApodNotifier
import com.radustavila.nasaexplorer.core.notifications.SystemTraySatellitePassNotifier
import com.radustavila.nasaexplorer.core.notifications.SystemTraySkyEventNotifier
import com.radustavila.nasaexplorer.core.notifications.SystemTrayStargazingAlertNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface NotificationsModule {

    @Binds
    fun bindNewApodNotifier(impl: SystemTrayNewApodNotifier): NewApodNotifier

    @Binds
    fun bindAuroraAlertNotifier(impl: SystemTrayAuroraAlertNotifier): AuroraAlertNotifier

    @Binds
    fun bindIssPassNotifier(impl: SystemTrayIssPassNotifier): IssPassNotifier

    @Binds
    fun bindSatellitePassNotifier(impl: SystemTraySatellitePassNotifier): SatellitePassNotifier

    @Binds
    fun bindSkyEventNotifier(impl: SystemTraySkyEventNotifier): SkyEventNotifier

    @Binds
    fun bindStargazingAlertNotifier(impl: SystemTrayStargazingAlertNotifier): StargazingAlertNotifier

    @Binds
    fun bindEarthquakeAlertNotifier(impl: SystemTrayEarthquakeAlertNotifier): EarthquakeAlertNotifier
}
