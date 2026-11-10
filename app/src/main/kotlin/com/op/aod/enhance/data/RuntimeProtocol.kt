package com.op.aod.enhance.data

import android.app.BroadcastOptions
import android.content.Context
import android.content.Intent
import android.os.Build

internal object RuntimeProtocol {
    const val APP = "com.op.aod.enhance"
    const val PREFS = "aod_runtime_status"
    const val NONCE = "request_nonce"
    const val QUERY = "$APP.QUERY_RUNTIME"
    const val REPLY = "$APP.REPLY_RUNTIME"
    val hosts = listOf("com.android.systemui", "com.oplus.aod")
    fun send(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) context.sendBroadcast(intent, null,
            BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle())
        else context.sendBroadcast(intent)
    }
}
