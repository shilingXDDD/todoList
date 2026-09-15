package com.example.todo_list.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlin.concurrent.thread

class TaskSyncService : Service() {
    companion object {
        const val ACTION_SYNC_FINISHED = "com.example.todo_list.SYNC_FINISHED"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) : Int{
        thread {
            try {
                Thread.sleep(2000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return@thread
            }

            sendBroadcast(Intent(ACTION_SYNC_FINISHED).setPackage(packageName))
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) : IBinder? = null
}