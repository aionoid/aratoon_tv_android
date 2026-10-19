package com.aionoid.aratoons

import android.app.Application
import com.aionoid.aratoons.data.StreamResolver
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class App : Application() {

    lateinit var http: OkHttpClient
        private set

    lateinit var resolver: StreamResolver
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        resolver = StreamResolver(http)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
