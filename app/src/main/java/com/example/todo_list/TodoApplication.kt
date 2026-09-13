package com.example.todo_list

import android.app.Application
import android.content.Context

class TodoApplication : Application() {
    companion object {
        lateinit var instance: TodoApplication
            private set

        val context: Context
            get() = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
