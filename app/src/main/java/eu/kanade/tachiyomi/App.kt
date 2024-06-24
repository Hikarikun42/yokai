package eu.kanade.tachiyomi

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.multidex.MultiDex
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import eu.kanade.tachiyomi.appwidget.TachiyomiWidgetManager
import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.data.coil.BufferedSourceFetcher
import eu.kanade.tachiyomi.data.coil.CoilDiskCache
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.coil.MangaCoverKeyer
import eu.kanade.tachiyomi.data.coil.TachiyomiImageDecoder
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.library.LibraryPresenter
import eu.kanade.tachiyomi.ui.recents.RecentsPresenter
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.source.SourcePresenter
import eu.kanade.tachiyomi.util.manga.MangaCoverMetadata
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.notification
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.conscrypt.Conscrypt
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.core.CrashlyticsLogWriter
import yokai.core.di.AppModule
import yokai.core.di.DomainModule
import yokai.core.di.PreferenceModule
import yokai.core.migration.Migrator
import yokai.core.migration.migrations.migrations
import yokai.domain.base.BasePreferences
import yokai.i18n.MR
import yokai.util.lang.getString
import java.security.Security

open class App : Application(), DefaultLifecycleObserver, SingletonImageLoader.Factory {

    val preferences: PreferencesHelper by injectLazy()
    val basePreferences: BasePreferences by injectLazy()

    private val disableIncognitoReceiver = DisableIncognitoReceiver()

    @SuppressLint("LaunchActivityFromNotification")
    override fun onCreate() {
        super<Application>.onCreate()

        if (!BuildConfig.DEBUG) Logger.addLogWriter(CrashlyticsLogWriter())

        // TLS 1.3 support for Android 10 and below
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        // Avoid potential crashes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val process = getProcessName()
            if (packageName != process) WebView.setDataDirectorySuffix(process)
        }

        Injekt.apply {
            importModule(PreferenceModule(this@App))
            importModule(AppModule(this@App))
            importModule(DomainModule())
        }

        basePreferences.crashReport().changes()
            .onEach {
                try {
                    Firebase.crashlytics.setCrashlyticsCollectionEnabled(it)
                } catch (e: Exception) {
                    // Probably already enabled/disabled
                }
            }
            .launchIn(ProcessLifecycleOwner.get().lifecycleScope)

        setupNotificationChannels()

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        MangaCoverMetadata.load()
        preferences.nightMode().changes()
            .onEach { AppCompatDelegate.setDefaultNightMode(it) }
            .launchIn(ProcessLifecycleOwner.get().lifecycleScope)

        ProcessLifecycleOwner.get().lifecycleScope.launchIO {
            with(TachiyomiWidgetManager()) { this@App.init() }
        }

        // Show notification to disable Incognito Mode when it's enabled
        preferences.incognitoMode().changes()
            .onEach { enabled ->
                val notificationManager = NotificationManagerCompat.from(this)
                if (enabled) {
                    disableIncognitoReceiver.register()
                    val nContext = localeContext
                    val notification = nContext.notification(Notifications.CHANNEL_INCOGNITO_MODE) {
                        val incogText = nContext.getString(MR.strings.incognito_mode)
                        setContentTitle(incogText)
                        setContentText(nContext.getString(MR.strings.turn_off_, incogText))
                        setSmallIcon(R.drawable.ic_incognito_24dp)
                        setOngoing(true)

                        val pendingIntent = PendingIntent.getBroadcast(
                            this@App,
                            0,
                            Intent(ACTION_DISABLE_INCOGNITO_MODE),
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        setContentIntent(pendingIntent)
                    }
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return@onEach
                    }
                    notificationManager.notify(Notifications.ID_INCOGNITO_MODE, notification)
                } else {
                    disableIncognitoReceiver.unregister()
                    notificationManager.cancel(Notifications.ID_INCOGNITO_MODE)
                }
            }
            .launchIn(ProcessLifecycleOwner.get().lifecycleScope)

        initializeMigrator()
    }

    private fun initializeMigrator() {
        val preferenceStore = Injekt.get<PreferenceStore>()

        val preference = preferenceStore.getInt(
            Preference.appStateKey("last_version_code"),
            0,
        )
        // TODO: Remove later
        val old = preferenceStore.getInt("last_version_code", -1)
        if (old.get() >= preference.get()) {
            preference.set(old.get())
            old.delete()
        }

        Logger.i { "Migration from ${preference.get()} to ${BuildConfig.VERSION_CODE}" }
        Migrator.initialize(
            old = preference.get(),
            new = BuildConfig.VERSION_CODE,
            migrations = migrations,
            onMigrationComplete = {
                Logger.i { "Updating last version to ${BuildConfig.VERSION_CODE}" }
                preference.set(BuildConfig.VERSION_CODE)
            },
        )
    }

    override fun onPause(owner: LifecycleOwner) {
        if (!AuthenticatorUtil.isAuthenticating && preferences.lockAfter().get() >= 0) {
            SecureActivityDelegate.locked = true
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        LibraryPresenter.onLowMemory()
        RecentsPresenter.onLowMemory()
        SourcePresenter.onLowMemory()
    }

    protected open fun setupNotificationChannels() {
        Notifications.createChannels(this)
    }

    private inner class DisableIncognitoReceiver : BroadcastReceiver() {
        private var registered = false

        override fun onReceive(context: Context, intent: Intent) {
            preferences.incognitoMode().set(false)
        }

        fun register() {
            if (!registered) {
                ContextCompat.registerReceiver(
                    this@App,
                    this,
                    IntentFilter(ACTION_DISABLE_INCOGNITO_MODE),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                unregisterReceiver(this)
                registered = false
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(this@App).apply {
            val callFactoryLazy = lazy { Injekt.get<NetworkHelper>().client }
            val diskCacheLazy = lazy { CoilDiskCache.get(this@App) }
            components {
                add(OkHttpNetworkFetcherFactory(callFactoryLazy::value))
                add(TachiyomiImageDecoder.Factory())
                add(MangaCoverFetcher.Factory(callFactoryLazy, diskCacheLazy))
                add(MangaCoverKeyer())
                add(BufferedSourceFetcher.Factory())
            }
            diskCache(diskCacheLazy::value)
            memoryCache { MemoryCache.Builder().maxSizePercent(this@App, 0.40).build() }
            crossfade(true)
            allowRgb565(this@App.getSystemService<ActivityManager>()!!.isLowRamDevice)
            allowHardware(true)
            if (BuildConfig.DEBUG) {
                logger(DebugLogger())
            }
        }.build()
    }
}

private const val ACTION_DISABLE_INCOGNITO_MODE = "tachi.action.DISABLE_INCOGNITO_MODE"
