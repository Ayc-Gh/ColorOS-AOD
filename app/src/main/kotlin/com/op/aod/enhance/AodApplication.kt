package com.op.aod.enhance

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import com.op.aod.enhance.data.AodConfigStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.atomic.AtomicReference

class AodApplication : Application(), XposedServiceHelper.OnServiceListener {
    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        serviceRef.set(service)
        configServiceConnected.value = AodConfigStore.onXposedServiceBound(this, service)
    }

    override fun onServiceDied(service: XposedService) {
        serviceRef.compareAndSet(service, null)
        configServiceConnected.value = false
    }

    companion object {
        private val serviceRef = AtomicReference<XposedService?>(null)
        val configServiceConnected = mutableStateOf(false)

        internal fun service(): XposedService? = serviceRef.get()
    }
}
