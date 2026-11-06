package com.mazidulhaque.kokhon.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Some phone makers stop background apps unless the user allows "auto-start" in their own settings app,
 * which silently kills reminders. There is no API to check it, so Kokhon opens the right screen.
 */
object AutoStart {
    private val screens: Map<String, List<ComponentName>> =
        mapOf(
            "xiaomi" to listOf(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            "oppo" to
                listOf(
                    ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                    ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                ),
            "vivo" to
                listOf(
                    ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                ),
            "huawei" to
                listOf(
                    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                ),
            "oneplus" to listOf(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
            "samsung" to listOf(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
        )

    private val aliases = mapOf("redmi" to "xiaomi", "poco" to "xiaomi", "realme" to "oppo", "honor" to "huawei", "iqoo" to "vivo")

    private fun maker(): String? {
        val name = Build.MANUFACTURER.lowercase()
        val key = aliases[name] ?: name
        return key.takeIf { it in screens }
    }

    /** The phone maker's name when its phones are known to need this, otherwise null. */
    fun makerName(): String? = maker()?.let { Build.MANUFACTURER.replaceFirstChar { c -> c.uppercase() } }

    fun open(context: Context) {
        val candidates =
            screens[maker()].orEmpty().map { Intent().setComponent(it) } +
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        for (intent in candidates) {
            val opened = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (opened) return
        }
    }
}
