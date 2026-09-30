package com.op.aod.enhance

import android.util.Log
import com.op.aod.enhance.hook.MainHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class ModuleEntry : XposedModule() {
    @Volatile
    private var processName: String? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        log(
            Log.INFO,
            TAG,
            "event=module_loaded process=${param.processName} api=${getApiVersion()} framework=${getFrameworkName()}",
        )
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        val pkg = param.packageName
        if (pkg != SYSTEM_UI && pkg != OPLUS_AOD) return

        val process = processName
        log(Log.INFO, TAG, "event=package_ready package=$pkg process=$process")
        runCatching {
            MainHook.install(this, param.classLoader, pkg)
        }.onFailure {
            log(
                Log.ERROR,
                TAG,
                "event=install_failed package=$pkg process=$process error=${it.stackTraceToString()}",
            )
        }
    }

    private companion object {
        const val TAG = "AOD_Enhance"
        const val SYSTEM_UI = "com.android.systemui"
        const val OPLUS_AOD = "com.oplus.aod"
    }
}
