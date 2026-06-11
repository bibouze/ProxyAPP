package com.example.proxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d("BootReceiver", "Reçu broadcast action : $action")
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON" || 
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val serviceIntent = Intent(context, ForegroundProxyService::class.java).apply {
                this.action = ForegroundProxyService.ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d("BootReceiver", "ForegroundProxyService démarré avec succès au boot")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Erreur lors du démarrage du service de proxy au boot", e)
            }
        }
    }
}
