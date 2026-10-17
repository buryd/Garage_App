package com.garagedoor.monitor

import android.app.Application
import com.garagedoor.monitor.data.DoorNotifier
import com.garagedoor.monitor.data.DoorRepository

class GarageApplication : Application() {
    lateinit var repository: DoorRepository
        private set

    override fun onCreate() {
        super.onCreate()
        DoorNotifier(this).ensureChannels()
        repository = DoorRepository(this)
    }
}
