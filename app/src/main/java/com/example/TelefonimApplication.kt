package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.agent.AgentOrchestrator
import com.example.agent.OfflineCommandEngine
import com.example.agent.PhoneCapabilities
import com.example.data.AppDatabase
import com.example.data.SettingsDataStore
import com.example.data.groq.GroqRepository

class TelefonimApplication : Application() {

    companion object {
        const val CHANNEL_ASSISTANT_ID = "telefonim_assistant_channel"
        const val CHANNEL_LOCKSCREEN_ORB_ID = "telefonim_lockscreen_orb_channel"
    }

    lateinit var settingsDataStore: SettingsDataStore
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var groqRepository: GroqRepository
        private set
    lateinit var phoneCapabilities: PhoneCapabilities
        private set
    lateinit var offlineCommandEngine: OfflineCommandEngine
        private set
    lateinit var agentOrchestrator: AgentOrchestrator
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        settingsDataStore = SettingsDataStore(this)
        database = AppDatabase.getInstance(this)
        groqRepository = GroqRepository()
        phoneCapabilities = PhoneCapabilities(this)
        offlineCommandEngine = OfflineCommandEngine(this, phoneCapabilities)
        agentOrchestrator = AgentOrchestrator(
            groqRepository = groqRepository,
            settingsDataStore = settingsDataStore,
            phoneCapabilities = phoneCapabilities,
            offlineEngine = offlineCommandEngine,
            historyDao = database.commandHistoryDao()
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                CHANNEL_ASSISTANT_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(serviceChannel)

            val lockscreenChannel = NotificationChannel(
                CHANNEL_LOCKSCREEN_ORB_ID,
                "Qulf ekrani ovozli oynasi",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Telefon qulf holatda chaqirilganda animatsiyali oynani ko'rsatish"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(lockscreenChannel)
        }
    }
}
