package com.chamika.dashtune.di

import android.accounts.AccountManager
import android.content.Context
import com.chamika.dashtune.R
import com.chamika.dashtune.auth.JellyfinAccountManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.chamika.dashtune.media.AudioCapabilities
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo

@Module
@InstallIn(SingletonComponent::class)
class DashTuneModule {

    @Provides
    fun provideJellyfin(@ApplicationContext appContext: Context): Jellyfin {
        val version =
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName

        return createJellyfin {
            clientInfo = ClientInfo(appContext.getString(R.string.app_name), version ?: "unknown")
            deviceInfo = androidDevice(appContext)
            context = appContext
        }
    }

    @Provides
    fun provideAccountManager(@ApplicationContext appContext: Context): JellyfinAccountManager {
        return JellyfinAccountManager(AccountManager.get(appContext))
    }

    @Provides
    fun provideAudioCapabilities(): AudioCapabilities = AudioCapabilities()
}
