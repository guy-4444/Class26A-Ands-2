package com.guy.class26a_ands_2

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        MCT6.initHelper()
    }
}