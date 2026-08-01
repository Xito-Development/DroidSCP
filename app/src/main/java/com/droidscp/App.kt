package com.droidscp

import android.app.Application
import com.droidscp.net.SshCrypto

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SshCrypto.init()
    }
}
