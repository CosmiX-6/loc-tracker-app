package com.loctracker.app

import android.app.Application
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File

class LocTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        container.appScope.launch {
            container.sessionManager.resume()
            container.scheduler.rescheduleAll()
            container.repository.prune(container.retentionDays())
        }
    }
}
